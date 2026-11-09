package edu.bu.cs501.habitable.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.di.AppContainer
import edu.bu.cs501.habitable.ui.HabitableViewModelFactory
import edu.bu.cs501.habitable.ui.home.HomeScreen
import edu.bu.cs501.habitable.ui.issuedetail.IssueDetailScreen
import edu.bu.cs501.habitable.ui.issuedetail.IssueDetailViewModel
import edu.bu.cs501.habitable.ui.issues.IssuesScreen
import edu.bu.cs501.habitable.ui.issues.NewIssueScreen
import edu.bu.cs501.habitable.ui.more.MoreScreen
import edu.bu.cs501.habitable.ui.sensors.SensorsScreen
import edu.bu.cs501.habitable.ui.setup.SetupScreen
import edu.bu.cs501.habitable.ui.theme.HabitableColors
import kotlinx.serialization.Serializable

/**
 * Type-safe routes (Navigation Compose 2.8). Four top-level tabs plus three
 * pushed destinations. Back behaviour: pushed screens pop to the tab they came
 * from; switching tabs preserves each tab's own stack.
 */
sealed interface Route {
    @Serializable data object Setup : Route
    @Serializable data object Home : Route
    @Serializable data object Issues : Route
    @Serializable data object Sensors : Route
    @Serializable data object More : Route
    @Serializable data object NewIssue : Route
    @Serializable data class IssueDetail(val issueId: Long) : Route
}

private data class Tab(
    val route: Route,
    val labelRes: Int,
    val selected: ImageVector,
    val unselected: ImageVector,
    /** True when [NavDestination] (or any parent) is this tab's route. */
    val matches: (NavDestination) -> Boolean,
)

private val tabs = listOf(
    Tab(Route.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home) { it.hasRoute<Route.Home>() },
    Tab(Route.Issues, R.string.nav_issues, Icons.Filled.ListAlt, Icons.Outlined.ListAlt) { it.hasRoute<Route.Issues>() },
    Tab(Route.Sensors, R.string.nav_sensors, Icons.Filled.Sensors, Icons.Outlined.Sensors) { it.hasRoute<Route.Sensors>() },
    Tab(Route.More, R.string.nav_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz) { it.hasRoute<Route.More>() },
)

/**
 * Switches to a bottom-bar tab. Home is always the bottom of the stack once setup is done
 * (the graph's start destination may be Setup, which is gone by then), so:
 *  - Home: pop straight back to it. Navigating to Home with restoreState could bring back
 *    whatever screen was saved on top of it, which is how "Open full chart" got stuck.
 *  - Other tabs: pop to Home, keep each tab's state, and restore it next time.
 */
private fun NavHostController.openTab(route: Route) {
    if (route == Route.Home) {
        if (!popBackStack<Route.Home>(inclusive = false)) {
            navigate(Route.Home) { launchSingleTop = true }
        }
        return
    }
    navigate(route) {
        popUpTo<Route.Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun HabitableNavHost(
    container: AppContainer,
    startAtSetup: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = tabs.any { tab -> destination?.hierarchy?.any(tab.matches) == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .shadow(18.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), spotColor = HabitableColors.Navy.copy(alpha = 0.25f))
                        .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)),
                ) {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any(tab.matches) == true
                        val label = stringResource(tab.labelRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.openTab(tab.route) },
                            icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                            label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = HabitableColors.Navy,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    ) { padding ->
        val factory = HabitableViewModelFactory(container)
        NavHost(
            navController = navController,
            startDestination = if (startAtSetup) Route.Setup else Route.Home,
            // Phones: full width. Tablets, foldables and landscape: content stays a readable
            // column (at most 720 dp) centred on the screen instead of stretching edge to edge.
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp),
            // Short fade + slide between screens.
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 12 } },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable<Route.Setup> {
                SetupScreen(
                    viewModel = viewModel(factory = factory),
                    onDone = {
                        // Clear everything (first setup, or editing from More) so Home is the only screen left.
                        navController.navigate(Route.Home) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<Route.Home> {
                HomeScreen(
                    viewModel = viewModel(factory = factory),
                    onOpenIssue = { id -> navController.navigate(Route.IssueDetail(id)) },
                    onReportIssue = { navController.navigate(Route.NewIssue) },
                    // "Open full chart" is a tab switch, exactly like tapping Sensors in the bottom bar.
                    onOpenSensors = { navController.openTab(Route.Sensors) },
                )
            }
            composable<Route.Issues> {
                IssuesScreen(
                    viewModel = viewModel(factory = factory),
                    onOpenIssue = { id -> navController.navigate(Route.IssueDetail(id)) },
                    onReportIssue = { navController.navigate(Route.NewIssue) },
                )
            }
            composable<Route.Sensors> {
                SensorsScreen(viewModel = viewModel(factory = factory))
            }
            composable<Route.More> {
                MoreScreen(
                    viewModel = viewModel(factory = factory),
                    onEditApartment = { navController.navigate(Route.Setup) },
                )
            }
            composable<Route.NewIssue> {
                NewIssueScreen(
                    viewModel = viewModel(factory = factory),
                    onSaved = { id ->
                        navController.navigate(Route.IssueDetail(id)) {
                            popUpTo<Route.NewIssue> { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Route.IssueDetail> { entry ->
                val args = entry.toRoute<Route.IssueDetail>()
                val vm: IssueDetailViewModel = viewModel(factory = HabitableViewModelFactory(container, issueId = args.issueId))
                IssueDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}
