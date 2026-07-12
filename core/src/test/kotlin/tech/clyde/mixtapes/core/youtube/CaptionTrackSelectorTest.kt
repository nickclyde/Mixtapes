package tech.clyde.mixtapes.core.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptionTrackSelectorTest {

    private fun track(lang: String, kind: String? = null, url: String = "https://example/tt?lang=$lang&kind=$kind") =
        CaptionTrack(baseUrl = url, languageCode = lang, kind = kind)

    @Test
    fun `manual english beats asr english`() {
        val manual = track("en")
        val asr = track("en", kind = "asr")
        assertEquals(manual, CaptionTrackSelector.pick(listOf(asr, manual)))
    }

    @Test
    fun `asr english beats manual non-english`() {
        val asrEn = track("en", kind = "asr")
        val manualDe = track("de")
        assertEquals(asrEn, CaptionTrackSelector.pick(listOf(manualDe, asrEn)))
    }

    @Test
    fun `regional english counts as english`() {
        val enUs = track("en-US", kind = "asr")
        val manualJa = track("ja")
        assertEquals(enUs, CaptionTrackSelector.pick(listOf(manualJa, enUs)))
    }

    @Test
    fun `non-english manual beats non-english asr`() {
        val asrJa = track("ja", kind = "asr")
        val manualDe = track("de")
        assertEquals(manualDe, CaptionTrackSelector.pick(listOf(asrJa, manualDe)))
    }

    @Test
    fun `lone asr non-english track is still picked`() {
        val asrJa = track("ja", kind = "asr")
        assertEquals(asrJa, CaptionTrackSelector.pick(listOf(asrJa)))
    }

    @Test
    fun `no tracks yields null`() {
        assertNull(CaptionTrackSelector.pick(emptyList()))
    }

    @Test
    fun `enigmatic language codes are not english`() {
        // "enm" (Middle English) or similar must not match the en prefix rule.
        val enm = track("enm")
        val asrEn = track("en", kind = "asr")
        assertEquals(asrEn, CaptionTrackSelector.pick(listOf(enm, asrEn)))
    }
}
