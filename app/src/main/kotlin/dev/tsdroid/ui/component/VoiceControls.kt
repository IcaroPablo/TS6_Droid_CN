package dev.tsdroid.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tsdroid.data.AwayStatus
import dev.tsdroid.han.R
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.InsetPadding
import dev.tsdroid.ui.theme.WhisperAmber

/*
 * How a voice button looks, wherever it is: the voice bar (server screen and stream viewer) and
 * the floating window all build theirs from these. Every icon shows the current state (a crossed-out
 * microphone while muted, a crossed-out speaker while deafened, the mode in use), never the action
 * a tap would take; that is left to the description read out by accessibility services. Whatever
 * is off is drawn in the error colour.
 */

fun micIcon(muted: Boolean): ImageVector = if (muted) AppIcons.MicOff else AppIcons.MicOn

fun speakerIcon(muted: Boolean): ImageVector = if (muted) AppIcons.SpeakerOff else AppIcons.SpeakerOn

/** What tapping the microphone does; while deafened it brings the speakers back as well. */
@Composable
fun micAction(muted: Boolean, deafened: Boolean): String = stringResource(
    when {
        muted && deafened -> R.string.voice_undeafen
        muted -> R.string.unmute_mic
        else -> R.string.mute_mic
    },
)

@Composable
fun speakerAction(muted: Boolean): String =
    stringResource(if (muted) R.string.voice_undeafen else R.string.voice_deafen)

/** The error colour for something switched off, [on] otherwise. */
@Composable
fun voiceTint(off: Boolean, on: Color): Color = if (off) MaterialTheme.colorScheme.error else on

/** Everything [VoiceBar] shows and does; built by each screen that has one. */
class VoiceBarState(
    val isMicMuted: Boolean,
    val isOutputMuted: Boolean,
    /** Push-to-talk: the microphone button is held to talk */
    val isPushToTalk: Boolean,
    val onToggleMic: () -> Unit,
    val onPushToTalk: (pressed: Boolean) -> Unit,
    val onToggleOutput: () -> Unit,
    /** Switches between push to talk and an open microphone */
    val onToggleMode: () -> Unit,
    /** Whether the whisper list has anyone on it */
    val whisperReady: Boolean,
    val onWhisperHold: () -> Unit,
    val onWhisperRelease: () -> Unit,
    /** Whether a hold-to-whisper is on, from whichever button started it */
    val whisperHolding: Boolean = false,
    /** Opens the whisper list; left out where it cannot open (over a full-screen stream) */
    val onWhisperList: (() -> Unit)? = null,
    /** Our status, and the way to change it where its sheet can open */
    val awayStatus: AwayStatus? = null,
    val onOpenStatus: (() -> Unit)? = null,
)

/** The size of every button on the voice bar: one row of equal circles. */
private val VoiceButtonSize = 44.dp

/**
 * The voice controls, as one island: status, hold-to-whisper, the microphone, the microphone mode
 * and the speakers. The server screen and the stream viewer both show this same bar.
 */
@Composable
fun VoiceBar(
    state: VoiceBarState,
    modifier: Modifier = Modifier,
    /** As an island of its own (over a full-screen stream) rather than a section of a tab */
    island: Boolean = false,
    /** Unfolded above the buttons: the status or the whisper list */
    panel: (@Composable () -> Unit)? = null,
) {
    // The last panel stays drawn while it folds away
    var shownPanel by remember { mutableStateOf(panel) }
    if (panel != null) shownPanel = panel
    val bar: @Composable (@Composable ColumnScope.() -> Unit) -> Unit = { content ->
        if (island) Island(modifier = modifier, wallpaper = null, padded = false, content = content)
        else InsetSection(modifier = modifier, padded = false, content = content)
    }
    bar {
        AnimatedVisibility(
            visible = panel != null,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            shownPanel?.invoke()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InsetPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Where its sheet cannot open (over the stream viewer) the dot still shows the status
            val status = state.awayStatus
            if (status != null) {
                VoiceButton(onClick = state.onOpenStatus) {
                    // The current status as a dot, as in the status sheet
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(awayStatusColor(status), CircleShape),
                    )
                }
            }
            // The whisper list, next to the button that talks to it; amber while it has anyone
            state.onWhisperList?.let { open ->
                VoiceButton(onClick = open) {
                    Icon(
                        AppIcons.Whisper,
                        contentDescription = stringResource(R.string.whisper_list_title),
                        modifier = Modifier.size(IconSize),
                        tint = if (state.whisperReady) WhisperAmber else LocalContentColor.current,
                    )
                }
            }
            WhisperHoldButton(state)
            MicButton(state)
            VoiceButton(onClick = state.onToggleMode) {
                // The current mode, like every icon on the bar shows the current state: a hand for
                // push to talk, a talking head for the open microphone
                Icon(
                    if (state.isPushToTalk) Icons.Default.TouchApp else Icons.Default.RecordVoiceOver,
                    contentDescription = stringResource(
                        R.string.mic_mode_switch_to,
                        stringResource(if (state.isPushToTalk) R.string.mic_mode_open else R.string.push_to_talk),
                    ),
                    modifier = Modifier.size(IconSize),
                )
            }
            SpeakerButton(state)
        }
    }
}

