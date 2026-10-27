package dev.tsdroid.bridge

import java.util.concurrent.ConcurrentHashMap

/**
 * Notices lost voice frames from the senders' sequence numbers, so playback
 * can conceal them. Whispers are tracked apart from channel voice: they may
 * be counted separately.
 */
class FrameLossTracker(private val nanoTime: () -> Long = System::nanoTime) {

    companion object {
        /**
         * Longest run of lost frames that gets concealed (100 ms). Opus's own
         * concealment fades to silence over longer gaps anyway, and after one
         * that long the sender has more likely paused than lost packets.
         */
        const val MAX_CONCEALED_FRAMES = 5

        /**
         * A sequence gap only counts as loss when the frame after it arrives
         * within this long of the one before: a sender that stopped talking
         * and starts again has nothing to conceal in between.
         */
        const val CONCEAL_WINDOW_NS = 300_000_000L
    }

    private class LastFrame(val seq: Int, val atNs: Long)

    private val lastFrames = ConcurrentHashMap<Long, LastFrame>()

    /**
     * How many frames went missing just before frame [seq] (a 16-bit counter)
     * of this sender, or null when the frame is a duplicate or arrived after a
     * newer one, too late to play in order.
     */
    fun lostBefore(userId: Int, seq: Int, isWhisper: Boolean): Int? {
        val key = (userId.toLong() shl 1) or (if (isWhisper) 1L else 0L)
        val now = nanoTime()
        val last = lastFrames[key]
        var lost = 0
        if (last != null && now - last.atNs < CONCEAL_WINDOW_NS) {
            val gap = (seq - last.seq - 1) and 0xFFFF
            // A "gap" of half the counter or more is a frame from the past.
            // Outside the window the sender simply starts over.
            if (gap >= 0x8000) return null
            if (gap <= MAX_CONCEALED_FRAMES) lost = gap
        }
        lastFrames[key] = LastFrame(seq and 0xFFFF, now)
        return lost
    }

    /** Forget senders that left, so a recycled client id starts afresh. */
    fun retainUsers(activeIds: Set<Int>) {
        lastFrames.keys.removeAll { (it shr 1).toInt() !in activeIds }
    }

    fun clear() {
        lastFrames.clear()
    }
}
