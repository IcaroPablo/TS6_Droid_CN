package dev.tsdroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A part over the server's limit is dropped without a word, and a part lost or misjoined loses a
 * whole history reply, so the cutting and the joining are pinned here.
 */
class CommandPartsTest {

    private fun join(parts: List<String>, senderId: Int = 7, assembler: CommandParts.Assembler = CommandParts.Assembler()): String? =
        parts.map { assembler.add(senderId, it) }.last()

    @Test
    fun `every part fits the server's limit`() {
        val data = "{\"m\":\"" + "olá, açaí 🎉 ".repeat(400) + "\"}"
        val parts = CommandParts.split(data, key = 42)
        assertTrue(parts.size > 1)
        for (part in parts) assertTrue(CommandParts.fits(part))
    }

    @Test
    fun `parts join back into the command`() {
        val data = "x".repeat(5000) + "🎉".repeat(300) + "fim"
        assertEquals(data, join(CommandParts.split(data, key = 1)))
    }

    @Test
    fun `parts arriving out of order still join`() {
        val data = "abc".repeat(1000)
        assertEquals(data, join(CommandParts.split(data, key = 3).reversed()))
    }

    @Test
    fun `nothing is returned until the last part`() {
        val assembler = CommandParts.Assembler()
        val parts = CommandParts.split("y".repeat(3000), key = 5)
        for (part in parts.dropLast(1)) assertNull(assembler.add(1, part))
        assertEquals("y".repeat(3000), assembler.add(1, parts.last()))
    }

    @Test
    fun `two senders' parts with the same key stay apart`() {
        val assembler = CommandParts.Assembler()
        val a = CommandParts.split("a".repeat(2000), key = 9)
        val b = CommandParts.split("b".repeat(2000), key = 9)
        assertNull(assembler.add(1, a[0]))
        assertNull(assembler.add(2, b[0]))
        assertEquals("a".repeat(2000), a.drop(1).map { assembler.add(1, it) }.last())
        assertEquals("b".repeat(2000), b.drop(1).map { assembler.add(2, it) }.last())
    }

    @Test
    fun `a short command fits whole`() {
        assertTrue(CommandParts.fits("x".repeat(1024)))
        assertFalse(CommandParts.fits("x".repeat(1025)))
        // Counted in bytes: two-byte characters fill it at half the count
        assertFalse(CommandParts.fits("é".repeat(600)))
    }

    @Test
    fun `malformed parts are ignored`() {
        val assembler = CommandParts.Assembler()
        assertNull(assembler.add(1, "not a part"))
        assertNull(assembler.add(1, "1:5:3:index past the count"))
        assertNull(assembler.add(1, "1:0:0:no parts"))
    }
}
