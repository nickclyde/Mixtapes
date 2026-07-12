package tech.clyde.mixtapes.core.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class TimedTextTest {

    @Test
    fun `appends json3 to a url without fmt`() {
        assertEquals(
            "https://www.youtube.com/api/timedtext?v=abc&lang=en&fmt=json3",
            TimedText.json3Url("https://www.youtube.com/api/timedtext?v=abc&lang=en"),
        )
    }

    @Test
    fun `replaces a baked-in fmt instead of appending a losing duplicate`() {
        // ANDROID-client baseUrls carry fmt=srv3; the endpoint honors the first fmt.
        assertEquals(
            "https://www.youtube.com/api/timedtext?v=abc&lang=en&signature=sig&fmt=json3",
            TimedText.json3Url("https://www.youtube.com/api/timedtext?v=abc&fmt=srv3&lang=en&signature=sig"),
        )
    }

    @Test
    fun `handles fmt as the first query param`() {
        assertEquals(
            "https://www.youtube.com/api/timedtext?v=abc&fmt=json3",
            TimedText.json3Url("https://www.youtube.com/api/timedtext?fmt=srv1&v=abc"),
        )
    }
}
