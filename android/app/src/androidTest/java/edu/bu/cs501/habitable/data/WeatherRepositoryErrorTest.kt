package edu.bu.cs501.habitable.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.data.local.HabitableDatabase
import edu.bu.cs501.habitable.data.remote.openmeteo.GeocodingResponse
import edu.bu.cs501.habitable.data.remote.openmeteo.GeocodingResult
import edu.bu.cs501.habitable.data.remote.openmeteo.Hourly
import edu.bu.cs501.habitable.data.remote.openmeteo.OpenMeteoApi
import edu.bu.cs501.habitable.data.remote.openmeteo.OpenMeteoResponse
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.repository.WeatherRepository
import edu.bu.cs501.habitable.data.repository.WeatherStatus
import edu.bu.cs501.habitable.domain.model.Apartment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Integration test with forced errors: a real (in-memory) Room cache plus a fake
 * Open-Meteo that we switch to "offline" or "server error" on purpose. Checks the
 * app degrades the way the Failure-states table in UPDATE2.md promises.
 */
@RunWith(AndroidJUnit4::class)
class WeatherRepositoryErrorTest {

    /** Stands in for the real web service; [fail] makes every call throw. */
    private class FakeOpenMeteo(private val now: Long) : OpenMeteoApi {
        var fail: Exception? = null
        var calls = 0
        var hourly = Hourly(
            time = listOf(now / 1000 - 3600, now / 1000),
            temperature2m = listOf(31.0, 29.5),
        )

        override suspend fun hourlyTemperature(
            latitude: Double, longitude: Double, hourly: String, unit: String,
            timezone: String, pastDays: Int, forecastDays: Int, timeFormat: String,
        ): OpenMeteoResponse {
            calls++
            fail?.let { throw it }
            return OpenMeteoResponse(hourly = this.hourly)
        }

        override suspend fun searchPlace(
            url: String, name: String, count: Int, language: String, format: String, countryCode: String,
        ): GeocodingResponse {
            calls++
            fail?.let { throw it }
            return GeocodingResponse(listOf(GeocodingResult(name = "Boston", latitude = 42.36, longitude = -71.06, admin1 = "Massachusetts", countryCode = "US")))
        }
    }

    private val now = 1_794_322_800_000L // 2026-11-10 10:00 America/New_York
    private lateinit var db: HabitableDatabase
    private lateinit var api: FakeOpenMeteo
    private lateinit var weather: WeatherRepository
    private var aptId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HabitableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = FakeOpenMeteo(now)
        weather = WeatherRepository(db, api, clock = { now })
        aptId = ApartmentRepository(db).save(
            Apartment(address = "12 Pratt St", unit = "3", city = "Boston", latitude = 42.36, longitude = -71.06, landlordName = "Mark Rivera", landlordEmail = "landlord@example.com")
        )
    }

    @After
    fun tearDown() = db.close() // a fresh database for every test

    @Test
    fun offline_withNothingCached_reportsErrorWithoutCache() = runTest {
        api.fail = IOException("airplane mode")
        val status = weather.refresh(aptId, 42.36, -71.06)
        assertThat(status).isEqualTo(WeatherStatus.Error("No connection to Open-Meteo", hasCache = false))
        assertThat(weather.status.value).isEqualTo(status)
    }

    @Test
    fun offline_afterASuccessfulFetch_keepsShowingTheCachedHours() = runTest {
        assertThat(weather.refresh(aptId, 42.36, -71.06)).isInstanceOf(WeatherStatus.Fresh::class.java)

        api.fail = IOException("airplane mode")
        val status = weather.refresh(aptId, 42.36, -71.06)

        assertThat(status).isEqualTo(WeatherStatus.Error("No connection to Open-Meteo", hasCache = true))
        val cached = weather.observeOutdoor(aptId, now - 6 * 3_600_000L, now + 3_600_000L).first()
        assertThat(cached.map { it.tempF }).containsExactly(31.0, 29.5).inOrder()
    }

    @Test
    fun serverError_isReportedWithItsMessage_notCrashing() = runTest {
        api.fail = IllegalStateException("HTTP 503 Service Unavailable")
        val status = weather.refresh(aptId, 42.36, -71.06)
        assertThat(status).isEqualTo(WeatherStatus.Error("HTTP 503 Service Unavailable", hasCache = false))
    }

    @Test
    fun emptyResponse_isAnError_notAnEmptyChart() = runTest {
        api.hourly = Hourly(time = emptyList(), temperature2m = emptyList())
        val status = weather.refresh(aptId, 42.36, -71.06)
        assertThat(status).isEqualTo(WeatherStatus.Error("Weather service returned no data", hasCache = false))
    }

    @Test
    fun missingHourlyValues_areSkipped() = runTest {
        api.hourly = Hourly(time = listOf(now / 1000 - 3600, now / 1000), temperature2m = listOf(null, 30.0))
        val status = weather.refresh(aptId, 42.36, -71.06)
        assertThat(status).isEqualTo(WeatherStatus.Fresh(fetchedAtMillis = now, hours = 1))
    }

    @Test
    fun cityLookup_offline_returnsNull_soSetupStillSaves() = runTest {
        api.fail = IOException("no network")
        assertThat(weather.findCity("Boston")).isNull()
    }

    @Test
    fun cityLookup_oneLetter_isNotSentToTheServer() = runTest {
        assertThat(weather.findCity("B")).isNull()
        assertThat(api.calls).isEqualTo(0)
    }
}
