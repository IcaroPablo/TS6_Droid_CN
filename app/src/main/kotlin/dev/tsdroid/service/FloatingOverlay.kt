package dev.tsdroid.service

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tsdroid.han.R
import dev.tsdroid.ui.component.ChoicePill
import dev.tsdroid.ui.component.micAction
import dev.tsdroid.ui.component.micIcon
import dev.tsdroid.ui.component.speakerAction
import dev.tsdroid.ui.component.speakerIcon
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.Spacing
import dev.tsdroid.ui.theme.TalkingBlue
import dev.tsdroid.ui.theme.WhisperAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Which of the panel's two lists is showing. Hoisted so it survives collapsing the panel. */
internal enum class OverlayView { Members, Channels }

internal data class FloatingOverlayMember(
    val id: Int,
    val nickname: String,
    val avatar: ImageBitmap?,
    val speaking: Boolean,
    val micMuted: Boolean,
    val outputMuted: Boolean,
)

internal data class FloatingOverlayChannel(
    val id: Long,
    val name: String,
    /** The server refuses a move into a protected channel without its password. */
    val hasPassword: Boolean = false,
)

/**
 * Everything the floating window draws.
 *
 * Built by the service from its own state plus the client's flows, so the composable below stays a
 * pure function of this.
 */
internal data class FloatingOverlayState(
    val connected: Boolean,
    val serverName: String?,
    val channelName: String?,
    val members: List<FloatingOverlayMember>,
    val channels: List<FloatingOverlayChannel>,
    val currentChannelId: Long,
    val localAvatar: ImageBitmap?,
    val speakingAvatar: ImageBitmap?,
    val speakingIsLocal: Boolean,
    val localSpeaking: Boolean,
    val remoteSpeaking: Boolean,
    /** The current remote burst arrived as a whisper packet, not channel talk. */
    val remoteSpeakingIsWhisper: Boolean,
    /** The local microphone is on a whisper target set right now. */
    val localWhisperActive: Boolean,
    /** A push-to-talk hold on the bubble is open right now: the microphone is transmitting. */
    val talking: Boolean,
    /** The capture path is actually running, so a hold really is being sent to the channel. */
    val micReady: Boolean,
    val micMuted: Boolean,
    val outputMuted: Boolean,
    val reconnecting: Boolean,
    val reconnectAttempt: Int,
    val micUnavailable: Boolean,
    val unread: Int,
)

/**
 * The floating window over other apps: a bubble that expands into a small control panel.
 *
 * Deliberate choices, because it draws in an overlay window rather than in the app:
 * * no elevation shadows — inside a `TYPE_APPLICATION_OVERLAY` window they render as a pale rectangle
 *   around the shape instead of a soft shadow;
 * * no ripples either (`indication = null`): their bounds are the rectangular touch target, so every
 *   tap flashed a grey square. Pressed/selected states are explicit colours instead;
 * * surfaces are translucent app colours with a hairline border, which reads the same over a game, a
 *   video or a desktop;
 * * the bubble carries the app's own pink ([AppPink], the launcher icon's background) while nobody is
 *   talking, at half opacity so it does not hide what is underneath;
 * * a push-to-talk hold paints the bubble's rim and a ring around it, and the ring follows the
 *   microphone's level — the user can see that they are live, and that they are being heard,
 *   without having to ask the channel;
 * * while a hold is open the window stays put, however much the hand holding the button shakes —
 *   and the hold steps aside the moment the finger is clearly dragging the bubble somewhere.
 */
