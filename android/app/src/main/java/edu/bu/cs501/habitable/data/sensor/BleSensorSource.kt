package edu.bu.cs501.habitable.data.sensor

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads a Govee H5075 thermometer without connecting to it: the device
 * broadcasts temperature, humidity and battery in its Bluetooth advertisement
 * every couple of seconds, and we listen.
 *
 * - One sample is emitted at most every [minIntervalMillis] (default 1 minute),
 *   so a night of logging is about a thousand rows, not tens of thousands.
 * - The scan is filtered to the paired address and uses low-power mode.
 * - Android quietly downgrades a scan that runs longer than 30 minutes, so the
 *   scan is restarted every [RESTART_MILLIS].
 * - If the phone is not ready (permission, Bluetooth off, Location off) the flow
 *   ends with a readable message instead of crashing; see [BleSupport.problem].
 */
class BleSensorSource(
    private val context: Context,
    address: String,
    override val model: String = MODEL,
    private val minIntervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) : SensorSource {

    override val address: String = address.trim().uppercase()

    @SuppressLint("MissingPermission") // checked by BleSupport.problem() before every start
    override fun samples(): Flow<SensorSample> = callbackFlow {
        BleSupport.problem(context)?.let { reason ->
            close(IllegalStateException(reason))
            return@callbackFlow
        }
        val scanner = BleSupport.scanner(context)
        if (scanner == null) {
            close(IllegalStateException("Turn on Bluetooth to read the thermometer."))
            return@callbackFlow
        }
        val filters = try {
            listOf(ScanFilter.Builder().setDeviceAddress(this@BleSensorSource.address).build())
        } catch (e: IllegalArgumentException) {
            close(IllegalArgumentException("Not a valid Bluetooth address: ${this@BleSensorSource.address}"))
            return@callbackFlow
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()

        var lastEmitAt = 0L
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val payload = result.scanRecord?.getManufacturerSpecificData(GOVEE_COMPANY_ID) ?: return
                val decoded = decodeGovee(payload) ?: return
                val now = clock()
                if (now - lastEmitAt < minIntervalMillis) return // throttle: the sensor talks every ~2 s
                lastEmitAt = now
                trySend(SensorSample(tempF = decoded.first, humidityPct = decoded.second, recordedAtMillis = now, simulated = false))
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Bluetooth scan failed (code $errorCode). Turn Bluetooth off and on, then start again."))
            }
        }

        fun start(): Boolean = try {
            scanner.startScan(filters, settings, callback)
            true
        } catch (e: SecurityException) {
            close(IllegalStateException("Bluetooth permission was removed. Allow it and start again."))
            false
        } catch (e: IllegalStateException) { // Bluetooth was switched off
            close(IllegalStateException("Turn on Bluetooth to read the thermometer."))
            false
        }

        fun stop() {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Permission revoked while scanning; nothing more to do.
            } catch (_: IllegalStateException) {
                // Bluetooth switched off while scanning; the scan is already gone.
            }
        }

        if (!start()) return@callbackFlow
        Log.d(TAG, "BLE scan started for ${this@BleSensorSource.address}")

        val restarter = launch {
            while (isActive) {
                delay(RESTART_MILLIS)
                stop()
                if (!start()) break
            }
        }

        awaitClose {
            restarter.cancel()
            stop()
            Log.d(TAG, "BLE scan stopped")
        }
    }

    companion object {
        private const val TAG = "BleSensorSource"

        const val MODEL = "Govee H5075"

        /** Govee's Bluetooth SIG company identifier. */
        const val GOVEE_COMPANY_ID = 0xEC88

        /** One stored reading a minute: fine enough for a heat record, light on storage and battery. */
        const val DEFAULT_INTERVAL_MILLIS = 60_000L

        /** Restart well inside Android's 30-minute limit for long-running scans. */
        const val RESTART_MILLIS = 20 * 60_000L

        /**
         * Govee H5075 packs temperature and humidity into a 3-byte big-endian
         * integer after one padding byte: value / 10000 = °C, (value % 1000) / 10 = RH%.
         * Negative temperatures set the top bit. Returns (tempF, humidity) or null.
         *
         * Example payload `00 03 2B 0F 64 00`: 0x032B0F = 207631 -> 20.7 °C (69.3 °F), 63.1 % RH, battery 100 %.
         */
        fun decodeGovee(payload: ByteArray): Pair<Double, Double>? {
            if (payload.size < 4) return null
            val buf = ByteBuffer.wrap(byteArrayOf(0, payload[1], payload[2], payload[3])).order(ByteOrder.BIG_ENDIAN)
            var raw = buf.int
            val negative = raw and 0x800000 != 0
            if (negative) raw = raw and 0x7FFFFF
            // Temperature is encoded with one decimal: raw / 1000 gives tenths of a degree.
            val tempC = (raw / 1000) / 10.0 * (if (negative) -1 else 1)
            val humidity = (raw % 1000) / 10.0
            val tempF = tempC * 9.0 / 5.0 + 32.0
            if (tempF < -40 || tempF > 150) return null
            return Math.round(tempF * 10.0) / 10.0 to humidity
        }

        /** Battery percentage from the fifth payload byte, or null if the packet is too short or out of range. */
        fun decodeBattery(payload: ByteArray): Int? {
            if (payload.size < 5) return null
            val pct = payload[4].toInt() and 0xFF
            return pct.takeIf { it in 0..100 }
        }
    }
}
