package com.mbaliga.csapp.domain.clustering

/** Small, dependency-free text-similarity helper used by [ClusteringEngine]. */
internal object TextSimilarity {
    private val STOPWORDS = setOf(
        "the", "a", "an", "is", "it", "to", "and", "of", "in", "on", "for", "this", "that",
        "with", "was", "were", "be", "are", "i", "my", "me", "app", "please",
    )

    fun tokenize(text: String): Set<String> {
        return text
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in STOPWORDS }
            .toSet()
    }

    /** Jaccard similarity between two token sets, in [0.0, 1.0]. */
    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size
        val union = a.union(b).size
        if (union == 0) return 0.0
        return intersection.toDouble() / union.toDouble()
    }
}
