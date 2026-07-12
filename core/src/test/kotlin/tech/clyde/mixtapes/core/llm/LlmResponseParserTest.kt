package tech.clyde.mixtapes.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.clyde.mixtapes.core.Fixtures
import tech.clyde.mixtapes.core.chapters.ChapterFilter
import tech.clyde.mixtapes.core.llm.LlmResponseParser.Parsed
import tech.clyde.mixtapes.core.model.Chapter

class LlmResponseParserTest {

    private val expectedTitles =
        listOf("Castlevania: Symphony of the Night", "Ys I & II", "Terranigma")

    @Test
    fun `parses content that is exactly a json array`() {
        val parsed = LlmResponseParser.parse(Fixtures.text("llm/chat_completion_clean.json"))
        assertEquals(Parsed.Titles(expectedTitles), parsed)
    }

    @Test
    fun `parses an array buried in prose and code fences`() {
        val parsed = LlmResponseParser.parse(Fixtures.text("llm/chat_completion_fenced.json"))
        assertEquals(Parsed.Titles(expectedTitles), parsed)
    }

    @Test
    fun `parses a single-array-value json object`() {
        val parsed = LlmResponseParser.parse(Fixtures.text("llm/chat_completion_object.json"))
        assertEquals(Parsed.Titles(expectedTitles), parsed)
    }

    @Test
    fun `surfaces gateway error messages`() {
        val parsed = LlmResponseParser.parse(Fixtures.text("llm/chat_completion_error.json"))
        assertEquals(Parsed.ApiError("Invalid API key"), parsed)
    }

    @Test
    fun `prose without any array is unparseable`() {
        val parsed = LlmResponseParser.parse(Fixtures.text("llm/chat_completion_garbage.json"))
        assertEquals(Parsed.Unparseable, parsed)
    }

    @Test
    fun `non-json body is unparseable`() {
        assertEquals(Parsed.Unparseable, LlmResponseParser.parse("<html>502 Bad Gateway</html>"))
        assertEquals(Parsed.Unparseable, LlmResponseParser.parse(""))
    }

    @Test
    fun `a fully fenced array parses without surrounding prose`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"```json\n[\"Ristar\"]\n```"}}]}"""
        assertEquals(Parsed.Titles(listOf("Ristar")), LlmResponseParser.parse(body))
    }

    @Test
    fun `blank entries are filtered and an empty array is valid`() {
        val blanks = """{"choices":[{"message":{"content":"[\"Ristar\", \"  \", \"\"]"}}]}"""
        assertEquals(Parsed.Titles(listOf("Ristar")), LlmResponseParser.parse(blanks))

        val empty = """{"choices":[{"message":{"content":"[]"}}]}"""
        assertEquals(Parsed.Titles(emptyList()), LlmResponseParser.parse(empty))
    }

    @Test
    fun `an array of non-strings is not silently coerced`() {
        val body = """{"choices":[{"message":{"content":"[1, 2, 3]"}}]}"""
        assertEquals(Parsed.Unparseable, LlmResponseParser.parse(body))
    }

    @Test
    fun `llm titles survive ChapterFilter unfiltered`() {
        // Transcript-derived titles reuse ChapterFilter as a stop-word net; real game
        // titles must pass through with skipped == false.
        val chapters = expectedTitles.map { Chapter(title = it, seconds = 0) }
        val filtered = ChapterFilter.markSkipped(chapters)
        assertTrue(filtered.none { it.skipped })
        // ...while junk the model might still emit gets caught.
        val junk = ChapterFilter.markSkipped(listOf(Chapter(title = "Intro", seconds = 0)))
        assertTrue(junk.single().skipped)
    }
}
