package tech.clyde.mixtapes.core.match

import org.junit.Assert.assertEquals
import org.junit.Test

class FuzzyTest {

    @Test
    fun levenshtein() {
        assertEquals(3, Fuzzy.levenshtein("kitten", "sitting"))
        assertEquals(3, Fuzzy.levenshtein("", "abc"))
        assertEquals(0, Fuzzy.levenshtein("same", "same"))
        assertEquals(Fuzzy.levenshtein("ab", "ba"), Fuzzy.levenshtein("ba", "ab"))
    }

    @Test
    fun ratio() {
        assertEquals(1.0, Fuzzy.ratio("abcd", "abcd"), 1e-9)
        assertEquals(0.75, Fuzzy.ratio("abcd", "abce"), 1e-9)
        assertEquals(1.0, Fuzzy.ratio("", ""), 1e-9)
        assertEquals(0.0, Fuzzy.ratio("", "abc"), 1e-9)
    }

    @Test
    fun `token sort is order-insensitive`() {
        assertEquals(1.0, Fuzzy.tokenSortRatio("mega man 2", "2 mega man"), 1e-9)
        assertEquals(
            Fuzzy.tokenSortRatio("a b", "b c"),
            Fuzzy.tokenSortRatio("b a", "c b"),
            1e-9,
        )
    }

    @Test
    fun `token set treats a contained title as a full match`() {
        assertEquals(1.0, Fuzzy.tokenSetRatio("final fantasy 7", "final fantasy 7 international"), 1e-9)
        assertEquals(
            Fuzzy.tokenSetRatio("chrono trigger", "trigger chrono something"),
            Fuzzy.tokenSetRatio("trigger chrono something", "chrono trigger"),
            1e-9,
        )
    }
}
