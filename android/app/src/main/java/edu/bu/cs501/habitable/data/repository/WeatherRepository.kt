package edu.bu.cs501.habitable.data.repository

import edu.bu.cs501.habitable.data.local.HabitableDatabase
import edu.bu.cs501.habitable.data.local.entity.OutdoorReadingEntity
import edu.bu.cs501.habitable.data.local.toDomain
import edu.bu.cs501.habitable.data.remote.openmeteo.OpenMeteoApi
import edu.bu.cs501.habitable.domain.model.OutdoorReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Open-Meteo is the required third-party API. We fetch hourly outdoor
 * temperature for the apartment and cache it in Room so the indoor chart keeps
 * its outdoor line when the service is down or the phone is offline.
 *
 * [status] drives the loading / error / stale UI states. The data itself is
 * always read from Room, never straight from the network response, which keeps
 * the chart code identical online and offline.
 */
class WeatherRepository(
    private val db: HabitableDatabase,
    private val api: OpenMeteoApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val outdoor = db.outdoorDao()

    private val _status = MutableStateFlow<WeatherStatus>(WeatherStatus.Idle)
    val status: StateFlow<WeatherStatus> = _status.asStateFlow()

    fun observeOutdoor(apartmentId: Long, fromMillis: Long, toMillis: Long): Flow<List<OutdoorReading>> =
        outdoor.observeBetween(apartmentId, fromMillis, toMillis).map { list -> list.map { it.toDomain() } }

    fun observeLastFetched(apartmentId: Long): Flow<Long?> = outdoor.observeLastFetched(apartmentId)

    /**
     * Refresh the cache. Never throws: failures become [WeatherStatus.Error]
     * and the cached rows stay available.
     */
    suspend fun refresh(apartmentId: Long, latitude: Double, longitude: Double): WeatherStatus {
        _status.value = WeatherStatus.Loading
        val result: WeatherStatus = try {
            val response = api.hourlyTemperature(latitude = latitude, longitude = longitude)
            val now = clock()
            val rows = response.hourly.time.zip(response.hourly.temperature2m)
                .mapNotNull { (sec, temp) ->
                    temp?.let { OutdoorReadingEntity(apartmentId = apartmentId, timeMillis = sec * 1000L, tempF = it, fetchedAt = now) }
                }
            if (rows.isEmpty()) {
                WeatherStatus.Error("Weather service returned no data", hasCache = hasCache(apartmentId))
            } else {
                outdoor.upsertAll(rows)
                outdoor.pruneBefore(now - KEEP_MILLIS)
                WeatherStatus.Fresh(fetchedAtMillis = now, hours = rows.size)
            }
        } catch (e: IOException) {
            WeatherStatus.Error("No connection to Open-Meteo", hasCache = hasCache(apartmentId))
        } catch (e: Exception) {
            WeatherStatus.Error(e.message ?: "Weather request failed", hasCache = hasCache(apartmentId))
        }
        _status.value = result
        return result
    }

    private suspend fun hasCache(apartmentId: Long): Boolean =
        outdoor.observeBetween(apartmentId, clock() - 48 * 3_600_000L, clock() + 24 * 3_600_000L)
            .first()
            .isNotEmpty()

    companion object {
        /** Keep three days of hourly rows; enough for the 7-day chart's recent end plus the demo night. */
        const val KEEP_MILLIS = 3 * 24 * 3_600_000L
    }
}

sealed interface WeatherStatus {
    data object Idle : WeatherStatus
    data object Loading : WeatherStatus
    data class Fresh(val fetchedAtMillis: Long, val hours: Int) : WeatherStatus
    data class Error(val message: String, val hasCache: Boolean) : WeatherStatus
}
