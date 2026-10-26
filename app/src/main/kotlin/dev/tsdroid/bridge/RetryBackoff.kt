package dev.tsdroid.bridge

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * When a failed download (an icon, an avatar) may be tried again.
 *
 * Callers ask on every refresh of the channel or member list, which comes every half second,
 * so without this a failing download is retried twice a second forever, and the burst is
 * itself what trips the server's per-client transfer limit (FileTransferLimitReached).
 * Each failure doubles the wait twice over — [BASE_DELAY_MS], ×4, ×16 … up to
 * [MAX_DELAY_MS] — and after [MAX_ATTEMPTS] failures, or one permanent failure (a missing
 * file, no permission), the key is given up until [reset].
 */
class RetryBackoff(private val now: () -> Long = SystemClock::elapsedRealtime) {

    companion object {
        const val BASE_DELAY_MS = 30_000L
        const val MAX_DELAY_MS = 30 * 60_000L
        const val MAX_ATTEMPTS = 5
    }

    private class State(val failures: Int, val retryAt: Long)

    private val states = ConcurrentHashMap<Any, State>()

    fun canTry(key: Any): Boolean {
        val state = states[key] ?: return true
        return state.failures < MAX_ATTEMPTS && now() >= state.retryAt
    }

    /** True once [key] will not be tried again before [reset]. */
    fun gaveUp(key: Any): Boolean = (states[key]?.failures ?: 0) >= MAX_ATTEMPTS

    fun failed(key: Any, permanent: Boolean) {
        states.compute(key) { _, old ->
            val failures = if (permanent) MAX_ATTEMPTS else (old?.failures ?: 0) + 1
            var delay = BASE_DELAY_MS
            repeat(failures - 1) { delay = minOf(delay * 4, MAX_DELAY_MS) }
            State(failures, now() + delay)
        }
    }

    fun succeeded(key: Any) {
        states.remove(key)
    }

    /** Forget all failures, e.g. on a new connection, where the server may behave differently. */
    fun reset(vararg keys: Any) {
        if (keys.isEmpty()) states.clear() else keys.forEach { states.remove(it) }
    }
}
