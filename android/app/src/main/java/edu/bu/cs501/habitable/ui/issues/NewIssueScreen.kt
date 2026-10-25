package edu.bu.cs501.habitable.ui.issues

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.ui.components.ChipTone
import edu.bu.cs501.habitable.ui.components.InlineBanner
import edu.bu.cs501.habitable.ui.components.SectionHeader
import edu.bu.cs501.habitable.ui.components.visual
import edu.bu.cs501.habitable.ui.theme.HabitableColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewIssueScreen(
    viewModel: NewIssueViewModel,
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The NavHost Scaffold already pads for the system bars; don't pad twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.issue_new_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Pick what's wrong, then describe it in your own words. The date and time are recorded automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader(stringResource(R.string.issue_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IssueCategory.entries.forEach { c -> CategoryOption(c, isSelected = form.category == c) { viewModel.setCategory(c) } }
            }

            SectionHeader("Details")
            OutlinedTextField(
                value = form.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.issue_title_label)) },
                placeholder = { Text("e.g. No heat in the bedroom") },
                isError = form.titleError,
                supportingText = { if (form.titleError) Text(stringResource(R.string.issue_title_required)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("field_title"),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = viewModel::setDescription,
                label = { Text(stringResource(R.string.issue_description_label)) },
                placeholder = { Text("When did it start? Which rooms? Have you told the landlord?") },
                minLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("field_description"),
            )

            InlineBanner(
                "Heat problems are also opened automatically when the sensor records 2 hours below the legal limit.",
                tone = ChipTone.Cold,
            )

            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = form.canSave,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HabitableColors.Heat, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("btn_save_issue"),
            ) { Text(stringResource(R.string.issue_save), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CategoryOption(category: IssueCategory, isSelected: Boolean, onClick: () -> Unit) {
    val v = category.visual()
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .clip(shape)
            .background(if (isSelected) HabitableColors.Navy else MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(1.dp, if (isSelected) HabitableColors.Navy else MaterialTheme.colorScheme.outlineVariant),
                shape,
            )
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton; selected = isSelected }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(v.icon, contentDescription = null, tint = if (isSelected) Color.White else v.tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            category.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}
