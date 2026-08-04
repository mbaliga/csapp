package com.mbaliga.csapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.mbaliga.csapp.di.AppContainer

/** Minimal manual ViewModel factory, matching the manual DI approach used throughout this app. */
class CsAppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(com.mbaliga.csapp.ui.dashboard.DashboardViewModel::class.java) ->
                com.mbaliga.csapp.ui.dashboard.DashboardViewModel(container.incidentRepository) as T
            modelClass.isAssignableFrom(com.mbaliga.csapp.ui.incident.ManualIncidentViewModel::class.java) ->
                com.mbaliga.csapp.ui.incident.ManualIncidentViewModel(container.incidentRepository) as T
            modelClass.isAssignableFrom(com.mbaliga.csapp.ui.settings.SettingsViewModel::class.java) ->
                com.mbaliga.csapp.ui.settings.SettingsViewModel(
                    container.credentialStore,
                    container.settingsStore,
                    container.gitHubIssuePollRepository,
                    container.playReviewRepository,
                    container.incidentRepository,
                ) as T
            modelClass.isAssignableFrom(com.mbaliga.csapp.ui.export.ExportViewModel::class.java) ->
                com.mbaliga.csapp.ui.export.ExportViewModel(container.incidentRepository, container.signalRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