@Composable
internal fun FloatingOverlay(
    state: FloatingOverlayState,
    /**
     * The microphone's current loudness, as a state rather than a value: the meter reads it inside
     * a draw lambda, so a moving needle redraws the bubble without recomposing the panel behind it.
     */
    inputLevel: State<Float>,
    expanded: Boolean,
    view: OverlayView,
    onViewChange: (OverlayView) -> Unit,
    onToggleExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onSizeChange: (Int, Int) -> Unit,
    onToggleMic: () -> Unit,
    onToggleOutput: () -> Unit,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit,
    onWhisperTalkStart: () -> Unit,
    onWhisperTalkStop: () -> Unit,
    onOpenApp: () -> Unit,
    onMoveToChannel: (Long, String?) -> Unit,
    /**
     * True while a text field inside the window wants the keyboard.
     *
     * The window runs with `FLAG_NOT_FOCUSABLE` (it must not steal focus from
     * the app underneath), and such a window never receives an IME — tapping the
     * channel-password field did nothing at all. The service clears that flag
     * while this is true and puts it back afterwards.
     */
    onKeyboardRequest: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
) {
    // The window is WRAP_CONTENT, and the system throttles overlay window resizes hard: animating the
    // *content size* turned a 200ms reveal into about ten seconds. So the layout jumps straight to the
    // target size (one resize), and the reveal is animated *inside* the window instead — the panel
    // fades and scales in, and on the way back it fades out before the window shrinks to the bubble.
    var showPanel by remember { mutableStateOf(expanded) }
    val reveal = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            showPanel = true
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(REVEAL_ANIMATION_MS))
        } else {
            reveal.animateTo(0f, tween(REVEAL_ANIMATION_MS))
            showPanel = false
            // The bubble takes the panel's place only after the window has shrunk, so it fades back in.
            reveal.animateTo(1f, tween(REVEAL_FADE_IN_MS))
        }
    }

    Box(
        modifier = Modifier
            .wrapContentSize()
            .background(Color.Transparent)
            // The window follows this size. The service only records it here: a window must not be
            // moved from inside a layout callback.
            .onSizeChanged { onSizeChange(it.width, it.height) },
    ) {
        if (showPanel) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = reveal.value
                    val scale = REVEAL_MIN_SCALE + (1f - REVEAL_MIN_SCALE) * reveal.value
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                ExpandedPanel(
                    state = state,
                    view = view,
                    onViewChange = onViewChange,
                    onToggleExpand = onToggleExpand,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onToggleMic = onToggleMic,
                    onToggleOutput = onToggleOutput,
                    onWhisperTalkStart = onWhisperTalkStart,
                    onWhisperTalkStop = onWhisperTalkStop,
                    onOpenApp = onOpenApp,
                    onMoveToChannel = onMoveToChannel,
                    onKeyboardRequest = onKeyboardRequest,
                    onDisconnect = onDisconnect,
                )
            }
        } else {
            Box(modifier = Modifier.graphicsLayer { alpha = reveal.value }) {
                CollapsedBubble(
                    state = state,
                    inputLevel = inputLevel,
                    onToggleExpand = onToggleExpand,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onTalkStart = onTalkStart,
                    onTalkStop = onTalkStop,
                )
            }
        }
    }
}

