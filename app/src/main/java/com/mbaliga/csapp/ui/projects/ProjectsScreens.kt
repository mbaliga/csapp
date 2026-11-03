package com.mbaliga.csapp.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mbaliga.csapp.data.export.IssuesManifestExporter
import com.mbaliga.csapp.domain.model.SourceKind
import com.mbaliga.csapp.ui.components.InfoText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(vm: ProjectsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val projects by vm.projects.collectAsState()
    var creating by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Projects") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "New project") } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { InfoText("A project groups the Play app, GitHub repo and support-mail filter of one product. Credentials are shared; sources are per project.") }
            items(projects, key = { it.id }) { p ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { onOpen(p.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(p.externalId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false }, title = { Text("New project") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. Fotoz") }, singleLine = true) },
            confirmButton = { Button(onClick = { creating = false; vm.create(name, onOpen) }, enabled = name.isNotBlank()) { Text("Create") } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(vm: ProjectDetailViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val project by vm.project.collectAsState()
    val sources by vm.sources.collectAsState()
    val status by vm.status.collectAsState()
    var kind by remember { mutableStateOf(SourceKind.GITHUB_REPO) }
    var value by remember { mutableStateOf("") }
    var month by remember { mutableStateOf(java.time.YearMonth.now().toString()) }
    var auto by remember { mutableStateOf(vm.autoTriage()) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(IssuesManifestExporter.MIME_TYPE)) { it?.let(vm::exportTo) }
    val backfillLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.backfill(ctx, u, month) } }

    Scaffold(topBar = { TopAppBar(title = { Text(project?.name ?: "Project") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Sources", style = MaterialTheme.typography.titleMedium)
                InfoText("Reviews, issues and mail from these sources are grouped into this project's incidents.")
            }
            items(sources, key = { it.id }) { s ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(when (s.kind) { SourceKind.GITHUB_REPO -> "◉ GitHub repo"; SourceKind.PLAY_PACKAGE -> "★ Play package"; SourceKind.MAILBOX_FILTER -> "✉ Mail filter" }, style = MaterialTheme.typography.labelMedium)
                            Text(s.value, style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = s.enabled, onCheckedChange = { vm.toggle(s) })
                        TextButton(onClick = { vm.remove(s) }) { Text("Remove") }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(kind == SourceKind.GITHUB_REPO, { kind = SourceKind.GITHUB_REPO }, { Text("◉ GitHub") })
                        FilterChip(kind == SourceKind.PLAY_PACKAGE, { kind = SourceKind.PLAY_PACKAGE }, { Text("★ Play") })
                        FilterChip(kind == SourceKind.MAILBOX_FILTER, { kind = SourceKind.MAILBOX_FILTER }, { Text("✉ Mail") })
                    }
                    OutlinedTextField(
                        value, { value = it }, singleLine = kind != SourceKind.MAILBOX_FILTER, modifier = Modifier.fillMaxWidth(),
                        label = { Text(when (kind) { SourceKind.GITHUB_REPO -> "owner/repo"; SourceKind.PLAY_PACKAGE -> "com.example.app"; SourceKind.MAILBOX_FILTER -> "Mail filter" }) },
                    )
                    if (kind == SourceKind.MAILBOX_FILTER) InfoText("e.g. subject contains \"[Fotoz]\" OR to:fotoz@example.com — also from:, body contains. Unmatched mail goes to Inbox.")
                    Button(onClick = { vm.addSource(kind, value); value = "" }, enabled = value.isNotBlank()) { Text("Add source") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = auto, onCheckedChange = { auto = it; vm.setAutoTriage(it) })
                        Text("Suggest triage after each sync")
                    }
                    InfoText("Only writes pending suggestions for you to accept. Nothing changes without your tap.")
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Play history", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(month, { month = it }, label = { Text("Report month (YYYY-MM)") }, singleLine = true)
                    OutlinedButton(onClick = { backfillLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "*/*")) }) { Text("Import monthly review CSV…") }
                    InfoText("Play only exposes about a week of reviews through its API. Download a monthly review report from Play Console and import it here.")
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Export", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch(IssuesManifestExporter.SUGGESTED_FILE_NAME) }) { Text("Save file…") }
                        OutlinedButton(onClick = { vm.share(ctx) }) { Text("Share to Fonebrew") }
                    }
                    InfoText("Writes issues-manifest.v1.json for ${project?.externalId ?: "this project"}. Nothing is exported unless you tap. Unchanged data exports identical bytes.")
                    OutlinedButton(onClick = { vm.syncNow() }) { Text("Sync all now") }
                }
            }
            status?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            item { TextButton(onClick = { vm.archive(onBack) }) { Text("Archive project") } }
        }
    }
}
