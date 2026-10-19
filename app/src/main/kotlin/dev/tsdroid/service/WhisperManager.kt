package dev.tsdroid.service

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.tsdroid.bridge.AudioBridge
import dev.tsdroid.bridge.TsClient
import dev.tsdroid.bridge.WhisperVoiceTargets
import dev.tsdroid.data.PrivateConversations
import dev.tslib.User

/**
 * 密聊 (whisper) state for the session.
 *
 * TS3 semantics, as on the desktop client: a whisper list — clients and/or
 * channels — and while that list is non-empty every captured packet is routed
 * to exactly those targets instead of the current channel. Whispering and
 * channel talk are mutually exclusive; the native side wraps
 * [TsClient.sendAudio] output as C2SWhisper, so the capture pipeline needs no
 * whisper awareness of its own.
 *
 * Targets are keyed like private conversations (uid when the server exposes
 * one, see `PrivateConversations.keyFor`), so the list survives a reconnect:
 * [TsClient] asks for the current set through the provider installed in
 * [attach] and pushes it into the freshly created native client.
 *
 * Text is a companion channel, not part of this class: a message typed while
 *密聊 is active is delivered by the ViewModel as a private message to every
 * online client target, so it lands in that peer's conversation like any other
 * private message. Channel targets receive voice only — TS3 has no text command
 * aimed at an arbitrary channel.
 */
object WhisperManager {

    private const val TAG = "WhisperManager"

    private var tsClient: TsClient? = null

    /** The capture path, so a hold can open and close the microphone itself. */
    private var audioBridge: AudioBridge? = null

    /** Current target list. Compose observes this directly. */
    var targets: WhisperTargets by mutableStateOf(WhisperTargets())
        private set

    /** True while a hold-to-whisper press is routing the microphone. */
    private var holding by mutableStateOf(false)

    /** Whether a hold-to-whisper press is live (read by the callers that open the mic). */
    val isHolding: Boolean get() = holding

    /** The list is configured; text and the whisper button have somewhere to go. */
    val hasTargets: Boolean get() = !targets.isEmpty

    /**
     * Voice is going to the whisper list right now. Only true while a
     * hold-to-whisper press is live — the list by itself never diverts the
     * microphone, so the normal microphone keeps talking to the channel.
     */
    val isRouted: Boolean get() = holding

    // ── 生命周期 ────────────────────────────────────────────

    /**
     * Bind to a live session. Must run once the connection is established and
     * before the UI can whisper.
     */
    fun attach(client: TsClient, audio: AudioBridge?) {
        tsClient = client
        audioBridge = audio
        // A reconnect creates a new native client with an empty set; hand it the
        // routing that is in effect at that moment (nothing unless a hold is open).
        client.setWhisperTargetsProvider { if (holding) voiceTargets() else EMPTY_TARGETS }
        Log.i(TAG, "WhisperManager attached")
    }

    /**
     * Unbind on teardown (service stop, failed connection, destroy). A dropped
     * connection deliberately does *not* come through here: the target list is
     * keyed by uid, so an automatic reconnect keeps it and re-applies it.
     */
    fun detach() {
        tsClient?.setWhisperTargetsProvider(null)
        tsClient = null
        audioBridge = null
        targets = WhisperTargets()
        holding = false
        Log.d(TAG, "WhisperManager detached")
    }

    // ── 按住密聊 ────────────────────────────────────────────

    /**
     * Whether a hold-to-whisper press has anywhere to go.
     *
     * Read this at press time, never into a cached UI value: a gesture handler
     * keeps the lambda it was created with, so a captured answer goes stale as
     * soon as the list is filled.
     */
    val canHold: Boolean get() = targets.isNotEmpty

    /**
     * Run one hold-to-whisper press end to end: routing, then microphone.
     *
     * Both are kept in one place on purpose — the bottom bar and the floating
     * window call this same method, and the order (routing first, microphone
     * second) is what keeps the opening frames of a whispered sentence out of the
     * channel.
     *
     * @return false when the list is empty
     */
    suspend fun beginHoldWithMic(): Boolean {
        if (!canHold) return false
        if (!beginHold()) return false
        // The release can land while the routing is being applied.
        if (holding) audioBridge?.beginWhisperHold()
        return holding
    }

    /** End a hold: close the microphone first, then drop the routing. */
    fun endHoldWithMic() {
        audioBridge?.endWhisperHold()
        endHold()
    }

