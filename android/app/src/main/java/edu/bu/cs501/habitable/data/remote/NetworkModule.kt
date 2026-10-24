package edu.bu.cs501.habitable.data.remote

import edu.bu.cs501.habitable.BuildConfig
import edu.bu.cs501.habitable.data.remote.backend.HabitableApi
import edu.bu.cs501.habitable.data.remote.openmeteo.OpenMeteoApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Builds the two Retrofit clients. Hand-rolled DI; see di/AppContainer.kt. */
object NetworkModule {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    fun openMeteo(client: OkHttpClient = okHttp()): OpenMeteoApi = Retrofit.Builder()
        .baseUrl(BuildConfig.OPEN_METEO_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OpenMeteoApi::class.java)

    fun habitable(client: OkHttpClient = okHttp()): HabitableApi = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(HabitableApi::class.java)
}
