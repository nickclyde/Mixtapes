package tech.clyde.mixtapes.core.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tech.clyde.mixtapes.core.Fixtures

class Json3TranscriptParserTest {

    @Test
    fun `parses a realistic asr track to plain text`() {
        val text = Json3TranscriptParser.parse(Fixtures.text("transcripts/sample.json3"))

        assertEquals(
            "hey everybody welcome back today we're counting down twenty more hidden gems " +
                "first up is castle vania symphony of the night an absolute classic" +
                "\n\n" +
                "next we have ease one and two on the turbografx such an underrated series",
            text,
        )
    }

    @Test
    fun `no text events yields null`() {
        assertNull(Json3TranscriptParser.parse(Fixtures.text("transcripts/empty.json3")))
    }

    @Test
    fun `garbage yields null`() {
        assertNull(Json3TranscriptParser.parse("<transcript>not json</transcript>"))
        assertNull(Json3TranscriptParser.parse(""))
        assertNull(Json3TranscriptParser.parse("""{"no_events": true}"""))
    }

    @Test
    fun `newline-only segs never produce text`() {
        val json3 = """{"events":[{"tStartMs":0,"segs":[{"utf8":"\n"}]},{"tStartMs":100,"segs":[{"utf8":"\n"},{"utf8":"\n"}]}]}"""
        assertNull(Json3TranscriptParser.parse(json3))
    }
}
