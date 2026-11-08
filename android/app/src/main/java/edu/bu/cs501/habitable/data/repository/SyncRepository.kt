package edu.bu.cs501.habitable.data.repository

import edu.bu.cs501.habitable.data.local.HabitableDatabase
import edu.bu.cs501.habitable.data.local.toDomain
import edu.bu.cs501.habitable.data.remote.backend.AuthRequest
import edu.bu.cs501.habitable.data.remote.backend.HabitableApi
import edu.bu.cs501.habitable.data.remote.backend.ReadingDto
import edu.bu.cs501.habitable.data.remote.backend.ReadingsBatch
import edu.bu.cs501.habitable.data.remote.backend.toDto
import edu.bu.cs501.habitable.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Pushes unsynced readings to the Habitable backend in batches, then backs up
 * the issue list (category, title, status, dates; never descriptions, photos or
 * the address). Works offline-first: if the network is down the rows simply
 * stay unsynced and the next run picks them up.
 */
class SyncRepository(
    private val db: HabitableDatabase,
    private val api: HabitableApi,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val readings = db.readingDao()
    private val sensors = db.sensorDao()
    private val issues = db.issueDao()

    sealed interface Result {
        data class Synced(val count: Int) : Result
        data object NothingToSync : Result
        data object NotSignedIn : Result
        data class Failed(val message: String) : Result
    }

    suspend fun signInOrRegister(email: String, password: String): Result {
        return try {
            val res = try {
                api.login(AuthRequest(email, password))
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401 || e.code() == 404) api.register(AuthRequest(email, password, name = email.substringBefore('@'))) else throw e
            }
            settings.setAuth(res.token, email)
            Result.Synced(0)
        } catch (e: IOException) {
            Result.Failed("Backend unreachable")
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Sign-in failed")
        }
    }

    suspend fun syncReadings(batchSize: Int = 500): Result {
        val token = settings.settings.first().authToken ?: return Result.NotSignedIn
        val pending = readings.getUnsynced(batchSize)
        if (pending.isEmpty()) return Result.NothingToSync

        val sensorAddress = sensors.getFirst()?.bleAddress ?: "SIM"
        val batch = ReadingsBatch(
            pending.map { e ->
                val d = e.toDomain()
                ReadingDto(
                    clientId = d.id,
                    sensorAddress = sensorAddress,
                    tempF = d.tempF,
                    humidityPct = d.humidityPct,
                    recordedAt = d.recordedAtMillis,
                    flags = d.flags.map { it.name },
                )
            }
        )
        return try {
            val ack = api.uploadReadings("Bearer $token", batch)
            readings.markSynced(ack.clientIds)
            settings.setLastSync(clock())
            Result.Synced(ack.accepted)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) {
                settings.setAuth(null, null)
                Result.NotSignedIn
            } else Result.Failed("Server error ${e.code()}")
        } catch (e: IOException) {
            Result.Failed("Backend unreachable")
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Sync failed")
        }
    }

    /**
     * Back up every issue with PUT /issues. The server upserts by clientId, so
     * sending an unchanged issue again is harmless; a status change (e.g. after
     * Send notice) simply overwrites the server copy.
     */
    suspend fun syncIssues(): Result {
        val token = settings.settings.first().authToken ?: return Result.NotSignedIn
        val all = issues.getAll()
        if (all.isEmpty()) return Result.NothingToSync
        return try {
            all.forEach { api.upsertIssue("Bearer $token", it.toDomain().toDto()) }
            Result.Synced(all.size)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) {
                settings.setAuth(null, null)
                Result.NotSignedIn
            } else Result.Failed("Server error ${e.code()}")
        } catch (e: IOException) {
            Result.Failed("Backend unreachable")
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Issue sync failed")
        }
    }
}
