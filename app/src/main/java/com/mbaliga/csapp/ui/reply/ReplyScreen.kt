package com.mbaliga.csapp.ui.reply

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplyScreen(viewModel: ReplyViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Reply to review") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            state.signal?.let { signal ->
                Text("${signal.rating ?: "?"}★ — ${signal.authorName ?: "anonymous"}")
                Text(signal.body, modifier = Modifier.padding(vertical = 8.dp))
            }

            OutlinedTextField(
                value = state.draftText,
                onValueChange = viewModel::updateDraft,
                label = { Text("Your reply") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                minLines = 3,
            )

            Button(
                onClick = { showConfirmDialog = true },
                enabled = state.draftText.isNotBlank() && state.sendState !is SendState.Sending,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Send reply")
            }

            when (val sendState = state.sendState) {
                is SendState.Sending -> Text("Sending…")
                is SendState.Sent -> Text("Sent.")
                is SendState.Failed -> Text("Failed: ${sendState.message}")
                is SendState.Idle -> Unit
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Send this reply?") },
            text = { Text("This will be posted publicly on Google Play as your developer reply:\n\n\"${state.draftText}\"") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    viewModel.confirmAndSend(state.draftText)
                }) { Text("Confirm and send") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            },
        )
    }
}
