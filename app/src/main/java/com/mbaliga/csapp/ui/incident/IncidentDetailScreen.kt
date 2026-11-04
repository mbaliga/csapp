package com.mbaliga.csapp.ui.incident

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mbaliga.csapp.data.db.entities.SuggestionEntity
import com.mbaliga.csapp.domain.ai.Provenance
import com.mbaliga.csapp.domain.ai.SuggestionKind
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.ui.components.ChannelTag
import com.mbaliga.csapp.ui.components.InfoText
import com.mbaliga.csapp.ui.components.Marks
import com.mbaliga.csapp.ui.components.ProvenanceBadge
import com.mbaliga.csapp.ui.components.SeverityTag
import com.mbaliga.csapp.ui.components.StatusTag
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailScreen(
    viewModel: IncidentDetailViewModel,
    onBack: () -> Unit,
    onOpenReply: (String) -> Unit,
    onOpenIncident: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var picker by remember { mutableStateOf<String?>(null) } // "merge" | "recurring"
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.incident?.title ?: "Incident", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val incident = state.incident
        if (incident == null) {
            Text(if (state.isLoading) "Loading…" else "Incident not found", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (incident.summary.isNotBlank()) Text(incident.summary, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { SeverityTag(incident.severity); StatusTag(incident.status) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeverityMenu(incident.severity, viewModel::setSeverity)
                        StatusMenu(incident.status, viewModel::setStatus)
                    }
                    incident.isRecurringOf?.let { Text("↻ Recurring of $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { SuggestionsPanel(state.suggestions, state.aiConfigured, state.busy, viewModel) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Organise", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picker = "merge" }, enabled = state.others.isNotEmpty()) { Text("Merge into…") }
                        OutlinedButton(onClick = { picker = "recurring" }, enabled = state.others.isNotEmpty()) { Text("Recurring of…") }
                    }
                    InfoText("Merging is deterministic: the earliest incident survives and takes every signal. Only incidents in this project are offered.")
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Signals (${state.signals.size})", style = MaterialTheme.typography.titleMedium)
                    if (state.selectedForSplit.isNotEmpty()) {
                        Button(onClick = { viewModel.splitOut { id -> if (id != null) onOpenIncident(id) } }) { Text("Split out ${state.selectedForSplit.size}") }
                    }
                }
                InfoText("Tick signals to split them into their own incident.")
            }
            items(state.signals, key = { it.sourceKey }) { sg ->
                SignalCard(sg, sg.sourceKey in state.selectedForSplit, { viewModel.toggleSplit(sg.sourceKey) }, { onOpenReply(sg.sourceKey) })
            }
        }
    }

    picker?.let { mode ->
        IncidentPicker(
            title = if (mode == "merge") "Merge into which incident?" else "Recurring of which incident?",
            options = state.others,
            onDismiss = { picker = null },
            onPick = { id -> if (mode == "merge") viewModel.mergeWith(id) else viewModel.markRecurringOf(id); picker = null },
        )
    }
}

