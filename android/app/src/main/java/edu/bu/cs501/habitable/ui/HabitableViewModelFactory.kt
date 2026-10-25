package edu.bu.cs501.habitable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import edu.bu.cs501.habitable.di.AppContainer
import edu.bu.cs501.habitable.ui.home.HomeViewModel
import edu.bu.cs501.habitable.ui.issuedetail.IssueDetailViewModel
import edu.bu.cs501.habitable.ui.issues.IssuesViewModel
import edu.bu.cs501.habitable.ui.issues.NewIssueViewModel
import edu.bu.cs501.habitable.ui.more.MoreViewModel
import edu.bu.cs501.habitable.ui.sensors.SensorsViewModel
import edu.bu.cs501.habitable.ui.setup.SetupViewModel

/**
 * Hand-written factory so every ViewModel receives exactly the repositories it
 * needs. [issueId] is only used by IssueDetailViewModel.
 */
class HabitableViewModelFactory(
    private val container: AppContainer,
    private val issueId: Long = -1L,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(container.apartments, container.readings, container.weather, container.issues, container.settings)
        modelClass.isAssignableFrom(SensorsViewModel::class.java) ->
            SensorsViewModel(container.apartments, container.readings, container.weather, container.settings)
        modelClass.isAssignableFrom(IssuesViewModel::class.java) ->
            IssuesViewModel(container.issues)
        modelClass.isAssignableFrom(NewIssueViewModel::class.java) ->
            NewIssueViewModel(container.issues)
        modelClass.isAssignableFrom(IssueDetailViewModel::class.java) ->
            IssueDetailViewModel(issueId, container.issues, container.apartments)
        modelClass.isAssignableFrom(SetupViewModel::class.java) ->
            SetupViewModel(container.apartments, container.settings)
        modelClass.isAssignableFrom(MoreViewModel::class.java) ->
            MoreViewModel(container.apartments, container.settings, container.sync, container.readings)
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    } as T
}
