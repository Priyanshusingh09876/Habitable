package edu.bu.cs501.habitable.ui

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.data.repository.IssueWriter
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.text.TextInput
import edu.bu.cs501.habitable.testdata.Gettysburg
import edu.bu.cs501.habitable.ui.issues.NewIssueViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * ViewModel unit tests with a fake repository injected through the IssueWriter
 * interface: no database, no Android, fully deterministic.
 * Each test: set up known data -> one action -> read the state -> assert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewIssueViewModelTest {

    /** Records what would have been saved; can be told to fail. */
    private class FakeIssueWriter(var failWith: Exception? = null) : IssueWriter {
        val saved = mutableListOf<Triple<IssueCategory, String, String>>()
        override suspend fun create(category: IssueCategory, title: String, description: String): Long {
            failWith?.let { throw it }
            saved += Triple(category, title, description)
            return 41L + saved.size
        }
    }

    private lateinit var fake: FakeIssueWriter
    private lateinit var vm: NewIssueViewModel

    @Before
    fun setUp() {
        // viewModelScope runs on Main; replace it so launches run immediately in the test.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fake = FakeIssueWriter()
        vm = NewIssueViewModel(fake)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsWithHeatSelectedAndNothingTyped() {
        assertThat(vm.form.category).isEqualTo(IssueCategory.HEAT)
        assertThat(vm.form.title).isEmpty()
        assertThat(vm.form.canSave).isFalse()
    }

    @Test
    fun emptyTitle_showsError_andSavesNothing() {
        var savedId: Long? = null
        vm.save { savedId = it }
        assertThat(vm.form.titleError).isTrue()
        assertThat(fake.saved).isEmpty()
        assertThat(savedId).isNull()
    }

    @Test
    fun titleBoundary_twoCharactersRejected_threeAccepted() {
        vm.setTitle("ab")
        assertThat(vm.form.canSave).isFalse()
        vm.save {}
        assertThat(vm.form.titleError).isTrue()

        vm.setTitle("abc")
        assertThat(vm.form.titleError).isFalse() // typing clears the error
        assertThat(vm.form.canSave).isTrue()
        vm.save {}
        assertThat(fake.saved).hasSize(1)
    }

    @Test
    fun spacesDoNotCountTowardTheTitle() {
        vm.setTitle("  a  ")
        assertThat(vm.form.canSave).isFalse()
    }

    @Test
    fun validIssue_isSavedTrimmed_andReportsItsId() {
        var savedId: Long? = null
        vm.setCategory(IssueCategory.WATER)
        vm.setTitle("  Leak under the sink ")
        vm.setDescription("Started Monday.\n")
        vm.save { savedId = it }

        assertThat(fake.saved).containsExactly(Triple(IssueCategory.WATER, "Leak under the sink", "Started Monday."))
        assertThat(savedId).isEqualTo(42L)
        assertThat(vm.form.saving).isFalse()
        assertThat(vm.form.saveError).isNull()
    }

    @Test
    fun choosingACategory_fillsAnEmptyTitle_butNeverOverwritesTypedText() {
        vm.setCategory(IssueCategory.MOLD)
        assertThat(vm.form.title).isEqualTo("Mold")

        vm.setTitle("Black spots in bathroom")
        vm.setCategory(IssueCategory.WATER)
        assertThat(vm.form.title).isEqualTo("Black spots in bathroom")
    }

    @Test
    fun storageFailure_keepsTheText_andShowsAnError() {
        fake.failWith = IllegalStateException("disk full")
        var savedId: Long? = null
        vm.setTitle("No heat")
        vm.save { savedId = it }

        assertThat(savedId).isNull()
        assertThat(vm.form.saving).isFalse()
        assertThat(vm.form.saveError).isEqualTo(NewIssueViewModel.SAVE_FAILED)
        assertThat(vm.form.title).isEqualTo("No heat")
    }

    @Test
    fun retryAfterFailure_clearsTheError() {
        fake.failWith = IllegalStateException("disk full")
        vm.setTitle("No heat")
        vm.save {}
        fake.failWith = null
        vm.save {}
        assertThat(vm.form.saveError).isNull()
        assertThat(fake.saved).hasSize(1)
    }

    @Test
    fun gettysburgPastedIntoTitle_isCutTo80_onOneLine_andSaves() {
        vm.setTitle(Gettysburg.TEXT)
        assertThat(vm.form.title).hasLength(TextInput.TITLE)
        assertThat(vm.form.title).doesNotContain("\n")
        vm.save {}
        assertThat(fake.saved.single().second.length).isAtMost(TextInput.TITLE)
    }

    @Test
    fun gettysburgPastedIntoDescription_isKeptWhole() {
        vm.setTitle("No heat")
        vm.setDescription(Gettysburg.TEXT)
        assertThat(vm.form.description).isEqualTo(Gettysburg.TEXT)
        vm.save {}
        assertThat(fake.saved.single().third).isEqualTo(Gettysburg.TEXT)
    }

    @Test
    fun descriptionLongerThanTheLimit_isCut() {
        vm.setDescription("x".repeat(TextInput.DESCRIPTION + 500))
        assertThat(vm.form.description).hasLength(TextInput.DESCRIPTION)
    }
}
