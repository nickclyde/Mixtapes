package tech.clyde.mixtapes.core.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixtapesIndexTest {
    private val sample = mapOf(
        "custom-Top 25 SNES RPGs.cfg" to MixtapeInfo(
            sourceUrl = "https://www.youtube.com/watch?v=abc123",
            sourceTitle = "TOP 25 SNES RPGs of All Time",
            sourceType = SourceType.YOUTUBE,
            createdAt = "2026-07-15T18:03:00Z",
        ),
        "custom-SNES Ranking.cfg" to MixtapeInfo(
            sourceUrl = "https://example.com/snes-ranking",
            sourceTitle = "50 Best SNES Games",
            sourceType = SourceType.ARTICLE,
        ),
    )

    @Test
    fun `version 2 render then parse round-trips`() {
        val rendered = MixtapesIndex.render(sample)
        assertTrue(rendered.contains("\"version\": 2"))
        assertTrue(rendered.contains("\"sourceType\": \"article\""))
        assertFalse(rendered.contains("videoUrl"))
        assertEquals(sample, MixtapesIndex.parse(rendered))
    }

    @Test
    fun `reads legacy version 1 video entries as YouTube sources`() {
        val json = """
            {"version": 1, "collections": {
                "custom-Legacy.cfg": {
                    "videoUrl": "https://youtu.be/abcdefghijk",
                    "videoTitle": "Legacy video",
                    "createdAt": "2026-01-01T00:00:00Z"
                }
            }}
        """.trimIndent()
        assertEquals(
            MixtapeInfo(
                sourceUrl = "https://youtu.be/abcdefghijk",
                sourceTitle = "Legacy video",
                sourceType = SourceType.YOUTUBE,
                createdAt = "2026-01-01T00:00:00Z",
            ),
            MixtapesIndex.parse(json)["custom-Legacy.cfg"],
        )
    }

    @Test
    fun `malformed json parses to empty`() {
        assertEquals(emptyMap<String, MixtapeInfo>(), MixtapesIndex.parse("not json {"))
        assertEquals(emptyMap<String, MixtapeInfo>(), MixtapesIndex.parse(""))
        assertEquals(emptyMap<String, MixtapeInfo>(), MixtapesIndex.parse("[1, 2, 3]"))
        assertEquals(emptyMap<String, MixtapeInfo>(), MixtapesIndex.parse("""{"version": 2}"""))
    }

    @Test
    fun `entries without a URL or recognized type are skipped`() {
        val json = """
            {"version": 2, "collections": {
                "custom-No Link.cfg": {"sourceTitle": "orphan", "sourceType": "article"},
                "custom-Future.cfg": {"sourceUrl": "https://example.com", "sourceType": "future"},
                "custom-Linked.cfg": {"sourceUrl": "https://example.com/a", "sourceType": "article"}
            }}
        """.trimIndent()
        assertEquals(setOf("custom-Linked.cfg"), MixtapesIndex.parse(json).keys)
    }

    @Test
    fun `unknown fields and missing legacy version are tolerated`() {
        val json = """
            {"collections": {
                "custom-A.cfg": {"videoUrl": "https://youtu.be/x", "futureField": 42}
            }}
        """.trimIndent()
        assertEquals("https://youtu.be/x", MixtapesIndex.parse(json)["custom-A.cfg"]?.sourceUrl)
    }

    @Test
    fun `empty map renders parseable json with no collections`() {
        val rendered = MixtapesIndex.render(emptyMap())
        assertTrue(rendered.contains("\"version\""))
        assertEquals(emptyMap<String, MixtapeInfo>(), MixtapesIndex.parse(rendered))
    }
}
