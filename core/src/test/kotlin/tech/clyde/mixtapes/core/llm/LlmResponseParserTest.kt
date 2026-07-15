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
        assertEquals(Parsed.Extraction(expectedTitles), parsed)
    }

    @Test
    fun `parses an array buried in prose and code fences`() {
        val parsed = LlmResponseParser.parse(Fixtures.text("llm/chat_completion_fenced.json"))
        assertEquals(Parsed.Extraction(expectedTitles), parsed)
    }

    @Test
    fun `parses a single-array-value json object`() {
        val parsed = LlmResponseParser.parse(Fixtures.text("llm/chat_completion_object.json"))
        assertEquals(Parsed.Extraction(expectedTitles), parsed)
    }

    @Test
    fun `parses an object with system and games`() {
        val parsed = LlmResponseParser.parse(Fixtures.text("llm/chat_completion_system.json"))
        assertEquals(Parsed.Extraction(expectedTitles, "snes"), parsed)
    }

    @Test
    fun `system aliases canonicalize to the family id`() {
        val parsed = LlmResponseParser.parse(Fixtures.text("llm/chat_completion_system_alias.json"))
        assertEquals(Parsed.Extraction(expectedTitles, "snes"), parsed)
    }

    @Test
    fun `unknown system becomes null but titles survive`() {
        val parsed = LlmResponseParser.parse(Fixtures.text("llm/chat_completion_system_unknown.json"))
        assertEquals(Parsed.Extraction(expectedTitles, null), parsed)
    }

    @Test
    fun `a fenced object with system parses`() {
        val parsed = LlmResponseParser.parse(Fixtures.text("llm/chat_completion_system_fenced.json"))
        assertEquals(Parsed.Extraction(expectedTitles, "snes"), parsed)
    }

    @Test
    fun `an explicit json null system parses`() {
        val body = """{"choices":[{"message":{"content":"{\"system\": null, \"games\": [\"Ristar\"]}"}}]}"""
        assertEquals(Parsed.Extraction(listOf("Ristar"), null), LlmResponseParser.parse(body))
    }

    @Test
    fun `a bare array still parses with a null system`() {
        // Backward-compat pin: stale gateways/models may keep answering the old
        // array-only contract.
        val body = """{"choices":[{"message":{"content":"[\"Ristar\"]"}}]}"""
        assertEquals(Parsed.Extraction(listOf("Ristar"), null), LlmResponseParser.parse(body))
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
        assertEquals(Parsed.Extraction(listOf("Ristar")), LlmResponseParser.parse(body))
    }

    @Test
    fun `blank entries are filtered and an empty array is valid`() {
        val blanks = """{"choices":[{"message":{"content":"[\"Ristar\", \"  \", \"\"]"}}]}"""
        assertEquals(Parsed.Extraction(listOf("Ristar")), LlmResponseParser.parse(blanks))

        val empty = """{"choices":[{"message":{"content":"[]"}}]}"""
        assertEquals(Parsed.Extraction(emptyList()), LlmResponseParser.parse(empty))
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
