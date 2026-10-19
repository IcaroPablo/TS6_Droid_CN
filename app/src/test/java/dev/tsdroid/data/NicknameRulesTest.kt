package dev.tsdroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same rules guard the connect form and the in-session rename, so they are
 * pinned here instead of living as a loose `3` in one ViewModel.
 */
class NicknameRulesTest {

    @Test
    fun `three characters are enough, two are not`() {
        assertTrue(NicknameRules.isValid("abc"))
        assertFalse(NicknameRules.isValid("ab"))
    }

    @Test
    fun `surrounding whitespace does not count`() {
        assertFalse(NicknameRules.isValid("  ab  "))
        assertTrue(NicknameRules.isValid("  abc  "))
        assertEquals("abc", NicknameRules.normalize("  abc  "))
    }

    @Test
    fun `blank input is invalid`() {
        assertFalse(NicknameRules.isValid(""))
        assertFalse(NicknameRules.isValid("   "))
    }

    @Test
    fun `an over-long nickname is refused before the server truncates it`() {
        assertTrue(NicknameRules.isValid("a".repeat(NicknameRules.MAX_LENGTH)))
        assertFalse(NicknameRules.isValid("a".repeat(NicknameRules.MAX_LENGTH + 1)))
    }

    @Test
    fun `a nickname of chinese characters counts characters, not bytes`() {
        assertTrue(NicknameRules.isValid("机器人小助手"))
        assertFalse(NicknameRules.isValid("机器"))
    }
}
