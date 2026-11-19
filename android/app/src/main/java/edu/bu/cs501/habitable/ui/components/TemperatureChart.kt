package edu.bu.cs501.habitable.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.domain.usecase.ChartData
import edu.bu.cs501.habitable.ui.theme.HabitableColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Indoor temperature vs. the Massachusetts step limit, with outdoor weather.
 *
 * Drawn with Compose Canvas rather than a chart library because the two things
 * that make this chart useful as evidence are hard to get from a generic line
 * chart: the limit *steps* at 7 AM and 11:01 PM (no slope between), and the
 * shading has to cover only the span where indoor < limit. Doing it by hand
 * keeps those semantics exact and the same code can later draw into a PDF
 * Canvas. See docs/UPDATE1.md, "Decisions".
 *
 * Visual layer: soft gradient under the indoor line, rounded strokes, dashed
 * grid, a haloed "now" dot, and a one-time left-to-right draw-in animation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemperatureChart(
    data: ChartData,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    showAxes: Boolean = true,
    showLegend: Boolean = true,
    indoorColor: Color = MaterialTheme.colorScheme.onSurface,
    limitColor: Color = HabitableColors.Heat,
    outdoorColor: Color = HabitableColors.Cold,
    shadeColor: Color = HabitableColors.Shade,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fillUnderIndoor: Boolean = true,
    animate: Boolean = true,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val cd = stringResource(R.string.chart_cd)
    val density = LocalDensity.current
    val labelPx = with(density) { 11.sp.toPx() }
    val strokePx = with(density) { 2.5.dp.toPx() }
    val timeFmt = remember(zone) { DateTimeFormatter.ofPattern("h a").withZone(zone) }
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) progress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }

    Column(modifier) {
        // drawWithCache: paths, gradients and labels are built once per size and data, not on
        // every frame. The draw-in animation only reads `progress` in the draw block, so each
        // animation frame just re-clips the cached drawing (no recomposition, no new paths).
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = cd }
                .drawWithCache {
                    val chart = buildChartDrawing(
                        data, showAxes, labelPx, strokePx, timeFmt, fillUnderIndoor,
                        indoorColor, limitColor, outdoorColor, shadeColor, gridColor, labelColor,
                    )
                    onDrawBehind { chart?.draw(this, progress.value) }
                }
        )
        if (showLegend) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LegendItem(indoorColor, stringResource(R.string.chart_legend_indoor))
                LegendItem(limitColor, stringResource(R.string.chart_legend_limit))
                LegendItem(outdoorColor, stringResource(R.string.chart_legend_outdoor), dashed = true)
                LegendItem(shadeColor, stringResource(R.string.chart_legend_below), box = true)
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, box: Boolean = false, dashed: Boolean = false) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            box -> Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
            dashed -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) { Box(Modifier.width(4.dp).height(3.dp).background(color, RoundedCornerShape(2.dp))) }
            }
            else -> Box(Modifier.width(14.dp).height(3.dp).background(color, RoundedCornerShape(2.dp)))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Everything needed to draw the chart at one size, built once and reused for every frame. */
private class ChartDrawing(
    private val grid: List<Pair<Float, String>>,
    private val timeLabels: List<Pair<Float, String>>,
    private val showAxes: Boolean,
    private val leftPad: Float,
    private val chartRight: Float,
    private val rightPad: Float,
    private val paint: android.graphics.Paint,
    private val labelPx: Float,
    private val gridColor: Color,
    private val gridDash: PathEffect,
    private val area: Path?,
    private val areaBrush: Brush?,
    private val shade: List<Path>,
    private val shadeBrush: Brush,
    private val outdoor: Path?,
    private val outdoorColor: Color,
    private val outdoorStroke: Stroke,
    private val limit: Path?,
    private val limitColor: Color,
    private val limitStroke: Stroke,
    private val indoor: Path?,
    private val indoorColor: Color,
    private val indoorStroke: Stroke,
    private val nowDot: Offset?,
    private val strokePx: Float,
) {
    fun draw(scope: DrawScope, progress: Float) = with(scope) {
        val native = drawContext.canvas.nativeCanvas
        for ((yy, label) in grid) {
            drawLine(gridColor, Offset(leftPad, yy), Offset(chartRight, yy), strokeWidth = 1.5f, pathEffect = gridDash)
            if (showAxes) {
                paint.textAlign = android.graphics.Paint.Align.RIGHT
                native.drawText(label, leftPad - labelPx * 0.5f, yy + labelPx * 0.35f, paint)
            }
        }
        if (showAxes) {
            paint.textAlign = android.graphics.Paint.Align.CENTER
            for ((xx, label) in timeLabels) native.drawText(label, xx, size.height - 2f, paint)
        }

        // Everything below draws in from left to right on first show.
        clipRect(left = 0f, top = 0f, right = leftPad + (chartRight - leftPad) * progress + rightPad, bottom = size.height) {
            if (area != null && areaBrush != null) drawPath(area, areaBrush)
            shade.forEach { drawPath(it, shadeBrush) }
            outdoor?.let { drawPath(it, outdoorColor, style = outdoorStroke) }
            limit?.let { drawPath(it, limitColor, style = limitStroke) }
            indoor?.let { drawPath(it, indoorColor, style = indoorStroke) }
        }

        // "Now" dot with a halo on the latest indoor reading (after the draw-in finishes).
        if (nowDot != null && progress >= 0.99f) {
            drawCircle(indoorColor.copy(alpha = 0.22f), radius = strokePx * 4f, center = nowDot)
            drawCircle(indoorColor, radius = strokePx * 1.9f, center = nowDot)
        }
    }
}

