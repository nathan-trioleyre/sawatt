package app.sawatt.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.sawatt.AppContainer
import app.sawatt.ui.screens.ComingLaterScreen
import app.sawatt.ui.screens.DevicesScreen
import app.sawatt.ui.screens.HistoryDetailScreen
import app.sawatt.ui.screens.HistoryScreen
import app.sawatt.ui.screens.HomeScreen
import app.sawatt.ui.screens.PlanEditorScreen
import app.sawatt.ui.screens.PlansScreen
import app.sawatt.ui.screens.ProfileScreen
import app.sawatt.ui.screens.SettingsScreen
import app.sawatt.ui.screens.SummaryScreen
import app.sawatt.ui.screens.WorkoutScreen

object Routes {
    const val HOME = "home"
    const val DEVICES = "devices"
    const val PLANS = "plans"
    const val PLAN_EDITOR = "plan/{id}"
    const val WORKOUT = "workout?plan={plan}"
    const val SUMMARY = "summary"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{id}"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val LATER = "later"

    fun planEditor(id: String?) = "plan/${id ?: "new"}"
    fun workout(planId: String?) = if (planId == null) "workout" else "workout?plan=$planId"
    fun historyDetail(id: String) = "history/$id"
}

@Composable
fun SawattNavHost(container: AppContainer, nav: NavHostController = rememberNavController()) {
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(container, nav) }
        composable(Routes.DEVICES) { DevicesScreen(container, onBack = nav::popBackStack) }
        composable(Routes.PLANS) { PlansScreen(container, nav) }
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
                    nav.navigate(if (saved) Routes.SUMMARY else Routes.HOME) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
        composable(Routes.SUMMARY) {
            SummaryScreen(container, onDone = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
        composable(Routes.HISTORY) { HistoryScreen(container, nav) }
        composable(Routes.HISTORY_DETAIL) { entry ->
            HistoryDetailScreen(container, entry.arguments?.getString("id").orEmpty(), onBack = nav::popBackStack)
        }
        composable(Routes.PROFILE) { ProfileScreen(container, onBack = nav::popBackStack) }
        composable(Routes.SETTINGS) { SettingsScreen(container, onBack = nav::popBackStack) }
        composable(Routes.LATER) { ComingLaterScreen(onBack = nav::popBackStack) }
    }
}
