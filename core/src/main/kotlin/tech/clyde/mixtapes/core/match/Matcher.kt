package tech.clyde.mixtapes.core.match

import kotlin.math.max
import tech.clyde.mixtapes.core.model.GameMatch
import tech.clyde.mixtapes.core.model.MatchResult
import tech.clyde.mixtapes.core.model.RomFile
import tech.clyde.mixtapes.core.model.ScoredCandidate
import tech.clyde.mixtapes.core.normalize.Normalized
import tech.clyde.mixtapes.core.normalize.TitleNormalizer
import tech.clyde.mixtapes.core.scan.RomExtensions

object Matcher {
    /** Best score at or above this (plus margin/system conditions) auto-matches. */
    const val AUTO_THRESHOLD = 0.92

    /** Auto-match requires beating the best *different-title* candidate by this much. */
    const val AUTO_MARGIN = 0.05

    /** Candidates within this of the best must all be on the same system to auto-match. */
    const val SAME_SYSTEM_WINDOW = 0.03

    /** Below this the game is reported as NoMatch. */
    const val REVIEW_THRESHOLD = 0.60

    const val MAX_CANDIDATES = 5

    /** Damping for matches against only the base title (before a subtitle separator). */
    private const val BASE_TEXT_WEIGHT = 0.95

    /** Token-set score cap when one title's tokens are a small strict subset of the other's. */
    private const val SUBSET_CAP = 0.90
    private const val SUBSET_EXTRA_TOKENS = 2

    private val NUMERIC = Regex("""\d+""")

    /**
     * Matches each game title against the ROM library. Same-title variants on
     * one system are collapsed via [CandidatePreference] before scoring;
     * multi-system hits always come back as NeedsReview. A non-null
     * [collectionSystem] (manual selection or LLM-detected video system)
     * restricts matching to ROMs on that system family — a per-title bracket
     * hint can veto within the filter but never widen it.
     */
    fun match(
        gameTitles: List<String>,
        roms: List<RomFile>,
        collectionSystem: String? = null,
    ): List<GameMatch> {
        val scoped =
            if (collectionSystem == null) roms
            else roms.filter { SystemHint.matches(collectionSystem, it.system) }
        val library = CandidatePreference.collapse(scoped.filter { RomExtensions.isLikelyRom(it.displayName) })
            .map { it to TitleNormalizer.normalizeRom(it.displayName) }

        val tokenIndex = buildMap<String, MutableList<Int>> {
            library.forEachIndexed { i, (_, norm) ->
                for (token in norm.tokens) getOrPut(token) { mutableListOf() }.add(i)
            }
        }

        return gameTitles.map { title ->
            val hint = SystemHint.fromTitle(title)
            val results = TitleVariants.of(title).map { variant ->
                matchOne(TitleNormalizer.normalizeGame(variant), hint, library, tokenIndex)
            }
            GameMatch(title, bestOf(results))
        }
    }

