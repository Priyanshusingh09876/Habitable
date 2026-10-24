package edu.bu.cs501.habitable.ui.more

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.service.SensorForegroundService
import edu.bu.cs501.habitable.ui.components.SectionHeader
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

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

            SectionHeader(stringResource(R.string.more_apartment))
            Card(Modifier.fillMaxWidth().clickable(onClick = onEditApartment), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    val a = state.apartment
                    Text(a?.let { listOf(it.address, it.unit).filter { s -> s.isNotBlank() }.joinToString(" · ") } ?: "Not set up", style = MaterialTheme.typography.titleMedium)
                    Text(a?.let { "${it.city} · ${it.landlordName.ifBlank { "no landlord on file" }}" } ?: "Tap to add your apartment", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionHeader(stringResource(R.string.more_sensor_mode))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(6.dp)) {
                    val simulated = state.settings?.simulatedSensor ?: true
                    ModeRow(stringResource(R.string.more_sensor_mode_sim), selected = simulated) { viewModel.setSimulated(true); SensorForegroundService.stop(context) }
                    ModeRow(stringResource(R.string.more_sensor_mode_ble), selected = !simulated) { viewModel.setSimulated(false); SensorForegroundService.stop(context) }
                    if (!simulated) {
                        OutlinedTextField(
                            value = bleAddress.ifBlank { state.settings?.bleAddress ?: "" },
                            onValueChange = { bleAddress = it },
                            label = { Text("Sensor MAC address (AA:BB:CC:DD:EE:FF)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                        TextButton(onClick = { viewModel.setBleAddress(bleAddress) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Save address") }
                    }
                }
            }

            SectionHeader(stringResource(R.string.more_sync))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val s = state.settings
                    Text(
                        s?.lastSyncMillis?.let { stringResource(R.string.more_last_sync, timeOf(it)) } ?: stringResource(R.string.more_never_synced),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (s?.authToken == null) {
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                        Button(onClick = { viewModel.signIn(email, password) }, enabled = !state.busy && email.contains('@') && password.length >= 8, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Sign in or create account") }
                        Text("Password must be at least 8 characters. Only readings and photo hashes are uploaded.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Signed in as ${s.userEmail}", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { viewModel.syncNow() }, enabled = !state.busy, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.more_sync_now)) }
                            OutlinedButton(onClick = { viewModel.signOut() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Sign out") }
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.more_privacy))
            Text(stringResource(R.string.more_privacy_body), style = MaterialTheme.typography.bodyMedium)

            SectionHeader(stringResource(R.string.more_about))
            Text(stringResource(R.string.more_about_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { viewModel.clearDemoData() }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.more_clear_demo)) }
        }
    }
}

@Composable
private fun ModeRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onSelect)
            .semantics { role = Role.RadioButton }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.padding(4.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun timeOf(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
