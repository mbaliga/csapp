package com.mbaliga.csapp.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mbaliga.csapp.domain.model.Incident

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenIncident: (String) -> Unit,
    onCreateManual: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
) {
    val incidents by viewModel.incidents.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Incidents") },
                actions = {
                    IconButton(onClick = { viewModel.runClusteringNow() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Re-run clustering")
                    }
                    IconButton(onClick = onOpenExport) {
                        Icon(Icons.Filled.Share, contentDescription = "Export manifest")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateManual) {
                Icon(Icons.Filled.Add, contentDescription = "New manual incident")
            }
        },
    ) { padding ->
        IncidentList(incidents = incidents, padding = padding, onOpenIncident = onOpenIncident)
    }
}

@Composable
private fun IncidentList(incidents: List<Incident>, padding: PaddingValues, onOpenIncident: (String) -> Unit) {
    if (incidents.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("No incidents yet. Poll a source from Settings, or create one manually with the + button.")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp)) {
        items(incidents, key = { it.id }) { incident ->
            IncidentRow(incident, onClick = { onOpenIncident(incident.id) })
        }
    }
}

@Composable
private fun IncidentRow(incident: Incident, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(incident.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text("${incident.severity} • ${incident.status}" + if (incident.isManual) " • manual" else "")
            Text(incident.summary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
    }
}
