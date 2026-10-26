package dev.tsdroid.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The schedule that keeps a failing download from being retried on every list refresh. */
class RetryBackoffTest {

    private var clock = 0L
    private val backoff = RetryBackoff { clock }

    @Test
    fun `a fresh key may be tried`() {
        assertTrue(backoff.canTry("icon"))
    }

    @Test
    fun `a failure waits 30 s, then four times longer each time`() {
        backoff.failed("icon", permanent = false)
        clock += 29_999
        assertFalse(backoff.canTry("icon"))
        clock += 1
        assertTrue(backoff.canTry("icon"))

        backoff.failed("icon", permanent = false)
        clock += 119_999
        assertFalse(backoff.canTry("icon"))
        clock += 1
        assertTrue(backoff.canTry("icon"))
    }

    @Test
    fun `it gives up after the last attempt until reset`() {
        repeat(RetryBackoff.MAX_ATTEMPTS) {
            backoff.failed("icon", permanent = false)
            clock += RetryBackoff.MAX_DELAY_MS
        }
        assertTrue(backoff.gaveUp("icon"))
        assertFalse(backoff.canTry("icon"))
        backoff.reset()
        assertTrue(backoff.canTry("icon"))
    }

    @Test
    fun `a permanent failure gives up at once`() {
        backoff.failed("icon", permanent = true)
        clock += RetryBackoff.MAX_DELAY_MS
        assertFalse(backoff.canTry("icon"))
    }

    @Test
    fun `success clears the record`() {
        backoff.failed("icon", permanent = false)
        backoff.succeeded("icon")
        assertTrue(backoff.canTry("icon"))
    }

    @Test
    fun `keys are independent`() {
        backoff.failed("a", permanent = true)
        assertTrue(backoff.canTry("b"))
    }
}
