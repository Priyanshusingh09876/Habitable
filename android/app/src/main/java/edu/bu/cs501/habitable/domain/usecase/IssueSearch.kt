package edu.bu.cs501.habitable.domain.usecase

import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueStatus

/**
 * Filters the issue list on the Issues screen: by scope (all / active /
 * resolved) and by a free-text query matched against the title, the
 * description and the category name. Pure Kotlin so it is unit-tested.
 */
object IssueSearch {

    enum class Scope(val label: String) { ALL("All"), ACTIVE("Active"), RESOLVED("Resolved") }

    fun apply(issues: List<Issue>, query: String, scope: Scope): List<Issue> {
        val q = query.trim().lowercase()
        return issues.filter { issue -> inScope(issue, scope) && matches(issue, q) }
    }

    private fun inScope(issue: Issue, scope: Scope): Boolean = when (scope) {
        Scope.ALL -> true
        Scope.ACTIVE -> issue.status != IssueStatus.RESOLVED
        Scope.RESOLVED -> issue.status == IssueStatus.RESOLVED
    }

    private fun matches(issue: Issue, q: String): Boolean =
        q.isEmpty() ||
            issue.title.lowercase().contains(q) ||
            issue.description.lowercase().contains(q) ||
            issue.category.label.lowercase().contains(q)
}