    /**
     * Start a hold-to-whisper press and apply the routing.
     *
     * Suspends until the native target set is in place, so the caller may open
     * the microphone afterwards: unmuting first would send the first frames of
     * the sentence to the channel, which is exactly what whispering must not do.
     * Private on purpose — [beginHoldWithMic] is the only entry point, so the
     * routing-then-microphone order cannot be half-applied by a caller.
     *
     * @return false when the list is empty (nothing to whisper to)
     */
    private suspend fun beginHold(): Boolean {
        if (targets.isEmpty) return false
        // Two hold buttons (bottom bar, floating window) can overlap; the routing
        // is already in place in that case.
        if (holding) return true
        val client = tsClient ?: return false
        holding = true
        val resolved = voiceTargets()
        try {
            client.setWhisperTargetsNow(resolved.clients, resolved.channels, resolved.groups)
        } catch (e: Throwable) {
            holding = false
            Log.w(TAG, "Whisper hold routing failed", e)
            return false
        }
        Log.i(TAG, "Whisper hold started: ${describe()}")
        return holding
    }

    /**
     * End a hold-to-whisper press. The microphone is closed by the caller
     * ([endHoldWithMic]); the routing can then be cleared asynchronously without
     * leaking a frame.
     */
    private fun endHold() {
        if (!isHolding) return
        holding = false
        applyVoiceTargets()
        Log.i(TAG, "Whisper hold ended")
    }

    // ── 目标列表 ────────────────────────────────────────────

    /**
     * Add or remove a member target.
     *
     * @return false when the list is full and the member could not be added
     */
    fun toggleClient(user: User): Boolean = toggle(PrivateConversations.keyFor(user.uid, user.id))

    /**
     * Add or remove a whole channel from the whisper list.
     *
     * @return false when the list is full and the channel could not be added
     */
    fun toggleChannel(channelId: Long): Boolean {
        val updated = targets.toggleChannel(channelId)
        if (updated == targets) {
            Log.w(TAG, "Whisper list is full (${WhisperRouting.MAX_TARGETS}); channel $channelId not added")
            return false
        }
        targets = updated
        applyVoiceTargets()
        Log.i(TAG, "Whisper targets: ${describe()}")
        return true
    }

    /**
     * Add or remove a server group from the whisper list.
     *
     * The native layer resolves the group's membership on every packet, so this
     * is one entry no matter how many members it has, and members who join the
     * group later are included automatically.
     *
     * @return false when the list is full and the group could not be added
     */
    fun toggleGroup(groupId: Long): Boolean {
        val updated = targets.toggleGroup(groupId)
        if (updated == targets) {
            Log.w(TAG, "Whisper list is full (${WhisperRouting.MAX_TARGETS}); group $groupId not added")
            return false
        }
        targets = updated
        applyVoiceTargets()
        Log.i(TAG, "Whisper targets: ${describe()}")
        return true
    }

    /** Clear the whisper list (and end any hold that is live). */
    fun stop() {
        if (targets.isEmpty && !isHolding) return
        targets = WhisperTargets()
        holding = false
        applyVoiceTargets()
        Log.i(TAG, "Whisper list cleared")
    }

    // ── 内部 ────────────────────────────────────────────────

    private fun toggle(key: String): Boolean {
        val updated = targets.toggleClient(key)
        // Equal means the toggle was refused: only a full list refuses, and only
        // when adding — removing always shrinks the list.
        if (updated == targets) {
            Log.w(TAG, "Whisper list is full (${WhisperRouting.MAX_TARGETS}); $key not added")
            return false
        }
        targets = updated
        applyVoiceTargets()
        Log.i(TAG, "Whisper targets: ${describe()}")
        return true
    }

    private fun describe() =
        "${targets.clients.size} client(s), ${targets.channels.size} channel(s), ${targets.groups.size} group(s)"

    /**
     * Snapshot handed to [TsClient] — used on every change and again right after
     * a connect, where the new native client starts with an empty set.
     */
    private fun voiceTargets() = WhisperRouting.resolve(targets, onlineClientIds())

    private fun onlineClientIds(): Map<String, Int> {
        val client = tsClient ?: return emptyMap()
        val map = HashMap<String, Int>()
        for (user in client.users.value) {
            map[PrivateConversations.keyFor(user.uid, user.id)] = user.id
        }
        return map
    }

    /**
     * Push the routing that is in effect right now: the target set while a hold
     * is live, nothing otherwise. Called on every list change and after a hold
     * ends; a list edit while the microphone is closed therefore does not divert
     * any voice by itself.
     */
    private fun applyVoiceTargets() {
        val client = tsClient ?: return
        val resolved = if (holding) voiceTargets() else EMPTY_TARGETS
        client.setWhisperTargets(resolved.clients, resolved.channels, resolved.groups)
        if (!holding) {
            Log.d(TAG, "Voice routing off, back to channel talk")
        } else {
            Log.d(
                TAG,
                "Voice whisper targets applied: clients=${resolved.clients.toList()} " +
                    "channels=${resolved.channels.toList()} groups=${resolved.groups.toList()}",
            )
        }
    }

    /** "No whisper routing": the native set is cleared and voice returns to the channel. */
    private val EMPTY_TARGETS = WhisperVoiceTargets(IntArray(0), LongArray(0), LongArray(0))
}
