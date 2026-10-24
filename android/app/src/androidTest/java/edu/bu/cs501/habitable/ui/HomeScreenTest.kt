package edu.bu.cs501.habitable.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.data.repository.WeatherStatus
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.rules.HeatStatus
import edu.bu.cs501.habitable.domain.usecase.BuildChartData
import edu.bu.cs501.habitable.ui.home.HomeContent
import edu.bu.cs501.habitable.ui.home.HomeUiState
import edu.bu.cs501.habitable.ui.theme.HabitableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test for the key flow: the Home screen shows the current reading,
 * the below-limit status, the open issue, and routes taps to the right places.
 * Drives the stateless HomeContent with a fixed state so it is deterministic.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 1_794_322_800_000L // 2026-11-10 10:00 America/New_York

    private fun loadedState(): HomeUiState.Loaded {
        val readings = (0 until 10).map { i ->
            Reading(sensorId = 1, tempF = 70.0 - i, humidityPct = null, recordedAtMillis = now - (10 - i) * 600_000L)
        }
        return HomeUiState.Loaded(
            addressLine = "12 Pratt St · Apt 3",
            latest = readings.last(),
            status = HeatStatus.BelowMinimum(requiredF = 68, shortfallF = 7.0),
            chart = BuildChartData(readings, emptyList(), now - 16 * 3_600_000L, now),
            hoursBelow = 13.0,
            outdoorNowF = 29.0,
            openIssues = listOf(
                Issue(id = 7, apartmentId = 1, category = IssueCategory.HEAT, title = "No heat · living room", description = "", status = IssueStatus.OPEN, openedAtMillis = now, closedAtMillis = null, openedAutomatically = true),
            ),
            monitoring = true,
            simulated = true,
            weather = WeatherStatus.Fresh(now, 72),
        )
    }

    @Test
    fun showsReadingStatusAndIssue_andRoutesTaps() {
        var openedIssue: Long? = null
        var reportTapped = false
        var sensorsTapped = false

        compose.setContent {
            HabitableTheme {
                HomeContent(
                    state = loadedState(),
                    onOpenIssue = { openedIssue = it },
                    onReportIssue = { reportTapped = true },
                    onOpenSensors = { sensorsTapped = true },
                    onRetryWeather = {},
                )
            }
        }

        compose.onNodeWithTag("current_temp").assertIsDisplayed()
        compose.onNodeWithText("61.0°F").assertIsDisplayed()
        compose.onNodeWithText("7.0° below the 68°F limit").assertIsDisplayed()
        compose.onNodeWithText("Simulated").assertIsDisplayed()
        compose.onNodeWithText("No heat · living room").assertIsDisplayed()

        compose.onAllNodesWithTag("issue_row")[0].performClick()
        assertThat(openedIssue).isEqualTo(7L)

        compose.onNodeWithTag("fab_report").performClick()
        assertThat(reportTapped).isTrue()

        compose.onNodeWithTag("hero_card").performClick()
        assertThat(sensorsTapped).isTrue()
    }

    @Test
    fun weatherError_showsRetry() {
        var retried = false
        compose.setContent {
            HabitableTheme {
                HomeContent(
                    state = loadedState().copy(weather = WeatherStatus.Error("No connection", hasCache = false)),
                    onOpenIssue = {}, onReportIssue = {}, onOpenSensors = {},
                    onRetryWeather = { retried = true },
                )
            }
        }
        compose.onNodeWithText("Retry").performClick()
        assertThat(retried).isTrue()
    }

    @Test
    fun loadingState_showsSpinnerNotContent() {
        compose.setContent {
            HabitableTheme {
                HomeContent(HomeUiState.Loading, {}, {}, {}, {})
            }
        }
        compose.onAllNodesWithTag("hero_card").assertCountEquals(0)
    }
}
