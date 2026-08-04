package com.mbaliga.csapp.ui.incident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailScreen(
    viewModel: IncidentDetailViewModel,
    onBack: () -> Unit,
    onOpenReply: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.incident?.title ?: "Incident") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val incident = state.incident
        if (incident == null) {
            Text("Loading…", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text(incident.summary)
            Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityMenu(current = incident.severity, onSelected = viewModel::setSeverity)
                StatusMenu(current = incident.status, onSelected = viewModel::setStatus)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.dismiss() }) { Text("Dismiss") }
            }

            MergeAndRecurringControls(viewModel)

            Text("Signals (${state.signals.size})", modifier = Modifier.padding(top = 16.dp))
            LazyColumn {
                items(state.signals, key = { it.sourceKey }) { signal ->
                    SignalRow(signal, onReply = { onOpenReply(signal.sourceKey) })
                }
            }
        }
    }
}

@Composable
private fun MergeAndRecurringControls(viewModel: IncidentDetailViewModel) {
    var otherId by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = otherId,
            onValueChange = { otherId = it },
            label = { Text("Other incident id (merge / recurring-of)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = { if (otherId.isNotBlank()) viewModel.mergeWith(otherId) }) { Text("Merge into earlier") }
            OutlinedButton(onClick = { if (otherId.isNotBlank()) viewModel.markRecurringOf(otherId) }) { Text("Mark recurring of") }
        }
    }
}

@Composable
private fun SignalRow(signal: Signal, onReply: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(signal.title)
            Text(signal.body, maxLines = 3)
            Text("${signal.type} • reply: ${signal.replyState}")
            if (signal.type == com.mbaliga.csapp.domain.model.SignalType.PLAY_REVIEW) {
                Button(onClick = onReply, modifier = Modifier.padding(top = 4.dp)) { Text("Reply") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeverityMenu(current: Severity, onSelected: (Severity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text("Severity: $current") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Severity.entries.forEach { severity ->
                DropdownMenuItem(text = { Text(severity.name) }, onClick = { onSelected(severity); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusMenu(current: IncidentStatus, onSelected: (IncidentStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text("Status: $current") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IncidentStatus.entries.forEach { status ->
                DropdownMenuItem(text = { Text(status.name) }, onClick = { onSelected(status); expanded = false })
            }
        }
    }
}
