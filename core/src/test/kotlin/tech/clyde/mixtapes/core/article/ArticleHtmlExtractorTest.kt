package tech.clyde.mixtapes.core.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.clyde.mixtapes.core.Fixtures

class ArticleHtmlExtractorTest {

    @Test
    fun `prefers article and preserves numbered heading order while removing noise`() {
        val result = ArticleHtmlExtractor.extract(Fixtures.text("articles/nintendo_style.html"))
        val article = (result as ArticleHtmlExtractor.Result.Success).article

        assertEquals("50 Best Super Nintendo (SNES) Games Of All Time", article.title)
        val fifty = article.content.indexOf("HEADING H2: 50. The Adventures of Batman & Robin")
        val fortyNine = article.content.indexOf("HEADING H2: 49. Tetris Attack")
        val fortyEight = article.content.indexOf("HEADING H2: 48. Super Mario RPG")
        assertTrue(fifty >= 0 && fifty < fortyNine && fortyNine < fortyEight)
        assertFalse(article.content.contains("fallback should not be selected"))
        assertFalse(article.content.contains("Related:"))
        assertFalse(article.content.contains("should have been number one"))
        assertFalse(article.content.contains("Duplicate image caption"))
        assertFalse(article.content.contains("HEADING H1")) // article header is deliberately noise
    }

    @Test
    fun `falls back to main and records ordered list positions`() {
        val result = ArticleHtmlExtractor.extract(Fixtures.text("articles/ordered_list.html"))
        val content = (result as ArticleHtmlExtractor.Result.Success).article.content

        assertTrue(content.contains("LIST ITEM 3: Shantae"))
        assertTrue(content.contains("LIST ITEM 4: Wave Race"))
        assertTrue(content.contains("LIST ITEM 5: Pokemon Emerald"))
        assertTrue(content.indexOf("LIST ITEM 3") < content.indexOf("LIST ITEM 5"))
    }

    @Test
    fun `malformed html falls back to body in DOM order`() {
        val malformed = "<html><title>Broken page</title><body><h2>2. EarthBound<p>A strange RPG<h2>1. Chrono Trigger<p>A time-travel RPG"
        val result = ArticleHtmlExtractor.extract(malformed)
        val article = (result as ArticleHtmlExtractor.Result.Success).article

        assertEquals("Broken page", article.title)
        assertTrue(article.content.indexOf("2. EarthBound") < article.content.indexOf("1. Chrono Trigger"))
    }

    @Test
    fun `decodes residual html entity text from article CMS markup`() {
        val html = """
            <article>
              <p>A ranked pair of role-playing games from the Super Nintendo library.</p>
              <h2>2. Lufia &amp;amp; The Fortress of Doom</h2>
              <h2>1. Tetris &amp;amp; Dr. Mario</h2>
            </article>
        """.trimIndent()
        val content = (ArticleHtmlExtractor.extract(html) as ArticleHtmlExtractor.Result.Success).article.content
        assertTrue(content.contains("Lufia & The Fortress of Doom"))
        assertTrue(content.contains("Tetris & Dr. Mario"))
        assertFalse(content.contains("&amp;"))
    }

    @Test
    fun `rejects empty and insubstantial pages`() {
        assertEquals(ArticleHtmlExtractor.Result.InsufficientContent, ArticleHtmlExtractor.extract(""))
        assertEquals(
            ArticleHtmlExtractor.Result.InsufficientContent,
            ArticleHtmlExtractor.extract("<html><body><article><p>One short line.</p></article></body></html>"),
        )
    }

    @Test
    fun `caps cleaned content without splitting a surrogate pair`() {
        val paragraphs = buildString {
            repeat(2_500) { index ->
                append("<p>$index A substantial paragraph about a ranked retro game 🎮 and why it belongs here.</p>")
            }
        }
        val result = ArticleHtmlExtractor.extract("<html><body><article>$paragraphs</article></body></html>")
        val article = (result as ArticleHtmlExtractor.Result.Success).article

        assertTrue(article.truncated)
        assertTrue(article.content.length <= ArticleHtmlExtractor.MAX_CONTENT_CHARS)
        assertFalse(article.content.last().isHighSurrogate())
    }
}
