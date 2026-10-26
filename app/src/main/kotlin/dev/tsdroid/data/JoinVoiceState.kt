package dev.tsdroid.data

/**
 * How the microphone and the speakers start out when a connection comes up.
 *
 * Joining a server always lands in a voice channel, which is not always a good
 * moment to be heard or to hear; the default therefore joins deafened, and the
 * user opens up from the bottom bar when ready.
 */
enum class JoinVoiceState {
    /** Microphone and speakers off. */
    DEAFENED,

    /** Microphone off, speakers on. */
    MUTED,

    /** Whatever the microphone mode rests at: muted for push-to-talk, open otherwise. */
    NORMAL;

    fun toRaw(): String = when (this) {
        DEAFENED -> "deafened"
        MUTED -> "muted"
        NORMAL -> "normal"
    }

    companion object {
        fun from(raw: String?): JoinVoiceState = when (raw) {
            "muted" -> MUTED
            "normal" -> NORMAL
            else -> DEAFENED
        }
    }
}
