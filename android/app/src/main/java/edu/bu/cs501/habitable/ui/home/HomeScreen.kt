package edu.bu.cs501.habitable.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.data.repository.WeatherStatus
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.rules.HeatStatus
import edu.bu.cs501.habitable.ui.components.CategoryBadge
import edu.bu.cs501.habitable.ui.components.ChipTone
import edu.bu.cs501.habitable.ui.components.EmptyState
import edu.bu.cs501.habitable.ui.components.EscalationProgress
import edu.bu.cs501.habitable.ui.components.InlineBanner
import edu.bu.cs501.habitable.ui.components.ScreenHeader
import edu.bu.cs501.habitable.ui.components.SectionHeader
import edu.bu.cs501.habitable.ui.components.StatTile
import edu.bu.cs501.habitable.ui.components.StatusChip
import edu.bu.cs501.habitable.ui.components.SurfaceCard
import edu.bu.cs501.habitable.ui.components.TemperatureChart
import edu.bu.cs501.habitable.ui.components.palette
import edu.bu.cs501.habitable.ui.components.tone
import edu.bu.cs501.habitable.ui.theme.HabitableBrushes
import edu.bu.cs501.habitable.ui.theme.HabitableColors
import java.time.LocalTime
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenIssue: (Long) -> Unit,
    onReportIssue: () -> Unit,
    onOpenSensors: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.tick()
        onPauseOrDispose { }
    }
    HomeContent(state, onOpenIssue, onReportIssue, onOpenSensors, { viewModel.refreshWeather() })
}

/** Stateless content so the Compose UI test can drive it with a fixed state. */
@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenIssue: (Long) -> Unit,
    onReportIssue: () -> Unit,
    onOpenSensors: () -> Unit,
    onRetryWeather: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The NavHost Scaffold already pads for the system bars; don't pad twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onReportIssue,
                containerColor = HabitableColors.Heat,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_report_issue), fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("fab_report"),
            )
        }
    ) { padding ->
        when (state) {
            HomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HabitableColors.Heat)
            }
            is HomeUiState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("home_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Header(state) }
                item { HeroCard(state, onOpenSensors) }
                item { WeatherBanner(state.weather, onRetryWeather) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            icon = Icons.Filled.Timer,
                            value = String.format(Locale.US, "%.0f h", state.hoursBelow),
                            label = stringResource(R.string.home_hours_below),
                            accent = HabitableColors.Heat,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            icon = Icons.Filled.AcUnit,
                            value = state.outdoorNowF?.let { String.format(Locale.US, "%.0f°F", it) } ?: "—",
                            label = stringResource(R.string.home_outside_now),
                            accent = HabitableColors.Cold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    SectionHeader("${stringResource(R.string.home_open_issues)} · ${state.openIssues.size}")
                }
                if (state.openIssues.isEmpty()) {
                    item {
                        SurfaceCard {
                            EmptyState(
                                text = stringResource(R.string.home_no_issues),
                                title = "All clear",
                                icon = Icons.Filled.CheckCircle,
                                tone = ChipTone.Good,
                            )
                        }
                    }
                } else {
                    items(state.openIssues, key = { it.id }) { issue -> IssueRow(issue) { onOpenIssue(issue.id) } }
                }
            }
        }
    }
}

private fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Late night check"
}

@Composable
private fun Header(state: HomeUiState.Loaded) {
    val (label, tone) = when {
        state.monitoring && state.simulated -> stringResource(R.string.home_sensor_simulated) to ChipTone.Cold
        state.monitoring -> stringResource(R.string.home_sensor_live) to ChipTone.Good
        else -> stringResource(R.string.home_sensor_off) to ChipTone.Neutral
    }
    ScreenHeader(
        title = state.addressLine.ifBlank { stringResource(R.string.nav_home) },
        eyebrow = greeting(),
        subtitle = "Heat record · Boston",
        trailing = { StatusChip(label, tone, Modifier.testTag("chip_sensor"), dot = true, pulse = state.monitoring) },
    )
}

