package edu.bu.cs501.habitable.ui.sensors

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.data.repository.WeatherStatus
import edu.bu.cs501.habitable.service.SensorForegroundService
import edu.bu.cs501.habitable.ui.components.ChipTone
import edu.bu.cs501.habitable.ui.components.EmptyState
import edu.bu.cs501.habitable.ui.components.IconTile
import edu.bu.cs501.habitable.ui.components.InlineBanner
import edu.bu.cs501.habitable.ui.components.ScreenHeader
import edu.bu.cs501.habitable.ui.components.SectionHeader
import edu.bu.cs501.habitable.ui.components.StatTile
import edu.bu.cs501.habitable.ui.components.StatusChip
import edu.bu.cs501.habitable.ui.components.SurfaceCard
import edu.bu.cs501.habitable.ui.components.TemperatureChart
import edu.bu.cs501.habitable.ui.theme.HabitableColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SensorsScreen(viewModel: SensorsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) { viewModel.tick(); onPauseOrDispose { } }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    // Android 13+ needs POST_NOTIFICATIONS for the foreground-service notification to be visible.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        SensorForegroundService.start(context)
    }
    val startMonitoring = {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else SensorForegroundService.start(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The NavHost Scaffold already pads for the system bars; don't pad twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = RoundedCornerShape(14.dp), containerColor = HabitableColors.Navy) } },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(
                title = stringResource(R.string.sensors_title),
                eyebrow = "Living room",
                subtitle = if (state.simulated) stringResource(R.string.sensors_subtitle_sim) else stringResource(R.string.sensors_subtitle_ble),
                trailing = {
                    StatusChip(
                        if (state.monitoring) stringResource(R.string.home_sensor_live) else stringResource(R.string.home_sensor_off),
                        if (state.monitoring) ChipTone.Good else ChipTone.Neutral,
                        dot = true, pulse = state.monitoring,
                    )
                },
            )

            // Device card + controls
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(
                        icon = if (state.simulated) Icons.Filled.Science else Icons.Filled.Bluetooth,
                        tint = Color.White,
                        background = if (state.monitoring) HabitableColors.Good else HabitableColors.Navy,
                        size = 48.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (state.simulated) "Simulated thermometer" else "Govee BLE thermometer", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.monitoring) "Recording in the background" else "Not recording",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.monitoring) {
                        OutlinedButton(
                            onClick = { SensorForegroundService.stop(context) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(50.dp).testTag("btn_stop"),
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.sensors_stop))
                        }
                    } else {
                        Button(
                            onClick = startMonitoring,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HabitableColors.Navy),
                            modifier = Modifier.weight(1f).height(50.dp).testTag("btn_start"),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.sensors_start))
                        }
                    }
                    Button(
                        onClick = { viewModel.replayColdNight() },
                        enabled = !state.busy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HabitableColors.Heat),
                        modifier = Modifier.weight(1f).height(50.dp).testTag("btn_replay"),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        else Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.sensors_replay_night))
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ChartRange.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = state.range == r,
                        onClick = { viewModel.setRange(r) },
                        shape = SegmentedButtonDefaults.itemShape(i, ChartRange.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = HabitableColors.Navy,
                            activeContentColor = Color.White,
                        ),
                        label = { Text(r.label) },
                    )
                }
            }

            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Indoor vs. legal limit", style = MaterialTheme.typography.titleMedium)
                        Text(windowLabel(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.Insights, contentDescription = null, tint = HabitableColors.Heat)
                }
                Spacer(Modifier.height(12.dp))
                if (state.chart.isEmpty) {
                    EmptyState(stringResource(R.string.sensors_empty), icon = Icons.Filled.Insights, title = "Nothing recorded yet")
                } else {
                    TemperatureChart(state.chart, modifier = Modifier.testTag("sensors_chart"), height = 240.dp)
                }
            }

            when (val w = state.weather) {
                WeatherStatus.Loading -> InlineBanner(stringResource(R.string.weather_loading), loading = true)
                is WeatherStatus.Error -> InlineBanner(
                    if (w.hasCache) stringResource(R.string.weather_stale, "cache") else stringResource(R.string.weather_error),
                    tone = ChipTone.Heat, actionLabel = stringResource(R.string.weather_retry), onAction = { viewModel.refreshWeather() },
                )
                else -> Unit
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    icon = Icons.Filled.ArrowDownward,
                    value = state.lowest?.let { String.format(Locale.US, "%.1f°", it.tempF) } ?: "—",
                    label = state.lowest?.let { "${stringResource(R.string.sensors_lowest)} · ${timeOf(it.recordedAtMillis)}" } ?: stringResource(R.string.sensors_lowest),
                    accent = HabitableColors.Cold,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Filled.Timer,
                    value = String.format(Locale.US, "%.0f h", state.hoursBelow),
                    label = stringResource(R.string.sensors_below),
                    accent = HabitableColors.Heat,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Filled.Schedule,
                    value = "${state.readingCount}",
                    label = "readings",
                    accent = HabitableColors.Navy,
                    modifier = Modifier.weight(1f),
                )
            }

            SectionHeader(stringResource(R.string.sensors_data_checks))
            SurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                val q = state.quality
                val flagged = q.flagged + state.weatherMismatchSpans
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (flagged == 0) Icons.Filled.Verified else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (flagged == 0) HabitableColors.Good else HabitableColors.Heat,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Evidence quality", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${state.readingCount} readings checked" + if (q.simulated > 0) " · simulated data" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (q.total > 0 && flagged == 0) StatusChip(stringResource(R.string.sensors_all_clear), ChipTone.Good)
                    else if (q.total > 0) StatusChip(stringResource(R.string.sensors_flags, flagged), ChipTone.Heat)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CheckRow(Icons.Filled.Warning, "Impossible jumps", "More than 2 °F per minute", q.jumps)
                CheckRow(Icons.Filled.Schedule, "Gaps", "Missing readings longer than 30 min", q.gaps)
                CheckRow(Icons.Filled.Cloud, "Weather mismatches", "Indoor tracking the cold outdoors", state.weatherMismatchSpans)
            }

            Text(
                stringResource(R.string.not_legal_advice),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun CheckRow(icon: ImageVector, title: String, detail: String, count: Int) {
    val ok = count == 0
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(
            icon, if (ok) HabitableColors.GoodInk else HabitableColors.HeatInk,
            if (ok) HabitableColors.GoodSoft else HabitableColors.HeatSoft, size = 34.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "$count",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = if (ok) HabitableColors.Good else HabitableColors.Heat,
        )
    }
}

private fun windowLabel(state: SensorsUiState): String {
    val fmt = DateTimeFormatter.ofPattern("MMM d, h a").withZone(ZoneId.systemDefault())
    return "${fmt.format(Instant.ofEpochMilli(state.chart.fromMillis))} – ${fmt.format(Instant.ofEpochMilli(state.chart.toMillis))}"
}

private fun timeOf(millis: Long): String =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
