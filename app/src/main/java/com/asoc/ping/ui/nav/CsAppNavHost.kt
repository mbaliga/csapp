package com.asoc.ping.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asoc.ping.di.AppContainer
import com.asoc.ping.ui.dashboard.HomeScreen
import com.asoc.ping.ui.dashboard.HomeViewModel
import com.asoc.ping.ui.incident.IncidentDetailScreen
import com.asoc.ping.ui.incident.IncidentDetailViewModel
import com.asoc.ping.ui.incident.ManualSignalScreen
import com.asoc.ping.ui.projects.ProjectDetailScreen
import com.asoc.ping.ui.projects.ProjectDetailViewModel
import com.asoc.ping.ui.projects.ProjectsScreen
import com.asoc.ping.ui.projects.ProjectsViewModel
import com.asoc.ping.ui.reply.ReplyScreen
import com.asoc.ping.ui.reply.ReplyViewModel
import com.asoc.ping.ui.settings.SettingsScreen
import com.asoc.ping.ui.settings.SettingsViewModel
import com.asoc.ping.ui.vmFactory

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
