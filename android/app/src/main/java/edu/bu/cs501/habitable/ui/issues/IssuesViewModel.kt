package edu.bu.cs501.habitable.ui.issues

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bu.cs501.habitable.data.repository.IssuesRepository
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IssuesViewModel(issues: IssuesRepository) : ViewModel() {
    val issues: StateFlow<List<Issue>> = issues.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Form state for "Report issue". Input is validated here, not in the
 * composable, so the rule is testable and survives rotation.
 */
class NewIssueViewModel(private val issues: IssuesRepository) : ViewModel() {

    data class Form(
        val category: IssueCategory = IssueCategory.HEAT,
        val title: String = "",
        val description: String = "",
        val titleError: Boolean = false,
        val saving: Boolean = false,
    ) {
        val canSave: Boolean get() = title.trim().length >= 3 && !saving
    }

    /**
     * Held in Compose state, not a StateFlow: text fields must see each keystroke in the same
     * frame, or fast typing can drop characters and jump the cursor. Still survives rotation
     * because it lives in the ViewModel.
     */
    var form by mutableStateOf(Form())
        private set

    private inline fun update(change: (Form) -> Form) {
        form = change(form)
    }

    fun setCategory(c: IssueCategory) = update { it.copy(category = c, title = if (it.title.isBlank()) defaultTitle(c) else it.title) }
    fun setTitle(t: String) = update { it.copy(title = t.take(80), titleError = false) }
    fun setDescription(d: String) = update { it.copy(description = d.take(2000)) }

    fun save(onSaved: (Long) -> Unit) {
        val f = form
        if (f.title.trim().length < 3) {
            update { it.copy(titleError = true) }
            return
        }
        update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = issues.create(f.category, f.title, f.description)
            update { it.copy(saving = false) }
            onSaved(id)
        }
    }

    private fun defaultTitle(c: IssueCategory) = when (c) {
        IssueCategory.HEAT -> "No heat"
        IssueCategory.WATER -> "Leak"
        IssueCategory.MOLD -> "Mold"
        IssueCategory.PESTS -> "Pests"
        IssueCategory.ELECTRICAL -> "Electrical problem"
        IssueCategory.NOISE -> "Noise"
        IssueCategory.LOCKS -> "Broken lock"
        IssueCategory.OTHER -> ""
    }
}
