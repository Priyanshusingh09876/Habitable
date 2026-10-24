package edu.bu.cs501.habitable.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * chart: the limit *steps* at 7 AM and 11 PM (no slope between), and the
 * shading has to cover only the span where indoor < limit. Doing it by hand
 * keeps those semantics exact and the same code can later draw into a PDF
 * Canvas. See docs/UPDATE1.md, "Decisions".
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
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val cd = stringResource(R.string.chart_cd)
    val density = LocalDensity.current
    val labelPx = with(density) { 11.sp.toPx() }
    val timeFmt = remember(zone) { DateTimeFormatter.ofPattern("h a").withZone(zone) }

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = cd }
        ) {
            drawChart(data, showAxes, labelPx, timeFmt, indoorColor, limitColor, outdoorColor, shadeColor, gridColor, labelColor)
        }
        if (showLegend) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LegendItem(indoorColor, stringResource(R.string.chart_legend_indoor))
                LegendItem(limitColor, stringResource(R.string.chart_legend_limit))
                LegendItem(outdoorColor, stringResource(R.string.chart_legend_outdoor))
                LegendItem(shadeColor, stringResource(R.string.chart_legend_below), box = true)
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, box: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (box) {
            Box(Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
        } else {
            Box(Modifier.width(14.dp).height(3.dp).background(color, RoundedCornerShape(2.dp)))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun DrawScope.drawChart(
    data: ChartData,
    showAxes: Boolean,
    labelPx: Float,
    timeFmt: DateTimeFormatter,
    indoorColor: Color,
    limitColor: Color,
    outdoorColor: Color,
    shadeColor: Color,
    gridColor: Color,
    labelColor: Color,
) {
    val leftPad = if (showAxes) labelPx * 2.6f else 4f
    val bottomPad = if (showAxes) labelPx * 1.8f else 4f
    val topPad = 6f
    val rightPad = 6f
    val w = size.width - leftPad - rightPad
    val h = size.height - topPad - bottomPad
    if (w <= 0 || h <= 0) return

    val tSpan = (data.toMillis - data.fromMillis).coerceAtLeast(1L).toFloat()
    val vSpan = (data.maxF - data.minF).coerceAtLeast(1.0).toFloat()
    fun x(t: Long) = leftPad + (t - data.fromMillis) / tSpan * w
    fun y(v: Double) = topPad + (data.maxF - v).toFloat() / vSpan * h

    // Grid + axis labels every 10 °F
    val paint = android.graphics.Paint().apply {
        color = labelColor.toArgb()
        textSize = labelPx
        isAntiAlias = true
    }
    var g = data.minF
    while (g <= data.maxF + 0.01) {
        val yy = y(g)
        drawLine(gridColor, Offset(leftPad, yy), Offset(leftPad + w, yy), strokeWidth = 1f)
        if (showAxes) {
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText("${g.toInt()}°", leftPad - labelPx * 0.4f, yy + labelPx * 0.35f, paint)
        }
        g += 10.0
    }

    // Time labels: pick a step that gives ~4-6 labels
    if (showAxes) {
        val hours = tSpan / 3_600_000f
        val stepH = when {
            hours <= 8 -> 1
            hours <= 20 -> 3
            hours <= 48 -> 6
            hours <= 24 * 8 -> 24
            else -> 24 * 7
        }
        val stepMs = stepH * 3_600_000L
        // Align the first label to a whole hour boundary in the chart zone.
        val first = (data.fromMillis / 3_600_000L + 1) * 3_600_000L
        var t = first
        paint.textAlign = android.graphics.Paint.Align.CENTER
        while (t <= data.toMillis) {
            val localHour = Instant.ofEpochMilli(t).atZone(timeFmt.zone).hour
            if (stepH >= 24 && localHour == 0 || stepH < 24 && localHour % stepH == 0) {
                val xx = x(t)
                drawLine(gridColor, Offset(xx, topPad + h), Offset(xx, topPad + h + 4f), strokeWidth = 1f)
                val label = if (stepH >= 24) DateTimeFormatter.ofPattern("MMM d").withZone(timeFmt.zone).format(Instant.ofEpochMilli(t))
                else timeFmt.format(Instant.ofEpochMilli(t))
                drawContext.canvas.nativeCanvas.drawText(label, xx, size.height - 2f, paint)
            }
            t += 3_600_000L
        }
    }

    // Violation shading: between the limit line and the indoor line where indoor < limit.
    if (data.limit.isNotEmpty() && data.indoor.isNotEmpty()) {
        for ((s, e) in data.violationSpans) {
            val path = Path()
            val limitPts = data.limit.filter { it.timeMillis in s..e }.toMutableList()
            // Ensure the limit polyline has endpoints at s and e.
            fun limitAt(t: Long): Double {
                val before = data.limit.lastOrNull { it.timeMillis <= t } ?: data.limit.first()
                return before.valueF
            }
            val top = listOf(ChartData.Pt(s, limitAt(s))) + limitPts + listOf(ChartData.Pt(e, limitAt(e)))
            val bottom = data.indoor.filter { it.timeMillis in s..e }
            if (bottom.isEmpty()) continue
            path.moveTo(x(top.first().timeMillis), y(top.first().valueF))
            for (p in top.drop(1)) path.lineTo(x(p.timeMillis), y(p.valueF))
            for (p in bottom.asReversed()) path.lineTo(x(p.timeMillis), y(p.valueF))
            path.close()
            drawPath(path, shadeColor.copy(alpha = 0.55f))
        }
    }

    // Outdoor (dashed, cold blue)
    if (data.outdoor.size >= 2) {
        val p = Path()
        data.outdoor.forEachIndexed { i, pt -> if (i == 0) p.moveTo(x(pt.timeMillis), y(pt.valueF)) else p.lineTo(x(pt.timeMillis), y(pt.valueF)) }
        drawPath(p, outdoorColor, style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
    }

    // Limit (step line)
    if (data.limit.size >= 2) {
        val p = Path()
        data.limit.forEachIndexed { i, pt -> if (i == 0) p.moveTo(x(pt.timeMillis), y(pt.valueF)) else p.lineTo(x(pt.timeMillis), y(pt.valueF)) }
        drawPath(p, limitColor, style = Stroke(width = 4f))
    }

    // Indoor
    if (data.indoor.size >= 2) {
        val p = Path()
        data.indoor.forEachIndexed { i, pt -> if (i == 0) p.moveTo(x(pt.timeMillis), y(pt.valueF)) else p.lineTo(x(pt.timeMillis), y(pt.valueF)) }
        drawPath(p, indoorColor, style = Stroke(width = 5f))
    } else if (data.indoor.size == 1) {
        val pt = data.indoor.first()
        drawCircle(indoorColor, radius = 6f, center = Offset(x(pt.timeMillis), y(pt.valueF)))
    }
}
