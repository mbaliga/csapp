package com.mbaliga.csapp.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mbaliga.csapp.di.AppContainer
import com.mbaliga.csapp.ui.dashboard.HomeScreen
import com.mbaliga.csapp.ui.dashboard.HomeViewModel
import com.mbaliga.csapp.ui.incident.IncidentDetailScreen
import com.mbaliga.csapp.ui.incident.IncidentDetailViewModel
import com.mbaliga.csapp.ui.incident.ManualSignalScreen
import com.mbaliga.csapp.ui.projects.ProjectDetailScreen
import com.mbaliga.csapp.ui.projects.ProjectDetailViewModel
import com.mbaliga.csapp.ui.projects.ProjectsScreen
import com.mbaliga.csapp.ui.projects.ProjectsViewModel
import com.mbaliga.csapp.ui.reply.ReplyScreen
import com.mbaliga.csapp.ui.reply.ReplyViewModel
import com.mbaliga.csapp.ui.settings.SettingsScreen
import com.mbaliga.csapp.ui.settings.SettingsViewModel
import com.mbaliga.csapp.ui.vmFactory

/**
 * Hardware-keyboard navigation for desktop-mode windows: Esc goes back, Ctrl+, opens Settings,
 * Ctrl+1 / Ctrl+2 switch between Home and Projects, Ctrl+N opens the new-signal screen.
 * Navigation only: no shortcut reaches confirm, send or accept; replies still go through the
 * dialog button (ReplyViewModel.confirmAndSend). Returns true when the event was consumed.
 */
fun NavHostController.handleShortcut(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key == Key.Escape && !event.isCtrlPressed) {
        // Never pop the start destination: that would leave an empty NavHost.
        return previousBackStackEntry != null && popBackStack()
    }
    if (!event.isCtrlPressed) return false
    when (event.key) {
        Key.Comma -> navigate(Destinations.SETTINGS) { launchSingleTop = true }
        Key.One -> popBackStack(Destinations.HOME, inclusive = false)
        Key.Two -> navigate(Destinations.PROJECTS) {
            popUpTo(Destinations.HOME)
            launchSingleTop = true
        }
        Key.N -> navigate(Destinations.NEW_SIGNAL) { launchSingleTop = true }
        else -> return false
    }
    return true
}

@Composable
fun CsAppNavHost(container: AppContainer, nav: NavHostController = rememberNavController()) {
    NavHost(navController = nav, startDestination = Destinations.HOME) {
        composable(Destinations.HOME) {
            val vm: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(container) })
            HomeScreen(
                vm,
                onOpenIncident = { nav.navigate(Destinations.incidentDetail(it)) },
                onNewSignal = { nav.navigate(Destinations.NEW_SIGNAL) },
                onOpenProjects = { nav.navigate(Destinations.PROJECTS) },
                onOpenSettings = { nav.navigate(Destinations.SETTINGS) },
            )
        }
        composable(Destinations.INCIDENT_DETAIL, arguments = listOf(navArgument("incidentId") { type = NavType.StringType })) { e ->
            val id = e.arguments?.getString("incidentId").orEmpty()
            val vm: IncidentDetailViewModel = viewModel(key = "inc-$id", factory = vmFactory { IncidentDetailViewModel(id, container) })
            IncidentDetailScreen(
                vm, onBack = { nav.popBackStack() },
                onOpenReply = { nav.navigate(Destinations.reply(it)) },
                onOpenIncident = { nav.navigate(Destinations.incidentDetail(it)) },
            )
        }
        composable(Destinations.NEW_SIGNAL) {
            ManualSignalScreen(container, onBack = { nav.popBackStack() }, onCreated = { id ->
                nav.popBackStack()
                if (id != null) nav.navigate(Destinations.incidentDetail(id))
            })
        }
        composable(Destinations.REPLY, arguments = listOf(navArgument("sourceKey") { type = NavType.StringType })) { e ->
            val key = android.net.Uri.decode(e.arguments?.getString("sourceKey").orEmpty())
            val vm: ReplyViewModel = viewModel(key = "reply-$key", factory = vmFactory { ReplyViewModel(key, container) })
            ReplyScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(Destinations.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = vmFactory { SettingsViewModel(container) })
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(Destinations.PROJECTS) {
            val vm: ProjectsViewModel = viewModel(factory = vmFactory { ProjectsViewModel(container) })
            ProjectsScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Destinations.project(it)) })
        }
        composable(Destinations.PROJECT_DETAIL, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { e ->
            val id = android.net.Uri.decode(e.arguments?.getString("projectId").orEmpty())
            val vm: ProjectDetailViewModel = viewModel(key = "proj-$id", factory = vmFactory { ProjectDetailViewModel(id, container) })
            ProjectDetailScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
