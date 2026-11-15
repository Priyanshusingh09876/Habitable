package edu.bu.cs501.habitable.data.remote.openmeteo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Open-Meteo hourly forecast + recent past. No API key required.
 * https://open-meteo.com/en/docs
 *
 * Example:
 * GET /v1/forecast?latitude=42.35&longitude=-71.06&hourly=temperature_2m
 *     &temperature_unit=fahrenheit&timezone=America%2FNew_York&past_days=2&forecast_days=1
 */
interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun hourlyTemperature(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m",
        @Query("temperature_unit") unit: String = "fahrenheit",
        @Query("timezone") timezone: String = "America/New_York",
        @Query("past_days") pastDays: Int = 2,
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("timeformat") timeFormat: String = "unixtime",
    ): OpenMeteoResponse

    /**
     * Open-Meteo's geocoding service: place name -> coordinates. It lives on a
     * different host from the forecast, so the full URL is passed with @Url.
     *
     * Example: GET https://geocoding-api.open-meteo.com/v1/search?name=Boston&count=5&countryCode=US
     */
    @GET
    suspend fun searchPlace(
        @Url url: String = GEOCODING_URL,
        @Query("name") name: String,
        @Query("count") count: Int = 5,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
        @Query("countryCode") countryCode: String = "US",
    ): GeocodingResponse

    companion object {
        const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
    }
}

/** `results` is missing altogether when nothing matches, hence the default. */
@Serializable
data class GeocodingResponse(val results: List<GeocodingResult> = emptyList())

@Serializable
data class GeocodingResult(
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /** State or first-level region, e.g. "Massachusetts". */
    val admin1: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
)

@Serializable
data class OpenMeteoResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timezone: String = "",
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val hourly: Hourly = Hourly(),
)

@Serializable
data class Hourly(
    /** Unix seconds (because we pass timeformat=unixtime). */
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature2m: List<Double?> = emptyList(),
)
