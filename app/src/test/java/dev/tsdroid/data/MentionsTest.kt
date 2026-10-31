package dev.tsdroid.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** A wrong match leaves a note in a stranger's mailbox, a missed one loses the mention. */
class MentionsTest {

    private val people = mapOf(
        "uidA" to "ushummu",
        "uidB" to "Ushummu (honor)",
        "uidC" to "Lixand",
        "uidD" to "ushummu1",
    )

    @Test
    fun `a plain mention is found, whatever the case`() {
        assertEquals(setOf("uidC"), Mentions.mentioned("hey @lixand, look", people))
    }

    @Test
    fun `the longest name wins`() {
        assertEquals(setOf("uidB"), Mentions.mentioned("@Ushummu (honor) are you there", people))
    }

    @Test
    fun `a name must end where the mention does`() {
        assertEquals(setOf("uidD"), Mentions.mentioned("@ushummu1 hi", people))
        assertEquals(setOf("uidA"), Mentions.mentioned("@ushummu: hi", people))
        assertEquals(emptySet<String>(), Mentions.mentioned("@ushummuX hi", people))
    }

    @Test
    fun `several mentions in one message`() {
        assertEquals(setOf("uidA", "uidC"), Mentions.mentioned("@Lixand and @ushummu, come", people))
    }

    @Test
    fun `no at sign, no mention`() {
        assertEquals(emptySet<String>(), Mentions.mentioned("lixand ushummu", people))
        assertEquals(emptySet<String>(), Mentions.mentioned("mail me at a@b.c", people))
    }
}
