package dev.tsdroid.service

import android.util.Log
import dev.tsdroid.bridge.TsClient
import dev.tsdroid.data.CommandParts
import dev.tsdroid.data.HistorySync
import dev.tsdroid.data.Mentions
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
import kotlin.random.Random

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
    /** Whether mentions of someone offline are left in their mailbox: a listener does it */
    private val forwardMentions: () -> Boolean = { false },
    /** The note's subject and text for a mention by (sender, channel, message) */
    private val mentionNote: (String, String, String) -> Pair<String, String> = { s, c, t -> s to "$c: $t" },
) {
    private companion object {
        const val TAG = "ChannelHistory"

        // After connecting, before asking for the history: the other clients' views of us settle
        const val REQUEST_DELAY_MS = 2000L

        // Writes are gathered: a burst of messages, or a history reply, is saved once
        const val SAVE_DELAY_MS = 2000L

        // A mention note quotes the message up to this long
        const val MENTION_TEXT_CHARS = 300
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())

    /** Every channel's messages on the server we are connected to, in time order */
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** The server [messages] belong to */
    private var address: String? = null
    private var saveJob: Job? = null

    /** One history command at a time, a second apart, so the server's flood protection never trips */
    private val sends = Mutex()

    /** Commands too long for the server arrive in parts (see [CommandParts]) */
    private val parts = CommandParts.Assembler()

    /** Everyone seen on the server this session, uid to the name they last had: a mention of
     *  someone who has left uses the name they left with. Main thread only. */
    private val seenNames = HashMap<String, String>()

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
            client.users.collect { users ->
                for (user in users) user.uid?.takeIf(String::isNotBlank)?.let { seenNames[it] = user.nickname }
            }
        }
        scope.launch {
            client.events.collect { event ->
                if (event.type != "plugin_command") return@collect
                val data = event.data["data"] as? String ?: return@collect
                val invokerId = (event.data["invoker_id"] as? Number)?.toInt() ?: return@collect
                when (event.data["name"]) {
                    HistorySync.COMMAND -> onSyncCommand(invokerId, data)
                    HistorySync.PART_COMMAND -> parts.add(invokerId, data)?.let { onSyncCommand(invokerId, it) }
                }
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
        val message = ChatMessage(
            sender = sender,
            text = attachment?.fileName ?: text.replace("\u0000", "").trim(),
            fileAttachment = attachment,
            channelId = myChannelId(),
            senderUid = client.users.value.firstOrNull { it.id == senderId }?.uid,
        )
        add(message)
        if (attachment == null && forwardMentions()) forwardMentions(message)
    }

    /**
     * Leaves a note in the mailbox of each offline person [message] mentions as "@name", so the
     * mention reaches them when they next connect. Names come from who has written here (the
     * history keeps their uids) and who is on now; someone on now saw it, and the sender knows.
     */
    private fun forwardMentions(message: ChatMessage) {
        val online = client.users.value.mapNotNull { it.uid?.takeIf(String::isNotBlank) }.toSet()
        val people = buildMap {
            for (known in _messages.value) {
                val uid = known.senderUid ?: continue
                if (!known.isMe) put(uid, known.sender)
            }
            putAll(seenNames)
            for (user in client.users.value) user.uid?.takeIf(String::isNotBlank)?.let { put(it, user.nickname) }
        }
        val absent = Mentions.mentioned(message.text, people) - online - setOfNotNull(myUid(), message.senderUid)
        if (absent.isEmpty()) return
        val channel = client.channels.value.firstOrNull { it.id == message.channelId }?.name ?: ""
        val (subject, note) = mentionNote(message.sender, channel, message.text.take(MENTION_TEXT_CHARS))
        for (uid in absent) {
            Log.i(TAG, "Leaving a mention note in an offline mailbox")
            client.needs("b_client_offline_textmessage_send")
            client.sendOfflineMessage(uid, subject, note)
        }
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
            }
        }
    }

    /** Sends our summary of [channelId], then waits out the gap the next command needs. */
    private suspend fun sendRequest(channelId: Long, target: Int, back: Boolean) {
        val now = System.currentTimeMillis()
        val summary = HistorySync.summary(_messages.value.filter { it.channelId == channelId }, now, myUid())
        send(HistorySync.request(channelId, now, summary, back), target)
    }

    /**
     * Sends one history command to [target] (-1: everyone), in parts when it is too long for the
     * server, each followed by the gap the server's flood protection needs. Called holding [sends].
     */
    private suspend fun send(data: String, target: Int) {
        if (CommandParts.fits(data)) {
            client.sendPluginCommand(HistorySync.COMMAND, data, target)
            delay(HistorySync.CHUNK_INTERVAL_MS)
            return
        }
        for (part in CommandParts.split(data, Random.nextInt(Int.MAX_VALUE))) {
            if (!client.isConnected) return
            client.sendPluginCommand(HistorySync.PART_COMMAND, part, target)
            delay(HistorySync.CHUNK_INTERVAL_MS)
        }
    }

    private fun onSyncCommand(invokerId: Int, data: String) {
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
                    send(reply, requesterId)
                }
                if (!request.back && HistorySync.hasMore(mine, request.summary, request.now, me.uid)) {
                    sendRequest(channel.id, target = requesterId, back = true)
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
