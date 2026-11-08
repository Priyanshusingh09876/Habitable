package edu.bu.cs501.habitable.data.remote.backend

import edu.bu.cs501.habitable.domain.model.Issue

/** Longest title the backend accepts (see backend/src/middleware/validate.js). */
const val MAX_ISSUE_TITLE = 120

/**
 * What an issue looks like on the server. Only the fields needed to show the
 * escalation history go up: the tenant's description stays on the phone.
 */
fun Issue.toDto(): IssueDto = IssueDto(
    clientId = id,
    category = category.name,
    title = title.take(MAX_ISSUE_TITLE),
    status = status.name,
    openedAt = openedAtMillis,
    closedAt = closedAtMillis,
)
