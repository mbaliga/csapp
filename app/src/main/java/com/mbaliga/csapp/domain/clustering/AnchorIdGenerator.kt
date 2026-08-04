package com.mbaliga.csapp.domain.clustering

import java.security.MessageDigest

/**
 * Produces stable, deterministic incident ids derived from an "anchor" signal's
 * [com.mbaliga.csapp.domain.model.Signal.sourceKey].
 *
 * The id is intentionally NOT a hash of the full cluster membership: membership grows over time
 * as new signals are attached to an existing incident, and if the id depended on the full member
 * set it would change every time clustering re-runs. Anchoring on a single, deterministically
 * chosen signal (the earliest-created member, tie-broken by sourceKey) means the id is fixed the
 * moment the incident is first created and never re-minted by ordinary polling/clustering runs.
 */
object AnchorIdGenerator {
    private const val PREFIX = "inc_"
    private const val ID_HEX_LENGTH = 16

    /** Deterministically derive an incident id from a single anchor source key. */
    fun fromAnchorKey(anchorSourceKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(anchorSourceKey.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString(separator = "") { "%02x".format(it) }
        return PREFIX + hex.take(ID_HEX_LENGTH)
    }

    /**
     * Choose the anchor source key among a set of candidate signals: the earliest by
     * (createdAt, sourceKey) so ties are broken deterministically regardless of iteration order.
     */
    fun chooseAnchor(candidates: List<Pair<String, Long>>): String {
        require(candidates.isNotEmpty()) { "cannot choose an anchor from an empty signal set" }
        return candidates
            .sortedWith(compareBy({ it.second }, { it.first }))
            .first()
            .first
    }
}
