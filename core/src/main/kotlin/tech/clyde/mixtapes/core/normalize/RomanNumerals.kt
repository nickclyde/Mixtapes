package tech.clyde.mixtapes.core.normalize

object RomanNumerals {
    private val ROMAN_TO_ARABIC: Map<String, String> = buildMap {
        val ones = listOf("", "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix")
        for (n in 2..30) {
            val roman = "x".repeat(n / 10) + ones[n % 10]
            put(roman, n.toString())
        }
    }

    /**
     * Converts a whole lowercase token that is a roman numeral in ii..xxx to
     * its arabic form ("vii" -> "7"). Returns null for anything else,
     * including a lone "i" (too likely to be a real word or initial).
     */
    fun toArabic(token: String): String? = ROMAN_TO_ARABIC[token]
}
