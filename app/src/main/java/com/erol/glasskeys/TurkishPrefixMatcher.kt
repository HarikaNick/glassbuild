package com.erol.glasskeys

/**
 * Small, allocation-light prefix matcher for correcting one common typing error.
 * This is typo tolerance, not a full spell-checking language model.
 */
internal object TurkishPrefixMatcher {
    fun hasSingleEditPrefixMatch(candidate: String, prefix: String): Boolean {
        val minLength = (prefix.length - 1).coerceAtLeast(1)
        val maxLength = (prefix.length + 1).coerceAtMost(candidate.length)
        if (minLength > maxLength) return false
        for (length in minLength..maxLength) {
            if (editDistanceAtMostOne(prefix, candidate.take(length))) return true
        }
        return false
    }

    fun distanceScore(candidate: String, prefix: String): Int {
        if (prefix.isEmpty()) return 0
        if (candidate.startsWith(prefix)) return 0
        val minLength = (prefix.length - 1).coerceAtLeast(1)
        val maxLength = (prefix.length + 1).coerceAtMost(candidate.length)
        if (minLength > maxLength) return 2
        for (length in minLength..maxLength) {
            if (editDistanceAtMostOne(prefix, candidate.take(length))) return 1
        }
        return 2
    }

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false

        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
                continue
            }
            edits++
            if (edits > 1) return false
            if (a.length == b.length) {
                if (i + 1 < a.length && j + 1 < b.length &&
                    a[i] == b[j + 1] && a[i + 1] == b[j]) {
                    i += 2
                    j += 2
                } else {
                    i++
                    j++
                }
            } else if (a.length > b.length) {
                i++
            } else {
                j++
            }
        }
        if (i < a.length || j < b.length) edits++
        return edits <= 1
    }
}
