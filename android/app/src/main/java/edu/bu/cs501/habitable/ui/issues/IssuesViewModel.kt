package edu.bu.cs501.habitable.ui.issues

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.bu.cs501.habitable.data.repository.IssueWriter
import edu.bu.cs501.habitable.data.repository.IssuesRepository
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.text.TextInput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class IssuesViewModel(issues: IssuesRepository) : ViewModel() {
    val issues: StateFlow<List<Issue>> = issues.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Form state for "Report issue". Input is validated here, not in the
 * composable, so the rule is testable and survives rotation.
 *
 * Depends on the small [IssueWriter] interface rather than the Room-backed
 * repository, so unit tests can inject a fake (NewIssueViewModelTest).
 */
class NewIssueViewModel(private val issues: IssueWriter) : ViewModel() {

    data class Form(
        val category: IssueCategory = IssueCategory.HEAT,
        val title: String = "",
        val description: String = "",
        val titleError: Boolean = false,
        val saving: Boolean = false,
        /** Set when storage failed; the typed text stays so the user can try again. */
        val saveError: String? = null,
    ) {
        val canSave: Boolean get() = title.trim().length >= MIN_TITLE && !saving
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
    fun setTitle(t: String) = update { it.copy(title = TextInput.singleLine(t, TextInput.TITLE), titleError = false) }
    fun setDescription(d: String) = update { it.copy(description = TextInput.multiLine(d, TextInput.DESCRIPTION)) }

    fun save(onSaved: (Long) -> Unit) {
        val f = form
        if (f.title.trim().length < MIN_TITLE) {
            update { it.copy(titleError = true) }
            return
        }
        update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val id = try {
                issues.create(f.category, f.title.trim(), f.description.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update { it.copy(saving = false, saveError = SAVE_FAILED) }
                return@launch
            }
            update { it.copy(saving = false) }
            onSaved(id)
        }
    }

    companion object {
        const val MIN_TITLE = 3
        const val SAVE_FAILED = "Could not save the issue. Your text is still here; try again."
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
