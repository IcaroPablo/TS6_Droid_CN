package dev.tsdroid.bridge

import android.os.SystemClock
import android.util.Log
import dev.tslib.AudioSink
import dev.tslib.Channel
import dev.tslib.ChannelGroup
import dev.tslib.Client
import dev.tslib.ConnectionState
import dev.tslib.Event
import dev.tslib.Identity
import dev.tslib.ServerGroup
import dev.tslib.ServerInfo
import dev.tslib.User
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class TsFileEntry(
    val name: String,
    val size: Long,
    val datetime: Long,
    val isFile: Boolean,
)

/**
 * Native whisper routing decision: the client ids, channel ids and server group
 * ids outgoing voice must reach. All empty means "no whisper routing" (the
 * native set is cleared and voice returns to the current channel).
 *
 * Group ids are passed through rather than expanded here: the native side
 * resolves their membership per packet, so late joiners are covered and the app
 * never has to re-push the set.
 */
class WhisperVoiceTargets(
    val clients: IntArray,
    val channels: LongArray,
    val groups: LongArray,
)

class TsClient {

    companion object {
        private const val TAG = "TsClient"
        private const val INITIAL_CONNECT_SETTLE_MS = 300L
        private const val RECONNECT_AFTER_DISCONNECT_DELAY_MS = 1_500L
        private const val DISCONNECT_MIN_FLUSH_MS = 500L
        private const val DISCONNECT_MAX_FLUSH_MS = 2_000L
        private const val DISCONNECT_POLL_MS = 20L
        /** Grace period for the group list answers, read once after connect. */
        private const val GROUP_LISTS_SETTLE_MS = 1_500L

// Status commands share the server's per-client command budget with everything
// else (mute updates, chat, file transfers). Exceed it and the server answers
// `ClientIsFlooding` and drops commands until the penalty expires, which is how
// a status could look switched here but never arrive. Taps are therefore
// coalesced and spaced out.
private const val AWAY_SEND_DEBOUNCE_MS = 600L
private const val AWAY_SEND_MIN_INTERVAL_MS = 1_200L
private const val FLOOD_RETRY_DELAY_MS = 10_000L
    }

    @Volatile
    private var nativeThread: Thread? = null

