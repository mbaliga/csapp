package com.mbaliga.csapp.ui.incident

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mbaliga.csapp.domain.model.Severity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualIncidentScreen(
    viewModel: ManualIncidentViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(Severity.MEDIUM) }
    var severityMenuExpanded by remember { mutableStateOf(false) }

    val created by viewModel.created.collectAsState()
    LaunchedEffect(created) { created?.let(onCreated) }

    Scaffold(topBar = { TopAppBar(title = { Text("New manual incident") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = summary,
                onValueChange = { summary = it },
                label = { Text("Summary") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { severityMenuExpanded = true }) { Text("Severity: $severity") }
                DropdownMenu(expanded = severityMenuExpanded, onDismissRequest = { severityMenuExpanded = false }) {
                    Severity.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.name) }, onClick = { severity = s; severityMenuExpanded = false })
                    }
                }
            }
            Row(modifier = Modifier.padding(top = 16.dp)) {
                Button(onClick = { if (title.isNotBlank()) viewModel.create(title, summary, severity) }) { Text("Create") }
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Cancel") }
            }
        }
    }
}
