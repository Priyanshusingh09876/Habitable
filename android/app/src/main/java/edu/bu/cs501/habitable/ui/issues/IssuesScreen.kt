package edu.bu.cs501.habitable.ui.issues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.ui.components.ChipTone
import edu.bu.cs501.habitable.ui.components.EmptyState
import edu.bu.cs501.habitable.ui.components.StatusChip
import edu.bu.cs501.habitable.ui.theme.HabitableColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun IssuesScreen(
    viewModel: IssuesViewModel,
    onOpenIssue: (Long) -> Unit,
    onReportIssue: () -> Unit,
) {
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onReportIssue,
                containerColor = HabitableColors.Heat,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_report_issue)) },
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text(stringResource(R.string.issues_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }
            if (issues.isEmpty()) {
                item { EmptyState(stringResource(R.string.issues_empty)) }
            } else {
                items(issues, key = { it.id }) { issue -> IssueCard(issue) { onOpenIssue(issue.id) } }
            }
        }
    }
}

@Composable
fun IssueCard(issue: Issue, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("issue_card_${issue.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(issue.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${issue.category.label} · opened ${dateOf(issue.openedAtMillis)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(issue.status.label, issue.status.tone())
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun IssueStatus.tone(): ChipTone = when (this) {
    IssueStatus.OPEN -> ChipTone.Heat
    IssueStatus.NOTICE_SENT, IssueStatus.INSPECTION_REQUESTED -> ChipTone.Cold
    IssueStatus.CERTIFIED -> ChipTone.Neutral
    IssueStatus.RESOLVED -> ChipTone.Good
}

fun dateOf(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
