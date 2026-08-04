package com.mbaliga.csapp.domain.clustering

import com.mbaliga.csapp.domain.model.Incident
import com.mbaliga.csapp.domain.model.IncidentStatus
import com.mbaliga.csapp.domain.model.Severity
import com.mbaliga.csapp.domain.model.Signal
import java.util.concurrent.TimeUnit

/** Outcome of a clustering pass: which signals should be (re)assigned and which incidents are new. */
data class ClusteringResult(
    /** sourceKey -> incidentId assignments that changed as a result of this pass. */
    val signalAssignments: Map<String, String>,
    val newIncidents: List<Incident>,
)

/**
 * Deterministic, local clustering of [Signal]s into [Incident]s.
 *
 * Determinism guarantees:
 *  - Running this twice on the same input produces the same incident ids and the same
 *    assignments (no randomness, no wall-clock-dependent tie-breaking).
 *  - An incident's id never changes as more signals are attached to it later; it is fixed at
 *    creation time from its anchor signal (see [AnchorIdGenerator]).
 */
class ClusteringEngine(
    private val similarityThreshold: Double = 0.30,
    private val maxTimeWindowMillis: Long = TimeUnit.DAYS.toMillis(30),
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * @param unclusteredSignals signals with no incidentId yet (new from a poll, or previously
     *   unmatched).
     * @param openIncidents incidents eligible to receive new members (not DISMISSED/MERGED).
     * @param signalsByIncidentId existing members of [openIncidents], used to compare new
     *   signals against incident content.
     */
    fun cluster(
        unclusteredSignals: List<Signal>,
        openIncidents: List<Incident>,
        signalsByIncidentId: Map<String, List<Signal>>,
    ): ClusteringResult {
        val assignments = mutableMapOf<String, String>()
        val stillUnassigned = mutableListOf<Signal>()

        // Pass 1: try to attach each new signal to an existing open incident.
        for (signal in unclusteredSignals) {
            val bestIncident = findBestMatchingIncident(signal, openIncidents, signalsByIncidentId)
            if (bestIncident != null) {
                assignments[signal.sourceKey] = bestIncident.id
            } else {
                stillUnassigned.add(signal)
            }
        }

        // Pass 2: greedily group the remaining signals with each other (union-find by pairwise
        // similarity), deterministically ordered by sourceKey so grouping never depends on the
        // order signals arrived from the network.
        val ordered = stillUnassigned.sortedBy { it.sourceKey }
        val parent = ordered.indices.associateWith { it }.toMutableMap()

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent.getValue(r)
            var c = x
            while (parent[c] != c) {
                val next = parent.getValue(c)
                parent[c] = r
                c = next
            }
            return r
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        val tokenCache = ordered.map { TextSimilarity.tokenize(it.title + " " + it.body) }
        for (i in ordered.indices) {
            for (j in (i + 1) until ordered.size) {
                if (Math.abs(ordered[i].createdAt - ordered[j].createdAt) > maxTimeWindowMillis) continue
                val sim = TextSimilarity.jaccard(tokenCache[i], tokenCache[j])
                if (sim >= similarityThreshold) union(i, j)
            }
        }

        val groups = ordered.indices.groupBy { find(it) }
        val newIncidents = mutableListOf<Incident>()
        val currentTime = now()

        for (memberIndices in groups.values) {
            val members = memberIndices.map { ordered[it] }
            val anchorCandidates = members.map { it.sourceKey to it.createdAt }
            val anchorKey = AnchorIdGenerator.chooseAnchor(anchorCandidates)
            val incidentId = AnchorIdGenerator.fromAnchorKey(anchorKey)
            val anchorSignal = members.first { it.sourceKey == anchorKey }

            for (member in members) {
                assignments[member.sourceKey] = incidentId
            }

            newIncidents.add(
                Incident(
                    id = incidentId,
                    title = deriveTitle(anchorSignal),
                    summary = deriveSummary(members),
                    severity = Severity.MEDIUM,
                    status = IncidentStatus.OPEN,
                    isManual = false,
                    createdAt = currentTime,
                    updatedAt = currentTime,
                )
            )
        }

        return ClusteringResult(signalAssignments = assignments, newIncidents = newIncidents)
    }

    private fun findBestMatchingIncident(
        signal: Signal,
        openIncidents: List<Incident>,
        signalsByIncidentId: Map<String, List<Signal>>,
    ): Incident? {
        val signalTokens = TextSimilarity.tokenize(signal.title + " " + signal.body)
        var best: Incident? = null
        var bestScore = similarityThreshold
        // Iterate incidents in a stable order (by id) so ties always resolve the same way.
        for (incident in openIncidents.sortedBy { it.id }) {
            val members = signalsByIncidentId[incident.id].orEmpty()
            for (member in members) {
                if (Math.abs(member.createdAt - signal.createdAt) > maxTimeWindowMillis) continue
                val score = TextSimilarity.jaccard(signalTokens, TextSimilarity.tokenize(member.title + " " + member.body))
                if (score >= bestScore) {
                    bestScore = score
                    best = incident
                }
            }
        }
        return best
    }

    private fun deriveTitle(anchor: Signal): String {
        val trimmed = anchor.title.ifBlank { anchor.body }.trim()
        return if (trimmed.length <= 80) trimmed else trimmed.take(77) + "..."
    }

    private fun deriveSummary(members: List<Signal>): String {
        val count = members.size
        val types = members.map { it.type }.toSet().joinToString(", ") { it.name }
        return "$count signal(s) from: $types"
    }
}
