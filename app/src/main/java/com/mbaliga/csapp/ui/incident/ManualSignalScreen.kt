package com.mbaliga.csapp.ui.incident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mbaliga.csapp.di.AppContainer
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal
import com.mbaliga.csapp.domain.model.SignalType
import com.mbaliga.csapp.ui.components.InfoText
import java.util.UUID
import kotlinx.coroutines.launch

/** Add a signal by hand (e.g. a message from chat or a phone call) to the selected project. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSignalScreen(container: AppContainer, onBack: () -> Unit, onCreated: (String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var own by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text("Add a signal") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("What happened?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text("Details") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(own, { own = it }); Text("Make it its own incident")
            }
            InfoText("Otherwise it is grouped with similar signals in the selected project the next time you sync.")
            error?.let { Text(it) }
            Button(enabled = title.isNotBlank(), onClick = {
                scope.launch {
                    try {
                        val pid = container.selectedProject.value ?: com.mbaliga.csapp.domain.model.INBOX_PROJECT_ID
                        if (own) {
                            val inc = container.incidentRepository.createManual(title.trim(), body.trim(), Severity.MEDIUM, pid)
                            onCreated(inc.id)
                        } else {
                            val now = System.currentTimeMillis()
                            container.signalRepository.ingestOrUpdate(
                                Signal(sourceKey = "manual:${UUID.randomUUID()}", type = SignalType.MANUAL, title = title.trim(), body = body.trim(),
                                    authorName = null, rating = null, createdAt = now, sourceUpdatedAt = now, ingestedAt = now, projectId = pid),
                            )
                            container.incidentRepository.runClustering()
                            onCreated(null)
                        }
                    } catch (e: Exception) { error = e.message }
                }
            }) { Text("Add") }
        }
    }
}
