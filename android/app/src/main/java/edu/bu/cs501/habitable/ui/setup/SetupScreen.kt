package edu.bu.cs501.habitable.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.ui.components.SectionHeader
import edu.bu.cs501.habitable.ui.components.SurfaceCard
import edu.bu.cs501.habitable.ui.theme.HabitableBrushes
import edu.bu.cs501.habitable.ui.theme.HabitableColors

@Composable
fun SetupScreen(viewModel: SetupViewModel, onDone: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val fieldShape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WelcomeBanner()

        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionHeader(stringResource(R.string.more_apartment))
        SurfaceCard {
            OutlinedTextField(
                value = form.address, onValueChange = viewModel::setAddress,
                label = { Text(stringResource(R.string.setup_address)) },
                leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                isError = form.addressError,
                supportingText = { if (form.addressError) Text(stringResource(R.string.setup_address_required)) },
                singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth().testTag("field_address"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = form.unit, onValueChange = viewModel::setUnit,
                    label = { Text(stringResource(R.string.setup_unit)) },
                    leadingIcon = { Icon(Icons.Filled.Apartment, contentDescription = null) },
                    singleLine = true, shape = fieldShape, modifier = Modifier.weight(1f).testTag("field_unit"),
                )
                OutlinedTextField(
                    value = form.city, onValueChange = viewModel::setCity,
                    label = { Text(stringResource(R.string.setup_city)) },
                    leadingIcon = { Icon(Icons.Filled.LocationCity, contentDescription = null) },
                    singleLine = true, shape = fieldShape, modifier = Modifier.weight(1f),
                )
            }
        }

        SectionHeader("Landlord")
        SurfaceCard {
            OutlinedTextField(
                value = form.landlordName, onValueChange = viewModel::setLandlordName,
                label = { Text(stringResource(R.string.setup_landlord_name)) },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.landlordEmail, onValueChange = viewModel::setLandlordEmail,
                label = { Text(stringResource(R.string.setup_landlord_email)) },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                isError = form.emailError,
                supportingText = {
                    if (form.emailError) Text(stringResource(R.string.setup_email_invalid))
                    else Text("Used only to pre-fill your notice letters.")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth().testTag("field_landlord_email"),
            )
        }

        Button(
            onClick = { viewModel.save(onDone) },
            enabled = !form.saving,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HabitableColors.Heat, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(54.dp).testTag("btn_continue"),
        ) {
            if (form.saving) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(10.dp))
            }
            Text(stringResource(R.string.setup_continue), fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(R.string.not_legal_advice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun WelcomeBanner() {
    val shape = MaterialTheme.shapes.large
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HabitableBrushes.Night)
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-60).dp)
                .size(200.dp)
                .background(Brush.radialGradient(listOf(HabitableColors.HeatBright.copy(alpha = 0.35f), Color.Transparent)), CircleShape)
        )
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(HabitableBrushes.Heat, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Thermostat, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Document heat problems with a dated, checkable record.",
                style = MaterialTheme.typography.titleMedium,
                color = HabitableColors.MistLight,
            )
            Spacer(Modifier.height(16.dp))
            Feature(Icons.Filled.Thermostat, "Logs your temperature every 10 minutes")
            Feature(Icons.Filled.Gavel, "Checks it against Massachusetts heat rules")
            Feature(Icons.Filled.Lock, "Photos and address stay on your phone")
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(Color(0x1FFFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = HabitableColors.HeatBright, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}
