package edu.bu.cs501.habitable.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.data.repository.WeatherStatus
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.rules.HeatStatus
import edu.bu.cs501.habitable.ui.components.ChipTone
import edu.bu.cs501.habitable.ui.components.IconDot
import edu.bu.cs501.habitable.ui.components.InlineBanner
import edu.bu.cs501.habitable.ui.components.SectionHeader
import edu.bu.cs501.habitable.ui.components.StatusChip
import edu.bu.cs501.habitable.ui.components.TemperatureChart
import edu.bu.cs501.habitable.ui.components.TwoStats
import edu.bu.cs501.habitable.ui.theme.HabitableColors
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onReportIssue,
                containerColor = HabitableColors.Heat,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_report_issue)) },
                modifier = Modifier.testTag("fab_report"),
            )
        }
    ) { padding ->
        when (state) {
            HomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is HomeUiState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("home_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Header(state) }
                item { HeroCard(state, onOpenSensors) }
                item { WeatherBanner(state.weather, onRetryWeather) }
                item {
                    val hours = String.format(Locale.US, "%.0f h", state.hoursBelow)
                    val outside = state.outdoorNowF?.let { String.format(Locale.US, "%.0f°F", it) } ?: "—"
                    TwoStats(hours to stringResource(R.string.home_hours_below), outside to stringResource(R.string.home_outside_now))
                }
                item { SectionHeader("${stringResource(R.string.home_open_issues)} · ${state.openIssues.size}") }
                if (state.openIssues.isEmpty()) {
                    item { Text(stringResource(R.string.home_no_issues), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp)) }
                } else {
                    items(state.openIssues, key = { it.id }) { issue -> IssueRow(issue) { onOpenIssue(issue.id) } }
                }
            }
        }
    }
}

@Composable
private fun Header(state: HomeUiState.Loaded) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.nav_home), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            if (state.addressLine.isNotBlank()) Text(state.addressLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val (label, tone) = when {
            state.monitoring && state.simulated -> stringResource(R.string.home_sensor_simulated) to ChipTone.Cold
            state.monitoring -> stringResource(R.string.home_sensor_live) to ChipTone.Good
            else -> stringResource(R.string.home_sensor_off) to ChipTone.Neutral
        }
        StatusChip(label, tone, Modifier.testTag("chip_sensor"))
    }
}

@Composable
private fun HeroCard(state: HomeUiState.Loaded, onOpenSensors: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSensors).testTag("hero_card"),
        colors = CardDefaults.cardColors(containerColor = HabitableColors.Navy, contentColor = Color.White),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_current_reading).uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFF9FB0C8))
            val latest = state.latest
            Text(
                text = latest?.let { String.format(Locale.US, "%.1f°F", it.tempF) } ?: "—",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp),
                color = Color.White,
                modifier = Modifier.testTag("current_temp"),
            )
            when (val s = state.status) {
                is HeatStatus.BelowMinimum -> StatusChipDark(stringResource(R.string.home_below_limit, s.shortfallF, s.requiredF), HabitableColors.HeatSoft)
                is HeatStatus.AboveMaximum -> StatusChipDark(stringResource(R.string.home_above_limit, s.excessF, s.maximumF), HabitableColors.HeatSoft)
                is HeatStatus.Compliant -> StatusChipDark("Within the ${s.requiredF}°F limit", HabitableColors.GoodSoft)
                HeatStatus.OutOfSeason -> StatusChipDark("Outside the Sept 15 – May 31 heating season", Color(0xFFC9D6E8))
                null -> Unit
            }
            Spacer(Modifier.height(10.dp))
            if (state.chart.isEmpty) {
                Text(stringResource(R.string.sensors_empty), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9D6E8))
            } else {
                TemperatureChart(
                    data = state.chart,
                    height = 96.dp,
                    showAxes = false,
                    showLegend = false,
                    indoorColor = Color.White,
                    gridColor = Color(0x33FFFFFF),
                    labelColor = Color(0xFF9FB0C8),
                    shadeColor = HabitableColors.Heat,
                )
            }
        }
    }
}

@Composable
private fun StatusChipDark(text: String, fg: Color) {
    Box(
        Modifier
            .padding(top = 4.dp)
            .background(Color(0x1FFFFFFF), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("issue_row"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint, bg) = when (issue.category) {
                IssueCategory.HEAT -> Triple(Icons.Filled.Thermostat, HabitableColors.HeatInk, HabitableColors.HeatSoft)
                IssueCategory.WATER, IssueCategory.MOLD -> Triple(Icons.Filled.WaterDrop, HabitableColors.ColdInk, HabitableColors.ColdSoft)
                else -> Triple(Icons.Outlined.ReportProblem, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
            }
            IconDot(icon, tint, bg)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(issue.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (issue.openedAutomatically) "${issue.status.label} · ${stringResource(R.string.issue_opened_auto)}" else issue.status.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}