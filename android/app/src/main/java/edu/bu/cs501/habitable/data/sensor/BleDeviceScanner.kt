package edu.bu.cs501.habitable.data.sensor

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A thermometer seen during a scan, with the reading it is broadcasting right now. */
data class FoundSensor(
    val address: String,
    val name: String,
    val tempF: Double,
    val humidityPct: Double,
    val batteryPct: Int?,
    /** Signal strength in dBm; closer to 0 is nearer. */
    val rssi: Int,
)

/**
 * Finds Govee thermometers nearby so the user can pick theirs from a list
 * instead of typing a Bluetooth address. Used only while the picker is open
 * (the app is in the foreground), so it scans fast and without a filter.
 */
class BleDeviceScanner(private val context: Context) {

    /** Null when the phone is ready to scan; otherwise what to fix, in plain words. */
    fun problem(): String? = BleSupport.problem(context)

    /** Emits the list of thermometers found so far, nearest first, each time one is heard. */
    @SuppressLint("MissingPermission") // checked by problem() first
    fun nearby(): Flow<List<FoundSensor>> = callbackFlow {
        problem()?.let { reason ->
            close(IllegalStateException(reason))
            return@callbackFlow
        }
        val scanner = BleSupport.scanner(context)
        if (scanner == null) {
            close(IllegalStateException("Turn on Bluetooth to find the thermometer."))
            return@callbackFlow
        }

        val found = LinkedHashMap<String, FoundSensor>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val name = record.deviceName
                // Other Govee models use the same company id with a different layout; only list the ones we can decode.
                if (name != null && !SUPPORTED_NAME_PREFIXES.any { name.startsWith(it) }) return
                val payload = record.getManufacturerSpecificData(BleSensorSource.GOVEE_COMPANY_ID) ?: return
                val decoded = BleSensorSource.decodeGovee(payload) ?: return
                val address = result.device.address ?: return
                found[address] = FoundSensor(
                    address = address,
                    name = name ?: "Govee thermometer",
                    tempF = decoded.first,
                    humidityPct = decoded.second,
                    batteryPct = BleSensorSource.decodeBattery(payload),
                    rssi = result.rssi,
                )
                trySend(found.values.sortedByDescending { it.rssi })
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Bluetooth scan failed (code $errorCode). Turn Bluetooth off and on, then try again."))
            }
        }

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, callback)
        } catch (e: SecurityException) {
            close(IllegalStateException("Allow Nearby devices and precise Location so Habitable can find the thermometer."))
            return@callbackFlow
        } catch (e: IllegalStateException) {
            close(IllegalStateException("Turn on Bluetooth to find the thermometer."))
            return@callbackFlow
        }

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
            } catch (_: IllegalStateException) {
            }
        }
    }

    companion object {
        /** H5075 and its sibling H5072 share the packet layout BleSensorSource decodes. */
        val SUPPORTED_NAME_PREFIXES = listOf("GVH5075", "GVH5072")
    }
}
