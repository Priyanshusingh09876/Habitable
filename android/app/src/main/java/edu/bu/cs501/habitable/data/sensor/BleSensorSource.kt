package edu.bu.cs501.habitable.data.sensor

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads a Bluetooth LE thermometer that advertises temperature and humidity in
 * its manufacturer data, without connecting. Many low-cost hygrometers
 * (Govee H5075/H5101-style, Xiaomi LYWSD03MMC with custom firmware) work this way.
 *
 * STATUS FOR UPDATE 1: scanning and the flow plumbing are implemented; the
 * payload decoder below targets the Govee H5075 layout and has not yet been
 * verified against hardware (sensor on order). Until then the app defaults to
 * [SimulatedSensorSource]. This is listed as a risk in docs/UPDATE1.md.
 *
 * Permissions: BLUETOOTH_SCAN / BLUETOOTH_CONNECT are requested by the UI only
 * when the user chooses BLE mode. If they are missing, the flow completes with
 * an error rather than crashing.
 */
class BleSensorSource(
    private val context: Context,
    override val address: String,
    override val model: String = "BLE thermometer",
) : SensorSource {

    override fun samples(): Flow<SensorSample> = callbackFlow {
        if (!hasScanPermission()) {
            close(SecurityException("Bluetooth scan permission not granted"))
            return@callbackFlow
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = manager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth LE not available on this device"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val payload = record.getManufacturerSpecificData(GOVEE_COMPANY_ID) ?: return
                val decoded = decodeGovee(payload) ?: return
                trySend(
                    SensorSample(
                        tempF = decoded.first,
                        humidityPct = decoded.second,
                        recordedAtMillis = System.currentTimeMillis(),
                        simulated = false,
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed with code $errorCode"))
            }
        }

        val filters = try {
            listOf(ScanFilter.Builder().setDeviceAddress(address.uppercase()).build())
        } catch (e: IllegalArgumentException) {
            close(IllegalArgumentException("Not a valid Bluetooth address: $address"))
            return@callbackFlow
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        try {
            scanner.startScan(filters, settings, callback)
        } catch (se: SecurityException) {
            close(se)
            return@callbackFlow
        }
        Log.d(TAG, "BLE scan started for $address")

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Permission revoked while scanning; nothing more to do.
            }
            Log.d(TAG, "BLE scan stopped")
        }
    }

    private fun hasScanPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "BleSensorSource"

        /** Govee's Bluetooth SIG company identifier. */
        const val GOVEE_COMPANY_ID = 0xEC88

        /**
         * Govee H5075 packs temperature and humidity into a 3-byte big-endian
         * integer: value / 10000 = °C, (value % 1000) / 10 = RH%. Negative
         * temperatures set the top bit. Returns (tempF, humidity) or null.
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
    }
}
