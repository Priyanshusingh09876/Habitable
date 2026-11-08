package edu.bu.cs501.habitable.data.remote.backend

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * The thin Habitable backend (backend/ in this repo). JWT in the Authorization
 * header. Only readings and photo hashes ever go up; photos stay on the phone.
 */
interface HabitableApi {

    @GET("health")
    suspend fun health(): HealthResponse

    @POST("auth/register")
    suspend fun register(@Body body: AuthRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: AuthRequest): AuthResponse

    @POST("readings")
    suspend fun uploadReadings(
        @Header("Authorization") bearer: String,
        @Body body: ReadingsBatch,
    ): ReadingsAck

    /** Upsert by clientId (the Room id), so re-sending the same issue never duplicates it. */
    @PUT("issues")
    suspend fun upsertIssue(
        @Header("Authorization") bearer: String,
        @Body body: IssueDto,
    ): IssueAck

    @POST("evidence/anchor")
    suspend fun anchor(
        @Header("Authorization") bearer: String,
        @Body body: AnchorRequest,
    ): AnchorResponse
}

@Serializable data class HealthResponse(val ok: Boolean, val service: String = "", val time: String = "")

@Serializable data class AuthRequest(val email: String, val password: String, val name: String? = null)
@Serializable data class AuthResponse(val token: String, val userId: Long)

@Serializable
data class ReadingDto(
    val clientId: Long,
    val sensorAddress: String,
    val tempF: Double,
    val humidityPct: Double?,
    val recordedAt: Long,
    val flags: List<String>,
)

@Serializable data class ReadingsBatch(val readings: List<ReadingDto>)
@Serializable data class ReadingsAck(val accepted: Int, val clientIds: List<Long>)

@Serializable
data class IssueDto(
    val clientId: Long,
    val category: String,
    val title: String,
    val status: String,
    val openedAt: Long,
    val closedAt: Long? = null,
)

@Serializable data class IssueAck(val id: Long)

@Serializable data class AnchorRequest(val sha256: String, val kind: String, val capturedAt: Long)
@Serializable data class AnchorResponse(val sha256: String, val serverTimestamp: Long)
