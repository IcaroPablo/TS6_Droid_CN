package dev.tsdroid.data

import android.util.Log
import dev.tsdroid.viewmodel.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Channel chat history shared between TeleSpeak clients.
 *
 * A TeamSpeak server keeps no channel chat: only the people in a channel at the time see a message.
 * Each TeleSpeak client keeps what it saw, so the group's history is all of those pieces together.
 *
 * On connecting, a client sends everyone, channel by channel, a summary of what it has: its
 * history cut into periods (a day each for the last [RECENT_DAYS] days, 30 days each before),
 * each period a count and a checksum. Another TeleSpeak client compares that with its own summary
 * and sends the messages of every period that differs, so a gap anywhere in the history fills,
 * not only what came after the asker's last message. It also asks back for the same channel, so
 * a client that stays connected (a recorder) gets what only the asker had.
 *
 * Everything travels as plugin commands: hidden data the server passes between clients, encrypted
 * like the rest of the connection, which other TeamSpeak clients ignore.
 *
 * - Request: `{"v":1,"op":"req","c":<channel id>,"now":<ms>,"d":{"<period>":"<count>:<checksum>",...}}`,
 *   to everyone, or with `"back":true` to the one client whose request showed it lacks something
 * - Reply, to the asker, as many as needed: `{"v":1,"op":"msgs","c":<channel id>,"m":[...]}`,
 *   each message in the form the history is stored in (see [toStoredJson]).
 *
 * TeamSpeak messages carry no id and no server time: each client stamps them with its own clock.
 * Two copies of a message are the same when the sender, the text and the channel match and their
 * times are close ([SAME_MESSAGE_WINDOW_MS]).
 */
object HistorySync {
    private const val TAG = "HistorySync"

    /** The plugin command's name: what tells these apart from other clients' commands */
    const val COMMAND = "telespeak.history"
    private const val VERSION = 1

    /** Days compared one by one; older history is compared 30 days at a time */
    private const val RECENT_DAYS = 60
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val OLD_PERIOD_DAYS = 30

    /** Two copies of a message are stamped this close at most, by two phones' clocks. */
    private const val SAME_MESSAGE_WINDOW_MS = 2 * 60 * 1000L

    /** A reply's size: commands this long still go through, a few kB each. */
    private const val CHUNK_CHARS = 4000

    /** Between two replies: the server blocks a client that sends commands faster. */
    const val CHUNK_INTERVAL_MS = 1000L

    sealed class Command {
        /**
         * The asker's summary of [channelId] (see [summary]), made at its [now]. [back] marks a
         * request sent in answer to another, which is never answered with a request again.
         */
        data class Request(val channelId: Long, val now: Long, val summary: Map<String, String>, val back: Boolean) : Command()

        data class Messages(val channelId: Long, val messages: List<ChatMessage>) : Command()
    }

    fun request(channelId: Long, now: Long, summary: Map<String, String>, back: Boolean = false): String = JSONObject().apply {
        put("v", VERSION)
        put("op", "req")
        put("c", channelId)
        put("now", now)
        put("d", JSONObject(summary))
        if (back) put("back", true)
    }.toString()

    /**
     * The period a message falls in, as seen at [now]: both sides cut by the asker's [now], so
     * they cut alike. Phones' clocks differ, so a message stamped near a period's edge can fall
     * on either side: that period then differs every time and is sent again, which the merge
     * absorbs.
     */
    private fun periodOf(timestamp: Long, now: Long): String {
        val day = timestamp / DAY_MS
        return if (day > now / DAY_MS - RECENT_DAYS) "d$day" else "m${day / OLD_PERIOD_DAYS}"
    }

    /**
     * Who sent [message] and what it says, as a number every client computes alike: not the
     * time, which each client stamps with its own clock.
     */
    private fun fingerprint(message: ChatMessage, myUid: String?): Long {
        val sender = message.senderUid ?: (if (message.isMe) myUid else null) ?: message.sender
        // FNV-1a, 64 bits
        var hash = -0x340d631b7bdddcdbL
        for (char in sender + '\u0000' + message.text) {
            hash = (hash xor char.code.toLong()) * 0x100000001b3L
        }
        return hash
    }

