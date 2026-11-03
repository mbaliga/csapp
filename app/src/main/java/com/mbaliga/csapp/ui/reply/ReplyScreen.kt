package com.mbaliga.csapp.ui.reply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mbaliga.csapp.data.play.PlayReviewRepository
import com.mbaliga.csapp.domain.ai.Provenance
import com.mbaliga.csapp.ui.components.InfoText
import com.mbaliga.csapp.ui.components.ProvenanceBadge
import java.text.DateFormat
import java.util.Date
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplyScreen(viewModel: ReplyViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var confirming by remember { mutableStateOf(false) }
    val max = PlayReviewRepository.MAX_REPLY_CHARS
    val editing = state.previouslySent != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Edit reply" else "Reply to review") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val signal = state.signal
            if (signal == null) { Text("Loading…"); return@Column }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("★ ${signal.rating ?: "?"}/5 · ${signal.authorName ?: "Anonymous"}", style = MaterialTheme.typography.labelLarge)
                    Text(signal.body.ifBlank { "(no text)" }, style = MaterialTheme.typography.bodyMedium)
                    state.language?.let { Text("Review language: $it — reply in the same language", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (signal.editedAt != null && signal.repliedAt != null && signal.editedAt > signal.repliedAt) {
                        Text("◐ This review was edited after your reply.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.aiConfigured) {
                OutlinedButton(onClick = viewModel::requestAiDraft, enabled = !state.aiBusy) { Text(if (state.aiBusy) "Drafting…" else "Draft with AI") }
            } else InfoText("AI drafting is off. Set a model in Settings to enable it; you can always write the reply yourself.")
            state.aiError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            state.aiDraft?.let { d ->
                val text = runCatching { Json.parseToJsonElement(d.payloadJson).jsonObject["text"]?.jsonPrimitive?.content }.getOrNull().orEmpty()
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Suggested draft", style = MaterialTheme.typography.labelLarge)
                        Text(text)
                        ProvenanceBadge(Provenance.parse(d.provenance), d.model)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::useAiDraft) { Text("Use draft") }
                            OutlinedButton(onClick = viewModel::dismissAiDraft) { Text("Dismiss") }
                        }
                        InfoText("Using a draft only fills the box below. Nothing is sent until you confirm.")
                    }
                }
            }

            OutlinedTextField(
                value = state.draftText, onValueChange = viewModel::updateDraft,
                label = { Text("Your reply") }, minLines = 4, isError = state.overLimit, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("${state.length}/$max" + if (state.overLimit) " — too long for Play" else "") },
            )
            Button(onClick = { confirming = true }, enabled = state.draftText.isNotBlank() && !state.overLimit && state.sendState != SendState.Sending) {
                Text(if (editing) "Review changes…" else "Review & send…")
            }
            InfoText("You'll see the exact text and confirm before anything is sent to Google Play.")

            when (val s = state.sendState) {
                SendState.Sending -> Text("Sending…")
                SendState.Sent -> Text("✓ Sent.", style = MaterialTheme.typography.titleMedium)
                is SendState.Failed -> Text("✕ Not sent: ${s.message}", color = MaterialTheme.colorScheme.error)
                SendState.Idle -> Unit
            }

            if (state.log.isNotEmpty()) {
                Text("Reply log", style = MaterialTheme.typography.titleMedium)
                state.log.forEach { e ->
                    Text(
                        "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.sentAt))} · ${e.outcome}" + (e.httpStatus?.let { " · HTTP $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(e.textSent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(if (editing) "Replace your public reply?" else "Send this public reply?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val prev = state.previouslySent
                    if (prev != null && prev != state.draftText) {
                        val d = ReplyDiff.of(prev, state.draftText)
                        Text("Changes", style = MaterialTheme.typography.labelLarge)
                        Text(buildAnnotatedString {
                            append(d.prefix)
                            if (d.removed.isNotEmpty()) withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append("[− ${d.removed}]") }
                            if (d.added.isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("[+ ${d.added}]") }
                            append(d.suffix)
                        })
                    }
                    Text("Exact text that will be posted:", style = MaterialTheme.typography.labelLarge)
                    Text("“${state.draftText}”")
                    InfoText("It is posted publicly under your developer name on Google Play.")
                }
            },
            confirmButton = { Button(onClick = { confirming = false; viewModel.confirmAndSend(state.draftText) }) { Text("Send to Google Play") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}
