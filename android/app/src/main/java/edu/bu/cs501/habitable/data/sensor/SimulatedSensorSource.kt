package edu.bu.cs501.habitable.data.sensor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simulated thermometer used for development, demos and tests.
 *
 * Two modes:
 *  - [Mode.Live] emits one sample every [intervalMillis] of real time, drifting
 *    gently around a comfortable temperature. Good for showing the pipeline
 *    runs without waiting all night.
 *  - [Mode.Replay] plays the proposal's "cold November night" (Nov 9, 6 PM to
 *    Nov 10, 10 AM): the apartment starts at 70 °F, slides to 59 °F around
 *    4 AM and recovers to 61 °F by morning. Timestamps are back-dated so the
 *    chart shows a full night at once. [replaySpeedup] controls how fast the
 *    16 hours are emitted; 0 means "emit everything immediately".
 *
 * The shape below is the same curve used in the proposal deck, so the demo and
 * the documentation agree.
 */
class SimulatedSensorSource(
    private val mode: Mode,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) : SensorSource {

    sealed interface Mode {
        /** Real-time drift around [baseTempF]. */
        data class Live(val baseTempF: Double = 69.5) : Mode

        /**
         * Replay the cold night ending at [endMillis] (defaults to "now").
         * [speedup] = how many simulated minutes pass per real second; 0 = instant.
         */
        data class Replay(val endMillis: Long? = null, val speedup: Int = 0) : Mode
    }

    override val address: String = ADDRESS
    override val model: String = "Simulated thermometer"

    override fun samples(): Flow<SensorSample> = when (mode) {
        is Mode.Live -> live(mode)
        is Mode.Replay -> replay(mode)
    }

    private fun live(m: Mode.Live): Flow<SensorSample> = flow {
        var i = 0
        while (true) {
            val now = clock()
            // Slow sine drift plus a little noise, never crossing the legal limit on its own.
            val temp = m.baseTempF + 0.8 * sin(i / 9.0) + 0.15 * cos(i * 1.7)
            emit(SensorSample(tempF = round1(temp), humidityPct = round1(38 + 3 * sin(i / 5.0)), recordedAtMillis = now, simulated = true))
            i++
            delay(intervalMillis)
        }
    }

    private fun replay(m: Mode.Replay): Flow<SensorSample> = flow {
        val end = m.endMillis ?: clock()
        val start = end - REPLAY_HOURS * 60L * 60L * 1000L
        val samples = coldNight(start)
        for (s in samples) {
            emit(s)
            if (m.speedup > 0) {
                // intervalMillis of simulated time takes (interval / speedup) of real time.
                val realDelay = (intervalMillis / 60_000.0) / m.speedup * 1000.0
                delay(realDelay.toLong().coerceAtLeast(1))
            }
        }
    }

    companion object {
        const val ADDRESS = "SIM"
        const val DEFAULT_INTERVAL_MILLIS = 10 * 60 * 1000L
        const val REPLAY_HOURS = 16

        /** Knots (hours since 6 PM, °F) that shape the cold night. */
        private val KNOTS = listOf(
            0.0 to 70.4, 1.5 to 68.0, 5.0 to 64.6, 7.0 to 63.4,
            10.0 to 59.2, 12.0 to 59.6, 13.0 to 60.4, 16.0 to 61.4,
        )

        /**
         * Build the full set of 10-minute samples for the cold night that
         * starts at [startMillis]. Exposed so tests and the repository can use
         * the exact same data without going through a Flow.
         */
        fun coldNight(startMillis: Long, intervalMillis: Long = DEFAULT_INTERVAL_MILLIS): List<SensorSample> {
            val count = (REPLAY_HOURS * 60L * 60L * 1000L / intervalMillis).toInt() + 1
            return (0 until count).map { i ->
                val hours = i * intervalMillis / 3_600_000.0
                val base = interpolate(hours)
                val noise = 0.12 * sin(i * 0.9)
                SensorSample(
                    tempF = round1(base + noise),
                    humidityPct = round1(42 + 4 * sin(hours / 3.0)),
                    recordedAtMillis = startMillis + i * intervalMillis,
                    simulated = true,
                )
            }
        }

        /** Cosine interpolation between knots; smooth like a real room. */
        internal fun interpolate(hours: Double): Double {
            for (k in 0 until KNOTS.size - 1) {
                val (a, va) = KNOTS[k]
                val (b, vb) = KNOTS[k + 1]
                if (hours >= a && hours <= b) {
                    val t = (hours - a) / (b - a)
                    val s = (1 - cos(PI * t)) / 2
                    return va + (vb - va) * s
                }
            }
            return KNOTS.last().second
        }

        private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
    }
}
