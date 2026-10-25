package edu.bu.cs501.habitable.ui.issuedetail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.NextAction
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.usecase.NoticeTemplate
import edu.bu.cs501.habitable.ui.components.EmptyState
import edu.bu.cs501.habitable.ui.components.SectionHeader
import edu.bu.cs501.habitable.ui.components.StatusChip
import edu.bu.cs501.habitable.ui.issues.tone
import edu.bu.cs501.habitable.ui.theme.HabitableColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(viewModel: IssueDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // rememberSaveable so a half-typed note survives rotation.
    var note by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? IssueDetailUiState.Loaded)?.issue?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
            )
        }
    ) { padding ->
        when (val s = state) {
            IssueDetailUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            IssueDetailUiState.NotFound -> EmptyState("This issue no longer exists.", Modifier.padding(padding))
            is IssueDetailUiState.Loaded -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(s.issue.status.label, s.issue.status.tone(), Modifier.testTag("chip_status"))
                    Text(
                        "${s.issue.category.label} · " + if (s.issue.openedAutomatically) stringResource(R.string.issue_opened_auto) else "reported by you",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (s.issue.description.isNotBlank()) Text(s.issue.description, style = MaterialTheme.typography.bodyLarge)

                SectionHeader(stringResource(R.string.issue_timeline))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        s.timeline.forEachIndexed { i, e -> TimelineRow(e, last = i == s.timeline.lastIndex) }
                        NextStepRow(s.nextAction)
                    }
                }

                when (s.nextAction) {
                    NextAction.NONE -> Unit
                    else -> Button(
                        onClick = {
                            // Notice and reminder steps open the tenant's email app with the letter filled in.
                            // The tenant reviews it and presses Send; the app only logs the step.
                            viewModel.letterFor(s)?.let { openEmail(context, it) }
                            viewModel.advance()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_advance"),
                    ) { Text(actionLabel(s.nextAction)) }
                }
                if (s.issue.status != IssueStatus.RESOLVED) {
                    OutlinedButton(onClick = { viewModel.resolve() }, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_resolve")) {
                        Text(stringResource(R.string.issue_action_resolve))
                    }
                }

                SectionHeader("Add a note")
                OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("e.g. Landlord replied: looking into it") })
                TextButton(onClick = { viewModel.addNote(note); note = "" }, enabled = note.isNotBlank()) { Text("Save note") }

                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.not_legal_advice), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Opens an email app with the letter pre-filled; falls back to the share sheet if none is installed. */
private fun openEmail(context: Context, notice: NoticeTemplate.Notice) {
    val email = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        if (notice.to.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(notice.to))
        putExtra(Intent.EXTRA_SUBJECT, notice.subject)
        putExtra(Intent.EXTRA_TEXT, notice.body)
    }
    try {
        context.startActivity(email)
    } catch (e: ActivityNotFoundException) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, notice.subject)
            putExtra(Intent.EXTRA_TEXT, "To: ${notice.to}\n\n${notice.body}")
        }
        context.startActivity(Intent.createChooser(share, "Send notice"))
    }
}

@Composable
private fun actionLabel(a: NextAction): String = when (a) {
    NextAction.SEND_NOTICE -> stringResource(R.string.issue_action_send_notice)
    NextAction.REQUEST_INSPECTION -> stringResource(R.string.issue_action_request_inspection)
    NextAction.MARK_CERTIFIED -> "Inspector certified the violation"
    NextAction.SEND_REMINDER -> stringResource(R.string.issue_action_reminder)
    NextAction.NONE -> ""
}

@Composable
private fun TimelineRow(e: TimelineEvent, last: Boolean) {
    val color = when (e.type) {
        TimelineEventType.SENSOR_VIOLATION_STARTED -> HabitableColors.Heat
        TimelineEventType.SENSOR_VIOLATION_ENDED, TimelineEventType.RESOLVED -> HabitableColors.Good
        else -> MaterialTheme.colorScheme.primary
    }
    Row(Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 4.dp).size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(timeOf(e.occurredAtMillis) + " · " + e.type.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(e.note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NextStepRow(a: NextAction) {
    if (a == NextAction.NONE) return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 4.dp).size(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.issue_next_step), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(actionLabel(a).removePrefix("Escalate → "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun timeOf(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d · h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
