package dev.tsdroid.service

import android.util.Log
import dev.tsdroid.bridge.TsClient
import dev.tsdroid.data.HistorySync
import dev.tsdroid.data.MessageStore
import dev.tsdroid.viewmodel.ChatMessage
import dev.tsdroid.viewmodel.FileAttachment
import dev.tsdroid.viewmodel.migrateMessage
import dev.tsdroid.viewmodel.parseFileAttachment
import dev.tslib.ConnectionState
import dev.tslib.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The channel chat of the server we are connected to, kept by the connection itself: it records
 * and answers other TeleSpeak clients' history requests (see [HistorySync]) for as long as the
 * connection lasts, whether or not a screen of the app is open. That is what lets a phone left
 * connected serve as the group's recorder.
 */
class ChannelHistory(
    private val client: TsClient,
    private val store: MessageStore,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val TAG = "ChannelHistory"

        // After connecting, before asking for the history: the other clients' views of us settle
        const val REQUEST_DELAY_MS = 2000L

        // Writes are gathered: a burst of messages, or a history reply, is saved once
        const val SAVE_DELAY_MS = 2000L
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())

    /** Every channel's messages on the server we are connected to, in time order */
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** The server [messages] belong to */
    private var address: String? = null
    private var saveJob: Job? = null

    /** One history command at a time, a second apart, so the server's flood protection never trips */
    private val sends = Mutex()

    init {
        // Each connection: the server's history, then a request for what others have that we lack
        scope.launch {
            client.state.collectLatest { state ->
                if (state != ConnectionState.CONNECTED) return@collectLatest
                val serverAddress = client.serverAddress ?: return@collectLatest
                if (serverAddress != address) load(serverAddress)
                client.users.first { users -> users.any { it.id == client.clientId } }
                client.channels.first { it.isNotEmpty() }
                delay(REQUEST_DELAY_MS)
                requestAll()
            }
        }
        scope.launch { client.chatEvents.collect(::onChatEvent) }
        scope.launch {
            client.events.collect { event ->
                if (event.type == "plugin_command" && event.data["name"] == HistorySync.COMMAND) onSyncCommand(event)
            }
        }
    }

    private suspend fun load(serverAddress: String) {
        saveNow()
        address = serverAddress
        _messages.value = emptyList()
        val stored = withContext(Dispatchers.IO) { store.loadChannel(serverAddress).map(::migrateMessage) }
        // Anything that arrived while it loaded goes after it
        _messages.update { arrived -> stored + arrived }
    }

    private fun myUid(): String? = client.users.value.firstOrNull { it.id == client.clientId }?.uid

    private fun myChannelId(): Long = client.users.value.firstOrNull { it.id == client.clientId }?.channelId ?: 0L

    private fun add(message: ChatMessage) {
        _messages.update { it + message }
        scheduleSave()
    }

    /** A message we sent to our channel, kept the moment it is sent. */
    fun addOwn(sender: String, text: String, attachment: FileAttachment? = null) {
        add(
            ChatMessage(
                sender = sender, text = text, isMe = true, fileAttachment = attachment,
                channelId = myChannelId(), senderUid = myUid(),
            ),
        )
    }

    /** Takes back the latest message of ours with [text] sent since [since]: the server refused it. */
    fun removeOwn(text: String, since: Long) {
        _messages.update { list ->
            val index = list.indexOfLast { it.isMe && it.text == text && it.timestamp >= since }
            if (index < 0) list else list.toMutableList().apply { removeAt(index) }
        }
        scheduleSave()
    }

    private fun onChatEvent(event: Event) {
        if (event.data["target"] != "channel") return
        val senderId = (event.data["sender_id"] as? Number)?.toInt() ?: return
        // Ours were kept when sent; the server's own notices have no sender of their own
        if (senderId == client.clientId) return
        val sender = event.data["sender_name"] as? String ?: return
        val text = event.data["message"] as? String ?: return
        val attachment = if (text.length > 10000) null else parseFileAttachment(text)
        // A channel message reaches only the channel we are in
        add(
            ChatMessage(
                sender = sender,
                text = attachment?.fileName ?: text.replace("\u0000", "").trim(),
                fileAttachment = attachment,
                channelId = myChannelId(),
                senderUid = client.users.value.firstOrNull { it.id == senderId }?.uid,
            ),
        )
    }

    // --- History sync (see HistorySync) ---

    /**
     * Sends everyone our summary of each channel we may read (those without a password, and the
     * one we are in), so they can send what we lack. One channel at a time, like the replies.
     */
    private suspend fun requestAll() {
        val myChannel = myChannelId()
        val channelIds = client.channels.value.filter { !it.hasPassword || it.id == myChannel }.map { it.id }
        Log.i(TAG, "Asking for the history of ${channelIds.size} channels")
        sends.withLock {
            for (channelId in channelIds) {
                sendRequest(channelId, target = -1, back = false)
                delay(HistorySync.CHUNK_INTERVAL_MS)
            }
        }
    }

    private fun sendRequest(channelId: Long, target: Int, back: Boolean) {
        val now = System.currentTimeMillis()
        val summary = HistorySync.summary(_messages.value.filter { it.channelId == channelId }, now, myUid())
        client.sendPluginCommand(HistorySync.COMMAND, HistorySync.request(channelId, now, summary, back), target)
    }

    private fun onSyncCommand(event: Event) {
        val data = event.data["data"] as? String ?: return
        val invokerId = (event.data["invoker_id"] as? Number)?.toInt() ?: return
        when (val command = HistorySync.parse(data)) {
            is HistorySync.Command.Request -> answer(invokerId, command)
            is HistorySync.Command.Messages -> {
                val before = _messages.value
                val merged = HistorySync.merge(before, command.messages, myUid()) ?: return
                Log.i(TAG, "History sync added ${merged.size - before.size} messages")
                _messages.value = merged
                scheduleSave()
            }
            null -> {}
        }
    }

    /**
     * Answers [requesterId]'s summary of a channel: the messages of every period where it differs
     * from ours, then, when its summary shows it has something we lack, a request back.
     */
    private fun answer(requesterId: Int, request: HistorySync.Command.Request) {
        if (requesterId == client.clientId) return
        scope.launch {
            sends.withLock {
                val me = client.users.value.firstOrNull { it.id == client.clientId } ?: return@withLock
                // A password-protected channel's history only goes to someone already in it
                val channel = client.channels.value.firstOrNull { it.id == request.channelId } ?: return@withLock
                val requesterChannel = client.users.value.firstOrNull { it.id == requesterId }?.channelId
                if (channel.hasPassword && channel.id != requesterChannel) return@withLock
                val mine = _messages.value.filter { it.channelId == channel.id }
                val missing = HistorySync.missing(mine, request.summary, request.now, me.uid)
                for (reply in HistorySync.replies(channel.id, missing, me.nickname, me.uid)) {
                    if (!client.isConnected) return@withLock
                    client.sendPluginCommand(HistorySync.COMMAND, reply, requesterId)
                    delay(HistorySync.CHUNK_INTERVAL_MS)
                }
                if (!request.back && HistorySync.hasMore(mine, request.summary, request.now, me.uid)) {
                    sendRequest(channel.id, target = requesterId, back = true)
                    delay(HistorySync.CHUNK_INTERVAL_MS)
                }
            }
        }
    }

    // --- Persistence ---

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            saveNow()
        }
    }

    /** Writes the history now, if anything is waiting to be written. */
    fun saveNow() {
        val job = saveJob ?: return
        job.cancel()
        saveJob = null
        val serverAddress = address ?: return
        store.saveChannel(serverAddress, _messages.value)
    }
}
