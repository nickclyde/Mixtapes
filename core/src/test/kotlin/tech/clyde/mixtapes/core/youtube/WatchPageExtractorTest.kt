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
    fun `page without a captions block yields no caption tracks`() {
        val result = WatchPageExtractor.extract(Fixtures.text("descriptions/watchpage_sample.html"))
        val meta = (result as ExtractionResult.Success).metadata
        assertEquals(emptyList<CaptionTrack>(), meta.captionTracks)
    }

    @Test
    fun `extracts caption tracks in page order with kind and name`() {
        val result = WatchPageExtractor.extract(Fixtures.text("descriptions/watchpage_captions.html"))

        assertTrue("expected Success, was $result", result is ExtractionResult.Success)
        val tracks = (result as ExtractionResult.Success).metadata.captionTracks
        assertEquals(4, tracks.size)

        // Order preserved; the first track is the PoToken-gated one (exp=xpe in baseUrl).
        assertTrue(tracks[0].baseUrl.contains("exp=xpe"))
        assertEquals("en", tracks[0].languageCode)
        assertEquals(null, tracks[0].kind)

        assertEquals("English", tracks[1].name)
        assertEquals(null, tracks[1].kind)

        // ASR track, name in the newer runs[] shape.
        assertEquals("asr", tracks[2].kind)
        assertEquals("English (auto-generated)", tracks[2].name)

        assertEquals("de", tracks[3].languageCode)
    }

    @Test
    fun `parses caption tracks from a bare innertube player response`() {
        val tracks = WatchPageExtractor.captionTracksFromPlayerResponse(
            Fixtures.text("descriptions/player_response_android.json"),
        )
        assertEquals(1, tracks.size)
        assertEquals("asr", tracks[0].kind)
        assertEquals("en", tracks[0].languageCode)
        assertTrue(tracks[0].baseUrl.contains("fromandroid=1"))
    }

    @Test
    fun `caption parsing is lenient about garbage`() {
        assertEquals(emptyList<CaptionTrack>(), WatchPageExtractor.captionTracksFromPlayerResponse("not json"))
        assertEquals(emptyList<CaptionTrack>(), WatchPageExtractor.captionTracksFromPlayerResponse("""{"captions":{}}"""))
        // Tracks missing required fields are skipped, not fatal.
        val tracks = WatchPageExtractor.captionTracksFromPlayerResponse(
            """{"captions":{"playerCaptionsTracklistRenderer":{"captionTracks":[
                {"languageCode":"en"},
                {"baseUrl":"https://example/tt","languageCode":"en"}
            ]}}}""",
        )
        assertEquals(1, tracks.size)
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