    /**
     * The best result across a title's variant readings. An [MatchResult.Auto]
     * beats any review; among the rest the top candidate score decides.
     * Replacement is strictly-greater so the original title wins ties. A
     * NeedsReview winner absorbs the other reviews' candidates, so the picker
     * offers both readings.
     */
    private fun bestOf(results: List<MatchResult>): MatchResult {
        val winner = results.maxByOrNull(::rank) ?: return MatchResult.NoMatch
        if (winner !is MatchResult.NeedsReview) return winner

        val merged = results.filterIsInstance<MatchResult.NeedsReview>()
            .flatMap { it.candidates }
            .groupBy { it.rom.relativePath }
            .map { (_, dupes) -> dupes.maxBy { it.score } }
            .sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenBy { it.rom.relativePath })
        return MatchResult.NeedsReview(merged.take(MAX_CANDIDATES))
    }

    private fun rank(result: MatchResult): Double = when (result) {
        is MatchResult.Auto -> 1.0 + result.score
        is MatchResult.NeedsReview -> result.candidates.firstOrNull()?.score ?: 0.0
        MatchResult.NoMatch -> -1.0
    }

    private fun matchOne(
        game: Normalized,
        hint: String?,
        library: List<Pair<RomFile, Normalized>>,
        tokenIndex: Map<String, List<Int>>,
    ): MatchResult {
        // Shared-token prefilter keeps scoring fast; a misspelled single-token
        // title ("Shante" vs "Shantae") shares nothing, so fall back to
        // scoring the whole library rather than declaring NoMatch early.
        val poolIndices = game.tokens.flatMap { tokenIndex[it].orEmpty() }.toSortedSet()
            .ifEmpty { library.indices.toSortedSet() }
        if (poolIndices.isEmpty()) return MatchResult.NoMatch

        val scored = poolIndices
            .map { i -> ScoredCandidate(library[i].first, score(game, library[i].second)) }
            .sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenBy { it.rom.relativePath })

        val best = scored.first()
        if (best.score < REVIEW_THRESHOLD) return MatchResult.NoMatch

        // An exact normalized-title match is definitive: it must not be dragged
        // into review by subset titles ("Adventure Island" vs "Super Adventure
        // Island" both score 1.0 on token-set). Only an exact match on another
        // system keeps the ambiguity.
        val exact = scored.filter { TitleNormalizer.normalizeRom(it.rom.displayName).text == game.text }
        if (exact.isNotEmpty()) {
            // A "[NES]"-style hint arbitrates: it settles a multi-system tie,
            // and it vetoes an exact match that only exists on other systems.
            val hinted = if (hint != null) exact.filter { SystemHint.matches(hint, it.rom.system) } else exact
            if (hint != null && hinted.isEmpty()) return MatchResult.NeedsReview(scored.take(MAX_CANDIDATES))
            val chosen = hinted.ifEmpty { exact }
            return if (chosen.all { it.rom.system == chosen.first().rom.system }) {
                MatchResult.Auto(
                    chosen.first().rom,
                    chosen.first().score,
                    alternates = scored.filter { it != chosen.first() }.take(MAX_CANDIDATES - 1),
                )
            } else {
                MatchResult.NeedsReview(scored.take(MAX_CANDIDATES))
            }
        }

        val bestTitle = TitleNormalizer.normalizeRom(best.rom.displayName).text
        val bestDifferent = scored.firstOrNull {
            TitleNormalizer.normalizeRom(it.rom.displayName).text != bestTitle
        }
        // The epsilon absorbs float error when the margin lands exactly on the
        // boundary (e.g. a 0.95 baseText match vs a 0.90 capped subset match).
        val marginOk = bestDifferent == null || best.score - bestDifferent.score >= AUTO_MARGIN - 1e-9
        val singleSystem = scored
            .takeWhile { best.score - it.score <= SAME_SYSTEM_WINDOW }
            .all { it.rom.system == best.rom.system }

        val hintOk = hint == null || SystemHint.matches(hint, best.rom.system)
        return if (best.score >= AUTO_THRESHOLD && marginOk && singleSystem && hintOk) {
            MatchResult.Auto(best.rom, best.score, alternates = scored.drop(1).take(MAX_CANDIDATES - 1))
        } else {
            MatchResult.NeedsReview(scored.take(MAX_CANDIDATES))
        }
    }

    private fun score(game: Normalized, rom: Normalized): Double {
        var tokenSet = Fuzzy.tokenSetRatio(game.text, rom.text)
        val gameTokens = Fuzzy.tokens(game.text)
        val romTokens = Fuzzy.tokens(rom.text)
        val smallStrictSubset =
            (gameTokens != romTokens) &&
                (
                    gameTokens.containsAll(romTokens) && gameTokens.size - romTokens.size >= SUBSET_EXTRA_TOKENS ||
                        romTokens.containsAll(gameTokens) && romTokens.size - gameTokens.size >= SUBSET_EXTRA_TOKENS
                    )
        // A numeric token on only one side is a different game, not a variant:
        // "Wave Race 64" vs "Wave Race", "Super Adventure Island" vs "... II".
        val numericMismatch =
            ((gameTokens - romTokens) + (romTokens - gameTokens)).any { it.matches(NUMERIC) }
        if (smallStrictSubset || numericMismatch) tokenSet = minOf(tokenSet, SUBSET_CAP)

        return max(
            max(tokenSet, Fuzzy.tokenSortRatio(game.text, rom.text)),
            max(
                BASE_TEXT_WEIGHT * Fuzzy.tokenSortRatio(game.text, rom.baseText),
                BASE_TEXT_WEIGHT * Fuzzy.tokenSortRatio(game.baseText, rom.text),
            ),
        )
    }
}
