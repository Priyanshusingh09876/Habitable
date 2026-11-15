package edu.bu.cs501.habitable.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import edu.bu.cs501.habitable.BuildConfig
import edu.bu.cs501.habitable.data.sensor.SimulatedSensorSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "habitable_settings")

/**
 * Small, non-relational settings: sensor mode, whether monitoring is on,
 * auth token for the backend, last sync time. DataStore rather than Room
 * because none of this is tabular.
 *
 * The auth token is the only secret stored on the device. It is a short-lived
 * JWT issued by our own backend, never a third-party key.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SIMULATED = booleanPreferencesKey("simulated_sensor")
        val MONITORING = booleanPreferencesKey("monitoring_enabled")
        val BLE_ADDRESS = stringPreferencesKey("ble_address")
        val BLE_NAME = stringPreferencesKey("ble_name")
        val SENSOR_ERROR = stringPreferencesKey("sensor_error")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val LAST_SYNC = longPreferencesKey("last_sync_millis")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            simulatedSensor = p[Keys.SIMULATED] ?: BuildConfig.SIMULATED_SENSOR_DEFAULT,
            monitoringEnabled = p[Keys.MONITORING] ?: false,
            bleAddress = p[Keys.BLE_ADDRESS],
            bleName = p[Keys.BLE_NAME],
            sensorError = p[Keys.SENSOR_ERROR],
            authToken = p[Keys.AUTH_TOKEN],
            userEmail = p[Keys.USER_EMAIL],
            lastSyncMillis = p[Keys.LAST_SYNC],
            onboarded = p[Keys.ONBOARDED] ?: false,
        )
    }

    suspend fun setSimulatedSensor(simulated: Boolean) = context.dataStore.edit { it[Keys.SIMULATED] = simulated }
    suspend fun setMonitoring(enabled: Boolean) = context.dataStore.edit { it[Keys.MONITORING] = enabled }
    /** Saves the paired thermometer. Addresses are stored upper-case, the form Android's scan filter needs. */
    suspend fun setBleAddress(address: String?, name: String? = null) = context.dataStore.edit {
        if (address == null) it.remove(Keys.BLE_ADDRESS) else it[Keys.BLE_ADDRESS] = address.trim().uppercase()
        if (address == null || name == null) it.remove(Keys.BLE_NAME) else it[Keys.BLE_NAME] = name
    }

    /** Why monitoring stopped by itself (Bluetooth off, permission missing…); null clears it. */
    suspend fun setSensorError(message: String?) = context.dataStore.edit {
        if (message == null) it.remove(Keys.SENSOR_ERROR) else it[Keys.SENSOR_ERROR] = message
    }
    suspend fun setAuth(token: String?, email: String?) = context.dataStore.edit {
        if (token == null) it.remove(Keys.AUTH_TOKEN) else it[Keys.AUTH_TOKEN] = token
        if (email == null) it.remove(Keys.USER_EMAIL) else it[Keys.USER_EMAIL] = email
    }
    suspend fun setLastSync(millis: Long) = context.dataStore.edit { it[Keys.LAST_SYNC] = millis }
    suspend fun setOnboarded(done: Boolean) = context.dataStore.edit { it[Keys.ONBOARDED] = done }
}

data class Settings(
    val simulatedSensor: Boolean,
    val monitoringEnabled: Boolean,
    val bleAddress: String?,
    val bleName: String? = null,
    val sensorError: String? = null,
    val authToken: String?,
    val userEmail: String?,
    val lastSyncMillis: Long?,
    val onboarded: Boolean,
)

/**
 * The sensor the app is currently using: the simulator, or the paired Bluetooth
 * thermometer once one is chosen. The service records under this address and the
 * screens show the readings stored under it.
 */
val Settings.activeSensorAddress: String
    get() = if (simulatedSensor || bleAddress.isNullOrBlank()) SimulatedSensorSource.ADDRESS else bleAddress

/** True when a real thermometer is selected and will be used by the service. */
val Settings.usesBluetooth: Boolean
    get() = !simulatedSensor && !bleAddress.isNullOrBlank()
