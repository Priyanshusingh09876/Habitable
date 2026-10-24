package edu.bu.cs501.habitable.ui.issuedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bu.cs501.habitable.data.repository.IssuesRepository
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.NextAction
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.Violation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IssueDetailViewModel(
    private val issueId: Long,
    private val issues: IssuesRepository,
) : ViewModel() {

    val uiState: StateFlow<IssueDetailUiState> = combine(
        issues.observe(issueId),
        issues.observeTimeline(issueId),
        issues.observeViolations(issueId),
    ) { issue, events, violations ->
        if (issue == null) IssueDetailUiState.NotFound
        else IssueDetailUiState.Loaded(issue, events, violations, issue.status.nextAction)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IssueDetailUiState.Loading)

    /** The Escalate / Send notice button. */
    fun advance() = viewModelScope.launch { issues.advance(issueId) }
    fun resolve() = viewModelScope.launch { issues.resolve(issueId) }
    fun addNote(note: String) = viewModelScope.launch { issues.addNote(issueId, note) }
}

sealed interface IssueDetailUiState {
    data object Loading : IssueDetailUiState
    data object NotFound : IssueDetailUiState
    data class Loaded(
        val issue: Issue,
        val timeline: List<TimelineEvent>,
        val violations: List<Violation>,
        val nextAction: NextAction,
    ) : IssueDetailUiState
}
