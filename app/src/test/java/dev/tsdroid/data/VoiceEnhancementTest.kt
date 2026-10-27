package dev.tsdroid.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The stored value must survive a round trip, and anything unknown is off. */
class VoiceEnhancementTest {

    @Test
    fun `every level round-trips through its stored value`() {
        for (level in VoiceEnhancement.entries) {
            assertEquals(level, VoiceEnhancement.from(level.toRaw()))
        }
    }

    @Test
    fun `missing or unknown values default to off`() {
        assertEquals(VoiceEnhancement.OFF, VoiceEnhancement.from(null))
        assertEquals(VoiceEnhancement.OFF, VoiceEnhancement.from("bogus"))
    }

    @Test
    fun `levels map to opus decoder complexities`() {
        assertEquals(listOf(0, 5, 6, 7), VoiceEnhancement.entries.map { it.decoderComplexity })
    }
}
