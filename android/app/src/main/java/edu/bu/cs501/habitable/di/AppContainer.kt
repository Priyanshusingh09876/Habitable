package edu.bu.cs501.habitable.di

import android.content.Context
import edu.bu.cs501.habitable.data.local.HabitableDatabase
import edu.bu.cs501.habitable.data.remote.NetworkModule
import edu.bu.cs501.habitable.data.remote.backend.HabitableApi
import edu.bu.cs501.habitable.data.remote.openmeteo.OpenMeteoApi
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.repository.IssuesRepository
import edu.bu.cs501.habitable.data.repository.ReadingsRepository
import edu.bu.cs501.habitable.data.repository.SyncRepository
import edu.bu.cs501.habitable.data.repository.WeatherRepository
import edu.bu.cs501.habitable.data.settings.SettingsRepository
import edu.bu.cs501.habitable.domain.quality.DataQualityChecker
import edu.bu.cs501.habitable.domain.rules.ViolationDetector

/**
 * Manual dependency container. For a two-person project this is clearer than
 * Hilt and keeps the build fast; every dependency boundary is still explicit
 * and every repository can be swapped in tests (see androidTest).
 */
interface AppContainer {
    val database: HabitableDatabase
    val openMeteoApi: OpenMeteoApi
    val habitableApi: HabitableApi
    val settings: SettingsRepository
    val apartments: ApartmentRepository
    val readings: ReadingsRepository
    val weather: WeatherRepository
    val issues: IssuesRepository
    val sync: SyncRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val database: HabitableDatabase by lazy { HabitableDatabase.get(appContext) }

    private val okHttp by lazy { NetworkModule.okHttp() }
    override val openMeteoApi: OpenMeteoApi by lazy { NetworkModule.openMeteo(okHttp) }
    override val habitableApi: HabitableApi by lazy { NetworkModule.habitable(okHttp) }

    override val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    override val apartments: ApartmentRepository by lazy { ApartmentRepository(database) }
    override val readings: ReadingsRepository by lazy {
        ReadingsRepository(database, quality = DataQualityChecker(), detector = ViolationDetector())
    }
    override val weather: WeatherRepository by lazy { WeatherRepository(database, openMeteoApi) }
    override val issues: IssuesRepository by lazy { IssuesRepository(database) }
    override val sync: SyncRepository by lazy { SyncRepository(database, habitableApi, settings) }
}
