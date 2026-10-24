package edu.bu.cs501.habitable.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R

@Composable
fun SetupScreen(viewModel: SetupViewModel, onDone: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = form.address, onValueChange = viewModel::setAddress,
            label = { Text(stringResource(R.string.setup_address)) },
            isError = form.addressError,
            supportingText = { if (form.addressError) Text(stringResource(R.string.setup_address_required)) },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("field_address"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = form.unit, onValueChange = viewModel::setUnit,
                label = { Text(stringResource(R.string.setup_unit)) }, singleLine = true, modifier = Modifier.weight(1f).testTag("field_unit"),
            )
            OutlinedTextField(
                value = form.city, onValueChange = viewModel::setCity,
                label = { Text(stringResource(R.string.setup_city)) }, singleLine = true, modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = form.landlordName, onValueChange = viewModel::setLandlordName,
            label = { Text(stringResource(R.string.setup_landlord_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.landlordEmail, onValueChange = viewModel::setLandlordEmail,
            label = { Text(stringResource(R.string.setup_landlord_email)) },
            isError = form.emailError,
            supportingText = { if (form.emailError) Text(stringResource(R.string.setup_email_invalid)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("field_landlord_email"),
        )
        Button(
            onClick = { viewModel.save(onDone) },
            enabled = !form.saving,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_continue"),
        ) { Text(stringResource(R.string.setup_continue)) }
        Text(stringResource(R.string.not_legal_advice), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
