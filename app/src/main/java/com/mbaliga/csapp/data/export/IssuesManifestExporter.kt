package com.mbaliga.csapp.data.export

import android.content.ContentResolver
import android.net.Uri
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.Signal

/**
 * Writes `issues-manifest.json` to a destination the user explicitly picked via the Storage
 * Access Framework (`ACTION_CREATE_DOCUMENT`). This class never chooses or writes to a location
 * on its own - it only ever writes to the [Uri] handed back from that system picker, and only
 * when the caller (a UI action) invokes [export] in direct response to a user tapping "export".
 */
class IssuesManifestExporter(private val contentResolver: ContentResolver) {

    fun export(
        destination: Uri,
        incidents: List<Incident>,
        signalsByIncidentId: Map<String, List<Signal>>,
    ) {
        val manifest = IssuesManifestBuilder.build(incidents, signalsByIncidentId)
        val jsonText = IssuesManifestBuilder.toJson(manifest)
        val stream = contentResolver.openOutputStream(destination, "wt")
            ?: throw IllegalStateException("Unable to open output stream for $destination")
        stream.use { it.write(jsonText.toByteArray(Charsets.UTF_8)) }
    }

    companion object {
        const val MIME_TYPE = "application/json"
        const val SUGGESTED_FILE_NAME = "issues-manifest.json"
    }
}
