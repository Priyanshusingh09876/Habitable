package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.usecase.IssueSearch
import edu.bu.cs501.habitable.domain.usecase.IssueSearch.Scope
import org.junit.Test

class IssueSearchTest {

    private fun issue(id: Long, title: String, category: IssueCategory, status: IssueStatus, description: String = "") = Issue(
        id = id, apartmentId = 1, category = category, title = title, description = description,
        status = status, openedAtMillis = id * 1000, closedAtMillis = null, openedAutomatically = false,
    )

    private val heat = issue(1, "No heat · living room", IssueCategory.HEAT, IssueStatus.NOTICE_SENT)
    private val leak = issue(2, "Bathroom ceiling", IssueCategory.WATER, IssueStatus.OPEN, description = "Water drips when upstairs showers")
    private val lock = issue(3, "Front door", IssueCategory.LOCKS, IssueStatus.RESOLVED)
    private val all = listOf(heat, leak, lock)

    @Test fun `empty query with ALL returns every issue in order`() {
        assertThat(IssueSearch.apply(all, "  ", Scope.ALL)).containsExactly(heat, leak, lock).inOrder()
    }

    @Test fun `active hides resolved and resolved shows only resolved`() {
        assertThat(IssueSearch.apply(all, "", Scope.ACTIVE)).containsExactly(heat, leak)
        assertThat(IssueSearch.apply(all, "", Scope.RESOLVED)).containsExactly(lock)
    }

    @Test fun `query matches title, description and category, ignoring case`() {
        assertThat(IssueSearch.apply(all, "HEAT", Scope.ALL)).containsExactly(heat)
        assertThat(IssueSearch.apply(all, "showers", Scope.ALL)).containsExactly(leak)
        assertThat(IssueSearch.apply(all, "locks", Scope.ALL)).containsExactly(lock)
        assertThat(IssueSearch.apply(all, "locks", Scope.ACTIVE)).isEmpty()
    }
}
