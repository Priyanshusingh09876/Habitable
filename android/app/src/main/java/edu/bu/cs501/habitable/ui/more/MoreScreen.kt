package edu.bu.cs501.habitable.ui.more

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.service.SensorForegroundService
import edu.bu.cs501.habitable.ui.components.ChipTone
import edu.bu.cs501.habitable.ui.components.IconTile
import edu.bu.cs501.habitable.ui.components.ScreenHeader
import edu.bu.cs501.habitable.ui.components.SectionHeader
import edu.bu.cs501.habitable.ui.components.StatusChip
import edu.bu.cs501.habitable.ui.components.SurfaceCard
import edu.bu.cs501.habitable.ui.theme.HabitableBrushes
import edu.bu.cs501.habitable.ui.theme.HabitableColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MoreScreen(viewModel: MoreViewModel, onEditApartment: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var bleAddress by rememberSaveable { mutableStateOf("") }
    val fieldShape = RoundedCornerShape(14.dp)

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The NavHost Scaffold already pads for the system bars; don't pad twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = RoundedCornerShape(14.dp), containerColor = HabitableColors.Navy) } },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeader(title = stringResource(R.string.more_title), eyebrow = "Settings")

            // Apartment
            SectionHeader(stringResource(R.string.more_apartment))
            SurfaceCard(onClick = onEditApartment, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(HabitableBrushes.Night, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Apartment, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        val a = state.apartment
                        Text(
                            a?.let { listOf(it.address, it.unit).filter { s -> s.isNotBlank() }.joinToString(" · ") } ?: "Not set up",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            a?.let { "${it.city} · ${it.landlordName.ifBlank { "no landlord on file" }}" } ?: "Tap to add your apartment",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("Edit", style = MaterialTheme.typography.labelLarge, color = HabitableColors.Heat)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = HabitableColors.Heat)
                }
            }

            // Sensor mode
            SectionHeader(stringResource(R.string.more_sensor_mode))
            SurfaceCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                val simulated = state.settings?.simulatedSensor ?: true
                ModeRow(
                    Icons.Filled.Science, stringResource(R.string.more_sensor_mode_sim), "Replays a cold night · no hardware needed",
                    selected = simulated,
                ) { viewModel.setSimulated(true); SensorForegroundService.stop(context) }
                HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ModeRow(
                    Icons.Filled.Bluetooth, stringResource(R.string.more_sensor_mode_ble), "Govee H5075-style advertisements",
                    selected = !simulated,
                ) { viewModel.setSimulated(false); SensorForegroundService.stop(context) }
                if (!simulated) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = bleAddress.ifBlank { state.settings?.bleAddress ?: "" },
                            onValueChange = { bleAddress = it },
                            label = { Text("Sensor MAC address (AA:BB:CC:DD:EE:FF)") },
                            singleLine = true,
                            shape = fieldShape,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = { viewModel.setBleAddress(bleAddress) }) { Text("Save address", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            // Sync
            SectionHeader(stringResource(R.string.more_sync))
            SurfaceCard {
                val s = state.settings
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(
                        if (s?.authToken != null) Icons.Filled.CloudDone else Icons.Filled.Sync,
                        HabitableColors.ColdInk, HabitableColors.ColdSoft, size = 40.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (s?.authToken != null) "Signed in as ${s.userEmail}" else "Not signed in", style = MaterialTheme.typography.titleSmall)
                        Text(
                            s?.lastSyncMillis?.let { stringResource(R.string.more_last_sync, timeOf(it)) } ?: stringResource(R.string.more_never_synced),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s?.authToken != null) StatusChip("Online", ChipTone.Good, dot = true)
                }
                Spacer(Modifier.height(12.dp))
                if (s?.authToken == null) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, shape = fieldShape, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.signIn(email, password) },
                        enabled = !state.busy && email.contains('@') && password.length >= 8,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HabitableColors.Navy),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("Sign in or create account", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(6.dp))
                    Text("Password must be at least 8 characters. Only readings and photo hashes are uploaded.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.syncNow() },
                            enabled = !state.busy,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HabitableColors.Navy),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.more_sync_now))
                        }
                        OutlinedButton(onClick = { viewModel.signOut() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(48.dp)) { Text("Sign out") }
                    }
                }
            }

            // Privacy & about
            SectionHeader(stringResource(R.string.more_privacy))
            SurfaceCard {
                InfoRow(Icons.Filled.Security, HabitableColors.GoodInk, HabitableColors.GoodSoft, stringResource(R.string.more_privacy_body))
            }
            SectionHeader(stringResource(R.string.more_about))
            SurfaceCard {
                InfoRow(Icons.Filled.Info, HabitableColors.Navy, HabitableColors.Card, stringResource(R.string.more_about_body))
            }

            OutlinedButton(
                onClick = { viewModel.clearDemoData() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HabitableColors.HeatInk),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(50.dp),
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.more_clear_demo))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ModeRow(icon: ImageVector, label: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onSelect)
            .semantics { role = Role.RadioButton }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon,
            if (selected) Color.White else HabitableColors.Navy,
            if (selected) HabitableColors.Navy else HabitableColors.Card,
            size = 38.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = HabitableColors.Heat))
    }
}

@Composable
private fun InfoRow(icon: ImageVector, tint: Color, bg: Color, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun timeOf(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
