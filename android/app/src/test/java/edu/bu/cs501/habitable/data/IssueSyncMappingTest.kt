package edu.bu.cs501.habitable.data

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.data.remote.backend.IssueDto
import edu.bu.cs501.habitable.data.remote.backend.MAX_ISSUE_TITLE
import edu.bu.cs501.habitable.data.remote.backend.toDto
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class IssueSyncMappingTest {

    private val issue = Issue(
        id = 7, apartmentId = 1, category = IssueCategory.HEAT, title = "No heat · living room",
        description = "Private note that must stay on the phone", status = IssueStatus.NOTICE_SENT,
        openedAtMillis = 1_794_322_800_000L, closedAtMillis = null, openedAutomatically = true,
    )

    @Test fun `issue maps to the fields the backend validates, without the description`() {
        val dto = issue.toDto()
        assertThat(dto).isEqualTo(
            IssueDto(clientId = 7, category = "HEAT", title = "No heat · living room", status = "NOTICE_SENT", openedAt = 1_794_322_800_000L, closedAt = null)
        )
        val keys = Json.parseToJsonElement(Json.encodeToString(dto)).jsonObject.keys
        assertThat(keys).containsAtLeast("clientId", "category", "title", "status", "openedAt")
        assertThat(keys).doesNotContain("description")
    }

    @Test fun `long titles are cut to the backend limit and resolved issues keep closedAt`() {
        val dto = issue.copy(title = "x".repeat(300), status = IssueStatus.RESOLVED, closedAtMillis = 1_794_400_000_000L).toDto()
        assertThat(dto.title.length).isEqualTo(MAX_ISSUE_TITLE)
        assertThat(dto.status).isEqualTo("RESOLVED")
        assertThat(dto.closedAt).isEqualTo(1_794_400_000_000L)
    }
}
