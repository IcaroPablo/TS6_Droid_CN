package dev.tsdroid.bridge

import dev.tsdroid.data.OggOpus
import dev.tslib.AudioConfig
import dev.tslib.OpusCodec

/**
 * Records a voice message from the microphone the voice connection already holds: while it
 * records, [AudioBridge] hands it every frame instead of sending it to the server, and it
 * encodes them with an Opus encoder of its own into an Ogg file (see [OggOpus]).
 */
class VoiceMessageRecorder(private val bridge: AudioBridge) {
    private companion object {
        const val SAMPLE_RATE = AudioBridge.SAMPLE_RATE
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000

        // Speech in a file someone keeps: clearer than the voice channel's, still a few kB a second
        const val BITRATE = 32000
    }

    /** A finished recording: the Ogg file and how long it plays. */
    class Recording(val data: ByteArray, val durationMs: Long)

    private val lock = Any()
    private val packets = mutableListOf<ByteArray>()
    private var encoder: OpusCodec? = null
    private val pcm = ByteArray(FRAME_SAMPLES * 2)

    /** Starts recording; the microphone must be capturing. */
    fun start() {
        synchronized(lock) {
            packets.clear()
            encoder = OpusCodec(AudioConfig(SAMPLE_RATE, 1, BITRATE, FRAME_MS))
        }
        bridge.messageTap = ::onFrame
    }

    private fun onFrame(frame: ShortArray) {
        synchronized(lock) {
            val codec = encoder ?: return
            for (i in 0 until FRAME_SAMPLES) {
                val sample = if (i < frame.size) frame[i].toInt() else 0
                pcm[i * 2] = sample.toByte()
                pcm[i * 2 + 1] = (sample shr 8).toByte()
            }
            runCatching { codec.encode(pcm) }.getOrNull()?.let { packets += it }
        }
    }

    /** How long the recording is so far. */
    val durationMs: Long
        get() = synchronized(lock) { packets.size.toLong() * FRAME_MS }

    /**
     * Stops recording and gives the voice message, or null when it is discarded or came out
     * shorter than [minDurationMs].
     */
    fun stop(keep: Boolean, minDurationMs: Long): Recording? {
        bridge.messageTap = null
        synchronized(lock) {
            encoder?.close()
            encoder = null
            val duration = packets.size.toLong() * FRAME_MS
            val recording = if (keep && duration >= minDurationMs) {
                Recording(OggOpus.write(packets.toList(), FRAME_SAMPLES, SAMPLE_RATE), duration)
            } else {
                null
            }
            packets.clear()
            return recording
        }
    }
}
