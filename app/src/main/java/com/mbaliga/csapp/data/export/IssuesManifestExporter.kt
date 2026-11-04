package com.mbaliga.csapp.data.export

import android.content.ContentResolver
import android.net.Uri
import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.Signal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes `issues-manifest.v1.json` for one project, only to a destination the user picked (SAF
 * `ACTION_CREATE_DOCUMENT`) or hands to the system share sheet on an explicit tap. Nothing is
 * ever written automatically.
 *
 * @param producerVersion csapp's own `versionName`, reported verbatim as `producer.version`.
 */
class IssuesManifestExporter(
    private val contentResolver: ContentResolver,
    private val producerVersion: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun export(
        destination: Uri,
        incidents: List<Incident>,
        signalsByIncidentId: Map<String, List<Signal>>,
        projectExternalId: String,
        projectCreatedAt: Long = 0L,
    ) {
        val text = render(incidents, signalsByIncidentId, projectExternalId, projectCreatedAt)
        withContext(io) {
            val stream = contentResolver.openOutputStream(destination, "wt")
                ?: throw IllegalStateException("Unable to open output stream for $destination")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    /** Pure: the exact text an export writes. Byte-identical for unchanged data. */
    fun render(
        incidents: List<Incident>,
        signalsByIncidentId: Map<String, List<Signal>>,
        projectExternalId: String,
        projectCreatedAt: Long = 0L,
    ): String {
        val manifest = IssuesManifestBuilder.build(
            incidents = incidents,
            signalsByIncidentId = signalsByIncidentId,
            producerVersion = producerVersion,
            projectExternalId = projectExternalId,
            projectCreatedAt = projectCreatedAt,
        )
        IssuesManifestBuilder.validate(manifest)
        return IssuesManifestBuilder.toJson(manifest)
    }

    companion object {
        const val MIME_TYPE = "application/json"
        const val SUGGESTED_FILE_NAME = "issues-manifest.v1.json"
    }
}
