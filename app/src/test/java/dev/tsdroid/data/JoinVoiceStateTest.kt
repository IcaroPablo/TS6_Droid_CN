package dev.tsdroid.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The stored value must survive a round trip, and anything unknown joins deafened. */
class JoinVoiceStateTest {

    @Test
    fun `every state round-trips through its stored value`() {
        for (state in JoinVoiceState.entries) {
            assertEquals(state, JoinVoiceState.from(state.toRaw()))
        }
    }

    @Test
    fun `missing or unknown values default to deafened`() {
        assertEquals(JoinVoiceState.DEAFENED, JoinVoiceState.from(null))
        assertEquals(JoinVoiceState.DEAFENED, JoinVoiceState.from("bogus"))
    }
}
