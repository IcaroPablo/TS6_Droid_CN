package dev.tsdroid.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperTargetsTest {

    private val uidAlice = "uid-alice"
    private val uidBob = "uid-bob"

    private fun online(vararg pairs: Pair<String, Int>) = pairs.toMap()

    @Test
    fun `an empty list means no whisper routing at all`() {
        val voice = WhisperRouting.resolve(WhisperTargets(), online(uidAlice to 7))
        assertArrayEquals(intArrayOf(), voice.clients)
        assertArrayEquals(longArrayOf(), voice.channels)
        assertArrayEquals(longArrayOf(), voice.groups)
    }

    @Test
    fun `client targets resolve to their live client ids`() {
        val targets = WhisperTargets(clients = listOf(uidBob, uidAlice))
        val voice = WhisperRouting.resolve(targets, online(uidAlice to 7, uidBob to 3))
        // Sorted, so the native call is stable regardless of click order.
        assertArrayEquals(intArrayOf(3, 7), voice.clients)
        assertArrayEquals(longArrayOf(), voice.channels)
        assertArrayEquals(longArrayOf(), voice.groups)
    }

    @Test
    fun `uid targets keep working after the peer reconnects under a new client id`() {
        val targets = WhisperTargets(clients = listOf(uidAlice))
        val before = WhisperRouting.resolve(targets, online(uidAlice to 7))
        val after = WhisperRouting.resolve(targets, online(uidAlice to 91))
        assertArrayEquals(intArrayOf(7), before.clients)
        assertArrayEquals(intArrayOf(91), after.clients)
    }

    @Test
    fun `channel targets are routed even with no client target online`() {
        val targets = WhisperTargets(clients = listOf(uidAlice), channels = listOf(42L))
        val voice = WhisperRouting.resolve(targets, online())
        assertArrayEquals(intArrayOf(), voice.clients)
        assertArrayEquals(longArrayOf(42L), voice.channels)
    }

    @Test
    fun `every target offline drops the voice instead of falling back to the channel`() {
        val targets = WhisperTargets(clients = listOf(uidAlice, uidBob))
        val voice = WhisperRouting.resolve(targets, online())
        // The drop sentinel: an empty set would mean "no whisper routing", which
        // would broadcast the microphone to the whole channel.
        assertArrayEquals(intArrayOf(WhisperRouting.DROP_SENTINEL_CLIENT_ID), voice.clients)
        assertArrayEquals(longArrayOf(), voice.channels)
        assertArrayEquals(longArrayOf(), voice.groups)
    }

    @Test
    fun `server group targets are handed to the native layer unresolved`() {
        // A group is one entry resolved per packet natively; expanding it here
        // would freeze its membership and count every member against the cap.
        val targets = WhisperTargets(clients = listOf(uidAlice), groups = listOf(6L, 8L, 6L))
        val voice = WhisperRouting.resolve(targets, online(uidAlice to 4))
        assertArrayEquals(intArrayOf(4), voice.clients)
        assertArrayEquals(longArrayOf(), voice.channels)
        assertArrayEquals(longArrayOf(6L, 8L), voice.groups)
    }

    @Test
    fun `an offline client does not disable a group target`() {
        // The listed client is gone but the group still resolves natively, so the
        // whisper set must stay a real set rather than the drop sentinel.
        val targets = WhisperTargets(clients = listOf(uidAlice), groups = listOf(6L))
        val voice = WhisperRouting.resolve(targets, online())
        assertArrayEquals(intArrayOf(), voice.clients)
        assertArrayEquals(longArrayOf(6L), voice.groups)
    }

    @Test
    fun `toggling a server group adds and removes it`() {
        val added = WhisperTargets().toggleGroup(6L)
        assertTrue(added.hasGroup(6L))
        assertFalse(added.toggleGroup(6L).hasGroup(6L))
    }

    @Test
    fun `isNotEmpty tracks all three target kinds`() {
        // This is what decides whether a hold-to-whisper press has anywhere to go,
        // so a group-only or channel-only list must not read as empty.
        assertFalse(WhisperTargets().isNotEmpty)
        assertTrue(WhisperTargets(clients = listOf(uidAlice)).isNotEmpty)
        assertTrue(WhisperTargets(channels = listOf(42L)).isNotEmpty)
        assertTrue(WhisperTargets(groups = listOf(6L)).isNotEmpty)
    }

    @Test
    fun `duplicate targets collapse into one`() {
        val targets = WhisperTargets(
            clients = listOf(uidAlice, uidAlice),
            channels = listOf(42L, 42L),
        )
        val voice = WhisperRouting.resolve(targets, online(uidAlice to 5))
        assertArrayEquals(intArrayOf(5), voice.clients)
        assertArrayEquals(longArrayOf(42L), voice.channels)
    }

    @Test
    fun `toggling a client adds and removes it`() {
        val added = WhisperTargets().toggleClient(uidAlice)
        assertTrue(added.hasClient(uidAlice))
        assertFalse(added.toggleClient(uidAlice).hasClient(uidAlice))
    }

    @Test
    fun `toggling a channel adds and removes it`() {
        val added = WhisperTargets().toggleChannel(42L)
        assertTrue(added.hasChannel(42L))
        assertFalse(added.toggleChannel(42L).hasChannel(42L))
    }

    @Test
    fun `clients and channels share the whisper target cap`() {
        var targets = WhisperTargets()
        repeat(WhisperRouting.MAX_TARGETS - 1) { index -> targets = targets.toggleClient("uid-$index") }
        targets = targets.toggleChannel(42L)

        assertEquals(WhisperRouting.MAX_TARGETS, targets.size)
        assertFalse(targets.canAdd())
        // A full list refuses the addition, and refusing changes nothing.
        assertEquals(targets, targets.toggleClient(uidAlice))
        assertEquals(targets, targets.toggleChannel(43L))
        assertEquals(targets, targets.toggleGroup(6L))
        // Removing is still possible, and frees a slot again.
        val freed = targets.toggleClient("uid-0")
        assertEquals(WhisperRouting.MAX_TARGETS - 1, freed.size)
        assertTrue(freed.canAdd())
        // The freed slot can be used by a group instead of another member.
        val withGroup = freed.toggleGroup(6L)
        assertEquals(WhisperRouting.MAX_TARGETS, withGroup.size)
        assertTrue(withGroup.hasGroup(6L))
    }
}
