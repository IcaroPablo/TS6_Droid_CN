package dev.tsdroid.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameLossTrackerTest {

    private var now = 0L
    private val tracker = FrameLossTracker { now }

    /** The next frame, 20 ms after the previous one. */
    private fun frame(seq: Int, userId: Int = 1, isWhisper: Boolean = false): Int? {
        now += 20_000_000L
        return tracker.lostBefore(userId, seq, isWhisper)
    }

    @Test
    fun `consecutive frames lose nothing`() {
        assertEquals(0, frame(10))
        assertEquals(0, frame(11))
        assertEquals(0, frame(12))
    }

    @Test
    fun `a gap counts the missing frames`() {
        frame(10)
        assertEquals(1, frame(12))
        assertEquals(3, frame(16))
    }

    @Test
    fun `the counter wraps`() {
        frame(0xFFFE)
        assertEquals(0, frame(0xFFFF))
        assertEquals(1, frame(1))
    }

    @Test
    fun `duplicates and late frames are dropped`() {
        frame(10)
        frame(11)
        assertNull(frame(11))
        assertNull(frame(9))
        assertEquals(0, frame(12))
    }

    @Test
    fun `long gaps are not concealed`() {
        frame(10)
        assertEquals(0, frame(10 + FrameLossTracker.MAX_CONCEALED_FRAMES + 2))
    }

    @Test
    fun `a sender starting over after a pause loses nothing`() {
        frame(10)
        now += FrameLossTracker.CONCEAL_WINDOW_NS
        assertEquals(0, frame(13))
        now += FrameLossTracker.CONCEAL_WINDOW_NS
        // Even a lower number: a recycled client id, or a new session
        assertEquals(0, frame(2))
    }

    @Test
    fun `senders and whispers are counted apart`() {
        frame(10, userId = 1)
        assertEquals(0, frame(500, userId = 2))
        assertEquals(0, frame(700, userId = 1, isWhisper = true))
        assertEquals(1, frame(12, userId = 1))
    }

    @Test
    fun `departed senders start afresh`() {
        frame(10, userId = 1)
        frame(10, userId = 2)
        tracker.retainUsers(setOf(2))
        assertEquals(0, frame(5, userId = 1))
        assertNull(frame(9, userId = 2))
    }
}
