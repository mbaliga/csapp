package com.mbaliga.csapp.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.ui.components.Marks
import com.mbaliga.csapp.ui.components.ProvenanceBadge
import com.mbaliga.csapp.ui.components.SeverityTag
import com.mbaliga.csapp.ui.components.StatusTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenIncident: (String) -> Unit,
    onNewSignal: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val s by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(s.message) {
        s.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    LaunchedEffect(s.undo) {
        val u = s.undo ?: return@LaunchedEffect
        val r = snackbar.showSnackbar(u.label, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.clearUndo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.selected?.name ?: "csapp", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    TextButton(onClick = onOpenProjects) { Text("Projects") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewSignal) { Icon(Icons.Filled.Add, contentDescription = "Add a signal manually") }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = s.syncing, onRefresh = viewModel::syncNow, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.projects, key = { it.id }) { p ->
                            FilterChip(selected = p.id == s.selected?.id, onClick = { viewModel.select(p.id) }, label = { Text(p.name) })
                        }
                    }
                }
                if (s.projects.size <= 1 && s.projects.all { it.id == "inbox" }) {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Add your first project", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "A project groups a Play app, a GitHub repo and a support-mail filter. Add one under Projects, then pull down to sync.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = onOpenProjects) { Text("Open Projects") }
                            }
                        }
                    }
                }
                item { DigestBlock(s, viewModel) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Severity.entries.reversed().forEach { sev ->
                            Column {
                                Text("${s.severityCounts[sev] ?: 0}", style = MaterialTheme.typography.headlineSmall)
                                SeverityTag(sev)
                            }
                        }
                    }
                }
                if (s.needsYou.total > 0) {
                    item {
                        Column {
                            Text("Needs you", style = MaterialTheme.typography.titleMedium)
                            val n = s.needsYou
                            val parts = buildList {
                                if (n.newSignals > 0) add("${n.newSignals} new signal(s) awaiting grouping — pull to sync")
                                if (n.pendingSuggestions > 0) add("${n.pendingSuggestions} AI suggestion(s) to accept or reject")
                                if (n.failedSends > 0) add("${n.failedSends} reply send(s) failed")
                            }
                            parts.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Incidents", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showFilters = !showFilters }) { Text(if (showFilters) "Hide filters" else "Filter & sort") }
                    }
                    if (showFilters) FilterPanel(s.filters, viewModel)
                }
                if (s.incidents.isEmpty()) {
                    item {
                        Text(
                            "Nothing here yet. Pull down to sync, or add a signal with +.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(s.incidents, key = { it.id }) { inc -> SwipeRow(inc, onOpenIncident, viewModel) }
            }
        }
    }
}

@Composable
private fun DigestBlock(s: HomeState, vm: HomeViewModel) {
    if (s.digest != null) {
        Column {
            Text(s.digest, style = MaterialTheme.typography.bodyLarge)
            s.digestProvenance?.let { ProvenanceBadge(it.first, it.second, Modifier.padding(top = 4.dp)) }
        }
    } else if (s.aiConfigured && s.selected != null) {
        TextButton(onClick = vm::refreshDigest) { Text("Write this week's digest") }
    } else if (!s.aiConfigured) {
        Text("AI digest is off — set a model in Settings to enable it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPanel(f: Filters, vm: HomeViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Status", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(IncidentStatus.entries.toList()) { st ->
                val m = Marks.status(st)
                FilterChip(selected = st in f.statuses, onClick = { vm.toggleStatus(st) }, label = { Text("${m.glyph} ${m.label}") })
            }
        }
        Text("Severity", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Severity.entries.reversed()) { sev ->
                val m = Marks.severity(sev)
                FilterChip(selected = sev in f.severities, onClick = { vm.toggleSeverity(sev) }, label = { Text("${m.glyph} ${m.label}") })
            }
        }
        Text("Channel", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SignalType.entries.toList()) { t ->
                val m = Marks.channel(t)
                FilterChip(selected = t in f.channels, onClick = { vm.toggleChannel(t) }, label = { Text("${m.glyph} ${m.label}") })
            }
        }
        Text("Sort by", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SortOrder.entries.toList()) { o -> FilterChip(selected = f.sort == o, onClick = { vm.setSort(o) }, label = { Text(o.label) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(inc: Incident, onOpen: (String) -> Unit, vm: HomeViewModel) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> { vm.setStatus(inc, IncidentStatus.ACKNOWLEDGED, "Acknowledged"); true }
                SwipeToDismissBoxValue.EndToStart -> { vm.setStatus(inc, IncidentStatus.DISMISSED, "Dismissed"); true }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val dir = state.dismissDirection
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) androidx.compose.ui.Alignment.CenterStart else androidx.compose.ui.Alignment.CenterEnd) {
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> Text("◐ Acknowledge", style = MaterialTheme.typography.labelLarge)
                    SwipeToDismissBoxValue.EndToStart -> Text("✕ Dismiss", style = MaterialTheme.typography.labelLarge)
                    else -> Unit
                }
            }
        },
    ) {
        OutlinedCard(Modifier.fillMaxWidth().clickable { onOpen(inc.id) }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(inc.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeverityTag(inc.severity); StatusTag(inc.status)
                }
                if (inc.summary.isNotBlank()) Text(inc.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}
