package com.mbaliga.csapp.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mbaliga.csapp.data.export.IssuesManifestExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(viewModel: ExportViewModel, exporter: IssuesManifestExporter) {
    val incidentCount by viewModel.incidentCount.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(IssuesManifestExporter.MIME_TYPE),
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri, exporter)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Export issues-manifest.json") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("$incidentCount incident(s) will be included in the export.")
            Text(
                "Exporting requires you to pick a destination file via the system picker below — " +
                    "nothing is written anywhere automatically.",
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { createDocumentLauncher.launch(IssuesManifestExporter.SUGGESTED_FILE_NAME) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Choose destination and export")
            }

            when (val state = exportState) {
                is ExportState.Exporting -> Text("Exporting…", modifier = Modifier.padding(top = 12.dp))
                is ExportState.Done -> Text("Export complete.", modifier = Modifier.padding(top = 12.dp))
                is ExportState.Failed -> Text("Export failed: ${state.message}", modifier = Modifier.padding(top = 12.dp))
                is ExportState.Idle -> Unit
            }
        }
    }
}
