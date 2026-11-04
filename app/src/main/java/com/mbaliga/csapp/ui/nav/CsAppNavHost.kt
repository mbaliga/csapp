package com.mbaliga.csapp.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mbaliga.csapp.widget.WidgetTap

@Composable
fun CsAppNavHost(
    container: AppContainer,
    nav: NavHostController = rememberNavController(),
    widgetTap: WidgetTap? = null,
    onWidgetTapHandled: () -> Unit = {},
) {
    // A home-screen widget tap only ever opens a screen; it never acts on anything.
    LaunchedEffect(widgetTap) {
        val tap = widgetTap ?: return@LaunchedEffect
        nav.popBackStack(Destinations.HOME, inclusive = false)
        when (tap) {
            // MainActivity is exported, so the id may come from anywhere (or name a project archived
            // since the widget was drawn): select only an existing, active project.
            is WidgetTap.Home -> tap.projectId
                ?.takeIf { pid -> container.projectRepository.find(pid)?.archived == false }
                ?.let { container.selectProject(it) }
            is WidgetTap.Incident -> nav.navigate(Destinations.incidentDetail(tap.incidentId))
            is WidgetTap.Reply -> nav.navigate(Destinations.reply(tap.sourceKey))
            WidgetTap.Projects -> nav.navigate(Destinations.PROJECTS)
        }
        onWidgetTapHandled()
    }
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
