package com.mbaliga.csapp.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mbaliga.csapp.di.AppContainer
import com.mbaliga.csapp.ui.CsAppViewModelFactory
import com.mbaliga.csapp.ui.dashboard.DashboardScreen
import com.mbaliga.csapp.ui.dashboard.DashboardViewModel
import com.mbaliga.csapp.ui.export.ExportScreen
import com.mbaliga.csapp.ui.export.ExportViewModel
import com.mbaliga.csapp.ui.incident.IncidentDetailScreen
import com.mbaliga.csapp.ui.incident.IncidentDetailViewModel
import com.mbaliga.csapp.ui.incident.ManualIncidentScreen
import com.mbaliga.csapp.ui.incident.ManualIncidentViewModel
import com.mbaliga.csapp.ui.reply.ReplyScreen
import com.mbaliga.csapp.ui.reply.ReplyViewModel
import com.mbaliga.csapp.ui.settings.SettingsScreen
import com.mbaliga.csapp.ui.settings.SettingsViewModel

@Composable
fun CsAppNavHost(container: AppContainer, navController: NavHostController = rememberNavController()) {
    val factory = CsAppViewModelFactory(container)

    NavHost(navController = navController, startDestination = Destinations.DASHBOARD) {
        composable(Destinations.DASHBOARD) {
            val viewModel: DashboardViewModel = viewModel(factory = factory)
            DashboardScreen(
                viewModel = viewModel,
                onOpenIncident = { navController.navigate(Destinations.incidentDetail(it)) },
                onCreateManual = { navController.navigate(Destinations.MANUAL_INCIDENT) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                onOpenExport = { navController.navigate(Destinations.EXPORT) },
            )
        }
        composable(
            Destinations.INCIDENT_DETAIL,
            arguments = listOf(navArgument("incidentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val incidentId = backStackEntry.arguments?.getString("incidentId").orEmpty()
            val viewModel: IncidentDetailViewModel = viewModel(
                factory = IncidentDetailViewModel.Factory(incidentId, container.incidentRepository, container.signalRepository),
            )
            IncidentDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenReply = { navController.navigate(Destinations.reply(it)) },
            )
        }
        composable(Destinations.MANUAL_INCIDENT) {
            val viewModel: ManualIncidentViewModel = viewModel(factory = factory)
            ManualIncidentScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.popBackStack()
                    navController.navigate(Destinations.incidentDetail(id))
                },
            )
        }
        composable(
            Destinations.REPLY,
            arguments = listOf(navArgument("sourceKey") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sourceKey = android.net.Uri.decode(backStackEntry.arguments?.getString("sourceKey").orEmpty())
            val viewModel: ReplyViewModel = viewModel(
                factory = ReplyViewModel.Factory(sourceKey, container.signalRepository, container.playReviewRepository),
            )
            ReplyScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Destinations.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(viewModel = viewModel)
        }
        composable(Destinations.EXPORT) {
            val viewModel: ExportViewModel = viewModel(factory = factory)
            ExportScreen(viewModel = viewModel, exporter = container.issuesManifestExporter)
        }
    }
}
