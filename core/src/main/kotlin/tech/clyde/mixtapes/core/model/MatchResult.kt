package tech.clyde.mixtapes.core.model

data class ScoredCandidate(val rom: RomFile, val score: Double)

sealed interface MatchResult {
    /** Confident single match; [alternates] are lower-scored options for the review screen. */
    data class Auto(
        val rom: RomFile,
        val score: Double,
        val alternates: List<ScoredCandidate> = emptyList(),
    ) : MatchResult

    /** Ambiguous — the user must pick. Candidates sorted by descending score. */
    data class NeedsReview(val candidates: List<ScoredCandidate>) : MatchResult

    data object NoMatch : MatchResult
}

data class GameMatch(val gameTitle: String, val result: MatchResult)
