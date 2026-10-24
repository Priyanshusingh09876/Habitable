package edu.bu.cs501.habitable.data.sensor

import kotlinx.coroutines.flow.Flow

/**
 * A source of temperature samples. The repository does not care whether the
 * numbers come from a BLE thermometer or the simulator, which is what lets us
 * demo a cold night in a warm classroom and swap in real hardware later
 * without touching the UI.
 */
interface SensorSource {

    /** Stable identifier stored in sensors.ble_address ("SIM" for the simulator). */
    val address: String

    /** Human-readable model name. */
    val model: String

    /**
     * Emit samples until the collector cancels. Implementations must be cold:
     * nothing happens until collected, and cancelling stops the hardware.
     */
    fun samples(): Flow<SensorSample>
}

data class SensorSample(
    val tempF: Double,
    val humidityPct: Double?,
    val recordedAtMillis: Long,
    val simulated: Boolean,
)
