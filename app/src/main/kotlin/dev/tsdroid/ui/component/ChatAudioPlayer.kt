package dev.tsdroid.ui.component

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays the chat's audio, voice messages and audio files, one at a time and from memory: the
 * downloaded bytes go straight to the player, so no copy is written anywhere.
 */
object ChatAudioPlayer {
    private const val TAG = "ChatAudioPlayer"

    // How often the position moves on screen
    private const val TICK_MS = 100L

    /** What is playing: [key] tells which message, so only its bubble shows the progress. */
    data class Playback(val key: Any, val playing: Boolean, val positionMs: Int, val durationMs: Int)

    private val _playback = MutableStateFlow<Playback?>(null)
    val playback: StateFlow<Playback?> = _playback.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    /** Plays [data] as [key]'s audio, or pauses or resumes it when [key] is the one playing. */
    fun toggle(key: Any, data: ByteArray) {
        val current = _playback.value
        val mp = player
        if (current != null && current.key == key && mp != null) {
            if (mp.isPlaying) mp.pause() else mp.start()
            publish(key)
            return
        }
        stop()
        val created = MediaPlayer()
        player = created
        try {
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            created.setDataSource(BytesSource(data))
            created.setOnPreparedListener {
                it.start()
                publish(key)
                ticker = scope.launch {
                    while (isActive) {
                        publish(key)
                        delay(TICK_MS)
                    }
                }
            }
            created.setOnCompletionListener { stop() }
            created.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Playback failed ($what, $extra)")
                stop()
                true
            }
            _playback.value = Playback(key, playing = false, positionMs = 0, durationMs = 0)
            created.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot play this audio", e)
            stop()
        }
    }

    /** Moves [key]'s audio, if it is the one loaded, to [fraction] of its length. */
    fun seek(key: Any, fraction: Float) {
        val mp = player ?: return
        if (_playback.value?.key != key) return
        mp.seekTo((mp.duration * fraction.coerceIn(0f, 1f)).toInt())
        publish(key)
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        player?.release()
        player = null
        _playback.value = null
    }

    private fun publish(key: Any) {
        val mp = player ?: return
        _playback.value = runCatching {
            Playback(key, mp.isPlaying, mp.currentPosition, mp.duration.coerceAtLeast(0))
        }.getOrNull() ?: return
    }

    /** The player reads straight from the downloaded bytes. */
    private class BytesSource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(size.toLong(), data.size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize(): Long = data.size.toLong()

        override fun close() {}
    }
}
