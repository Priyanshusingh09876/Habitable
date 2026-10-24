package edu.bu.cs501.habitable.domain.usecase

import edu.bu.cs501.habitable.domain.model.OutdoorReading
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.rules.HeatRules
import edu.bu.cs501.habitable.domain.rules.LimitSeries
import java.time.ZoneId

/**
 * Everything the temperature chart needs, computed once per window so the
 * composable only draws. Pure Kotlin; unit-tested.
 */
data class ChartData(
    val fromMillis: Long,
    val toMillis: Long,
    val indoor: List<Pt>,
    val outdoor: List<Pt>,
    val limit: List<Pt>,
    /** Segments where indoor < limit, each as (startMillis, endMillis). */
    val violationSpans: List<Pair<Long, Long>>,
    val minF: Double,
    val maxF: Double,
) {
    data class Pt(val timeMillis: Long, val valueF: Double)

    val isEmpty: Boolean get() = indoor.isEmpty()
}

object BuildChartData {

    operator fun invoke(
        readings: List<Reading>,
        outdoor: List<OutdoorReading>,
        fromMillis: Long,
        toMillis: Long,
        zone: ZoneId = HeatRules.BOSTON,
    ): ChartData {
        val indoor = readings.sortedBy { it.recordedAtMillis }.map { ChartData.Pt(it.recordedAtMillis, it.tempF) }
        val out = outdoor.sortedBy { it.timeMillis }
            .filter { it.timeMillis in (fromMillis - 3_600_000L)..(toMillis + 3_600_000L) }
            .map { ChartData.Pt(it.timeMillis, it.tempF) }
        val limit = LimitSeries.build(fromMillis, toMillis, zone)
            .mapNotNull { p -> p.limitF?.let { ChartData.Pt(p.timeMillis, it.toDouble()) } }

        val spans = violationSpans(indoor, zone)

        val values = indoor.map { it.valueF } + out.map { it.valueF } + limit.map { it.valueF }
        val lo = (values.minOrNull() ?: 60.0)
        val hi = (values.maxOrNull() ?: 72.0)
        // Round to tidy 10 °F gridlines with a little headroom.
        val minF = (kotlin.math.floor((lo - 2) / 10.0) * 10.0)
        val maxF = (kotlin.math.ceil((hi + 2) / 10.0) * 10.0)

        return ChartData(fromMillis, toMillis, indoor, out, limit, spans, minF, maxF)
    }

    /** Contiguous runs of readings below the limit in force at each reading's time. */
    internal fun violationSpans(indoor: List<ChartData.Pt>, zone: ZoneId): List<Pair<Long, Long>> {
        val spans = ArrayList<Pair<Long, Long>>()
        var start: Long? = null
        var lastT = 0L
        for (p in indoor) {
            val limit = HeatRules.requiredMinimumF(p.timeMillis, zone)
            val below = limit != null && p.valueF < limit
            if (below && start == null) start = p.timeMillis
            if (!below && start != null) {
                spans += start to p.timeMillis
                start = null
            }
            lastT = p.timeMillis
        }
        if (start != null) spans += start to lastT
        return spans
    }
}
