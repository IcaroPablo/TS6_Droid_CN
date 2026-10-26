package dev.tsdroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The status enum decides what goes on the wire, so the mapping is pinned here:
 * online must clear the away message, and a typed message must win over the
 * default while a blank one falls back to it.
 */
class AwayStatusTest {

    @Test
    fun `online carries no message at all`() {
        assertFalse(AwayStatus.ONLINE.away)
        assertNull(AwayStatus.ONLINE.defaultMessage)
        assertNull(AwayStatus.ONLINE.message(""))
        // A leftover custom message must not be resurrected by going online.
        assertNull(AwayStatus.ONLINE.message("lunch"))
    }

    @Test
    fun `away and do-not-disturb are away states with their own defaults`() {
        assertTrue(AwayStatus.AWAY.away)
        assertTrue(AwayStatus.DND.away)
        assertEquals("Away", AwayStatus.AWAY.message(""))
        assertEquals("Do not disturb", AwayStatus.DND.message(""))
    }

    @Test
    fun `a typed message wins over the default and is trimmed`() {
        assertEquals("in a meeting", AwayStatus.AWAY.message("  in a meeting  "))
        assertEquals("复盘", AwayStatus.DND.message("复盘"))
    }

    @Test
    fun `blank input falls back to the default message`() {
        assertEquals("Away", AwayStatus.AWAY.message("   "))
        assertEquals("Do not disturb", AwayStatus.DND.message(""))
    }
}
