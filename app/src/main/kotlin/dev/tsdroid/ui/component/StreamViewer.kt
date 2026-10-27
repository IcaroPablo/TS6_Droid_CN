package dev.tsdroid.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tsdroid.bridge.LiveStream
import dev.tsdroid.han.R
import dev.tsdroid.stream.StreamSession
import dev.tsdroid.stream.WebRtc
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import kotlin.math.roundToInt

/**
 * Full-screen viewer for TeamSpeak 6 streams: one stream fills the screen,
 * two split it, three or four share a grid. In each, pinch to zoom, drag to
 * move, double-tap to fit again.
 */
@Composable
fun StreamViewer(
    sessions: List<StreamSession>,
    /** A member's nickname, by client id */
    nameOf: (Int) -> String,
    /** Streams in the channel that can be added to the viewer */
    addable: List<LiveStream>,
    /** A new volume for one stream's sound; `commit` once the slider is let go */
    onVolumeChange: (session: StreamSession, volume: Float, commit: Boolean) -> Unit,
    onAdd: (LiveStream) -> Unit,
    /** After a failed direct connection: turn on TeamSpeak's server and try again */
    onRetryWithTeamSpeakStun: (StreamSession) -> Unit,
    onCloseStream: (StreamSession) -> Unit,
    onRotate: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var addOpen by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Column(modifier = Modifier.fillMaxWidth().safeDrawingPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // With one stream the bar names it; with several, each tile does
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        sessions.singleOrNull()?.let { session ->
                            StreamTitle(nameOf(session.stream.clientId), session.stream.name)
                        }
                    }
                    if (addable.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { addOpen = true }) {
                                Icon(Icons.Default.Add, stringResource(R.string.stream_add), tint = Color.White)
                            }
                            DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }) {
                                addable.forEach { stream ->
                                    DropdownMenuItem(
                                        text = {
                                            val name = nameOf(stream.clientId)
                                            Text(if (stream.name.isBlank()) name else "$name · ${stream.name}")
                                        },
                                        onClick = {
                                            addOpen = false
                                            onAdd(stream)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onRotate) {
                        Icon(Icons.Default.ScreenRotation, stringResource(R.string.stream_rotate), tint = Color.White)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White)
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val landscape = maxWidth > maxHeight
                val tile = @Composable { session: StreamSession, modifier: Modifier ->
                    key(session) {
                        StreamTile(
                            session = session,
                            streamerName = nameOf(session.stream.clientId),
                            showHeader = sessions.size > 1,
                            onRetryWithTeamSpeakStun = { onRetryWithTeamSpeakStun(session) },
                            onVolumeChange = { volume, commit -> onVolumeChange(session, volume, commit) },
                            onClose = { onCloseStream(session) },
                            modifier = modifier,
                        )
                    }
                }
                when (sessions.size) {
                    0 -> {}
                    1 -> tile(sessions[0], Modifier.fillMaxSize())
                    2 -> if (landscape) {
                        Row(Modifier.fillMaxSize()) {
                            sessions.forEach { tile(it, Modifier.weight(1f).fillMaxSize()) }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            sessions.forEach { tile(it, Modifier.weight(1f).fillMaxWidth()) }
                        }
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        sessions.chunked(2).forEach { pair ->
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                pair.forEach { tile(it, Modifier.weight(1f).fillMaxSize()) }
                                if (pair.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamTitle(streamerName: String, streamName: String) {
    Text(
        text = streamerName,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (streamName.isNotBlank()) {
        Text(
            text = streamName,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One stream: its video, its state, and (beside others) its name and close button. */
@Composable
private fun StreamTile(
    session: StreamSession,
    streamerName: String,
    showHeader: Boolean,
    onRetryWithTeamSpeakStun: () -> Unit,
    onVolumeChange: (volume: Float, commit: Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val volume by session.volume.collectAsStateWithLifecycle()
    var volumeOpen by remember { mutableStateOf(false) }
    // The level to go back to when unmuting
    var unmutedVolume by remember { mutableFloatStateOf(if (volume > 0f) volume else 1f) }
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(session, renderer) {
        val sink = renderer
        if (sink != null) session.attach(sink)
        onDispose { if (sink != null) session.detach(sink) }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { context ->
                // Measured to the video's shape (stretched to the whole tile it
                // would crop the picture) and centred
                SurfaceViewRenderer(context).apply {
                    init(WebRtc.eglBase.eglBaseContext, null)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    keepScreenOn = true
                    renderer = this
                }
            },
            update = { view ->
                view.scaleX = scale
                view.scaleY = scale
                view.translationX = offset.x
                view.translationY = offset.y
            },
            onRelease = { it.release() },
            modifier = Modifier.align(Alignment.Center),
        )

        // Gestures on a layer above the video: the SurfaceView itself takes no touch
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    })
                },
        )

        if (state != StreamSession.State.PLAYING) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state == StreamSession.State.JOINING || state == StreamSession.State.CONNECTING) {
                    CircularProgressIndicator(color = Color.White)
                }
                Text(
                    text = when (state) {
                        StreamSession.State.JOINING -> stringResource(R.string.stream_joining, streamerName)
                        StreamSession.State.CONNECTING -> stringResource(R.string.stream_connecting)
                        StreamSession.State.DECLINED -> stringResource(R.string.stream_declined, streamerName)
                        StreamSession.State.FAILED ->
                            if (session.useTeamSpeakStun) stringResource(R.string.stream_failed)
                            else stringResource(R.string.stream_failed_direct, streamerName)
                        StreamSession.State.ENDED -> stringResource(R.string.stream_ended)
                        StreamSession.State.PLAYING -> ""
                    },
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (state == StreamSession.State.FAILED && !session.useTeamSpeakStun) {
                    Button(onClick = onRetryWithTeamSpeakStun, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.stream_retry_with_teamspeak_stun), textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // This stream's sound, when it has any: a button in the corner, opening
        // a slider along the tile's bottom
        if (session.stream.hasAudio) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(if (volumeOpen) Modifier.fillMaxWidth() else Modifier)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (volumeOpen) {
                    IconButton(onClick = {
                        if (volume > 0f) {
                            unmutedVolume = volume
                            onVolumeChange(0f, true)
                        } else {
                            onVolumeChange(unmutedVolume, true)
                        }
                    }) {
                        Icon(
                            if (volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            stringResource(if (volume == 0f) R.string.stream_unmute else R.string.stream_mute),
                            tint = Color.White,
                        )
                    }
                    Slider(
                        value = volume,
                        onValueChange = {
                            if (it > 0f) unmutedVolume = it
                            onVolumeChange(it, false)
                        },
                        onValueChangeFinished = { onVolumeChange(volume, true) },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(volume * 100).roundToInt()} %",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                IconButton(onClick = { volumeOpen = !volumeOpen }) {
                    Icon(
                        if (volumeOpen) Icons.Default.Close
                        else if (volume == 0f) Icons.AutoMirrored.Filled.VolumeOff
                        else Icons.AutoMirrored.Filled.VolumeUp,
                        stringResource(R.string.stream_volume),
                        tint = Color.White,
                    )
                }
            }
        }

        if (showHeader) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    StreamTitle(streamerName, session.stream.name)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White)
                }
            }
        }
    }
}
