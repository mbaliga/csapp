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
 *
 * @param producerVersion CSApp's own real release version (`PackageInfo.versionName`), reported
 *   verbatim as `producer.version` in the hub contract - never a hardcoded literal.
 * @param projectExternalIdProvider Resolves `projectRef.externalId` at export time (not at
 *   construction) so a settings change between app start and export is reflected.
 */
class IssuesManifestExporter(
    private val contentResolver: ContentResolver,
    private val producerVersion: String,
    private val projectExternalIdProvider: () -> String,
) {

    fun export(
        destination: Uri,
        incidents: List<Incident>,
        signalsByIncidentId: Map<String, List<Signal>>,
    ) {
        val manifest = IssuesManifestBuilder.build(
            incidents = incidents,
            signalsByIncidentId = signalsByIncidentId,
            producerVersion = producerVersion,
            projectExternalId = projectExternalIdProvider(),
        )
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
