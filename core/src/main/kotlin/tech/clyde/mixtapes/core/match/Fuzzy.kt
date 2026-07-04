package tech.clyde.mixtapes.core.match

import kotlin.math.max
import kotlin.math.min

/**
 * Self-contained fuzzy string scoring (fuzzywuzzy-style), operating on
 * already-normalized, space-tokenized strings. All ratios are in [0.0, 1.0].
 */
object Fuzzy {
    fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val substitution = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(prev[j] + 1, curr[j - 1] + 1), substitution)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    /** 1 - levenshtein / max(length); 1.0 for two empty strings. */
    fun ratio(a: String, b: String): Double {
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    /** [ratio] over space-joined, sorted token lists — word order insensitive. */
    fun tokenSortRatio(a: String, b: String): Double =
        ratio(sortedJoined(a), sortedJoined(b))

    /**
     * Fuzzywuzzy token-set construction: with t0 = sorted common tokens,
     * t1 = t0 + rest(a), t2 = t0 + rest(b), returns the max pairwise [ratio]
     * among (t0,t1), (t0,t2), (t1,t2). 1.0 when one token set contains the other.
     */
    fun tokenSetRatio(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        val common = (ta intersect tb).sorted()
        val restA = (ta - tb).sorted()
        val restB = (tb - ta).sorted()
        val s0 = common.joinToString(" ")
        val s1 = (common + restA).joinToString(" ")
        val s2 = (common + restB).joinToString(" ")
        return max(max(ratio(s0, s1), ratio(s0, s2)), ratio(s1, s2))
    }

    fun tokens(s: String): Set<String> =
        s.split(' ').filterTo(mutableSetOf()) { it.isNotEmpty() }

    private fun sortedJoined(s: String): String =
        s.split(' ').filter { it.isNotEmpty() }.sorted().joinToString(" ")
}
