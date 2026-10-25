package edu.bu.cs501.habitable.ui.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.ui.components.CategoryBadge
import edu.bu.cs501.habitable.ui.components.EmptyState
import edu.bu.cs501.habitable.ui.components.EscalationProgress
import edu.bu.cs501.habitable.ui.components.EscalationSteps
import edu.bu.cs501.habitable.ui.components.ScreenHeader
import edu.bu.cs501.habitable.ui.components.StatusChip
import edu.bu.cs501.habitable.ui.components.SurfaceCard
import edu.bu.cs501.habitable.ui.components.stepsDone
import edu.bu.cs501.habitable.ui.components.tone
import edu.bu.cs501.habitable.ui.theme.HabitableColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class IssueFilter(val label: String) { ALL("All"), OPEN("Active"), RESOLVED("Resolved") }

@Composable
fun IssuesScreen(
    viewModel: IssuesViewModel,
    onOpenIssue: (Long) -> Unit,
    onReportIssue: () -> Unit,
) {
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(IssueFilter.ALL) }
    val active = issues.count { it.status != IssueStatus.RESOLVED }
    val shown = when (filter) {
        IssueFilter.ALL -> issues
        IssueFilter.OPEN -> issues.filter { it.status != IssueStatus.RESOLVED }
        IssueFilter.RESOLVED -> issues.filter { it.status == IssueStatus.RESOLVED }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The NavHost Scaffold already pads for the system bars; don't pad twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onReportIssue,
                containerColor = HabitableColors.Heat,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_report_issue), fontWeight = FontWeight.Bold) },
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.issues_title),
                    eyebrow = "Your record",
                    subtitle = "$active active · ${issues.size - active} resolved",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IssueFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(f.label) },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HabitableColors.Navy,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    SurfaceCard {
                        EmptyState(stringResource(R.string.issues_empty), icon = Icons.Outlined.Description, title = "Nothing here yet")
                    }
                }
            } else {
                items(shown, key = { it.id }) { issue -> IssueCard(issue) { onOpenIssue(issue.id) } }
            }
        }
    }
}

@Composable
fun IssueCard(issue: Issue, onClick: () -> Unit) {
    SurfaceCard(Modifier.testTag("issue_card_${issue.id}"), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(issue.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(issue.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    "${issue.category.label} · opened ${dateOf(issue.openedAtMillis)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(issue.status.label, issue.status.tone(), dot = true)
            Spacer(Modifier.width(10.dp))
            Text(
                if (issue.status == IssueStatus.RESOLVED) "Closed" else "Step ${issue.status.stepsDone()} of 4 · ${EscalationSteps[issue.status.stepsDone() - 1]}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        EscalationProgress(issue.status)
    }
}

fun dateOf(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
