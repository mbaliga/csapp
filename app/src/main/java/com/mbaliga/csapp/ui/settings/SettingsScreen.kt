package com.mbaliga.csapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()

    var owner by remember { mutableStateOf(state.githubOwner) }
    var repo by remember { mutableStateOf(state.githubRepo) }
    var token by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf(state.playPackageName) }
    var serviceAccountJson by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("GitHub issue polling", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Repo") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { viewModel.saveGithubRepo(owner, repo) }, modifier = Modifier.padding(top = 4.dp)) { Text("Save repo") }

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(if (state.hasGithubToken) "GitHub token (saved — enter to replace)" else "GitHub token") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(onClick = { if (token.isNotBlank()) viewModel.saveGithubToken(token) }, modifier = Modifier.padding(top = 4.dp)) {
                Text("Save token (encrypted)")
            }
            Button(onClick = { viewModel.pollGithubNow() }, modifier = Modifier.padding(top = 4.dp), enabled = !state.isPolling) {
                Text("Poll GitHub now")
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Play review polling", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text("Package name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { viewModel.savePlayPackageName(packageName) }, modifier = Modifier.padding(top = 4.dp)) {
                Text("Save package name")
            }

            OutlinedTextField(
                value = serviceAccountJson,
                onValueChange = { serviceAccountJson = it },
                label = { Text(if (state.hasPlayKey) "Service-account key JSON (saved — paste to replace)" else "Service-account key JSON") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                minLines = 3,
            )
            Button(
                onClick = { if (serviceAccountJson.isNotBlank()) viewModel.savePlayServiceAccountKey(serviceAccountJson) },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Save key (encrypted)") }
            Button(onClick = { viewModel.pollPlayNow() }, modifier = Modifier.padding(top = 4.dp), enabled = !state.isPolling) {
                Text("Poll Play now")
            }

            state.statusMessage?.let {
                Text(it, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