private fun CacheDrawScope.buildChartDrawing(
    data: ChartData,
    showAxes: Boolean,
    labelPx: Float,
    strokePx: Float,
    timeFmt: DateTimeFormatter,
    fillUnderIndoor: Boolean,
    indoorColor: Color,
    limitColor: Color,
    outdoorColor: Color,
    shadeColor: Color,
    gridColor: Color,
    labelColor: Color,
): ChartDrawing? {
    val leftPad = if (showAxes) labelPx * 2.6f else 4f
    val bottomPad = if (showAxes) labelPx * 1.9f else 8f
    val topPad = 10f
    val rightPad = 12f
    val w = size.width - leftPad - rightPad
    val h = size.height - topPad - bottomPad
    if (w <= 0 || h <= 0) return null

    val tSpan = (data.toMillis - data.fromMillis).coerceAtLeast(1L).toFloat()
    val vSpan = (data.maxF - data.minF).coerceAtLeast(1.0).toFloat()
    fun x(t: Long) = leftPad + (t - data.fromMillis) / tSpan * w
    fun y(v: Double) = topPad + (data.maxF - v).toFloat() / vSpan * h
    fun line(points: List<ChartData.Pt>): Path? {
        if (points.size < 2) return null
        val p = Path()
        points.forEachIndexed { i, pt -> if (i == 0) p.moveTo(x(pt.timeMillis), y(pt.valueF)) else p.lineTo(x(pt.timeMillis), y(pt.valueF)) }
        return p
    }

    // Grid every 10 °F.
    val grid = ArrayList<Pair<Float, String>>()
    var g = data.minF
    while (g <= data.maxF + 0.01) {
        grid += y(g) to "${g.toInt()}°"
        g += 10.0
    }

    // Time labels: a step that gives about 4-6 labels.
    val timeLabels = ArrayList<Pair<Float, String>>()
    if (showAxes) {
        val hours = tSpan / 3_600_000f
        val stepH = when {
            hours <= 8 -> 1
            hours <= 20 -> 3
            hours <= 48 -> 6
            hours <= 24 * 8 -> 24
            else -> 24 * 7
        }
        val dayFmt = DateTimeFormatter.ofPattern("MMM d").withZone(timeFmt.zone)
        var t = (data.fromMillis / 3_600_000L + 1) * 3_600_000L
        while (t <= data.toMillis) {
            val localHour = Instant.ofEpochMilli(t).atZone(timeFmt.zone).hour
            if (stepH >= 24 && localHour == 0 || stepH < 24 && localHour % stepH == 0) {
                val instant = Instant.ofEpochMilli(t)
                timeLabels += x(t) to (if (stepH >= 24) dayFmt.format(instant) else timeFmt.format(instant))
            }
            t += 3_600_000L
        }
    }

    // Soft gradient under the indoor line.
    var area: Path? = null
    var areaBrush: Brush? = null
    if (fillUnderIndoor && data.indoor.size >= 2) {
        area = Path().apply {
            moveTo(x(data.indoor.first().timeMillis), topPad + h)
            data.indoor.forEach { pt -> lineTo(x(pt.timeMillis), y(pt.valueF)) }
            lineTo(x(data.indoor.last().timeMillis), topPad + h)
            close()
        }
        areaBrush = Brush.verticalGradient(listOf(indoorColor.copy(alpha = 0.22f), indoorColor.copy(alpha = 0f)), startY = topPad, endY = topPad + h)
    }

    // Violation shading: between the limit line and the indoor line where indoor < limit.
    val shade = ArrayList<Path>()
    if (data.limit.isNotEmpty() && data.indoor.isNotEmpty()) {
        fun limitAt(t: Long): Double = (data.limit.lastOrNull { it.timeMillis <= t } ?: data.limit.first()).valueF
        for ((s, e) in data.violationSpans) {
            val bottom = data.indoor.filter { it.timeMillis in s..e }
            if (bottom.isEmpty()) continue
            val top = listOf(ChartData.Pt(s, limitAt(s))) + data.limit.filter { it.timeMillis in s..e } + listOf(ChartData.Pt(e, limitAt(e)))
            shade += Path().apply {
                moveTo(x(top.first().timeMillis), y(top.first().valueF))
                for (p in top.drop(1)) lineTo(x(p.timeMillis), y(p.valueF))
                for (p in bottom.asReversed()) lineTo(x(p.timeMillis), y(p.valueF))
                close()
            }
        }
    }

    val last = data.indoor.lastOrNull()
    return ChartDrawing(
        grid = grid,
        timeLabels = timeLabels,
        showAxes = showAxes,
        leftPad = leftPad,
        chartRight = leftPad + w,
        rightPad = rightPad,
        paint = android.graphics.Paint().apply { color = labelColor.toArgb(); textSize = labelPx; isAntiAlias = true },
        labelPx = labelPx,
        gridColor = gridColor,
        gridDash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
        area = area,
        areaBrush = areaBrush,
        shade = shade,
        shadeBrush = Brush.verticalGradient(listOf(shadeColor.copy(alpha = 0.75f), shadeColor.copy(alpha = 0.35f)), startY = topPad, endY = topPad + h),
        outdoor = line(data.outdoor),
        outdoorColor = outdoorColor,
        outdoorStroke = Stroke(width = strokePx * 0.9f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
        limit = line(data.limit),
        limitColor = limitColor,
        limitStroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        indoor = line(data.indoor),
        indoorColor = indoorColor,
        indoorStroke = Stroke(width = strokePx * 1.25f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        nowDot = last?.let { Offset(x(it.timeMillis), y(it.valueF)) },
        strokePx = strokePx,
    )
}
