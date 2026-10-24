package edu.bu.cs501.habitable.ui.sensors

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.data.repository.WeatherStatus
import edu.bu.cs501.habitable.service.SensorForegroundService
import edu.bu.cs501.habitable.ui.components.ChipTone
import edu.bu.cs501.habitable.ui.components.EmptyState
import edu.bu.cs501.habitable.ui.components.InlineBanner
import edu.bu.cs501.habitable.ui.components.StatusChip
import edu.bu.cs501.habitable.ui.components.TemperatureChart
import edu.bu.cs501.habitable.ui.components.TwoStats
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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sensors_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (state.simulated) stringResource(R.string.sensors_subtitle_sim) else stringResource(R.string.sensors_subtitle_ble),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(
                    if (state.monitoring) stringResource(R.string.home_sensor_live) else stringResource(R.string.home_sensor_off),
                    if (state.monitoring) ChipTone.Good else ChipTone.Neutral,
                )
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ChartRange.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = state.range == r,
                        onClick = { viewModel.setRange(r) },
                        shape = SegmentedButtonDefaults.itemShape(i, ChartRange.entries.size),
                        label = { Text(r.label) },
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(windowLabel(state), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    if (state.chart.isEmpty) {
                        EmptyState(stringResource(R.string.sensors_empty))
                    } else {
                        TemperatureChart(state.chart, modifier = Modifier.testTag("sensors_chart"), height = 230.dp)
                    }
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

            TwoStats(
                (state.lowest?.let { String.format(Locale.US, "%.1f°F", it.tempF) } ?: "—") to
                    (state.lowest?.let { "${stringResource(R.string.sensors_lowest)} · ${timeOf(it.recordedAtMillis)}" } ?: stringResource(R.string.sensors_lowest)),
                String.format(Locale.US, "%.0f h", state.hoursBelow) to stringResource(R.string.sensors_below),
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sensors_data_checks), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        val q = state.quality
                        val flagged = q.flagged + state.weatherMismatchSpans
                        if (q.total > 0 && flagged == 0) StatusChip(stringResource(R.string.sensors_all_clear), ChipTone.Good)
                        else if (q.total > 0) StatusChip(stringResource(R.string.sensors_flags, flagged), ChipTone.Heat)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.readingCount} readings · ${state.quality.jumps} impossible jumps · ${state.quality.gaps} gaps · " +
                            "${state.weatherMismatchSpans} weather mismatches" +
                            if (state.quality.simulated > 0) " · simulated data" else "",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.monitoring) {
                    OutlinedButton(onClick = { SensorForegroundService.stop(context) }, modifier = Modifier.weight(1f).testTag("btn_stop")) {
                        Icon(Icons.Filled.Stop, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.sensors_stop))
                    }
                } else {
                    Button(onClick = startMonitoring, modifier = Modifier.weight(1f).testTag("btn_start")) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.sensors_start))
                    }
                }
                Button(
                    onClick = { viewModel.replayColdNight() },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = HabitableColors.Heat),
                    modifier = Modifier.weight(1f).testTag("btn_replay"),
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.sensors_replay_night))
                }
            }
            Text(stringResource(R.string.not_legal_advice), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun windowLabel(state: SensorsUiState): String {
    val fmt = DateTimeFormatter.ofPattern("MMM d, h a").withZone(ZoneId.systemDefault())
    return "${fmt.format(Instant.ofEpochMilli(state.chart.fromMillis))} – ${fmt.format(Instant.ofEpochMilli(state.chart.toMillis))}"
}

private fun timeOf(millis: Long): String =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
