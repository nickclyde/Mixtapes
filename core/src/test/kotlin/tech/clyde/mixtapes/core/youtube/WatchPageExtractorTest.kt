package tech.clyde.mixtapes.core.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.clyde.mixtapes.core.Fixtures
import tech.clyde.mixtapes.core.chapters.ChapterParser

class WatchPageExtractorTest {

    @Test
    fun `extracts title and description from a real-shaped watch page`() {
        val result = WatchPageExtractor.extract(Fixtures.text("descriptions/watchpage_sample.html"))

        assertTrue("expected Success, was $result", result is ExtractionResult.Success)
        val meta = (result as ExtractionResult.Success).metadata
        assertEquals("20 MORE Awesome SNES Hidden Gems!", meta.title)
        assertTrue(meta.description.contains("brace pair }; right here"))
        assertTrue(meta.description.contains("Tricky \"quoted\" text"))

        // End-to-end into the parser: the JSON-escaped \n newlines must have
        // become real newlines with parseable chapter lines.
        val chapters = ChapterParser.parse(meta.description)
        assertEquals(
            listOf("Intro", "Chrono Trigger", "EarthBound", "Outro"),
            chapters.map { it.title },
        )
        assertEquals(83, chapters[1].seconds)
    }

    @Test
    fun `page without the player response marker fails typed`() {
        val result = WatchPageExtractor.extract("<html><body>consent wall</body></html>")
        assertEquals(ExtractionResult.Failure(ExtractionError.MARKER_NOT_FOUND), result)
    }

    @Test
    fun `truncated json fails typed`() {
        val result = WatchPageExtractor.extract("""var ytInitialPlayerResponse = {"videoDetails": {"title": "x""")
        assertEquals(ExtractionResult.Failure(ExtractionError.MALFORMED_JSON), result)
    }

    @Test
    fun `missing videoDetails fails typed`() {
        val result = WatchPageExtractor.extract("""var ytInitialPlayerResponse = {"playabilityStatus": {"status": "OK"}};""")
        assertEquals(ExtractionResult.Failure(ExtractionError.MISSING_FIELDS), result)
    }
}
