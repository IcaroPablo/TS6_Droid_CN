package dev.tsdroid.service

import dev.tsdroid.bridge.WhisperVoiceTargets

/**
 * Immutable whisper (密聊) target list — clients keyed the way private
 * conversations key their peers (uid when known, else `clid:<n>`, see
 * `PrivateConversations.keyFor`), whole channels, and server groups. Pure data,
 * so the toggle and cap rules stay unit-testable.
 *
 * A server group is *one* entry no matter how many members it has: the native
 * layer sends group-whisper packets and the server delivers them to whoever
 * holds the group at that moment, so the list never has to be rebuilt when
 * someone joins the group or comes online.
 */
data class WhisperTargets(
    val clients: List<String> = emptyList(),
    val channels: List<Long> = emptyList(),
    val groups: List<Long> = emptyList(),
) {
    val isEmpty: Boolean get() = clients.isEmpty() && channels.isEmpty() && groups.isEmpty()

    val isNotEmpty: Boolean get() = !isEmpty

    val size: Int get() = clients.size + channels.size + groups.size

    fun hasClient(key: String): Boolean = clients.contains(key)

    fun hasChannel(channelId: Long): Boolean = channels.contains(channelId)

    fun hasGroup(groupId: Long): Boolean = groups.contains(groupId)

    fun canAdd(): Boolean = size < WhisperRouting.MAX_TARGETS

    /** Add or remove one client; a full list keeps its previous state. */
    fun toggleClient(key: String): WhisperTargets = when {
        clients.contains(key) -> copy(clients = clients - key)
        !canAdd() -> this
        else -> copy(clients = clients + key)
    }

    /** Add or remove one channel; a full list keeps its previous state. */
    fun toggleChannel(channelId: Long): WhisperTargets = when {
        channels.contains(channelId) -> copy(channels = channels - channelId)
        !canAdd() -> this
        else -> copy(channels = channels + channelId)
    }

    /** Add or remove one server group; a full list keeps its previous state. */
    fun toggleGroup(groupId: Long): WhisperTargets = when {
        groups.contains(groupId) -> copy(groups = groups - groupId)
        !canAdd() -> this
        else -> copy(groups = groups + groupId)
    }
}

object WhisperRouting {

    /**
     * Server-side cap on whisper targets (`virtualserver_maxwhisperclients`,
     * which the server reports as WhisperTooManyTargets beyond it). It counts
     * the entries of the whisper set; a server group counts once, whatever its
     * membership.
     */
    const val MAX_TARGETS = 10

    /**
     * Virtual client the server always has: packets whispered to client 0 are
     * discarded rather than routed anywhere.
     */
    const val DROP_SENTINEL_CLIENT_ID = 0

    /**
     * Turn a target list into the native routing decision.
     *
     * [onlineClientIds] maps a target key to the client id that currently holds
     * it, so uid-keyed targets keep working after the peer reconnects.
     *
     * Explicitly listed clients that are currently offline are dropped from
     * *voice* routing. When nothing at all is left — every client offline and no
     * channel or group target — the result is the drop sentinel instead of an
     * empty set: an empty set means "no whisper routing", which would broadcast
     * the microphone to the whole channel while the UI still claims 密聊.
     *
     * Group ids are handed to the native layer untouched; it resolves their
     * membership per packet and applies the same drop rule when a group has no
     * members online.
     */
    fun resolve(targets: WhisperTargets, onlineClientIds: Map<String, Int>): WhisperVoiceTargets {
        if (targets.isEmpty) return WhisperVoiceTargets(IntArray(0), LongArray(0), LongArray(0))

        val clients = targets.clients
            .mapNotNull { onlineClientIds[it] }
            .distinct()
            .sorted()
            .toIntArray()
        val channels = targets.channels.distinct().sorted().toLongArray()
        val groups = targets.groups.distinct().sorted().toLongArray()

        if (clients.isEmpty() && channels.isEmpty() && groups.isEmpty()) {
            return WhisperVoiceTargets(intArrayOf(DROP_SENTINEL_CLIENT_ID), LongArray(0), LongArray(0))
        }
        return WhisperVoiceTargets(clients, channels, groups)
    }
}
