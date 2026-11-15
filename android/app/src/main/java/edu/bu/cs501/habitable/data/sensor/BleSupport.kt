package edu.bu.cs501.habitable.data.sensor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * Everything Bluetooth scanning needs from the phone, in one place, so the
 * picker, the Sensors screen and the foreground service all agree.
 *
 * Why location: the Govee thermometer also broadcasts an iBeacon-format packet.
 * Android hides beacon-like devices from apps that promise "never for location",
 * so the thermometer would be invisible. We therefore ask for location, as
 * Govee's own app does, and never read or store the phone's position.
 */
object BleSupport {

    /** Runtime permissions to request before scanning, for this Android version. */
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        } else {
            // Android 10 and 11: scanning is covered by the location permission.
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    fun hasPermissions(context: Context): Boolean {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val scan = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_SCAN)
        // Precise location is required: with "approximate" only, Android returns no scan results.
        return scan && granted(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun scanner(context: Context): BluetoothLeScanner? = adapter(context)?.bluetoothLeScanner

    /**
     * What the user has to fix before a scan can work, in plain words, or null
     * when everything is ready. Checked in the order the user can fix things.
     */
    fun problem(context: Context): String? {
        val adapter = adapter(context) ?: return "This phone does not have Bluetooth."
        if (!hasPermissions(context)) return "Allow Nearby devices and precise Location so Habitable can find the thermometer."
        if (!adapter.isEnabled) return "Turn on Bluetooth to read the thermometer."
        val location = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (location != null && !LocationManagerCompat.isLocationEnabled(location)) {
            return "Turn on Location in the phone's settings. Android needs it to find Bluetooth sensors; Habitable does not record where you are."
        }
        return null
    }

    /** True for "AA:BB:CC:DD:EE:FF". */
    fun isValidAddress(address: String): Boolean = BluetoothAdapter.checkBluetoothAddress(address.trim().uppercase())
}
