package edu.bu.cs501.habitable.data.sensor

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
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
 * Battery: the expensive part of Bluetooth scanning is not the radio but waking
 * the phone's processor for every broadcast (about every 2 s, all night). So:
 *  - The scan is filtered to the paired address in the Bluetooth chip, and runs
 *    in low-power mode.
 *  - **Batched scanning.** On phones whose Bluetooth chip supports it (most from
 *    the last several years) the chip collects matching broadcasts on its own and
 *    hands them over once per [intervalMillis]. The processor can stay asleep in
 *    between, and we keep only the newest reading of each batch.
 *  - Without batching, results arrive one by one and are throttled to one stored
 *    reading per [intervalMillis].
 *  - The interval is the user's choice (1, 5 or 10 minutes; see SensorInterval).
 *
 * Android quietly downgrades a scan that runs longer than 30 minutes, so the scan
 * is restarted every [RESTART_MILLIS], flushing any pending batch first.
 * If the phone is not ready (permission, Bluetooth off, Location off) the flow
 * ends with a readable message instead of crashing; see [BleSupport.problem].
 */
class BleSensorSource(
    private val context: Context,
    address: String,
    override val model: String = MODEL,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) : SensorSource {

    override val address: String = address.trim().uppercase()

    /** True when this phone's Bluetooth chip can batch results itself (checked once per start). */
    var usesBatching: Boolean = false
        private set

    @SuppressLint("MissingPermission") // checked by BleSupport.problem() before every start
    override fun samples(): Flow<SensorSample> = callbackFlow {
        BleSupport.problem(context)?.let { reason ->
            close(IllegalStateException(reason))
            return@callbackFlow
        }
        val adapter = BleSupport.adapter(context)
        val scanner = adapter?.bluetoothLeScanner
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
        usesBatching = adapter.isOffloadedScanBatchingSupported
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .apply { if (usesBatching) setReportDelay(intervalMillis) }
            .build()

        var lastEmitAt = 0L
        /** Stores [result] if it is a Govee reading and at least most of an interval after the last one. */
        fun offer(result: ScanResult, recordedAt: Long): Boolean {
            val payload = result.scanRecord?.getManufacturerSpecificData(GOVEE_COMPANY_ID) ?: return false
            val decoded = decodeGovee(payload) ?: return false
            // 80 %: a batch that arrives a few seconds early must not be dropped.
            if (recordedAt - lastEmitAt < intervalMillis * 8 / 10) return false
            lastEmitAt = recordedAt
            trySend(SensorSample(tempF = decoded.first, humidityPct = decoded.second, recordedAtMillis = recordedAt, simulated = false))
            return true
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                offer(result, clock())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                // Newest first: keep one reading per batch, stamped with when it was actually heard.
                val now = clock()
                val nowNanos = SystemClock.elapsedRealtimeNanos()
                for (r in results.sortedByDescending { it.timestampNanos }) {
                    val heardAt = now - (nowNanos - r.timestampNanos).coerceAtLeast(0L) / 1_000_000L
                    if (offer(r, heardAt)) break
                }
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
                if (usesBatching) scanner.flushPendingScanResults(callback) // deliver what the chip is holding
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Permission revoked while scanning; nothing more to do.
            } catch (_: IllegalStateException) {
                // Bluetooth switched off while scanning; the scan is already gone.
            }
        }

        if (!start()) return@callbackFlow
        Log.d(TAG, "BLE scan started for ${this@BleSensorSource.address}, every ${intervalMillis / 1000} s, batching=$usesBatching")

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

        /** Five minutes: the "Balanced" choice. See SensorInterval for the others. */
        const val DEFAULT_INTERVAL_MILLIS = 5 * 60_000L

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