// ── Collapsed ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollapsedBubble(
    state: FloatingOverlayState,
    inputLevel: State<Float>,
    onToggleExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val talking = state.talking
    // A hold with an unusable capture path is worse than a closed microphone: the user believes
    // they are being heard. The rings say so in the error colour instead.
    val liveColor = if (state.micReady) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val speaking = state.localSpeaking || state.remoteSpeaking
    // Whisper owns the ring colour: either the incoming burst is a whisper, or the
    // local microphone is on a whisper target set and we are the ones transmitting.
    // Amber matches the member-list ring, so the two views agree.
    val whisperTint = state.remoteSpeakingIsWhisper ||
        (state.localWhisperActive && (talking || state.localSpeaking))
    // Whoever is talking gets the bubble; the app icon sits there the rest of the time.
    val avatar = when {
        state.remoteSpeaking -> state.speakingAvatar
        state.localSpeaking -> state.localAvatar
        else -> null
    }
    // A drag must not also read as a tap, and neither must a push-to-talk press.
    var dragged by remember { mutableStateOf(false) }
    var suppressClick by remember { mutableStateOf(false) }
    // True while a hold owns the bubble: the window must not follow a shaking hand. The hold's own
    // escape hatch clears it, handing the gesture over to the drag.
    var holdOwnsBubble by remember { mutableStateOf(false) }
    // Only a drag that actually moved the window may dock it against the edge on release.
    var windowMoved by remember { mutableStateOf(false) }

    // Read inside the gestures, so neither ever acts on a stale microphone state.
    val micClosed by rememberUpdatedState(state.micMuted)
    val latestTalkStart by rememberUpdatedState(onTalkStart)
    val latestTalkStop by rememberUpdatedState(onTalkStop)
    val haptics = LocalHapticFeedback.current

    // The live ring breathes so a hold is obvious even in silence. It only animates while the hold
    // is open: the bubble sits on screen for hours and must not tick away in the background.
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(talking) {
        pulse.snapTo(0f)
        while (talking) {
            pulse.animateTo(1f, tween(LIVE_PULSE_MS))
            pulse.animateTo(0f, tween(LIVE_PULSE_MS))
        }
    }

    // The bubble's own box is a size larger than the circle so the badge can sit on the rim without
    // being clipped by it — and so the drag/click target is the whole square, not just the circle.
    Box(
        modifier = Modifier
            .size(BubbleTouchSize)
            // Both rings are drawn in the margin between the circle and the box, so a hold changes
            // nothing about the window's size — an overlay window resize is throttled by the system
            // and would slide the bubble out from under the finger mid-sentence.
            .drawBehind {
                if (!talking) return@drawBehind
                val stroke = LiveRingWidth.toPx()
                val radius = size.minDimension / 2f - stroke
                val diameter = radius * 2f
                // The needle: it follows the microphone, which is the honest answer to "can they
                // hear me?". Silence keeps a faint full ring so the meter reads as idle, not broken.
                drawCircle(
                    color = liveColor.copy(alpha = LIVE_IDLE_ALPHA + LIVE_PULSE_ALPHA * pulse.value),
                    radius = radius,
                    style = Stroke(width = stroke),
                )
                val level = inputLevel.value.coerceIn(0f, 1f)
                if (level > 0.01f) {
                    drawArc(
                        color = liveColor,
                        startAngle = -90f,
                        sweepAngle = 360f * level,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            .pointerInput(Unit) {
                // Hold to talk. This handler waits on its own terms (see [awaitHoldStart]) instead
                // of using Compose's `awaitLongPressOrCancellation`/`waitForUpOrCancellation`: those
                // treat *any* consumed change, or a finger that leaves the node, as a cancellation —
                // and the drag handler next to this one consumes every movement past the touch slop.
                // That is what used to close the microphone mid-sentence.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // A new gesture starts here: nothing from the previous one may keep suppressing
                    // taps or moving the window.
                    dragged = false
                    suppressClick = false
                    windowMoved = false
                    // Only a microphone that starts out closed can be opened by a hold, so a press
                    // can never "restore" a state it did not change.
                    if (!micClosed) return@awaitEachGesture
                    if (!awaitHoldStart(down, dragStarted = { dragged })) return@awaitEachGesture

                    suppressClick = true
                    holdOwnsBubble = true
                    // In the hand, not on the screen: the fastest possible "you are live".
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    latestTalkStart()
                    try {
                        val escape = awaitHoldEnd(down)
                        if (escape != null) {
                            // The finger is dragging the bubble rather than holding it: hand the
                            // gesture over, taking the hold's own movement with it so the bubble
                            // lands under the finger instead of behind it.
                            holdOwnsBubble = false
                            windowMoved = true
                            onDrag(escape.x, escape.y)
                        }
                    } finally {
                        holdOwnsBubble = false
                        // A gesture the system cancels, the window going away and a normal lift all
                        // have to close the microphone.
                        latestTalkStop()
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragged = true },
                    onDragEnd = { if (windowMoved) onDragEnd() },
                    onDragCancel = { if (windowMoved) onDragEnd() },
                ) { change, amount ->
                    change.consume()
                    // A hold owns the bubble until its escape hatch fires. Dropping the movement
                    // here is the point: a hand that trembles while holding a button must not throw
                    // the window across the screen.
                    if (holdOwnsBubble) return@detectDragGestures
                    windowMoved = true
                    onDrag(amount.x, amount.y)
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    if (dragged || suppressClick) {
                        dragged = false
                        suppressClick = false
                    } else {
                        onToggleExpand()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(BubbleSize),
            shape = CircleShape,
            // The launcher icon's own pink, half transparent: the icon stays legible while a game or a
            // desktop keeps showing through. A talking avatar simply covers it.
            color = AppPink.copy(alpha = AppPinkAlpha),
            border = BorderStroke(
                width = when {
                    talking -> 2.5.dp
                    speaking -> 2.dp
                    else -> 1.dp
                },
                color = when {
                    whisperTint -> WhisperAmber
                    talking -> liveColor
                    // The member list's talking colour, in the app as here
                    speaking -> TalkingBlue
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (avatar != null) {
                    Image(
                        bitmap = avatar,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_bubble_headset),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.fillMaxSize(0.78f),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }

        // Outside the circle's clip, so it is drawn whole. The hold has to be unmistakable, so it
        // outranks everything else the badge can say — unread messages are still counted while it
        // is open, they simply wait their turn.
        when {
            talking -> BubbleBadge(
                modifier = Modifier.align(Alignment.BottomEnd),
                color = liveColor,
            ) {
                Icon(
                    imageVector = if (state.micReady) AppIcons.MicOn else AppIcons.MicOff,
                    contentDescription = stringResource(
                        if (state.micReady) R.string.floating_overlay_talking
                        else R.string.floating_overlay_mic_not_working,
                    ),
                    tint = if (state.micReady) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onError
                    },
                    modifier = Modifier.size(12.dp),
                )
            }
            // Unread wins over "muted": the user can hear that they are muted, but not that someone
            // wrote to them.
            state.unread > 0 -> BubbleBadge(
                modifier = Modifier.align(Alignment.BottomEnd),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = if (state.unread > 99) "99+" else state.unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            state.micMuted -> BubbleBadge(
                modifier = Modifier.align(Alignment.BottomEnd),
                color = MaterialTheme.colorScheme.error,
            ) {
                Icon(
                    imageVector = AppIcons.MicOff,
                    contentDescription = stringResource(R.string.floating_overlay_muted),
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

// ── The bubble's gesture ─────────────────────────────────────────────────────────

/**
 * Waits out the long-press timeout that turns a press into push-to-talk.
 *
 * Reads on the [PointerEventPass.Initial] pass and ignores whether the event was consumed, which
 * is the whole point: the drag handler beside this one consumes every movement past the touch
 * slop, and Compose's own `awaitLongPressOrCancellation` reads exactly that consumption as
 * "cancelled".
 *
 * @param dragStarted whether the drag handler has taken the gesture over already. A pointer's
 *   position is measured inside the window the drag is moving, so on its own it cannot always tell
 *   a moving finger from a moving window.
 * @return true when the finger is still down — and still — once the timeout has run out.
 */
private suspend fun AwaitPointerEventScope.awaitHoldStart(
    down: PointerInputChange,
    dragStarted: () -> Boolean,
): Boolean {
    val slop = viewConfiguration.touchSlop
    var released = false
    var moved = false
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (!released && !moved) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || change.changedToUpIgnoreConsumed()) {
                released = true
            } else {
                // Nothing is being transmitted yet, so a finger already on its way somewhere is a
                // drag — exactly as it was before the hold existed.
                moved = dragStarted() ||
                    (change.position - down.position).getDistance() > slop
            }
        }
    }
    return !released && !moved
}

/**
 * Waits for a push-to-talk hold to end.
 *
 * Nothing a hand does while holding a button down may end it: trembling, drifting past the touch
 * slop, wandering off the bubble, the window being clamped against a screen edge. Every one of
 * those used to read as "cancelled" and closed the microphone mid-sentence.
 *
 * The single exception is a finger that has clearly been moved on purpose — that is a drag, and the
 * window has to obey it.
 *
 * @return the movement to hand over to the drag, or null when the hold ended by itself.
 */
private suspend fun AwaitPointerEventScope.awaitHoldEnd(down: PointerInputChange): Offset? {
    val escape = HoldDragEscape.toPx()
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return null
        if (change.changedToUpIgnoreConsumed()) return null
        val moved = change.position - down.position
        if (moved.getDistance() >= escape) return moved
    }
}

@Composable
private fun BubbleBadge(
    modifier: Modifier = Modifier,
    color: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(20.dp),
        shape = CircleShape,
        color = color,
        border = BorderStroke(1.5.dp, overlaySurface()),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

// ── Expanded ─────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandedPanel(
    state: FloatingOverlayState,
    view: OverlayView,
    onViewChange: (OverlayView) -> Unit,
    onToggleExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleOutput: () -> Unit,
    onWhisperTalkStart: () -> Unit,
    onWhisperTalkStop: () -> Unit,
    onOpenApp: () -> Unit,
    onMoveToChannel: (Long, String?) -> Unit,
    onKeyboardRequest: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
) {
    // Three islands, like the app's screens: the server, the members or channels, the controls
    Column(
        modifier = Modifier
            .width(PanelWidth)
            .heightIn(max = PanelMaxHeight),
        verticalArrangement = Arrangement.spacedBy(Spacing.Gap),
    ) {
        OverlayIsland {
            Column {
                PanelHeader(
                    state = state,
                    onToggleExpand = onToggleExpand,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onOpenApp = onOpenApp,
                )
                if (state.reconnecting || state.micUnavailable) {
                    WarningStrip(
                        text = if (state.reconnecting) {
                            stringResource(R.string.reconnect_in_progress, maxOf(state.reconnectAttempt, 1))
                        } else {
                            stringResource(R.string.mic_unavailable_short)
                        },
                    )
                }
            }
        }

        OverlayIsland(modifier = Modifier.weight(1f, fill = false)) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChoicePill(
                        label = stringResource(R.string.floating_overlay_members, state.members.size),
                        selected = view == OverlayView.Members,
                        onClick = { onViewChange(OverlayView.Members) },
                        modifier = Modifier.weight(1f),
                    )
                    ChoicePill(
                        label = stringResource(R.string.floating_overlay_channels),
                        selected = view == OverlayView.Channels,
                        onClick = { onViewChange(OverlayView.Channels) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Box {
                    when (view) {
                        OverlayView.Members -> MembersList(state.members)
                        OverlayView.Channels -> ChannelsList(
                            channels = state.channels,
                            currentChannelId = state.currentChannelId,
                            onMoveToChannel = onMoveToChannel,
                            onKeyboardRequest = onKeyboardRequest,
                        )
                    }
                }
            }
        }

        OverlayIsland {
            ActionBar(
                state = state,
                onToggleMic = onToggleMic,
                onToggleOutput = onToggleOutput,
                onWhisperTalkStart = onWhisperTalkStart,
                onWhisperTalkStop = onWhisperTalkStop,
                onDisconnect = onDisconnect,
            )
        }
    }
}

/** One of the floating window's islands: the app's island corner, on the window's own surface. */
@Composable
private fun OverlayIsland(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Shapes.Medium,
        color = overlaySurface(),
        // Explicit: a translucent surface colour matches no theme colour, so Surface could not
        // derive one and the texts without their own colour (the server name) came out black.
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
private fun PanelHeader(
    state: FloatingOverlayState,
    onToggleExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onOpenApp: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Dragging by the header is the usual way to move a panel; the bubble is dragged whole.
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            }
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(connected = state.connected)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.serverName ?: stringResource(R.string.floating_overlay_offline),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.channelName ?: stringResource(R.string.floating_overlay_no_channel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HeaderIconButton(
            icon = AppIcons.Home,
            contentDescription = stringResource(R.string.floating_overlay_open_app),
            onClick = onOpenApp,
        )
        Spacer(Modifier.width(4.dp))
        HeaderIconButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.floating_overlay_collapse),
            onClick = onToggleExpand,
            iconSize = 20.dp,
        )
    }
}

/** A 32dp icon button with a press colour instead of a ripple (see the note on [FloatingOverlay]). */
@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (pressed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(IconSize),
        )
    }
}

@Composable
private fun StatusDot(connected: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                shape = CircleShape,
            ),
    )
}

@Composable
private fun WarningStrip(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.errorContainer, Shapes.Small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(IconSize),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MembersList(members: List<FloatingOverlayMember>) {
    if (members.isEmpty()) {
        EmptyHint(stringResource(R.string.floating_overlay_alone))
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ListMaxHeight)
            .padding(vertical = 2.dp),
    ) {
        items(members, key = { it.id }) { member ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .then(
                            if (member.speaking) {
                                Modifier.background(TalkingBlue, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (member.avatar != null) {
                        Image(
                            bitmap = member.avatar,
                            contentDescription = null,
                            modifier = Modifier
                                .size(if (member.speaking) 19.dp else 24.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = member.nickname,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (member.speaking) {
                        TalkingBlue
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (member.micMuted) {
                    Icon(
                        imageVector = AppIcons.MicOff,
                        contentDescription = stringResource(R.string.floating_overlay_muted),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(IconSize),
                    )
                }
                if (member.outputMuted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = AppIcons.SpeakerOff,
                        contentDescription = stringResource(R.string.floating_overlay_deafened),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(IconSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelsList(
    channels: List<FloatingOverlayChannel>,
    currentChannelId: Long,
    onMoveToChannel: (Long, String?) -> Unit,
    onKeyboardRequest: (Boolean) -> Unit,
) {
    if (channels.isEmpty()) {
        EmptyHint(stringResource(R.string.floating_overlay_no_channels))
        return
    }
    // The channel waiting for its password. An inline field instead of a dialog:
    // this panel lives in a WRAP_CONTENT overlay window, where a full-screen
    // dialog would be clipped to the window.
    var passwordChannel by remember { mutableStateOf<FloatingOverlayChannel?>(null) }
    var password by remember { mutableStateOf("") }
    val passwordFocus = remember { FocusRequester() }

    // The overlay window is not focusable by default and cannot receive the IME
    // at all. Ask the service to make it focusable while the prompt is open,
    // then focus the field so the keyboard comes up on its own.
    LaunchedEffect(passwordChannel != null) {
        val open = passwordChannel != null
        onKeyboardRequest(open)
        if (open) {
            // The window has to be focusable first; focusing in the same frame
            // is too early and the IME would not attach.
            delay(120)
            passwordFocus.requestFocus()
        }
    }
    // Leaving this list (panel collapsed, other tab) must hand the keyboard back.
    DisposableEffect(Unit) {
        onDispose { onKeyboardRequest(false) }
    }

    passwordChannel?.let { channel ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.channel_password_title, channel.name),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.channel_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    if (password.isNotEmpty()) {
                        onMoveToChannel(channel.id, password)
                        passwordChannel = null
                        password = ""
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocus),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    passwordChannel = null
                    password = ""
                }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onMoveToChannel(channel.id, password)
                        passwordChannel = null
                        password = ""
                    },
                    enabled = password.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.channel_join))
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ListMaxHeight)
            .padding(vertical = 2.dp),
    ) {
        items(channels, key = { it.id }) { channel ->
            val current = channel.id == currentChannelId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !current) {
                        if (channel.hasPassword) {
                            passwordChannel = channel
                            password = ""
                        } else {
                            onMoveToChannel(channel.id, null)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AppIcons.Channel,
                    contentDescription = null,
                    tint = if (current) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(IconSize),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (current) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (channel.hasPassword) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = AppIcons.Lock,
                        contentDescription = stringResource(R.string.channel_password_required),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize),
                    )
                }
                if (current) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
    )
}

/**
 * The panel's actions: microphone, sound, disconnect.
 *
 * One microphone control only — the push-to-talk duplicate was confusing, and the toggle it sat next
 * to does the same job. Disconnecting takes a second tap: the button sits among the others in a
 * small window that is easy to brush past.
 */
@Composable
private fun ActionBar(
    state: FloatingOverlayState,
    onToggleMic: () -> Unit,
    onToggleOutput: () -> Unit,
    onWhisperTalkStart: () -> Unit,
    onWhisperTalkStop: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var confirmDisconnect by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDisconnect) {
        if (confirmDisconnect) {
            delay(DISCONNECT_CONFIRM_MS)
            confirmDisconnect = false
        }
    }
    if (confirmDisconnect) {
        WarningStrip(text = stringResource(R.string.floating_overlay_tap_again_to_disconnect))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            icon = micIcon(state.micMuted),
            contentDescription = micAction(state.micMuted, state.outputMuted),
            active = state.micMuted,
            onClick = onToggleMic,
        )
        // Hold-to-whisper: the same gesture as the bubble's push-to-talk, but the
        // voice only reaches the whisper list. It stays usable while the normal
        // microphone is muted, because the hold opens the microphone itself.
        HoldActionButton(
            icon = AppIcons.Whisper,
            contentDescription = stringResource(R.string.whisper_hold_mic),
            holds = state.localWhisperActive,
            onHold = onWhisperTalkStart,
            onRelease = onWhisperTalkStop,
        )
        ActionButton(
            icon = speakerIcon(state.outputMuted),
            contentDescription = speakerAction(state.outputMuted),
            active = state.outputMuted,
            onClick = onToggleOutput,
        )
        // Red, as a warning, but filled only once a first tap has armed it: a filled
        // button is what a muted microphone or speaker looks like
        ActionButton(
            icon = AppIcons.Exit,
            contentDescription = stringResource(R.string.disconnect),
            active = confirmDisconnect,
            tint = MaterialTheme.colorScheme.error,
            onClick = {
                if (confirmDisconnect) onDisconnect() else confirmDisconnect = true
            },
        )
    }
}

/** A 42dp button that is held rather than tapped; used for hold-to-whisper. */
@Composable
private fun HoldActionButton(
    icon: ImageVector,
    contentDescription: String,
    holds: Boolean,
    onHold: () -> Unit,
    onRelease: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val active = holds || pressed
    // The gesture handler is created once; read the newest callbacks through
    // these references so a recomposition with new lambdas still takes effect.
    val latestHold by rememberUpdatedState(onHold)
    val latestRelease by rememberUpdatedState(onRelease)
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        latestHold()
                        tryAwaitRelease()
                        latestRelease()
                        pressed = false
                    },
                )
            },
        shape = CircleShape,
        color = if (active) {
            WhisperAmber.copy(alpha = if (pressed) 0.34f else 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) WhisperAmber else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(IconSize),
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    /** The icon's colour while not active */
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = CircleShape,
        color = when {
            active -> MaterialTheme.colorScheme.error.copy(alpha = if (pressed) 0.34f else 0.22f)
            pressed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) MaterialTheme.colorScheme.error else tint,
                modifier = Modifier.size(IconSize),
            )
        }
    }
}