/**
 * A round voice button; [highlight] fills it, for the microphone. The voice bar and the floating
 * window build theirs from this. A press shades it instead of a ripple: in the floating window, a
 * window over other apps, ripples showed as grey squares.
 */
@Composable
fun VoiceButton(
    onClick: (() -> Unit)?,
    highlight: Color = Color.Transparent,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(VoiceButtonSize)
            .clip(CircleShape)
            .background(highlight)
            .background(if (pressed) LocalContentColor.current.copy(alpha = 0.12f) else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** The speakers: red and crossed out while deafened. */
@Composable
fun SpeakerButton(state: VoiceBarState) {
    VoiceButton(onClick = state.onToggleOutput) {
        Icon(
            speakerIcon(state.isOutputMuted),
            contentDescription = speakerAction(state.isOutputMuted),
            tint = voiceTint(off = state.isOutputMuted, on = LocalContentColor.current),
            modifier = Modifier.size(IconSize),
        )
    }
}

/**
 * The microphone: a tap mutes and unmutes, or, in push to talk, holding it talks. It is the one
 * filled button on the bar, red while muted.
 */
@Composable
fun MicButton(state: VoiceBarState) {
    var pressed by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(state)
    val off = state.isMicMuted && !pressed
    val fill = when {
        pressed -> MaterialTheme.colorScheme.primary
        off -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val tint = when {
        pressed -> MaterialTheme.colorScheme.onPrimary
        off -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    VoiceButton(
        onClick = if (state.isPushToTalk) null else state.onToggleMic,
        highlight = fill,
        modifier = if (state.isPushToTalk) {
            Modifier.pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    latest.onPushToTalk(true)
                    // A button that leaves the screen mid-press cancels this wait: release anyway,
                    // or the microphone stays open with nobody holding it
                    try {
                        tryAwaitRelease()
                    } finally {
                        latest.onPushToTalk(false)
                        pressed = false
                    }
                })
            }
        } else {
            Modifier
        },
    ) {
        Icon(
            micIcon(off),
            contentDescription = if (state.isPushToTalk) {
                stringResource(R.string.push_to_talk)
            } else {
                micAction(state.isMicMuted, state.isOutputMuted)
            },
            tint = tint,
            modifier = Modifier.size(IconSize),
        )
    }
}

/**
 * Hold-to-whisper: a second push to talk that only reaches the whisper list, so nothing has to be
 * switched on beforehand. Amber, like everything about whispering.
 */
@Composable
fun WhisperHoldButton(state: VoiceBarState) {
    var pressed by remember { mutableStateOf(false) }
    // `pointerInput(Unit)` keeps the lambda it was created with, so the newest
    // callbacks have to be read through this reference on every press.
    val latest by rememberUpdatedState(state)
    val tint = if (state.whisperReady) WhisperAmber else LocalContentColor.current.copy(alpha = 0.35f)
    VoiceButton(
        onClick = null,
        // Lit while the whisper hold is on, not just while pressed: a hold that outlived its
        // press is then visible instead of whispering behind the user's back
        highlight = if (pressed || state.whisperHolding) WhisperAmber.copy(alpha = 0.2f) else Color.Transparent,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                pressed = true
                latest.onWhisperHold()
                // Released even when the button leaves the screen mid-press (see MicButton)
                try {
                    tryAwaitRelease()
                } finally {
                    latest.onWhisperRelease()
                    pressed = false
                }
            })
        },
    ) {
        Box {
            Icon(
                AppIcons.MicOn,
                contentDescription = stringResource(R.string.whisper_hold_mic),
                tint = tint,
                modifier = Modifier.size(IconSize),
            )
            Icon(
                AppIcons.Whisper,
                contentDescription = null,
                modifier = Modifier
                    .size(11.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 2.dp),
                tint = tint,
            )
        }
    }
}
