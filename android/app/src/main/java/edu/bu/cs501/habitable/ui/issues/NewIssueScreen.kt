package edu.bu.cs501.habitable.ui.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewIssueScreen(
    viewModel: NewIssueViewModel,
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.issue_new_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.issue_category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IssueCategory.entries.forEach { c ->
                    FilterChip(selected = form.category == c, onClick = { viewModel.setCategory(c) }, label = { Text(c.label) })
                }
            }
            OutlinedTextField(
                value = form.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.issue_title_label)) },
                isError = form.titleError,
                supportingText = { if (form.titleError) Text(stringResource(R.string.issue_title_required)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("field_title"),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = viewModel::setDescription,
                label = { Text(stringResource(R.string.issue_description_label)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("field_description"),
            )
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = form.canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_save_issue"),
            ) { Text(stringResource(R.string.issue_save)) }
        }
    }
}
