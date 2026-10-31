package dev.tsdroid.data

import android.content.Context
import android.util.Log
import dev.tsdroid.viewmodel.ChatMessage
import dev.tsdroid.viewmodel.FileAttachment
import dev.tsdroid.viewmodel.normalizeFilePath
import java.io.File

/**
 * Chat history per server, kept on the device encrypted (see [DeviceCipher]): the private
 * conversations in one file, written by the screens, and the channel history in another, written
 * by the connection itself (see [dev.tsdroid.service.ChannelHistory]). Both files used to be one.
 */
class MessageStore(private val context: Context) {

    companion object {
        private const val TAG = "MessageStore"
        /** Private conversations are capped; channel history is kept whole, since friends sync it */
        private const val MAX_PRIVATE_MESSAGES = 500
        private val EMPTY = Parsed(emptyList(), emptyMap(), emptyMap())
    }

    private val messagesDir = File(context.filesDir, "messages")
    private val cipher = DeviceCipher()

    /** The private conversations persisted for one server. */
    data class Stored(
        val privateMessages: Map<String, List<ChatMessage>>,
        val unreadPrivate: Map<String, Int>,
    )

    /** What the private conversations' file holds; older ones also hold the channel history. */
    private data class Parsed(
        val channel: List<ChatMessage>,
        val privateMessages: Map<String, List<ChatMessage>>,
        val unreadPrivate: Map<String, Int>,
    )

    fun load(serverAddress: String): Stored {
        val parsed = readParsed(serverAddress)
        return Stored(parsed.privateMessages, parsed.unreadPrivate)
    }

    /** Returns whether the history reached the disk. */
    fun save(
        serverAddress: String,
        privateMessages: Map<String, List<ChatMessage>>,
        unreadPrivate: Map<String, Int> = emptyMap(),
    ): Boolean {
        // The file may still hold the channel history, which this write would drop
        migrateChannelHistory(serverAddress)
        return try {
            val json = serializeServerMessages(privateMessages, unreadPrivate)
            cipher.writeFile(fileFor(serverAddress), json.encodeToByteArray())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save messages for $serverAddress", e)
            false
        }
    }

