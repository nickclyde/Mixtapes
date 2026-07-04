package tech.clyde.mixtapes.core

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.clyde.mixtapes.core.model.Chapter

class SmokeTest {
    @Test
    fun `module wiring works`() {
        val chapter = Chapter(title = "Chrono Trigger", seconds = 83)
        assertEquals("Chrono Trigger", chapter.title)
    }
}