@Composable
private fun HeroCard(state: HomeUiState.Loaded, onOpenSensors: () -> Unit) {
    val violating = state.status?.isViolation == true
    val top by animateColorAsState(if (violating) HabitableColors.EmberDeep else HabitableColors.NavyDeep, tween(700), label = "heroTop")
    val bottom by animateColorAsState(if (violating) HabitableColors.Ember else HabitableColors.NavyGlow, tween(700), label = "heroBottom")
    val glow = if (violating) HabitableColors.HeatBright else HabitableColors.ColdBright
    val shape = MaterialTheme.shapes.large

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = bottom.copy(alpha = 0.35f), spotColor = bottom.copy(alpha = 0.55f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom)))
            .clickable(onClick = onOpenSensors)
            .testTag("hero_card")
    ) {
        // Decorative glow in the corner.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-70).dp)
                .size(220.dp)
                .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent)), CircleShape)
        )

        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NightsStay, contentDescription = null, tint = HabitableColors.Mist, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.home_current_reading).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = HabitableColors.Mist,
                    modifier = Modifier.weight(1f),
                )
                state.latest?.humidityPct?.let { rh ->
                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = HabitableColors.ColdBright, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(String.format(Locale.US, "%.0f%%", rh), style = MaterialTheme.typography.labelLarge, color = HabitableColors.MistLight)
                }
            }

            val latest = state.latest
            Text(
                text = if (latest == null) buildAnnotatedString { append("—") } else buildAnnotatedString {
                    append(String.format(Locale.US, "%.1f", latest.tempF))
                    withStyle(SpanStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = HabitableColors.MistLight)) { append("°F") }
                },
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp).testTag("current_temp"),
            )

            when (val s = state.status) {
                is HeatStatus.BelowMinimum -> StatusPill(Icons.Filled.AcUnit, stringResource(R.string.home_below_limit, s.shortfallF, s.requiredF), Color(0xFFFFC7B5))
                is HeatStatus.AboveMaximum -> StatusPill(Icons.Filled.Warning, stringResource(R.string.home_above_limit, s.excessF, s.maximumF), Color(0xFFFFC7B5))
                is HeatStatus.Compliant -> StatusPill(Icons.Filled.CheckCircle, "Within the ${s.requiredF}°F limit", HabitableColors.GoodBright)
                HeatStatus.OutOfSeason -> StatusPill(Icons.Filled.CheckCircle, "Outside the Sept 15 – May 31 heating season", HabitableColors.MistLight)
                null -> Unit
            }

            // Where the reading sits on the 50–80 °F scale, with the legal limit marked.
            val limit = when (val s = state.status) {
                is HeatStatus.BelowMinimum -> s.requiredF
                is HeatStatus.Compliant -> s.requiredF
                is HeatStatus.AboveMaximum -> s.maximumF
                else -> null
            }
            if (latest != null && limit != null) {
                Spacer(Modifier.height(16.dp))
                LimitMeter(tempF = latest.tempF, limitF = limit)
            }

            Spacer(Modifier.height(14.dp))
            if (state.chart.isEmpty) {
                Text(stringResource(R.string.sensors_empty), style = MaterialTheme.typography.bodyMedium, color = HabitableColors.MistLight)
            } else {
                TemperatureChart(
                    data = state.chart,
                    height = 110.dp,
                    showAxes = false,
                    showLegend = false,
                    indoorColor = Color.White,
                    limitColor = HabitableColors.HeatBright,
                    outdoorColor = HabitableColors.ColdBright,
                    gridColor = Color(0x22FFFFFF),
                    labelColor = HabitableColors.Mist,
                    shadeColor = HabitableColors.HeatBright,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last 16 hours", style = MaterialTheme.typography.labelSmall, color = HabitableColors.Mist, modifier = Modifier.weight(1f))
                Text("Open full chart", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StatusPill(icon: ImageVector, text: String, fg: Color) {
    Row(
        Modifier
            .padding(top = 2.dp)
            .background(Color(0x1FFFFFFF), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** Horizontal temperature scale (50–80 °F) with a tick for the legal limit and a dot for now. */
@Composable
private fun LimitMeter(tempF: Double, limitF: Int, lowF: Double = 50.0, highF: Double = 80.0) {
    val dotColor = if (tempF < limitF) HabitableColors.ColdBright else HabitableColors.GoodBright
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Scale from 50 to 80 degrees; limit $limitF" }) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            fun xOf(v: Double) = (((v - lowF) / (highF - lowF)).coerceIn(0.0, 1.0) * size.width).toFloat()
            val trackH = 6.dp.toPx()
            val cy = size.height / 2
            drawRoundRect(
                brush = HabitableBrushes.TempScale,
                topLeft = Offset(0f, cy - trackH / 2),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH / 2, trackH / 2),
                alpha = 0.85f,
            )
            val lx = xOf(limitF.toDouble())
            drawLine(Color.White, Offset(lx, 0f), Offset(lx, size.height), strokeWidth = 2.dp.toPx())
            val tx = xOf(tempF)
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(tx, cy))
            drawCircle(dotColor, radius = 5.dp.toPx(), center = Offset(tx, cy))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("${lowF.toInt()}°", style = MaterialTheme.typography.labelSmall, color = HabitableColors.Mist)
            Spacer(Modifier.weight(1f))
            Text("limit $limitF°", style = MaterialTheme.typography.labelSmall, color = Color.White)
            Spacer(Modifier.weight(1f))
            Text("${highF.toInt()}°", style = MaterialTheme.typography.labelSmall, color = HabitableColors.Mist)
        }
    }
}

@Composable
private fun WeatherBanner(status: WeatherStatus, onRetry: () -> Unit) {
    when (status) {
        WeatherStatus.Loading -> InlineBanner(stringResource(R.string.weather_loading), loading = true)
        is WeatherStatus.Error -> InlineBanner(
            text = if (status.hasCache) stringResource(R.string.weather_stale, "cache") else stringResource(R.string.weather_error),
            tone = ChipTone.Heat,
            actionLabel = stringResource(R.string.weather_retry),
            onAction = onRetry,
        )
        else -> Unit
    }
}

@Composable
private fun IssueRow(issue: Issue, onClick: () -> Unit) {
    SurfaceCard(Modifier.testTag("issue_row"), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(issue.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(issue.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(issue.status.tone().palette().accent, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (issue.openedAutomatically) "${issue.status.label} · ${stringResource(R.string.issue_opened_auto)}" else issue.status.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.height(10.dp))
                EscalationProgress(issue.status)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
