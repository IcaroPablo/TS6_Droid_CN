package dev.tsdroid.stream

import android.content.Context
import android.media.AudioAttributes
import android.util.Log
import dev.tsdroid.bridge.LiveStream
import dev.tsdroid.bridge.TsClient
import dev.tslib.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.util.concurrent.Executors
import org.webrtc.audio.JavaAudioDeviceModule

/** The WebRTC objects every stream shares, created on first use. */
object WebRtc {
    val eglBase: EglBase by lazy { EglBase.create() }

    private var factory: PeerConnectionFactory? = null

    @Synchronized
    fun factory(context: Context): PeerConnectionFactory {
        factory?.let { return it }
        val app = context.applicationContext
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions()
        )
        // Stream sound is media, not a call: it follows the media volume and is
        // not routed like the voice chat.
        val audio = JavaAudioDeviceModule.builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setUseStereoOutput(true)
            .createAudioDeviceModule()
        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(audio)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
            .also { factory = it }
    }
}

/**
 * Watching one TeamSpeak 6 stream: receive-only WebRTC, with the signalling
 * carried by the TeamSpeak connection.
 *
 * The streamer makes the offers (the first one comes with the join answer, and
 * it renegotiates with new ones), this side answers. Signalling is TeamSpeak's
 * JSON: `{"cmd":"offer"|"answer"|"iceCandidate","args":{…}}`.
 *
 * Without [useTeamSpeakStun] only this device's own addresses (local network and
 * IPv6) are offered, so no third party is contacted; a stream then only
 * connects when the two sides can reach each other directly.
 */
