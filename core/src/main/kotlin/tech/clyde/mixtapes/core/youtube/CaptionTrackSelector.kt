package tech.clyde.mixtapes.core.youtube

object CaptionTrackSelector {

    /**
     * Picks the best transcript source: uploader-provided English beats auto-generated
     * (ASR) English beats uploader-provided anything beats ASR anything. Returns null
     * when there are no tracks at all.
     */
    fun pick(tracks: List<CaptionTrack>): CaptionTrack? =
        tracks.firstOrNull { it.isEnglish && !it.isAsr }
            ?: tracks.firstOrNull { it.isEnglish && it.isAsr }
            ?: tracks.firstOrNull { !it.isAsr }
            ?: tracks.firstOrNull()

    private val CaptionTrack.isEnglish: Boolean
        get() = languageCode == "en" || languageCode.startsWith("en-")

    private val CaptionTrack.isAsr: Boolean
        get() = kind == "asr"
}
