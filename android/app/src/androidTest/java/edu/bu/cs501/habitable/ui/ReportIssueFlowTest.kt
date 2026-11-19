package edu.bu.cs501.habitable.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.data.local.HabitableDatabase
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.repository.IssuesRepository
import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.text.TextInput
import edu.bu.cs501.habitable.testdata.Gettysburg
import edu.bu.cs501.habitable.ui.issues.IssuesScreen
import edu.bu.cs501.habitable.ui.issues.IssuesViewModel
import edu.bu.cs501.habitable.ui.issues.NewIssueScreen
import edu.bu.cs501.habitable.ui.issues.NewIssueViewModel
import edu.bu.cs501.habitable.ui.theme.HabitableTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI tests of one complete task: the real screens, real view models and a
 * real (in-memory) Room database. Nodes are found by their test tags and visible text,
 * the way a user (or TalkBack) finds them.
 */
@RunWith(AndroidJUnit4::class)
class ReportIssueFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: HabitableDatabase
    private lateinit var issues: IssuesRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HabitableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        issues = IssuesRepository(db)
        ApartmentRepository(db).save(
            Apartment(address = "12 Pratt St", unit = "3", city = "Boston", latitude = 42.36, longitude = -71.06, landlordName = "Mark Rivera", landlordEmail = "landlord@example.com")
        )
        Unit
    }

    @After
    fun tearDown() = db.close()

    private fun showReportScreen(onSaved: (Long) -> Unit = {}) {
        val vm = NewIssueViewModel(issues)
        compose.setContent {
            HabitableTheme { NewIssueScreen(viewModel = vm, onSaved = onSaved, onBack = {}) }
        }
    }

    @Test
    fun reportAnIssue_isSavedToTheDatabase() {
        var savedId: Long? = null
        showReportScreen { savedId = it }

        compose.onNodeWithTag("field_title").performTextReplacement("No heat in the bedroom")
        compose.onNodeWithTag("field_description").performTextReplacement("Radiator cold since Monday night.")
        compose.onNodeWithTag("btn_save_issue").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { savedId != null }
        val stored = runBlocking { issues.all.first() }.single()
        assertThat(stored.id).isEqualTo(savedId)
        assertThat(stored.title).isEqualTo("No heat in the bedroom")
        assertThat(stored.description).isEqualTo("Radiator cold since Monday night.")
        assertThat(stored.category).isEqualTo(IssueCategory.HEAT)
    }

    @Test
    fun gettysburgPastedIntoTitle_isCutTo80_andTheScreenSaysSo() {
        showReportScreen()

        compose.onNodeWithTag("field_title").performTextReplacement(Gettysburg.TEXT)

        val expected = TextInput.singleLine(Gettysburg.TEXT, TextInput.TITLE)
        compose.onNodeWithTag("field_title").assert(hasText(expected))
        compose.onNodeWithText("Limit reached: ${TextInput.TITLE} characters").assertIsDisplayed()
    }

    @Test
    fun gettysburgPastedIntoDescription_isKeptAndSaved() {
        var savedId: Long? = null
        showReportScreen { savedId = it }

        compose.onNodeWithTag("field_title").performTextReplacement("No heat")
        compose.onNodeWithTag("field_description").performTextReplacement(Gettysburg.TEXT)
        compose.onNodeWithTag("btn_save_issue").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { savedId != null }
        assertThat(runBlocking { issues.all.first() }.single().description).isEqualTo(Gettysburg.TEXT)
    }

    @Test
    fun issueList_searchFiltersAndTapOpensTheIssue() {
        val heatId = runBlocking { issues.create(IssueCategory.HEAT, "No heat in the bedroom", "") }
        val leakId = runBlocking { issues.create(IssueCategory.WATER, "Leak under the sink", "") }
        var opened: Long? = null
        val vm = IssuesViewModel(issues)
        compose.setContent {
            HabitableTheme { IssuesScreen(viewModel = vm, onOpenIssue = { opened = it }, onReportIssue = {}) }
        }

        // Both issues are listed (scroll first: on small screens the second card may be off-screen).
        compose.onNodeWithTag("issues_list").performScrollToNode(hasText("Leak under the sink"))
        compose.onNodeWithTag("issues_list").performScrollToNode(hasText("No heat in the bedroom"))
        compose.onNodeWithText("No heat in the bedroom").assertExists()

        compose.onNodeWithTag("issues_search").performTextInput("leak")
        compose.onNodeWithText("No heat in the bedroom").assertDoesNotExist()
        compose.onNodeWithTag("issues_list").performScrollToNode(hasText("Leak under the sink"))
        compose.onNodeWithTag("issue_card_$leakId").performClick()

        assertThat(opened).isEqualTo(leakId)
        assertThat(opened).isNotEqualTo(heatId)
    }

    @Test
    fun gettysburgPastedIntoSearch_showsNoMatches_insteadOfCrashing() {
        runBlocking { issues.create(IssueCategory.HEAT, "No heat in the bedroom", "") }
        val vm = IssuesViewModel(issues)
        compose.setContent {
            HabitableTheme { IssuesScreen(viewModel = vm, onOpenIssue = {}, onReportIssue = {}) }
        }

        compose.onNodeWithTag("issues_search").performTextReplacement(Gettysburg.TEXT)

        compose.onNodeWithTag("issues_search").assert(hasText(TextInput.singleLine(Gettysburg.TEXT, TextInput.SEARCH)))
        compose.onNodeWithTag("issues_list").performScrollToNode(hasText("Nothing here yet"))
        compose.onNodeWithText("No heat in the bedroom").assertDoesNotExist()
    }
}