@Composable
private fun IncidentPicker(title: String, options: List<Incident>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, label = { Text("Search by title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(options.filter { it.title.contains(q, ignoreCase = true) }.take(50), key = { it.id }) { inc ->
                        TextButton(onClick = { onPick(inc.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(inc.title, maxLines = 2)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SeverityTag(inc.severity); StatusTag(inc.status) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SuggestionsPanel(list: List<SuggestionEntity>, aiConfigured: Boolean, busy: Boolean, vm: IncidentDetailViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Suggestions", style = MaterialTheme.typography.titleMedium)
        if (!aiConfigured) {
            InfoText("AI is off. Choose a model in Settings to get triage and merge suggestions. Nothing is ever applied without your tap.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::runTriage, enabled = !busy) { Text(if (busy) "Working…" else "Run triage") }
                OutlinedButton(onClick = vm::runMergeSuggestions, enabled = !busy) { Text("Find duplicates") }
            }
            InfoText("A model proposes; you accept or reject. Each suggestion shows where it ran.")
        }
        list.filter { it.kind != SuggestionKind.DIGEST.name && it.kind != SuggestionKind.REPLY_DRAFT.name }.forEach { s ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val o = runCatching { Json.parseToJsonElement(s.payloadJson).jsonObject }.getOrNull()
                    when (s.kind) {
                        SuggestionKind.TRIAGE.name -> {
                            val sev = o?.get("severity")?.jsonPrimitive?.content?.let { runCatching { Severity.valueOf(it) }.getOrNull() }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("Triage", style = MaterialTheme.typography.labelLarge); sev?.let { SeverityTag(it) } }
                            o?.get("title")?.jsonPrimitive?.content?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                            o?.get("summary")?.jsonPrimitive?.content?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            o?.get("reasons")?.jsonArray?.forEach { Text("• ${it.jsonPrimitive.content}", style = MaterialTheme.typography.bodySmall) }
                        }
                        SuggestionKind.MERGE.name -> {
                            Text("Merge — likely the same issue", style = MaterialTheme.typography.labelLarge)
                            Text(o?.get("reason")?.jsonPrimitive?.content.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                            Text(o?.get("incidentIds")?.jsonArray?.joinToString(" + ") { it.jsonPrimitive.content }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ProvenanceBadge(Provenance.parse(s.provenance), s.model)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.accept(s.id) }) { Text("Accept") }
                        OutlinedButton(onClick = { vm.reject(s.id) }) { Text("Reject") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalCard(signal: Signal, selected: Boolean, onToggle: () -> Unit, onReply: () -> Unit) {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChannelTag(signal.type)
                Text(signal.title, style = MaterialTheme.typography.titleMedium)
                Text(signal.body, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
                signal.rating?.let { Text("Rating: $it/5", style = MaterialTheme.typography.bodySmall) }
                if (signal.type == SignalType.PLAY_REVIEW) {
                    val edited = signal.editedAt != null && signal.repliedAt != null && signal.editedAt > signal.repliedAt
                    Text(
                        "Reply: ${signal.replyState.name.lowercase().replace('_', ' ')}" + if (edited) " · review edited after your reply" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!signal.sourceKey.startsWith("play-backfill:")) {
                        Button(onClick = onReply) { Text(if (signal.replyState == com.mbaliga.csapp.domain.model.ReplyState.SENT) "Edit reply" else "Reply") }
                    } else InfoText("Imported from a report: no review id, so it can't be replied to from here.")
                }
                if (signal.type == SignalType.EMAIL) {
                    OutlinedButton(onClick = {
                        val to = Regex("<([^>]+)>").find(signal.authorName.orEmpty())?.groupValues?.get(1) ?: signal.authorName.orEmpty()
                        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                            putExtra(Intent.EXTRA_SUBJECT, if (signal.title.startsWith("Re:", true)) signal.title else "Re: ${signal.title}")
                            putExtra(Intent.EXTRA_TEXT, "\n\n> " + signal.body.lines().take(12).joinToString("\n> "))
                        }
                        runCatching { context.startActivity(Intent.createChooser(i, "Reply in mail app")) }
                    }) { Text("Reply in mail app") }
                    InfoText("csapp never sends email. This opens your mail app with the thread pre-filled.")
                }
            }
        }
    }
}

@Composable
private fun SeverityMenu(current: Severity, onSelected: (Severity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text("Severity") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Severity.entries.reversed().forEach { sev ->
                val m = Marks.severity(sev)
                DropdownMenuItem(text = { Text("${m.glyph} ${m.label}" + if (sev == current) "  (current)" else "") }, onClick = { onSelected(sev); expanded = false })
            }
        }
    }
}

@Composable
private fun StatusMenu(current: IncidentStatus, onSelected: (IncidentStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text("Status") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IncidentStatus.entries.filter { it != IncidentStatus.MERGED }.forEach { st ->
                val m = Marks.status(st)
                DropdownMenuItem(text = { Text("${m.glyph} ${m.label}" + if (st == current) "  (current)" else "") }, onClick = { onSelected(st); expanded = false })
            }
        }
    }
}
