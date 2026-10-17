package app.sawatt.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.sawatt.AppContainer
import app.sawatt.R
import app.sawatt.ui.components.SawattIcons
import app.sawatt.ui.screens.DevicesScreen
import app.sawatt.ui.screens.HistoryDetailScreen
import app.sawatt.ui.screens.HistoryScreen
import app.sawatt.ui.screens.MeScreen
import app.sawatt.ui.screens.PlanEditorScreen
import app.sawatt.ui.screens.PlansScreen
import app.sawatt.ui.screens.ProfileScreen
import app.sawatt.ui.screens.SummaryScreen
import app.sawatt.ui.screens.TodayScreen
import app.sawatt.ui.screens.WorkoutScreen
import app.sawatt.ui.theme.SawattColors

object Routes {
    // Tabs.
    const val TODAY = "today"
    const val PLANS = "plans"
    const val HISTORY = "history"
    const val ME = "me"

    // Pushed screens, shown without the tab bar.
    const val DEVICES = "devices"
    const val PLAN_EDITOR = "plan/{id}"
    const val WORKOUT = "workout?plan={plan}"
    const val SUMMARY = "summary"
    const val HISTORY_DETAIL = "history/{id}"
    const val PROFILE = "profile"

    fun planEditor(id: String?) = "plan/${id ?: "new"}"
    fun workout(planId: String?) = if (planId == null) "workout" else "workout?plan=$planId"
    fun historyDetail(id: String) = "history/$id"
}

private class Tab(val route: String, @StringRes val label: Int, val icon: @Composable () -> ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, R.string.nav_today) { SawattIcons.TabToday },
    Tab(Routes.PLANS, R.string.nav_workouts) { SawattIcons.TabWorkouts },
    Tab(Routes.HISTORY, R.string.nav_history) { SawattIcons.TabHistory },
    Tab(Routes.ME, R.string.nav_me) { SawattIcons.TabMe },
)

/** Switches tab the usual way: one back stack entry per tab, each keeping its state. */
fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun SawattNavHost(container: AppContainer, nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    Column(Modifier.fillMaxSize().background(SawattColors.Background)) {
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.weight(1f)) {
            composable(Routes.TODAY) { TodayScreen(container, nav) }
            composable(Routes.PLANS) { PlansScreen(container, nav) }
            composable(Routes.HISTORY) { HistoryScreen(container, nav) }
            composable(Routes.ME) { MeScreen(container, nav) }

            composable(Routes.DEVICES) { DevicesScreen(container, onBack = nav::popBackStack) }
            composable(Routes.PLAN_EDITOR) { entry ->
                val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
                PlanEditorScreen(container, id, onDone = nav::popBackStack)
            }
            composable(
                Routes.WORKOUT,
                arguments = listOf(navArgument("plan") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                WorkoutScreen(
                    container = container,
                    planId = entry.arguments?.getString("plan"),
                    onBack = nav::popBackStack,
                    onDevices = { nav.navigate(Routes.DEVICES) },
                    onFinished = { saved ->
                        nav.navigate(if (saved) Routes.SUMMARY else Routes.TODAY) {
                            popUpTo(Routes.TODAY)
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.SUMMARY) {
                SummaryScreen(container, onDone = { nav.popBackStack(Routes.TODAY, inclusive = false) })
            }
            composable(Routes.HISTORY_DETAIL) { entry ->
                HistoryDetailScreen(container, entry.arguments?.getString("id").orEmpty(), onBack = nav::popBackStack)
            }
            composable(Routes.PROFILE) { ProfileScreen(container, onBack = nav::popBackStack) }
        }
        if (tabs.any { it.route == route }) TabBar(route) { nav.openTab(it) }
    }
}

@Composable
private fun TabBar(current: String?, onSelect: (String) -> Unit) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = SawattColors.Hairline)
        NavigationBar(containerColor = SawattColors.Background, tonalElevation = 0.dp) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = tab.route == current,
                    onClick = { onSelect(tab.route) },
                    icon = { Icon(tab.icon(), contentDescription = null) },
                    label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, letterSpacing = 0.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SawattColors.Accent,
                        selectedTextColor = SawattColors.Accent,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = SawattColors.TextSecondary,
                        unselectedTextColor = SawattColors.TextSecondary,
                    ),
                )
            }
        }
    }
}