    /**
     * One channel's [messages] by period: how many, and the sum of their fingerprints, which
     * does not depend on their order and changes with any message added or missing.
     */
    fun summary(messages: List<ChatMessage>, now: Long, myUid: String?): Map<String, String> =
        messages.groupBy { periodOf(it.timestamp, now) }.mapValues { (_, inPeriod) ->
            "${inPeriod.size}:${java.lang.Long.toHexString(inPeriod.sumOf { fingerprint(it, myUid) })}"
        }

    /**
     * The messages of the periods where [mine] differs from [theirs] (made at [now]); empty
     * when the asker has everything we have.
     */
    fun missing(mine: List<ChatMessage>, theirs: Map<String, String>, now: Long, myUid: String?): List<ChatMessage> {
        val differing = summary(mine, now, myUid).filter { (period, digest) -> theirs[period] != digest }.keys
        return mine.filter { periodOf(it.timestamp, now) in differing }
    }

    /** Whether [theirs] has periods we lack or that differ from ours: we may be missing messages. */
    fun hasMore(mine: List<ChatMessage>, theirs: Map<String, String>, now: Long, myUid: String?): Boolean {
        val ours = summary(mine, now, myUid)
        return theirs.any { (period, digest) -> ours[period] != digest }
    }

    /** Reads a command; null for anything else, including a later version's. */
    fun parse(data: String): Command? = try {
        val root = JSONObject(data)
        if (root.optInt("v") != VERSION) {
            null
        } else when (root.optString("op")) {
            "req" -> {
                val digests = root.getJSONObject("d")
                Command.Request(
                    channelId = root.getLong("c"),
                    now = root.getLong("now"),
                    summary = digests.keys().asSequence().associateWith { digests.getString(it) },
                    back = root.optBoolean("back", false),
                )
            }
            "msgs" -> {
                val channelId = root.getLong("c")
                val array = root.getJSONArray("m")
                val messages = (0 until array.length()).mapNotNull { index ->
                    storedMessageFromJson(array.getJSONObject(index), isPrivate = false)
                        ?.copy(channelId = channelId, isMe = false)
                }
                Command.Messages(channelId, messages)
            }
            else -> null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Unreadable history command: ${e.message}")
        null
    }

    /**
     * The replies carrying [messages] of one channel, each under [CHUNK_CHARS]. Our own messages
     * are stored as sent by "me": they go out under [myNickname] and [myUid], as others saw them.
     */
    fun replies(channelId: Long, messages: List<ChatMessage>, myNickname: String, myUid: String?): List<String> {
        val replies = mutableListOf<String>()
        var batch = JSONArray()
        var size = 0
        fun flush() {
            if (batch.length() == 0) return
            replies += JSONObject().apply {
                put("v", VERSION)
                put("op", "msgs")
                put("c", channelId)
                put("m", batch)
            }.toString()
            batch = JSONArray()
            size = 0
        }
        for (message in messages) {
            val shared = if (message.isMe) {
                message.copy(sender = myNickname, senderUid = message.senderUid ?: myUid)
            } else {
                message
            }
            val json = shared.toStoredJson().apply {
                // The receiver files it under the reply's channel, and works out "me" itself
                remove("c")
                remove("me")
                remove("sid")
            }
            val length = json.toString().length
            if (size + length > CHUNK_CHARS) flush()
            batch.put(json)
            size += length
        }
        flush()
        return replies
    }

    /**
     * [existing] with what [incoming] adds, in time order; null when it adds nothing. A message
     * from [myUid] is marked as ours.
     */
    fun merge(existing: List<ChatMessage>, incoming: List<ChatMessage>, myUid: String?): List<ChatMessage>? {
        fun senderOf(message: ChatMessage) = message.senderUid ?: if (message.isMe) myUid else message.sender
        val added = mutableListOf<ChatMessage>()
        for (message in incoming) {
            val sender = senderOf(message)
            val known = (existing.asSequence() + added.asSequence()).any { other ->
                other.channelId == message.channelId &&
                    other.text == message.text &&
                    senderOf(other) == sender &&
                    abs(other.timestamp - message.timestamp) <= SAME_MESSAGE_WINDOW_MS
            }
            if (!known) added += if (myUid != null && message.senderUid == myUid) message.copy(isMe = true) else message
        }
        if (added.isEmpty()) return null
        return (existing + added).sortedBy { it.timestamp }
    }
}
