package app.sift.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.sift.ui.dashboard.DashboardScreen
import app.sift.ui.graph.GraphScreen
import app.sift.ui.home.HomeScreen
import app.sift.ui.notes.NoteDetailScreen
import app.sift.ui.notes.NotesListScreen
import app.sift.ui.report.ReportScreen
import app.sift.ui.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val NOTES = "notes"
    const val NOTE_DETAIL = "note/{id}"
    const val DASHBOARD = "dashboard"
    const val REPORT = "report"
    const val GRAPH = "graph"
    fun noteDetail(id: String) = "note/$id"
}

@Composable
fun SiftNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenNotes = { nav.navigate(Routes.NOTES) },
                onOpenDashboard = { nav.navigate(Routes.DASHBOARD) },
                onOpenReport = { nav.navigate(Routes.REPORT) },
                onOpenGraph = { nav.navigate(Routes.GRAPH) },
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.REPORT) {
            ReportScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.GRAPH) {
            GraphScreen(
                onBack = { nav.popBackStack() },
                onOpenNote = { id -> nav.navigate(Routes.noteDetail(id)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.NOTES) {
            NotesListScreen(
                onBack = { nav.popBackStack() },
                onOpenNote = { id -> nav.navigate(Routes.noteDetail(id)) },
            )
        }
        composable(Routes.NOTE_DETAIL) {
            NoteDetailScreen(
                onBack = { nav.popBackStack() },
                onOpenNote = { id -> nav.navigate(Routes.noteDetail(id)) },
            )
        }
    }
}