class StreamSession(
    context: Context,
    private val client: TsClient,
    val stream: LiveStream,
    val useTeamSpeakStun: Boolean,
    scope: CoroutineScope,
) {
    enum class State {
        /** Waiting for the streamer's answer */
        JOINING,
        /** Accepted; setting up the connection */
        CONNECTING,
        /** Video is flowing */
        PLAYING,
        /** The streamer declined */
        DECLINED,
        /** No connection could be made */
        FAILED,
        /** The streamer stopped or left the channel */
        ENDED,
    }

    companion object {
        // Disposing a connection waits for WebRTC's threads and the video
        // decoder, which can take seconds: never on the main thread.
        private val disposer = Executors.newSingleThreadExecutor { Thread(it, "StreamDispose") }
        private const val TAG = "StreamSession"
        private const val TEAMSPEAK_STUN = "stun:turn.teamspeak.com:3478"
        private const val CONNECT_TIMEOUT_MS = 25_000L
    }

    private val _state = MutableStateFlow(State.JOINING)
    val state: StateFlow<State> = _state.asStateFlow()

    // The received video and the views showing it. Views attach here rather
    // than to the track, so the session can detach them before it disposes the
    // track (a disposed track throws on removeSink).
    private var videoTrack: VideoTrack? = null
    private val sinks = mutableSetOf<VideoSink>()

    // The stream's sound and its volume (0 to 1, the stream as sent at most);
    // set before the track arrives, it applies when it does
    private var audioTrack: AudioTrack? = null
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val peer: PeerConnection

    // Candidates that arrive before the offer they belong to is applied
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    private val signalJob: Job
    private val timeoutJob: Job

    @Volatile
    private var closed = false

    init {
        val iceServers = if (useTeamSpeakStun) {
            listOf(PeerConnection.IceServer.builder(TEAMSPEAK_STUN).createIceServer())
        } else {
            emptyList()
        }
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        }
        peer = WebRtc.factory(context).createPeerConnection(config, PeerObserver())
            ?: error("Could not create a WebRTC connection")

        // Subscribed before the join request goes out, so the answer is not missed
        signalJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.streamSignals.collect { handleSignal(it) }
        }
        timeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_state.value == State.JOINING || _state.value == State.CONNECTING) {
                Log.w(TAG, "No connection after ${CONNECT_TIMEOUT_MS / 1000} s")
                _state.value = State.FAILED
            }
        }
        client.joinStream(stream.clientId, stream.id)
    }

    /** Stop watching: tells the streamer and releases the connection. */
    fun close() {
        if (release()) client.leaveStream(stream.clientId, stream.id)
    }

    /** The stream is gone: releases the connection and says so. */
    fun ended() {
        if (release()) _state.value = State.ENDED
    }

    /** Show the video in [sink], now or as soon as it arrives. */
    fun attach(sink: VideoSink) {
        synchronized(sinks) {
            if (closed) return
            sinks += sink
            videoTrack?.addSink(sink)
        }
    }

    /** The stream's sound volume, from 0 (silent) to 1 (as the streamer sends it). */
    fun setVolume(value: Float) {
        synchronized(sinks) {
            _volume.value = value.coerceIn(0f, 1f)
            if (!closed) audioTrack?.setVolume(_volume.value.toDouble())
        }
    }

    fun detach(sink: VideoSink) {
        synchronized(sinks) {
            if (sinks.remove(sink)) videoTrack?.removeSink(sink)
        }
    }

    private fun release(): Boolean {
        synchronized(sinks) {
            if (closed) return false
            closed = true
            sinks.forEach { videoTrack?.removeSink(it) }
            sinks.clear()
            videoTrack = null
            audioTrack = null
        }
        signalJob.cancel()
        timeoutJob.cancel()
        disposer.execute { peer.dispose() }
        return true
    }

    private fun handleSignal(event: Event) {
        val data = event.data
        if ((data["client_id"] as? Number)?.toInt() != stream.clientId || data["stream_id"] != stream.id) return
        when (event.type) {
            "stream_join_response" -> {
                if (data["accepted"] != true) {
                    _state.value = State.DECLINED
                    return
                }
                _state.value = State.CONNECTING
                (data["offer"] as? String)?.let(::applyOffer)
            }
            "stream_signaling" -> {
                val json = try {
                    JSONObject(data["json"] as? String ?: return)
                } catch (e: Exception) {
                    Log.w(TAG, "Unreadable signalling", e)
                    return
                }
                val args = json.optJSONObject("args") ?: return
                when (json.optString("cmd")) {
                    "offer" -> applyOffer(args.optString("offer"))
                    "iceCandidate" -> addRemoteCandidate(
                        IceCandidate(args.optString("mid"), args.optInt("mLine"), args.optString("sdp"))
                    )
                }
            }
        }
    }

    private fun applyOffer(sdp: String) {
        if (closed || sdp.isEmpty()) return
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peer.setRemoteDescription(object : SdpCallbacks("set offer") {
            override fun onSetSuccess() {
                val queued = synchronized(pendingCandidates) {
                    remoteDescriptionSet = true
                    pendingCandidates.toList().also { pendingCandidates.clear() }
                }
                queued.forEach { peer.addIceCandidate(it) }
                createAnswer()
            }
        }, offer)
    }

    private fun createAnswer() {
        peer.createAnswer(object : SdpCallbacks("create answer") {
            override fun onCreateSuccess(answer: SessionDescription) {
                peer.setLocalDescription(object : SdpCallbacks("set answer") {
                    override fun onSetSuccess() {
                        sendSignal("answer", JSONObject().put("answer", answer.description))
                    }
                }, answer)
            }
        }, MediaConstraints())
    }

    private fun addRemoteCandidate(candidate: IceCandidate) {
        val now = synchronized(pendingCandidates) {
            if (!remoteDescriptionSet) pendingCandidates += candidate
            remoteDescriptionSet
        }
        if (now && !closed) peer.addIceCandidate(candidate)
    }

    private fun sendSignal(cmd: String, args: JSONObject) {
        if (closed) return
        val json = JSONObject().put("args", args).put("cmd", cmd).toString()
        client.sendStreamSignaling(stream.clientId, stream.id, json)
    }

    private inner class PeerObserver : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            sendSignal(
                "iceCandidate",
                JSONObject()
                    .put("mLine", candidate.sdpMLineIndex)
                    .put("mid", candidate.sdpMid)
                    .put("sdp", candidate.sdp),
            )
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            when (val track = transceiver.receiver.track()) {
                is VideoTrack -> synchronized(sinks) {
                    if (closed) return
                    videoTrack = track
                    sinks.forEach { track.addSink(it) }
                }
                is AudioTrack -> synchronized(sinks) {
                    if (closed) return
                    audioTrack = track
                    track.setVolume(_volume.value.toDouble())
                }
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            Log.i(TAG, "Connection $newState")
            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    timeoutJob.cancel()
                    _state.value = State.PLAYING
                }
                PeerConnection.PeerConnectionState.FAILED -> _state.value = State.FAILED
                else -> {}
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: org.webrtc.DataChannel) {}
        override fun onRenegotiationNeeded() {}
    }

    private open inner class SdpCallbacks(private val step: String) : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) = fail(error)
        override fun onSetFailure(error: String?) = fail(error)

        private fun fail(error: String?) {
            Log.w(TAG, "WebRTC $step failed: $error")
            _state.value = State.FAILED
        }
    }
}
