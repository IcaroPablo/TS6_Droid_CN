package dev.tsdroid.bridge

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import dev.tslib.AudioConfig
import dev.tslib.OpusCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class AudioBridge(
    private val context: Context,
    private val tsClient: TsClient,
) {
    companion object {
        private const val TAG = "AudioBridge"
        const val SAMPLE_RATE = 48000
        const val CODEC_OPUS_VOICE = 4
        private const val FRAME_SIZE_MS = 20
        private const val FRAME_SIZE_SAMPLES = SAMPLE_RATE * FRAME_SIZE_MS / 1000 // 960
        private const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2 // 16-bit PCM = 2 bytes/sample
        private const val MAX_QUEUE_FRAMES = 10 // Max buffered frames per user

        /**
         * Exact digital silence for this long means the system muted/silenced our
         * capture (another app took the mic, privacy toggle, OEM background policy).
         * A real microphone never returns mathematically zero samples.
         */
        private const val SILENT_FRAMES_BEFORE_RECOVERY = 100 // 100 * 20 ms = 2 s

        /** Empty reads (read() == 0) for this long mean the record stopped delivering. */
        private const val EMPTY_READS_BEFORE_RECOVERY = 25 // 25 * 20 ms = 0.5 s

        /** Ignore recording-configuration callbacks right after a session opens. */
        private const val FRAMES_BEFORE_CONFIG_RECOVERY = 50 // 1 s

        /** Reopen quickly at first, then settle into a slow poll so the mic can come back later. */
        private const val MAX_FAST_RECOVERY_ATTEMPTS = 5
        private const val SLOW_RECOVERY_INTERVAL_MS = 10_000L
    }

    /** Health of the capture path — logged (and available for UI surfacing). */
    enum class MicStatus { IDLE, STARTING, RUNNING, RECOVERING, UNAVAILABLE }

    private val audioConfig = AudioConfig()

    // Encoder for capture (our mic)
    private var encoder: OpusCodec? = null

    // Per-user decoders and raw opus queues for playback mixing
    private val userDecoders = ConcurrentHashMap<Int, OpusCodec>()
    private val userQueues = ConcurrentHashMap<Int, ArrayDeque<ByteArray>>()

    private var audioTrack: AudioTrack? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private var playbackJob: Job? = null

    /**
     * Capture owns its own scope. Handing an Activity/ViewModel scope to the capture
     * loop (as the UI layer used to do) cancels the microphone the moment that scope
     * dies — which silently killed the mic while the app sat in the background, with
     * nothing left to restart it.
     */
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val captureLock = Any()

    @Volatile
    private var mic: MicHandle? = null
    private var captureJob: Job? = null

    /** Bumped on every start/stop so a stale capture loop can never touch newer state. */
    @Volatile
    private var captureGeneration = 0

    private var noiseSuppressionEnabled = true

    /** Set by the audio-recording callback when the system silences/reroutes us. */
    @Volatile
    private var recordingConfigDirty = false
    private var recordingCallbackRegistered = false

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            val sessionId = mic?.record?.audioSessionId ?: return
            val own = configs.firstOrNull { it.clientAudioSessionId == sessionId } ?: return
            if (own.isClientSilenced) {
                Log.w(TAG, "System silenced our microphone (session=$sessionId); scheduling recovery")
                recordingConfigDirty = true
            } else {
                Log.i(TAG, "Audio recording config changed (session=$sessionId, silenced=false)")
            }
        }
    }

    // Dedicated single-thread scope for audio playback
    @OptIn(ExperimentalCoroutinesApi::class)
    private val playbackScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1)
    )

    @Volatile
    var gainFactor: Float = 1.0f

    @Volatile
    private var mutedUserIds: Set<Int> = emptySet()

    private val _isMuted = MutableStateFlow(true) // Start muted (PTT default)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isOutputMuted = MutableStateFlow(false)
    val isOutputMuted: StateFlow<Boolean> = _isOutputMuted.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _isLocalVoiceActive = MutableStateFlow(false)
    val isLocalVoiceActive: StateFlow<Boolean> = _isLocalVoiceActive.asStateFlow()

    private val _micStatus = MutableStateFlow(MicStatus.IDLE)
    val micStatus: StateFlow<MicStatus> = _micStatus.asStateFlow()

    fun initialize() {
        try {
            // Explicitly release any old audio stream resources if lingering
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null

            encoder = OpusCodec(audioConfig)
            initAudioTrack()
            startPlaybackLoop()
        } catch (e: Exception) {
            android.util.Log.e("TS6_DEBUG", "Caught audio initialization friction safely", e)
            // We don't throw here to prevent JE_AppCustomException
        }
    }

    /**
     * Start microphone capture.
     *
     * The capture loop owns its own scope and rebuilds the [AudioRecord] by itself
     * whenever the system takes the microphone away, so callers never pass a scope
     * and the mic can no longer be killed by a dead Activity/ViewModel scope.
     */
    @SuppressLint("MissingPermission")
    fun startCapture(noiseSuppressionEnabled: Boolean = true) {
        synchronized(captureLock) {
            if (_isCapturing.value) return
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Cannot start capture: RECORD_AUDIO permission is missing")
                return
            }

            this.noiseSuppressionEnabled = noiseSuppressionEnabled
            recordingConfigDirty = false
            _isCapturing.value = true
            _micStatus.value = MicStatus.STARTING
            val generation = ++captureGeneration
            registerRecordingCallback()
            captureJob = captureScope.launch { runCaptureLoop(generation) }
            Log.i(TAG, "Capture requested (generation=$generation, noiseSuppression=$noiseSuppressionEnabled)")
        }
    }

    fun stopCapture() {
        val job: Job?
        val handle: MicHandle?
        synchronized(captureLock) {
            if (!_isCapturing.value && mic == null && captureJob == null) {
                _micStatus.value = MicStatus.IDLE
                return
            }
            _isCapturing.value = false
            captureGeneration++ // Invalidate the running loop
            job = captureJob
            captureJob = null
            handle = mic
            mic = null
            unregisterRecordingCallback()
        }
        // Stopping the record also unblocks a read() parked in the capture loop, which
        // then releases the record itself. A delayed idempotent close covers the case
        // where that loop is wedged inside read().
        handle?.requestStop()
        job?.cancel()
        if (handle != null) {
            captureScope.launch {
                delay(500)
                handle.close()
            }
        }
        _isLocalVoiceActive.value = false
        _micStatus.value = MicStatus.IDLE
        Log.i(TAG, "Capture stopped")
    }

    /**
     * Supervises one microphone session at a time. Whenever the capture path stops
     * delivering real audio the record is closed and reopened, with backoff, so a
     * silenced or revoked microphone recovers on its own instead of staying dead
     * until the user reconnects.
     */
    private suspend fun runCaptureLoop(generation: Int) {
        var failures = 0
        Log.i(TAG, "Capture loop started (generation=$generation)")
        try {
            while (isLoopActive() && isCurrentGeneration(generation) && _isCapturing.value) {
                val handle = try {
                    openMicrophone()
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    failures++
                    Log.e(TAG, "Microphone open failed (attempt $failures)", t)
                    _micStatus.value =
                        if (failures > MAX_FAST_RECOVERY_ATTEMPTS) MicStatus.UNAVAILABLE
                        else MicStatus.RECOVERING
                    if (recoveryDelay(failures, generation)) break
                    continue
                }

                if (!isCurrentGeneration(generation) || !_isCapturing.value) {
                    handle.close()
                    break
                }

                mic = handle
                _micStatus.value = MicStatus.RUNNING
                failures = 0
                Log.i(
                    TAG,
                    "Microphone open (session=${handle.record.audioSessionId}, " +
                        "source=${handle.record.audioSource}, state=${handle.record.state})",
                )

                val outcome = try {
                    pumpFrames(handle, generation)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Log.e(TAG, "Capture loop error", t)
                    CaptureOutcome.RECOVER
                } finally {
                    if (mic === handle) mic = null
                    handle.close()
                }

                if (outcome == CaptureOutcome.STOP) break
                if (!isCurrentGeneration(generation) || !_isCapturing.value) break

                failures++
                _micStatus.value =
                    if (failures > MAX_FAST_RECOVERY_ATTEMPTS) MicStatus.UNAVAILABLE
                    else MicStatus.RECOVERING
                Log.w(TAG, "Rebuilding microphone (attempt $failures)")
                if (recoveryDelay(failures, generation)) break
            }
        } finally {
            if (isCurrentGeneration(generation)) {
                _isCapturing.value = false
                _isLocalVoiceActive.value = false
                _micStatus.value = MicStatus.IDLE
            }
            Log.i(TAG, "Capture loop finished (generation=$generation)")
        }
    }

    /** Reads frames and sends them; returns RECOVER when the capture path went bad. */
    private suspend fun pumpFrames(handle: MicHandle, generation: Int): CaptureOutcome {
        val record = handle.record
        val codec = encoder
        if (codec == null) {
            Log.e(TAG, "Cannot capture: Opus encoder is not initialized")
            return CaptureOutcome.STOP
        }

        val buffer = ShortArray(FRAME_SIZE_SAMPLES)
        var silentFrames = 0
        var emptyReads = 0
        var framesInSession = 0

        while (isLoopActive() && isCurrentGeneration(generation) && _isCapturing.value) {
            val read = try {
                record.read(buffer, 0, FRAME_SIZE_SAMPLES)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.e(TAG, "Microphone read failed", t)
                return CaptureOutcome.RECOVER
            }

            if (read < 0) {
                Log.e(TAG, "Microphone read returned error $read")
                return CaptureOutcome.RECOVER
            }

            if (read == 0) {
                // An empty read means the record is stopped or no longer fed.
                emptyReads++
                if (emptyReads >= EMPTY_READS_BEFORE_RECOVERY) {
                    Log.w(TAG, "Microphone stopped delivering samples ($emptyReads empty reads)")
                    return CaptureOutcome.RECOVER
                }
                delay(FRAME_SIZE_MS.toLong()) // Avoid a hot spin
                continue
            }

            emptyReads = 0
            framesInSession++
            if (read < FRAME_SIZE_SAMPLES) {
                Arrays.fill(buffer, read, FRAME_SIZE_SAMPLES, 0.toShort())
            }

            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w(TAG, "Microphone is no longer recording (state=${record.recordingState})")
                return CaptureOutcome.RECOVER
            }

            if (recordingConfigDirty && framesInSession > FRAMES_BEFORE_CONFIG_RECOVERY) {
                recordingConfigDirty = false
                Log.w(TAG, "Audio recording configuration changed; rebuilding microphone")
                return CaptureOutcome.RECOVER
            }

            if (!_isMuted.value) {
                var energy = 0L
                for (i in 0 until read) {
                    energy += buffer[i].toLong() * buffer[i].toLong()
                }
                val rms = Math.sqrt(energy.toDouble() / read)
                _isLocalVoiceActive.value = rms > 150.0 // Adjusted threshold for voice activity

                if (isDigitalSilence(buffer, read)) {
                    silentFrames++
                    if (silentFrames >= SILENT_FRAMES_BEFORE_RECOVERY) {
                        Log.w(
                            TAG,
                            "Microphone is delivering digital silence " +
                                "(${silentFrames * FRAME_SIZE_MS} ms); rebuilding",
                        )
                        return CaptureOutcome.RECOVER
                    }
                } else {
                    silentFrames = 0
                }

                val pcmBytes = shortsToBytes(buffer)
                try {
                    val encoded = codec.encode(pcmBytes)
                    tsClient.sendAudio(encoded, CODEC_OPUS_VOICE)
                } catch (_: Exception) {
                }
            } else {
                silentFrames = 0
                _isLocalVoiceActive.value = false
            }
        }
        return CaptureOutcome.STOP
    }

    @SuppressLint("MissingPermission")
    private fun openMicrophone(): MicHandle {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, FRAME_SIZE_BYTES * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            val state = record.state
            runCatching { record.release() }
            throw IllegalStateException("AudioRecord is not initialized (state=$state)")
        }
        try {
            record.startRecording()
        } catch (t: Throwable) {
            runCatching { record.release() }
            throw t
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            val state = record.recordingState
            runCatching { record.release() }
            throw IllegalStateException("AudioRecord did not start recording (state=$state)")
        }
        attachNoiseSuppressor(record)
        return MicHandle(record)
    }

    private fun attachNoiseSuppressor(record: AudioRecord) {
        noiseSuppressor?.release()
        noiseSuppressor = null
        if (noiseSuppressionEnabled && NoiseSuppressor.isAvailable()) {
            try {
                NoiseSuppressor.create(record.audioSessionId)?.also {
                    noiseSuppressor = it
                    Log.i(TAG, "NoiseSuppressor enabled (session=${record.audioSessionId})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create NoiseSuppressor", e)
            }
        } else {
            Log.i(TAG, "NoiseSuppressor skipped: enabled=$noiseSuppressionEnabled, available=${NoiseSuppressor.isAvailable()}")
        }
    }

    /** @return true when the loop must stop instead of retrying. */
    private suspend fun recoveryDelay(failures: Int, generation: Int): Boolean {
        if (!isCurrentGeneration(generation) || !_isCapturing.value) return true
        val waitMs = if (failures <= MAX_FAST_RECOVERY_ATTEMPTS) {
            failures * 500L
        } else {
            SLOW_RECOVERY_INTERVAL_MS
        }
        logMicrophoneEnvironment(failures)
        delay(waitMs)
        return !isCurrentGeneration(generation) || !_isCapturing.value
    }

    private fun logMicrophoneEnvironment(failures: Int) {
        try {
            val muteState = audioManager?.let { manager ->
                runCatching { manager.isMicrophoneMute }.getOrNull()
            }
            Log.w(
                TAG,
                "Microphone unavailable (attempt $failures): systemMicMute=$muteState, " +
                    "permission=${ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)}",
            )
        } catch (_: Throwable) {
        }
    }

    private fun isCurrentGeneration(generation: Int) = captureGeneration == generation

    /** Cancellation state of the capture coroutine (these helpers have no scope receiver). */
    private suspend fun isLoopActive() = currentCoroutineContext().isActive

    /** True only for mathematically silent frames (a silenced client), not a quiet room. */
    private fun isDigitalSilence(buffer: ShortArray, count: Int): Boolean {
        for (i in 0 until count) {
            if (buffer[i].toInt() != 0) return false
        }
        return true
    }

    private fun registerRecordingCallback() {
        val manager = audioManager ?: return
        if (recordingCallbackRegistered) return
        try {
            manager.registerAudioRecordingCallback(recordingCallback, Handler(Looper.getMainLooper()))
            recordingCallbackRegistered = true
            Log.d(TAG, "Audio recording callback registered")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register audio recording callback", e)
        }
    }

    private fun unregisterRecordingCallback() {
        val manager = audioManager ?: return
        if (!recordingCallbackRegistered) return
        recordingCallbackRegistered = false
        try {
            manager.unregisterAudioRecordingCallback(recordingCallback)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to unregister audio recording callback", e)
        }
    }

    private enum class CaptureOutcome { STOP, RECOVER }

    /** Owns one [AudioRecord]; [close] is idempotent so either owner may call it. */
    private class MicHandle(val record: AudioRecord) {
        private val closed = AtomicBoolean(false)
        private val stopped = AtomicBoolean(false)

        /** Unblocks a read() parked on this record without releasing it. */
        fun requestStop() {
            if (!stopped.compareAndSet(false, true)) return
            runCatching { record.stop() }.onFailure { Log.w(TAG, "AudioRecord.stop failed", it) }
        }

        fun close() {
            requestStop()
            if (!closed.compareAndSet(false, true)) return
            runCatching { record.release() }.onFailure { Log.w(TAG, "AudioRecord.release failed", it) }
        }
    }

    private fun initAudioTrack() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, FRAME_SIZE_BYTES * 4))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        audioTrack?.play()
    }

    /**
     * Playback loop: decodes one opus frame per user, mixes PCM, writes
     * one combined frame to AudioTrack. All decoder access is on this
     * single thread so no synchronization is needed on decoders.
     */
    private fun startPlaybackLoop() {
        playbackJob = playbackScope.launch {
            val mixBuffer = ShortArray(FRAME_SIZE_SAMPLES)
            val decodeBuffer = ShortArray(FRAME_SIZE_SAMPLES)

            while (isActive) {
                var hasData = false
                mixBuffer.fill(0)

                for ((userId, queue) in userQueues) {
                    val opusData = synchronized(queue) { queue.pollFirst() } ?: continue
                    val decoder = userDecoders.getOrPut(userId) { OpusCodec(audioConfig) }
                    try {
                        val pcmBytes = decoder.decode(opusData)
                        bytesToShorts(pcmBytes, decodeBuffer)
                        hasData = true
                        // Mix: sum with clipping
                        for (i in mixBuffer.indices) {
                            val sum = mixBuffer[i].toInt() + decodeBuffer[i].toInt()
                            mixBuffer[i] = sum.coerceIn(
                                Short.MIN_VALUE.toInt(),
                                Short.MAX_VALUE.toInt(),
                            ).toShort()
                        }
                    } catch (_: Exception) {}
                }

                if (hasData) {
                    val gain = gainFactor
                    if (gain != 1.0f) {
                        for (i in mixBuffer.indices) {
                            mixBuffer[i] = (mixBuffer[i] * gain).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                .toShort()
                        }
                    }
                    val bytes = shortsToBytes(mixBuffer)
                    audioTrack?.write(bytes, 0, bytes.size)
                } else {
                    // No audio data — sleep briefly to avoid busy-waiting
                    delay(5)
                }
            }
        }
    }

    /**
     * Queue an opus packet for a specific user. Called from any thread;
     * decoding happens on the playback thread.
     */
    fun setMutedUserIds(userIds: Set<Int>) {
        mutedUserIds = userIds
    }

    fun playAudio(userId: Int, opusData: ByteArray) {
        if (userId in mutedUserIds) return
        if (_isOutputMuted.value) return // Global output mute — discard incoming audio
        val queue = userQueues.getOrPut(userId) { ArrayDeque() }
        synchronized(queue) {
            if (queue.size < MAX_QUEUE_FRAMES) {
                queue.addLast(opusData)
            }
            // Drop oldest if queue is full (prevents unbounded lag)
        }
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        tsClient.setInputMuted(muted)
    }

    fun toggleMute() {
        val newState = !_isMuted.value
        _isMuted.value = newState
        tsClient.setInputMuted(newState)
    }

    fun setOutputMuted(muted: Boolean) {
        _isOutputMuted.value = muted
        // When output is muted, clear all queued audio so nothing plays
        if (muted) {
            for ((_, queue) in userQueues) {
                synchronized(queue) { queue.clear() }
            }
        }
    }

    fun toggleOutputMute() {
        setOutputMuted(!_isOutputMuted.value)
    }

    fun release() {
        stopCapture()
        playbackJob?.cancel()
        playbackJob = null
        playbackScope.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        encoder?.close()
        encoder = null
        // Close per-user decoders
        for (decoder in userDecoders.values) {
            try { decoder.close() } catch (_: Exception) {}
        }
        userDecoders.clear()
        userQueues.clear()
    }

    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (shorts[i].toInt() shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    private fun bytesToShorts(bytes: ByteArray, out: ShortArray) {
        val count = minOf(bytes.size / 2, out.size)
        for (i in 0 until count) {
            out[i] = ((bytes[i * 2].toInt() and 0xFF) or
                    (bytes[i * 2 + 1].toInt() shl 8)).toShort()
        }
    }
}
