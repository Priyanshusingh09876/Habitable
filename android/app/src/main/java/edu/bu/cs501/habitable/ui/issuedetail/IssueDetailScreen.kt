package edu.bu.cs501.habitable.ui.issuedetail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.NextAction
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.usecase.NoticeTemplate
import edu.bu.cs501.habitable.ui.components.CategoryBadge
import edu.bu.cs501.habitable.ui.components.ChipTone
import edu.bu.cs501.habitable.ui.components.EmptyState
import edu.bu.cs501.habitable.ui.components.EscalationSteps
import edu.bu.cs501.habitable.ui.components.SectionHeader
import edu.bu.cs501.habitable.ui.components.StatTile
import edu.bu.cs501.habitable.ui.components.StatusChip
import edu.bu.cs501.habitable.ui.components.SurfaceCard
import edu.bu.cs501.habitable.ui.components.stepsDone
import edu.bu.cs501.habitable.ui.components.tone
import edu.bu.cs501.habitable.ui.theme.EvidenceFont
import edu.bu.cs501.habitable.ui.theme.HabitableColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(viewModel: IssueDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // rememberSaveable so a half-typed note survives rotation.
    var note by rememberSaveable { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The NavHost Scaffold already pads for the system bars; don't pad twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Issue", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        }
    ) { padding ->
        when (val s = state) {
            IssueDetailUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HabitableColors.Heat)
            }
            IssueDetailUiState.NotFound -> EmptyState("This issue no longer exists.", Modifier.padding(padding))
            is IssueDetailUiState.Loaded -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(s.issue.category, size = 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.issue.title, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "${s.issue.category.label} · " + if (s.issue.openedAutomatically) stringResource(R.string.issue_opened_auto) else "reported by you",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(s.issue.status.label, s.issue.status.tone(), Modifier.testTag("chip_status"), dot = true)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Opened ${timeOf(s.issue.openedAtMillis)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = EvidenceFont),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s.issue.description.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(s.issue.description, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // Evidence summary (heat issues with sensor data)
                if (s.violations.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val hours = s.violations.sumOf { ((it.endedAtMillis ?: now) - it.startedAtMillis).coerceAtLeast(0L) } / 3_600_000.0
                    val lowest = s.violations.minOf { it.minTempF }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(Icons.Filled.Timer, String.format(Locale.US, "%.0f h", hours), "below the legal limit", HabitableColors.Heat, Modifier.weight(1f))
                        StatTile(Icons.Filled.ArrowDownward, String.format(Locale.US, "%.1f°F", lowest), "lowest recorded", HabitableColors.Cold, Modifier.weight(1f))
                        StatTile(Icons.Filled.AcUnit, "${s.violations.size}", if (s.violations.size == 1) "cold period" else "cold periods", HabitableColors.Navy, Modifier.weight(1f))
                    }
                }

                // Escalation path
                SectionHeader("Escalation path")
                SurfaceCard {
                    EscalationStepper(s.issue.status)
                }

                // Next step + action
                if (s.nextAction != NextAction.NONE) {
                    SurfaceCard(containerColor = HabitableColors.Navy) {
                        Text(stringResource(R.string.issue_next_step).uppercase(), style = MaterialTheme.typography.labelMedium, color = HabitableColors.HeatBright)
                        Spacer(Modifier.height(4.dp))
                        Text(actionLabel(s.nextAction).removePrefix("Escalate → "), style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            actionHelp(s.nextAction, s.apartment?.landlordName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HabitableColors.MistLight,
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                // Notice and reminder steps open the tenant's email app with the letter filled in.
                                // The tenant reviews it and presses Send; the app only logs the step.
                                viewModel.letterFor(s)?.let { openEmail(context, it) }
                                viewModel.advance()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HabitableColors.Heat, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_advance"),
                        ) {
                            Icon(actionIcon(s.nextAction), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(actionLabel(s.nextAction), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (s.issue.status != IssueStatus.RESOLVED) {
                    OutlinedButton(
                        onClick = { viewModel.resolve() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("btn_resolve"),
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = HabitableColors.Good, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.issue_action_resolve))
                    }
                }

                // Timeline
                SectionHeader("${stringResource(R.string.issue_timeline)} · ${s.timeline.size}")
                SurfaceCard {
                    if (s.timeline.isEmpty()) {
                        Text("No events yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    s.timeline.forEachIndexed { i, e -> TimelineRow(e, last = i == s.timeline.lastIndex) }
                }

                // Note
                SectionHeader("Add a note")
                SurfaceCard {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("e.g. Landlord replied: looking into it") },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${note.length}/500", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { viewModel.addNote(note); note = "" }, enabled = note.isNotBlank(), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save note")
                        }
                    }
                }

                Text(
                    stringResource(R.string.not_legal_advice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
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

private fun actionHelp(a: NextAction, landlord: String?): String = when (a) {
    NextAction.SEND_NOTICE ->
        "Opens your email app with a dated letter to ${landlord?.ifBlank { null } ?: "your landlord"} that cites 105 CMR 410.180 and your sensor record. You review it and press Send."
    NextAction.REQUEST_INSPECTION ->
        "If the problem continues, ask the city to inspect (Boston: call 311 or Inspectional Services). Log it here once you have asked."
    NextAction.MARK_CERTIFIED ->
        "When the inspector cites the violation, record it here so your timeline shows the official finding."
    NextAction.SEND_REMINDER ->
        "Send the landlord a follow-up letter that refers to the certified violation."
    NextAction.NONE -> ""
}

private fun actionIcon(a: NextAction): ImageVector = when (a) {
    NextAction.SEND_NOTICE, NextAction.SEND_REMINDER -> Icons.AutoMirrored.Filled.Send
    NextAction.REQUEST_INSPECTION -> Icons.Filled.Search
    NextAction.MARK_CERTIFIED -> Icons.Filled.Gavel
    NextAction.NONE -> Icons.Filled.CheckCircle
}

/** Four circles joined by a line: Open → Notice → Inspection → Certified. */
@Composable
private fun EscalationStepper(status: IssueStatus) {
    val done = status.stepsDone()
    val resolved = status == IssueStatus.RESOLVED
    val accent = if (resolved) HabitableColors.Good else HabitableColors.Heat
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EscalationSteps.forEachIndexed { i, _ ->
                val isDone = i < done
                val isCurrent = i == done - 1 && !resolved
                Box(
                    Modifier
                        .size(if (isCurrent) 30.dp else 26.dp)
                        .background(if (isDone) accent else MaterialTheme.colorScheme.surface, CircleShape)
                        .border(2.dp, if (isDone) accent else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDone && !isCurrent) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isDone) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (i < EscalationSteps.lastIndex) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .padding(horizontal = 4.dp)
                            .background(if (i < done - 1) accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            EscalationSteps.forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == done - 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (i < done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        EscalationSteps.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (resolved) {
            Spacer(Modifier.height(10.dp))
            StatusChip("Resolved — record kept", ChipTone.Good, icon = Icons.Filled.Verified)
        }
    }
}

private data class EventStyle(val icon: ImageVector, val color: Color, val label: String)

private fun TimelineEventType.style(): EventStyle = when (this) {
    TimelineEventType.SENSOR_VIOLATION_STARTED -> EventStyle(Icons.Filled.AcUnit, HabitableColors.Heat, "Below the limit")
    TimelineEventType.SENSOR_VIOLATION_ENDED -> EventStyle(Icons.Filled.Thermostat, HabitableColors.Good, "Back within the limit")
    TimelineEventType.PHOTO_ADDED -> EventStyle(Icons.Filled.PhotoCamera, HabitableColors.Cold, "Photo added")
    TimelineEventType.NOTICE_SENT -> EventStyle(Icons.Filled.Email, HabitableColors.Amber, "Notice sent")
    TimelineEventType.INSPECTION_REQUESTED -> EventStyle(Icons.Filled.Search, HabitableColors.Cold, "Inspection requested")
    TimelineEventType.VIOLATION_CERTIFIED -> EventStyle(Icons.Filled.Gavel, HabitableColors.Navy, "Violation certified")
    TimelineEventType.REMINDER_SENT -> EventStyle(Icons.Filled.Email, HabitableColors.Amber, "Reminder sent")
    TimelineEventType.LANDLORD_REPLY -> EventStyle(Icons.Filled.Email, HabitableColors.Good, "Landlord replied")
    TimelineEventType.NOTE -> EventStyle(Icons.Filled.Edit, HabitableColors.Muted, "Note")
    TimelineEventType.RESOLVED -> EventStyle(Icons.Filled.CheckCircle, HabitableColors.Good, "Resolved")
}

@Composable
private fun TimelineRow(e: TimelineEvent, last: Boolean) {
    val st = e.type.style()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(32.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(28.dp).background(st.color.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(st.icon, contentDescription = null, tint = st.color, modifier = Modifier.size(15.dp))
            }
            if (!last) {
                Box(
                    Modifier
                        .padding(vertical = 2.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp))
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 16.dp, top = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    timeOf(e.occurredAtMillis),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = EvidenceFont),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (e.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(e.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun timeOf(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d · h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

