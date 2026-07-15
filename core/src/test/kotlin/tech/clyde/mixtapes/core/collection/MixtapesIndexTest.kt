package tech.clyde.mixtapes.core.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixtapesIndexTest {

    private val sample = mapOf(
        "custom-Top 25 SNES RPGs.cfg" to MixtapeInfo(
            videoUrl = "https://www.youtube.com/watch?v=abc123",
            videoTitle = "TOP 25 SNES RPGs of All Time",
            createdAt = "2026-07-15T18:03:00Z",
        ),
        "custom-GBA Gems.cfg" to MixtapeInfo(videoUrl = "https://www.youtube.com/watch?v=xyz"),
    )

    @Test
    fun `render then parse round-trips`() {
        assertEquals(sample, MixtapesIndex.parse(MixtapesIndex.render(sample)))
    }

    @Test
    fun `malformed json parses to empty`() {
        assertEquals(emptyMap<String, MixtapeInfo>(), MixtapesIndex.parse("not json {"))
        assertEquals(emptyMap<String, MixtapeInfo>(), MixtapesIndex.parse(""))
        assertEquals(emptyMap<String, MixtapeInfo>(), MixtapesIndex.parse("[1, 2, 3]"))
        assertEquals(emptyMap<String, MixtapeInfo>(), MixtapesIndex.parse("""{"version": 1}"""))
    }

    @Test
    fun `entry without videoUrl is skipped`() {
        val json = """
            {"version": 1, "collections": {
                "custom-No Link.cfg": {"videoTitle": "orphan"},
                "custom-Linked.cfg": {"videoUrl": "https://youtu.be/x"}
            }}
        """.trimIndent()
        val parsed = MixtapesIndex.parse(json)
        assertEquals(setOf("custom-Linked.cfg"), parsed.keys)
    }

    @Test
    fun `unknown fields and missing version are tolerated`() {
        val json = """
            {"collections": {
                "custom-A.cfg": {"videoUrl": "https://youtu.be/x", "futureField": 42}
            }}
        """.trimIndent()
        assertEquals("https://youtu.be/x", MixtapesIndex.parse(json)["custom-A.cfg"]?.videoUrl)
    }

    @Test
    fun `non-object collection values are skipped`() {
        val json = """{"collections": {"custom-A.cfg": "oops", "custom-B.cfg": {"videoUrl": "u"}}}"""
        assertEquals(setOf("custom-B.cfg"), MixtapesIndex.parse(json).keys)
    }

    @Test
    fun `empty map renders parseable json with no collections`() {
        val rendered = MixtapesIndex.render(emptyMap())
        assertTrue(rendered.contains("\"version\""))
        assertEquals(emptyMap<String, MixtapeInfo>(), MixtapesIndex.parse(rendered))
    }
}
