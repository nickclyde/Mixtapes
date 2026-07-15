package tech.clyde.mixtapes.core.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameListPromptTest {

    @Test
    fun `builds a valid chat completions body`() {
        val body = GameListPrompt.requestBody(
            model = "google/gemini-2.5-flash",
            videoTitle = "20 \"MORE\" SNES Hidden Gems!",
            transcript = "first up is castle vania\n\nnext we have ease one and two",
        )

        val root = Json.parseToJsonElement(body).jsonObject
        assertEquals("google/gemini-2.5-flash", root["model"]?.jsonPrimitive?.content)

        val messages = root["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("user", messages[1].jsonObject["role"]?.jsonPrimitive?.content)

        val system = messages[0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(system.contains("\"games\""))
        assertTrue(system.contains("\"system\""))
        // The system-id vocabulary is embedded from SystemHint.canonicalIds.
        assertTrue(system.contains("snes"))
        assertTrue(system.contains("pcengine"))

        // Quotes in the title must survive JSON encoding; transcript must be present verbatim.
        val user = messages[1].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(user.contains("20 \"MORE\" SNES Hidden Gems!"))
        assertTrue(user.contains("next we have ease one and two"))
    }

    @Test
    fun `never sends response_format`() {
        val body = GameListPrompt.requestBody("m", "t", "x")
        val root = Json.parseToJsonElement(body).jsonObject
        // Some OpenAI-compatible gateways 400 on response_format; we parse defensively instead.
        assertFalse(root.containsKey("response_format"))
    }
}
