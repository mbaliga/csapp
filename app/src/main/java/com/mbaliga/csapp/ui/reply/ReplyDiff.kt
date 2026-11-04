package com.mbaliga.csapp.ui.reply

/** Minimal old-vs-new diff: shared prefix / changed middle / shared suffix. */
data class ReplyDiff(val prefix: String, val removed: String, val added: String, val suffix: String) {
    val unchanged get() = removed.isEmpty() && added.isEmpty()

    companion object {
        fun of(old: String, new: String): ReplyDiff {
            var p = 0
            while (p < old.length && p < new.length && old[p] == new[p]) p++
            var s = 0
            while (s < old.length - p && s < new.length - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
            return ReplyDiff(old.take(p), old.substring(p, old.length - s), new.substring(p, new.length - s), old.takeLast(s))
        }
    }
}
