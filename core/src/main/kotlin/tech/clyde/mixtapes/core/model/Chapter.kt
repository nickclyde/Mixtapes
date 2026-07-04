package tech.clyde.mixtapes.core.model

/**
 * A single entry parsed from a video description's chapter list.
 *
 * @param seconds chapter start offset from the beginning of the video
 * @param skipped true when the chapter looks like a non-game segment
 *   (intro, outro, sponsor, ...) — kept so the review screen can offer
 *   re-inclusion instead of silently dropping it
 */
data class Chapter(
    val title: String,
    val seconds: Int,
    val skipped: Boolean = false,
)