    private val nativeDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "TsClientNative").also { nativeThread = it }
    }.asCoroutineDispatcher()

    @Volatile
    private var client: Client? = null

    @Volatile
    private var cachedClientId: Int? = null

    @Volatile
    var serverAddress: String? = null
        private set

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    // Chat messages get a replay buffer: the UI subscribes some time after
    // startEventLoop() begins (navigation + first composition), and events
    // emitted with no subscriber are dropped by a SharedFlow. Without replay
    // those messages are lost forever. Audio/talk events stay replay-free —
    // the service subscribes in onCreate before any connection exists.
    private val _chatEvents = MutableSharedFlow<Event>(replay = 64, extraBufferCapacity = 16)
    val chatEvents: SharedFlow<Event> = _chatEvents.asSharedFlow()

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<Int> = _state.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    // Server groups (id + name) for the whisper list. Filled from the answer to
    // the group list request the native layer sends on connect; empty until then.
    private val _serverGroups = MutableStateFlow<List<ServerGroup>>(emptyList())
    val serverGroups: StateFlow<List<ServerGroup>> = _serverGroups.asStateFlow()

    // Channel groups (id + name), used to name a member's channel group.
    private val _channelGroups = MutableStateFlow<List<ChannelGroup>>(emptyList())
    val channelGroups: StateFlow<List<ChannelGroup>> = _channelGroups.asStateFlow()

    private val _commandErrors = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)
    val commandErrors: SharedFlow<String> = _commandErrors.asSharedFlow()

    private val downloadCallbacks = ConcurrentHashMap<String, CompletableDeferred<ByteArray>>()
    private val uploadCallbacks = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    // path → channelId of the in-flight request; completion events carry only
    // the path, so the channel is resolved from here (prevents files with the
    // same name in different channels from mixing up callbacks)
    private val downloadKeys = ConcurrentHashMap<String, Long>()
    private val uploadKeys = ConcurrentHashMap<String, Long>()
    private val fileListCallbacks = ConcurrentHashMap<String, CompletableDeferred<List<TsFileEntry>>>()

    private var eventLoopJob: Job? = null
    private val clientCoroutineScope = CoroutineScope(nativeDispatcher + SupervisorJob())
    private val connectMutex = Mutex()

    // Audio sink requested before a native client exists (or across reconnects):
    // re-attached to every freshly created native client.
    private var pendingAudioSink: AudioSink? = null

    // Whisper (密聊) routing to push into every freshly created native client.
    // A reconnect builds a new native client whose target set starts empty, but
    // the UID/channel-keyed target list is still on screen, so it is re-applied.
    private var whisperTargetsProvider: (() -> WhisperVoiceTargets)? = null

    // Status (在线 / 离开 / 勿扰) the user picked, re-applied to every freshly
    // created native client: a reconnect starts online and would otherwise drop
    // a status the user still sees on screen.
    private var desiredAway: Pair<Boolean, String?>? = null

    // Coalesced sender for that status (see setAway) plus the bookkeeping that
    // keeps consecutive status commands far enough apart.
    private var awaySendJob: Job? = null
    private var lastAwaySentAt = 0L

    // Last value actually handed to the server for `client_input_muted`, so a
    // PTT press that does not change the state costs no command.
    private var lastSentInputMuted: Boolean? = null

    val isConnected: Boolean
        get() = _state.value == ConnectionState.CONNECTED

    val clientId: Int?
        get() = cachedClientId

    suspend fun connect(
        address: String,
        identity: Identity,
        nickname: String,
        password: String? = null,
        channel: String? = null,
    ) = withContext(nativeDispatcher) {
        connectMutex.withLock {
            try {
                stopEventLoop()
                val hadExistingClient = disconnectOnNativeThread()
                
                delay(if (hadExistingClient) RECONNECT_AFTER_DISCONNECT_DELAY_MS else INITIAL_CONNECT_SETTLE_MS)

                _state.value = ConnectionState.CONNECTING

                var lastFailure: Throwable? = null
                for (attempt in 0 until MAX_NICKNAME_COLLISION_ATTEMPTS) {
                    val candidateNickname = nicknameWithCollisionSuffix(nickname, attempt)
                    var pendingClient: Client? = null
                    var pendingClientConnected = false
                    var retrying = false
                    try {
                        try {
                            identity.setNickname(candidateNickname)
                        } catch (e: Throwable) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "Failed to update identity nickname before connect", e)
                        }
                        val c = Client(address, identity, candidateNickname, password, channel)
                        pendingClient = c
                        c.waitConnected()
                        pendingClientConnected = true
                        // Log immediately after waitConnected
                        val users = c.users
                        val channels = c.channels
                        Log.i(TAG, "After waitConnected: ${users?.size ?: "null"} users, ${channels?.size ?: "null"} channels")
                        if (users != null) {
                            for (u in users) {
                                if (u != null) Log.d(TAG, "  User: ${u.nickname} (id=${u.id}, ch=${u.channelId})")
                            }
                        }

                        if (hasNicknameCollision(users, c.clientId, candidateNickname)) {
                            Log.w(TAG, "Nickname '$candidateNickname' is already in use; retrying with suffix")
                            lastFailure = IllegalStateException("Nickname already in use: $candidateNickname")
                            closeClient(c, "nickname collision")
                            pendingClient = null
                            retrying = true
                            continue
                        }

                        client = c
                        pendingClient = null
                        serverAddress = address
                        // A brand-new native client starts with no audio sink, and
                        // this is the only point where it is guaranteed to exist.
                        // Without the sink the new libtslib_jni delivers no voice
                        // at all: frames go to the registered callback instead of
                        // audio_received events.
                        pendingAudioSink?.let { sink ->
                            try {
                                c.setAudioSink(sink)
                                Log.i(TAG, "Audio sink attached to new native client")
                            } catch (e: Throwable) {
                                Log.w(TAG, "Audio sink attach failed", e)
                            }
                        }
                        _state.value = ConnectionState.CONNECTED
                        // A reconnected native client has an empty whisper target
                        // set even though the密聊 targets (UID/channel keyed) are
                        // still displayed — re-apply them before any audio flows.
                        whisperTargetsProvider?.let { provider ->
                            try {
                                val targets = provider()
                                c.setWhisperTargets(targets.clients, targets.channels, targets.groups)
                                if (targets.clients.isNotEmpty() || targets.channels.isNotEmpty() ||
                                    targets.groups.isNotEmpty()
                                ) {
                                    Log.i(TAG, "Whisper targets re-applied after connect")
                                }
                            } catch (e: Throwable) {
                                Log.w(TAG, "Whisper target re-apply failed", e)
                            }
                        }
                        refreshState()
                        // The status is per connection as far as the server is
                        // concerned: a new native client logs in online, so the
                        // status the user picked is pushed again here.
                        desiredAway?.let { (away, message) ->
                            try {
                                c.setAway(away, message)
                                lastAwaySentAt = SystemClock.elapsedRealtime()
                                Log.i(TAG, "Status re-applied after connect (away=$away)")
                            } catch (e: Throwable) {
                                Log.w(TAG, "Status re-apply failed", e)
                            }
                        }
                        // The server answers both group list requests asynchronously,
                        // so give it a moment before reading the names once.
                        clientCoroutineScope.launch {
                            delay(GROUP_LISTS_SETTLE_MS)
                            refreshServerGroups()
                            refreshChannelGroups()
                        }
                        if (client == null) {
                            throw IllegalStateException("Connection closed during initial state sync")
                        }
                        return@withLock
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        lastFailure = e
                        retrying = e.isNicknameCollisionFailure()
                        Log.w(TAG, "Connection attempt failed with nickname '$candidateNickname'", e)
                        if (!retrying || attempt == MAX_NICKNAME_COLLISION_ATTEMPTS - 1) {
                            throw e
                        }
                    } finally {
                        pendingClient?.let {
                            if (pendingClientConnected) {
                                closeClient(it, "failed connection attempt")
                            } else {
                                destroyClient(it, "failed connection attempt before connected")
                            }
                        }
                        if (retrying && attempt < MAX_NICKNAME_COLLISION_ATTEMPTS - 1) {
                            delay(200)
                        }
                    }
                }

                throw Exception(lastFailure.userMessage() ?: "Server rejected the connection", lastFailure)
            } catch (e: Throwable) {
                closeAfterNativeFailure()
                if (e is CancellationException) throw e

                Log.e("TS6_CRASH_PREVENTION", "Connection failed; native client cleaned up", e)
                throw Exception("Connection failed: ${e.message ?: "Server busy or rejected"}", e)
            }
        }
    }

    private fun Throwable?.userMessage(): String? {
        var current = this
        var fallback: String? = null
        while (current != null) {
            current.message?.takeIf { it.isNotBlank() }?.let { fallback = it }
            current = current.cause
        }
        return fallback
    }

    private fun Throwable.isNicknameCollisionFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            val message = current.message?.lowercase().orEmpty()
            if (
                "nickname" in message &&
                (
                    "already" in message ||
                    "in use" in message ||
                    "taken" in message ||
                    "duplicate" in message ||
                    "exists" in message
                )
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun startEventLoop() {
        // 1. Cancel any active event loop cleanly first
        stopEventLoop()

        // 2. Launch a completely fresh lifecycle track
        eventLoopJob = clientCoroutineScope.launch {
            try {
                var refreshCounter = 0
                while (isActive && client != null) {
                    ensureActive()
                    try {
                        val c = client ?: break
                        val events = c.processEvents() ?: emptyArray()
                        for (event in events) {
                            if (event.type == "text_message") _chatEvents.tryEmit(event)
                            else _events.tryEmit(event)
                            handleEvent(event)
                        }
                        refreshCounter++
                        // Refresh on events or every ~500ms (25 * 20ms)
                        if (events.isNotEmpty() || refreshCounter >= 25) {
                            refreshState()
                            refreshCounter = 0
                        }
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        if (client == null) break
                        Log.e(TAG, "Event loop native failure; closing connection", e)
                        _state.value = ConnectionState.DISCONNECTED
                        closeAfterNativeFailure()
                        break
                    }
                    delay(20)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Event loop coroutine clean cancelled.")
            } catch (e: Throwable) {
                Log.e(TAG, "Gracefully trapped event loop runtime friction", e)
                _state.value = ConnectionState.DISCONNECTED
                closeAfterNativeFailure()
            }
        }
    }

    fun stopEventLoop() {
        eventLoopJob?.let {
            if (it.isActive) {
                it.cancel()
                Log.d(TAG, "Killing stagnant legacy event loop forcefully.")
            }
        }
        eventLoopJob = null
    }

    private fun handleEvent(event: Event) {
        when (event.type) {
            "disconnected" -> _state.value = ConnectionState.DISCONNECTED
            "connected" -> _state.value = ConnectionState.CONNECTED
            "file_downloaded" -> {
                val path = event.data["path"] as? String ?: return
                val data = event.data["data"] as? ByteArray ?: return
                val channelId = downloadKeys.remove(path) ?: 0L
                Log.d(TAG, "File downloaded: ch=$channelId path=$path (${data.size} bytes)")
                downloadCallbacks.remove("$channelId:$path")?.complete(data)
            }
            "file_uploaded" -> {
                val path = event.data["path"] as? String ?: return
                val channelId = uploadKeys.remove(path) ?: 0L
                Log.d(TAG, "File uploaded: ch=$channelId path=$path")
                uploadCallbacks.remove("$channelId:$path")?.complete(true)
            }
            "file_transfer_failed" -> {
                val path = event.data["path"] as? String ?: return
                val error = event.data["error"] as? String ?: "unknown"
                Log.w(TAG, "File transfer failed: $path — $error")
                val dlChannel = downloadKeys.remove(path) ?: 0L
                downloadCallbacks.remove("$dlChannel:$path")?.completeExceptionally(
                    Exception("File transfer failed: $error")
                )
                val upChannel = uploadKeys.remove(path) ?: 0L
                uploadCallbacks.remove("$upChannel:$path")?.complete(false)
            }
            "file_list_received" -> {
                val channelId = (event.data["channel_id"] as? Number)?.toLong() ?: return
                val path = event.data["path"] as? String ?: return
                val filesJson = event.data["files"] as? String ?: return
                val entries = parseFileEntries(filesJson)
                Log.d(TAG, "File list received: channel=$channelId path=$path entries=${entries.size}")
                fileListCallbacks.remove("$channelId:$path")?.complete(entries)
            }
            "command_error" -> {
                val message = event.data["message"] as? String ?: return
                Log.w(TAG, "Command error: $message")
                _commandErrors.tryEmit(message)
                // The server's flood protection rejects commands for a while
                // once tripped, so a status change queued just before that would
                // otherwise be lost silently.
                if (message.contains("flood", ignoreCase = true)) {
                    scheduleFloodRetry()
                }
            }
            "channel_permissions_updated" -> {
                val channelId = (event.data["channel_id"] as? Number)?.toLong() ?: return
                val hints = (event.data["permission_hints"] as? Number)?.toLong() ?: return
                Log.i(TAG, "Channel $channelId permissions updated: ${hints.toString(16)}")
                // Force refresh to propagate updated permission_hints
                refreshState()
            }
        }
    }

    private fun refreshState() {
        val c = client ?: return
        try {
            _channels.value = c.channels?.filterNotNull() ?: emptyList()
            val rawUsers = c.users
            val filteredUsers = rawUsers?.filterNotNull() ?: emptyList()
            Log.d(TAG, "refreshState: rawUsers=${rawUsers?.size}, filtered=${filteredUsers.size}")
            if (filteredUsers.isNotEmpty()) {
                for (u in filteredUsers) {
                    Log.d(TAG, "  user: ${u.nickname} id=${u.id} ch=${u.channelId}")
                }
            }
            _users.value = filteredUsers
            _serverInfo.value = c.serverInfo
            cachedClientId = c.clientId
            val st = c.state
            if (_state.value != st) _state.value = st
        } catch (e: Throwable) {
            Log.w(TAG, "refreshState failed", e)
            closeAfterNativeFailure()
        }
    }

    private fun closeAfterNativeFailure() {
        val c = client
        client = null
        resetState()
        if (c != null) {
            closeClient(c, "native failure")
        }
    }

    private fun disconnectOnNativeThread(): Boolean {
        stopEventLoop()
        val c = client
        client = null
        resetState()
        if (c != null) {
            closeClient(c, "disconnect")
            return true
        }
        return false
    }

    private fun resetState() {
        _state.value = ConnectionState.DISCONNECTED
        _channels.value = emptyList()
        _users.value = emptyList()
        _serverInfo.value = null
        cachedClientId = null
        // A new connection knows nothing about what we sent on the old one.
        lastSentInputMuted = null
        lastAwaySentAt = 0L
    }

    private fun closeClient(c: Client, reason: String) {
        var disconnectSent = false
        try {
            c.disconnect()
            disconnectSent = true
            Log.d(TAG, "Native disconnect command sent ($reason)")
        } catch (e: Throwable) {
            Log.w(TAG, "disconnect during $reason failed", e)
        }

        if (disconnectSent) {
            flushDisconnect(c, reason)
        }

        destroyClient(c, reason)
    }

    private fun flushDisconnect(c: Client, reason: String) {
        val startedAt = System.currentTimeMillis()
        val minFlushEnd = startedAt + DISCONNECT_MIN_FLUSH_MS
        val maxFlushEnd = startedAt + DISCONNECT_MAX_FLUSH_MS
        var observedDisconnected = false

        while (System.currentTimeMillis() < maxFlushEnd) {
            try {
                val events = c.processEvents() ?: emptyArray()
                if (events.any { it.type == "disconnected" }) {
                    observedDisconnected = true
                }
                if (!c.isConnected || c.state == ConnectionState.DISCONNECTED) {
                    observedDisconnected = true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "disconnect flush during $reason failed", e)
                break
            }

            if (observedDisconnected && System.currentTimeMillis() >= minFlushEnd) {
                break
            }

            try {
                Thread.sleep(DISCONNECT_POLL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }

        Log.d(TAG, "Disconnect flush complete ($reason, observedDisconnected=$observedDisconnected)")
    }

    private fun destroyClient(c: Client, reason: String) {
        try {
            c.close()
        } catch (e: Throwable) {
            Log.w(TAG, "close during $reason failed", e)
        }
    }

    private fun launchNativeCommand(name: String, block: Client.() -> Unit) {
        clientCoroutineScope.launch {
            val c = client ?: return@launch
            try {
                c.block()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "$name failed", e)
            }
        }
    }

    /**
     * Register the receiver for incoming voice frames. Remembered so that a
     * reconnect (which creates a new native client) re-attaches it.
     */
    fun setAudioSink(sink: AudioSink?) {
        pendingAudioSink = sink
        launchNativeCommand("setAudioSink") {
            this.setAudioSink(sink)
        }
    }

    /**
     * Install the callback that supplies the current whisper target set, so a
     * reconnect can push it into the new native client.
     */
    fun setWhisperTargetsProvider(provider: (() -> WhisperVoiceTargets)?) {
        whisperTargetsProvider = provider
    }

    fun sendChannelMessage(msg: String) {
        launchNativeCommand("sendChannelMessage") {
            this.sendChannelMessage(msg)
        }
    }

    fun sendServerMessage(msg: String) {
        launchNativeCommand("sendServerMessage") {
            this.sendServerMessage(msg)
        }
    }

    fun sendPrivateMessage(userId: Int, msg: String) {
        launchNativeCommand("sendPrivateMessage") {
            this.sendPrivateMessage(userId, msg)
        }
    }

    /**
     * Route outgoing voice to an explicit whisper (密聊) target set. Empty
     * arrays restore normal channel talk. Capture keeps calling [sendAudio]
     * unchanged — the native side wraps each packet as C2SWhisper.
     */
    fun setWhisperTargets(clients: IntArray, channels: LongArray, groups: LongArray) {
        launchNativeCommand("setWhisperTargets") {
            this.setWhisperTargets(clients, channels, groups)
        }
    }

    /**
     * Apply a whisper target set and wait until the native client has it.
     *
     * Hold-to-whisper needs this ordering: routing first, microphone second.
     * Unmuting before the native set is in place sends the first frames of the
     * sentence to the channel, which is exactly what whispering must not do.
     */
    suspend fun setWhisperTargetsNow(clients: IntArray, channels: LongArray, groups: LongArray) =
        withContext(nativeDispatcher) {
            try {
                client?.setWhisperTargets(clients, channels, groups)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Whisper target apply failed", e)
            }
        }

    /**
     * Re-read the server group list. The native layer asks the server on every
     * connect; this is for the UI to pick up names after the answer landed
     * (and to retry when it had not arrived yet).
     */
    suspend fun refreshServerGroups() = withContext(nativeDispatcher) {
        val c = client ?: return@withContext
        try {
            _serverGroups.value = c.serverGroups?.filterNotNull() ?: emptyList()
            Log.d(TAG, "Server groups: ${_serverGroups.value.size}")
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Server group refresh failed", e)
        }
    }

    /** Re-read the channel group list (names for the member panel). */
    suspend fun refreshChannelGroups() = withContext(nativeDispatcher) {
        val c = client ?: return@withContext
        try {
            _channelGroups.value = c.channelGroups?.filterNotNull() ?: emptyList()
            Log.d(TAG, "Channel groups: ${_channelGroups.value.size}")
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Channel group refresh failed", e)
        }
    }

    fun moveToChannel(channelId: Long) {
        moveToChannel(channelId, null)
    }

    fun moveToChannel(channelId: Long, password: String?) {
        launchNativeCommand("moveToChannel") {
            this.moveToChannel(channelId, password)
        }
    }

    fun sendAudio(data: ByteArray, codec: Int) {
        launchNativeCommand("sendAudio") {
            this.sendAudio(data, codec)
        }
    }

    /**
     * Tell the server our microphone mute state (`clientupdate client_input_muted`).
     *
     * Every call is one command on the server's control connection, and the
     * server's flood protection counts them: push-to-talk fires this on every
     * press and release. Sending only real changes keeps a talkative session
     * well inside the budget, which is what used to break the *status* menu
     * (its commands were the ones rejected once the client was "flooding").
     */
    fun setInputMuted(muted: Boolean, force: Boolean = false) {
        if (!force && lastSentInputMuted == muted) return
        lastSentInputMuted = muted
        Log.i(TAG, "setInputMuted($muted) force=$force client=${if (client != null) "present" else "NULL"}")
        launchNativeCommand("setInputMuted") {
            this.setInputMuted(muted)
        }
    }

    /**
     * Change our nickname for this session (`clientupdate client_nickname`).
     *
     * Temporary by design: it never touches the identity file, so a reconnect
     * comes back with the nickname the connection was opened with.
     */
    fun setNickname(nickname: String) {
        Log.i(TAG, "setNickname($nickname)")
        launchNativeCommand("setNickname") {
            this.setNickname(nickname)
        }
    }

    /**
     * Set our own status (`clientupdate client_away`), i.e. the desktop
     * client's status menu: online, away or do-not-disturb.
     *
     * Away is a marker only — voice and text keep working.
     *
     * The command is **coalesced**: the server rejects bursts with
     * `ClientIsFlooding` ("client is flooding") and then keeps rejecting for a
     * while, so a user tapping through the menu must produce one command for the
     * final choice instead of one per tap. The desired state is remembered and
     * pushed again after a reconnect.
     */
    suspend fun setAway(away: Boolean, message: String?) = withContext(nativeDispatcher) {
        Log.i(TAG, "setAway(away=$away, message=$message)")
        desiredAway = away to message
        awaySendJob?.cancel()
        awaySendJob = clientCoroutineScope.launch {
            delay(AWAY_SEND_DEBOUNCE_MS)
            sendDesiredAway("debounced")
        }
    }

    /** One coalesced send of [desiredAway]; runs on the native thread. */
    private fun sendDesiredAway(reason: String) {
        val (away, message) = desiredAway ?: return
        val c = client ?: return
        val now = SystemClock.elapsedRealtime()
        val sinceLast = now - lastAwaySentAt
        if (sinceLast < AWAY_SEND_MIN_INTERVAL_MS) {
            // Too soon after the previous command: wait out the gap instead of
            // spending flood budget, then send whatever the user picked last.
            awaySendJob?.cancel()
            awaySendJob = clientCoroutineScope.launch {
                delay(AWAY_SEND_MIN_INTERVAL_MS - sinceLast)
                sendDesiredAway("spaced")
            }
            return
        }
        try {
            c.setAway(away, message)
            lastAwaySentAt = now
            Log.i(TAG, "Status sent ($reason, away=$away)")
            refreshState()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w(TAG, "setAway failed", e)
        }
    }

    /**
     * The server throttled us (`ClientIsFlooding`). Everything after that is
     * dropped until the penalty expires, so retry the wanted status once, late
     * enough to be outside the window — and never in a tight loop, which would
     * only extend the penalty.
     */
    private fun scheduleFloodRetry() {
        if (desiredAway == null) return
        awaySendJob?.cancel()
        awaySendJob = clientCoroutineScope.launch {
            Log.i(TAG, "Flood penalty: retrying status in ${FLOOD_RETRY_DELAY_MS}ms")
            delay(FLOOD_RETRY_DELAY_MS)
            lastAwaySentAt = 0L
            sendDesiredAway("flood-retry")
        }
    }

    /**
     * Forget the status the user picked, called when the session ends: the next
     * connection starts online, so nothing is re-applied behind their back.
     */
    fun forgetAway() {
        Log.i(TAG, "forgetAway()")
        desiredAway = null
        awaySendJob?.cancel()
        awaySendJob = null
    }

    suspend fun downloadFile(channelId: Long, path: String): ByteArray? {
        val deferred = CompletableDeferred<ByteArray>()
        val key = "$channelId:$path"
        downloadKeys[path] = channelId
        downloadCallbacks[key] = deferred
        val started = withContext(nativeDispatcher) {
            try {
                val c = client ?: return@withContext false
                c.downloadFile(channelId, path)
                true
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "downloadFile failed for $path", e)
                false
            }
        }
        if (!started) {
            downloadCallbacks.remove(key)
            downloadKeys.remove(path)
            return null
        }
        return withTimeoutOrNull(10_000) {
            try {
                deferred.await()
            } catch (e: Exception) {
                Log.w(TAG, "downloadFile await failed for $path", e)
                null
            }
        }.also {
            downloadCallbacks.remove(key)
            downloadKeys.remove(path)
        }
    }

    suspend fun listFiles(channelId: Long, path: String): List<TsFileEntry>? {
        val key = "$channelId:$path"
        val deferred = CompletableDeferred<List<TsFileEntry>>()
        fileListCallbacks[key] = deferred
        val started = withContext(nativeDispatcher) {
            try {
                val c = client ?: return@withContext false
                c.listFiles(channelId, path)
                true
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "listFiles failed for $path", e)
                false
            }
        }
        if (!started) {
            fileListCallbacks.remove(key)
            return null
        }
        return withTimeoutOrNull(5_000) {
            try { deferred.await() } catch (e: Exception) {
                Log.w(TAG, "listFiles await failed for $path", e)
                null
            }
        }.also { fileListCallbacks.remove(key) }
    }

    fun deleteFile(channelId: Long, name: String) {
        launchNativeCommand("deleteFile") {
            this.deleteFile(channelId, name)
        }
    }

    fun renameFile(channelId: Long, oldName: String, newName: String) {
        launchNativeCommand("renameFile") {
            this.renameFile(channelId, oldName, newName)
        }
    }

    fun createDirectory(channelId: Long, dirname: String) {
        launchNativeCommand("createDirectory") {
            this.createDirectory(channelId, dirname)
        }
    }

    fun queryChannelPermissions(channelId: Long) {
        launchNativeCommand("queryChannelPermissions") {
            this.queryChannelPermissions(channelId)
        }
    }

    private fun parseFileEntries(json: String): List<TsFileEntry> {
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                TsFileEntry(
                    name = obj.getString("name"),
                    size = obj.getLong("size"),
                    datetime = obj.getLong("datetime"),
                    isFile = obj.getBoolean("is_file"),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseFileEntries failed", e)
            emptyList()
        }
    }

    suspend fun uploadFile(channelId: Long, path: String, data: ByteArray, overwrite: Boolean = true): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val key = "$channelId:$path"
        uploadKeys[path] = channelId
        uploadCallbacks[key] = deferred
        val started = withContext(nativeDispatcher) {
            try {
                val c = client ?: return@withContext false
                c.uploadFile(channelId, path, data, overwrite)
                true
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "uploadFile failed for $path", e)
                false
            }
        }
        if (!started) {
            uploadCallbacks.remove(key)
            uploadKeys.remove(path)
            return false
        }
        return withTimeoutOrNull(30_000) {
            try {
                deferred.await()
            } catch (e: Exception) {
                Log.w(TAG, "uploadFile await failed for $path", e)
                false
            }
        }.also {
            uploadCallbacks.remove(key)
            uploadKeys.remove(path)
        } ?: false
    }

    fun disconnect() {
        if (Thread.currentThread() == nativeThread) {
            disconnectOnNativeThread()
        } else {
            runBlocking(nativeDispatcher) {
                disconnectOnNativeThread()
            }
        }
    }
}
