package dev.tsdroid.data

/**
 * The nickname rules both nickname entry points share — the connect form and the
 * in-session "change temporary nickname" dialog.
 *
 * They used to be a loose `3` inside the connect ViewModel; the server has its
 * own idea of a valid nickname, but the app refuses the obviously-too-short ones
 * before bothering it.
 */
object NicknameRules {

    /** Shortest nickname the app accepts, matching the connect form. */
    const val MIN_LENGTH = 3

    /** Longest nickname sent to the server; TS3 truncates beyond its own limit. */
    const val MAX_LENGTH = 30

    /** The nickname as it is sent: surrounding whitespace never means anything. */
    fun normalize(raw: String): String = raw.trim()

    /** True when [raw] (normalized) is a nickname worth sending. */
    fun isValid(raw: String): Boolean = normalize(raw).length in MIN_LENGTH..MAX_LENGTH
}