    /** Every channel's history on one server, in time order. */
    fun loadChannel(serverAddress: String): List<ChatMessage> {
        migrateChannelHistory(serverAddress)
        val file = channelFileFor(serverAddress)
        if (!file.exists()) return emptyList()
        return try {
            val array = org.json.JSONArray(cipher.readFile(file).decodeToString())
            (0 until array.length()).mapNotNull { storedMessageFromJson(array.getJSONObject(it), isPrivate = false) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load the channel history for $serverAddress", e)
            emptyList()
        }
    }

    /** Returns whether the history reached the disk. */
    fun saveChannel(serverAddress: String, messages: List<ChatMessage>): Boolean {
        return try {
            val array = org.json.JSONArray()
            messages.forEach { array.put(it.toStoredJson()) }
            cipher.writeFile(channelFileFor(serverAddress), array.toString().encodeToByteArray())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save the channel history for $serverAddress", e)
            false
        }
    }

    /** Moves the channel history out of the shared file it used to live in, once. */
    private fun migrateChannelHistory(serverAddress: String) {
        if (channelFileFor(serverAddress).exists()) return
        val legacy = readParsed(serverAddress).channel
        if (legacy.isNotEmpty()) saveChannel(serverAddress, legacy)
    }

    private fun readParsed(serverAddress: String): Parsed {
        val file = fileFor(serverAddress)
        if (!file.exists()) return EMPTY
        return try {
            parseServerMessages(cipher.readFile(file).decodeToString())
        } catch (e: Exception) {
            // A missing key (app data cleared, Keystore reset) or a damaged file:
            // the history cannot be read by anybody, so start over.
            Log.e(TAG, "Failed to load messages for $serverAddress", e)
            EMPTY
        }
    }

    private fun fileFor(serverAddress: String): File {
        return File(messagesDir, sanitizeFilename(serverAddress) + ".enc")
    }

    private fun channelFileFor(serverAddress: String): File {
        return File(messagesDir, sanitizeFilename(serverAddress) + ".channel.enc")
    }

    private fun sanitizeFilename(address: String): String {
        return address.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    // --- Serialization ---

    private fun serializeServerMessages(
        private_: Map<String, List<ChatMessage>>,
        unreadPrivate: Map<String, Int>,
    ): String {
        val root = org.json.JSONObject()
        val privateObj = org.json.JSONObject()
        for ((peerKey, msgs) in private_) {
            val arr = org.json.JSONArray()
            msgs.takeLast(MAX_PRIVATE_MESSAGES).forEach { arr.put(it.toStoredJson()) }
            privateObj.put(peerKey, arr)
        }
        root.put("private", privateObj)
        // Unread badges live with the history so they survive a disconnect or a
        // restart instead of resetting to zero.
        val unreadObj = org.json.JSONObject()
        for ((peerKey, count) in unreadPrivate) {
            if (count > 0) unreadObj.put(peerKey, count)
        }
        root.put("unreadPrivate", unreadObj)
        return root.toString()
    }

    // --- Parsing ---

    private fun parseServerMessages(json: String): Parsed {
        if (json.isBlank()) return EMPTY
        return try {
            val root = org.json.JSONObject(json)
            val channelMessages = mutableListOf<ChatMessage>()
            root.optJSONArray("channel")?.let { arr ->
                for (i in 0 until arr.length()) {
                    storedMessageFromJson(arr.getJSONObject(i), isPrivate = false)?.let { channelMessages.add(it) }
                }
            }
            val privateMessages = mutableMapOf<String, List<ChatMessage>>()
            root.optJSONObject("private")?.let { obj ->
                for (key in obj.keys()) {
                    // History written before conversations were uid-keyed used the
                    // session client id as the key. Those keys are numeric; a uid is
                    // not. Migration is lazy (no rewrite on read) and idempotent.
                    val peerKey = PrivateConversations.migrateStoredKey(key)
                    val arr = obj.optJSONArray(key) ?: continue
                    val msgs = mutableListOf<ChatMessage>()
                    for (i in 0 until arr.length()) {
                        storedMessageFromJson(arr.getJSONObject(i), isPrivate = true)?.let { msgs.add(it) }
                    }
                    privateMessages[peerKey] = (privateMessages[peerKey] ?: emptyList()) + msgs
                }
            }
            val unreadPrivate = mutableMapOf<String, Int>()
            root.optJSONObject("unreadPrivate")?.let { obj ->
                for (key in obj.keys()) {
                    val peerKey = PrivateConversations.migrateStoredKey(key)
                    val count = obj.optInt(key, 0)
                    if (count > 0) unreadPrivate[peerKey] = (unreadPrivate[peerKey] ?: 0) + count
                }
            }
            Parsed(channelMessages, privateMessages, unreadPrivate)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message history", e)
            EMPTY
        }
    }
}

/** A message as it is stored, and as history sync sends it. */
internal fun ChatMessage.toStoredJson(): org.json.JSONObject {
    return org.json.JSONObject().apply {
        put("s", sender)
        put("t", text)
        put("ts", timestamp)
        put("me", isMe)
        put("sid", senderId)
        if (channelId != 0L) put("c", channelId)
        senderUid?.let { put("u", it) }
        fileAttachment?.let { fa ->
            put(
                "fa",
                org.json.JSONObject().apply {
                    put("fn", fa.fileName)
                    put("fs", fa.fileSize)
                    put("fi", fa.fileId)
                    put("im", fa.isImage)
                    put("ch", fa.channelId)
                    if (fa.path != "/") put("p", fa.path)
                }
            )
        }
    }
}

/** Reads what [toStoredJson] wrote; null for an entry that cannot be read. */
internal fun storedMessageFromJson(o: org.json.JSONObject, isPrivate: Boolean): ChatMessage? {
    return try {
        ChatMessage(
            sender = o.getString("s"),
            text = o.getString("t"),
            timestamp = o.optLong("ts", System.currentTimeMillis()),
            isMe = o.optBoolean("me", false),
            isPrivate = isPrivate,
            senderId = o.optInt("sid", 0),
            channelId = o.optLong("c", 0L),
            senderUid = o.optString("u").takeIf { it.isNotEmpty() },
            fileAttachment = o.optJSONObject("fa")?.let { fa ->
                FileAttachment(
                    fileName = fa.getString("fn"),
                    fileSize = fa.optLong("fs", 0),
                    fileId = fa.optString("fi"),
                    isImage = fa.optBoolean("im", false),
                    channelId = fa.optLong("ch", 0),
                    path = normalizeFilePath(fa.optString("p", "/")),
                )
            },
        )
    } catch (e: Exception) {
        Log.w("MessageStore", "Failed to parse message: ${e.message}")
        null
    }
}
