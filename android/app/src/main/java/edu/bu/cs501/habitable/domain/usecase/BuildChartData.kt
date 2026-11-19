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
        maxPoints: Int = DEFAULT_MAX_POINTS,
    ): ChartData {
        val allIndoor = readings.sortedBy { it.recordedAtMillis }.map { ChartData.Pt(it.recordedAtMillis, it.tempF) }
        val out = outdoor.sortedBy { it.timeMillis }
            .filter { it.timeMillis in (fromMillis - 3_600_000L)..(toMillis + 3_600_000L) }
            .map { ChartData.Pt(it.timeMillis, it.tempF) }
        val limit = LimitSeries.build(fromMillis, toMillis, zone)
            .mapNotNull { p -> p.limitF?.let { ChartData.Pt(p.timeMillis, it.toDouble()) } }

        // Cold spans and the axis range come from every reading; only the line drawn is thinned.
        val spans = violationSpans(allIndoor, zone)
        val indoor = downsample(allIndoor, maxPoints)

        val values = allIndoor.map { it.valueF } + out.map { it.valueF } + limit.map { it.valueF }
        val lo = (values.minOrNull() ?: 60.0)
        val hi = (values.maxOrNull() ?: 72.0)
        // Round to tidy 10 °F gridlines with a little headroom.
        val minF = (kotlin.math.floor((lo - 2) / 10.0) * 10.0)
        val maxF = (kotlin.math.ceil((hi + 2) / 10.0) * 10.0)

        return ChartData(fromMillis, toMillis, indoor, out, limit, spans, minF, maxF)
    }

    /**
     * A phone screen is about 1,000 pixels wide, so drawing 43,000 one-minute readings
     * (30 days) only costs time. Above this many points the indoor line is thinned.
     */
    const val DEFAULT_MAX_POINTS = 800

    /**
     * Thins [points] to at most [maxPoints] while keeping what matters on a heat
     * chart: the points are split into equal buckets and each bucket keeps its
     * lowest and its highest reading, in time order. A short cold dip therefore
     * still reaches its true minimum on screen, and the first and last readings
     * are always kept. Points must be sorted by time.
     */
    fun downsample(points: List<ChartData.Pt>, maxPoints: Int): List<ChartData.Pt> {
        if (points.size <= maxPoints || maxPoints < 4) return points
        val buckets = (maxPoints - 2) / 2
        val out = ArrayList<ChartData.Pt>(buckets * 2 + 2)
        out += points.first()
        val inner = points.size - 2
        for (b in 0 until buckets) {
            val start = 1 + (b.toLong() * inner / buckets).toInt()
            val end = 1 + ((b + 1).toLong() * inner / buckets).toInt() // exclusive
            if (start >= end) continue
            var lo = start
            var hi = start
            for (i in start until end) {
                if (points[i].valueF < points[lo].valueF) lo = i
                if (points[i].valueF > points[hi].valueF) hi = i
            }
            if (lo == hi) out += points[lo]
            else if (lo < hi) { out += points[lo]; out += points[hi] }
            else { out += points[hi]; out += points[lo] }
        }
        out += points.last()
        return out
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