// ── Shared ───────────────────────────────────────────────────────────────────────

private val BubbleSize = 46.dp

/** The bubble's touch/total box: the circle plus room for the badge on its rim. */
private val BubbleTouchSize = 56.dp

/** The live ring and the level arc both live in the margin that box leaves around the circle. */
private val LiveRingWidth = 3.dp

/** Half of the live ring's breathing period, in milliseconds. */
private const val LIVE_PULSE_MS = 750

/** A silent hold still shows a faint ring — an empty meter that flickers reads as a broken one. */
private const val LIVE_IDLE_ALPHA = 0.16f

/** How much louder the ring gets at the top of each breath. */
private const val LIVE_PULSE_ALPHA = 0.18f

/**
 * How far a holding finger has to travel before the bubble is being dragged rather than held.
 *
 * Comfortably above the touch slop (≈8dp) and above what a hand does while holding a button down,
 * so a tremor can no longer cut the microphone — while a deliberate drag still moves the window.
 */
private val HoldDragEscape = 24.dp

private val PanelWidth = 264.dp
private val PanelMaxHeight = 360.dp
private val ListMaxHeight = 200.dp

/** How long the disconnect button waits for its second tap. */
private const val DISCONNECT_CONFIRM_MS = 3_000L

/** How long expanding/collapsing takes. Must match the anchor window in the service. */
private const val REVEAL_ANIMATION_MS = 200

/** How long the bubble takes to fade back in once the window has shrunk to it. */
private const val REVEAL_FADE_IN_MS = 120

/** The panel grows into place from this much of its size. */
private const val REVEAL_MIN_SCALE = 0.88f

/**
 * The bubble's colour while nobody is talking (the original app icon's background; the launcher icon
 * is now a picture).
 */
private val AppPink = Color(0xFFFF69B4)

/** Half transparent on purpose: the bubble must not hide much of what is behind it. */
private const val AppPinkAlpha = 0.5f

/**
 * The overlay's surface colour: a translucent version of the app's own surface, so the window follows
 * the system theme while still letting a game or a desktop show through at its edges.
 */
@Composable
private fun overlaySurface(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
