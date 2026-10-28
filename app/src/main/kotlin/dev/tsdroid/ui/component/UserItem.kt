package dev.tsdroid.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tsdroid.han.R
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.AlertRed
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.MutedOrange
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.TalkingBlue
import dev.tsdroid.ui.theme.WhisperAmber
import dev.tslib.User
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserItem(
    user: User,
    avatar: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onToggleMute: (() -> Unit)? = null,
    onToggleWhisper: (() -> Unit)? = null,
    isLocallyMuted: Boolean = false,
    /** This member is on the whisper (密聊) target list. */
    isWhisperTarget: Boolean = false,
    /** This member's current talk burst arrived as a whisper packet. */
    isWhisperTalking: Boolean = false,
    /** The local microphone is on a whisper target set right now. */
    isWhisperModeActive: Boolean = false,
    isSelf: Boolean = false,
    /** This member is streaming in our channel; tapping the badge watches it. */
    isStreaming: Boolean = false,
    onWatchStream: (() -> Unit)? = null,
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = { onLongClick?.invoke() },
                )
                .padding(vertical = 3.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarWithRing(
                avatar = avatar,
                nickname = user.nickname,
                isTalking = user.isTalking,
                // Own row: the server never echoes our talk status back, so the
                // ring runs on local VAD — mark it amber while密聊 is on, which
                // is exactly when the microphone is going somewhere else.
                isWhisperTalking = isWhisperTalking || (isSelf && isWhisperModeActive),
                isRecording = user.isRecording,
            )

            Spacer(Modifier.width(8.dp))

            // The name, then what the member has switched off: the icons used to sit in the
            // avatar's corners, too small to read and easy to take for part of the picture.
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = user.nickname,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    color = when {
                        user.isTalking -> MaterialTheme.colorScheme.primary
                        user.isAway -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                if (isSelf) {
                    Text(
                        text = " " + stringResource(R.string.self_marker),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (user.isInputMuted || !user.hasInputHardware) {
                    StatusIcon(AppIcons.MicOff, stringResource(R.string.status_mic_off))
                }
                if (user.isOutputMuted) {
                    StatusIcon(AppIcons.SpeakerOff, stringResource(R.string.status_speakers_off))
                }
            }

            if (isStreaming) {
                Text(
                    text = stringResource(R.string.stream_live),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(Shapes.Small)
                        .background(AlertRed)
                        .clickable(enabled = onWatchStream != null) { onWatchStream?.invoke() }
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
                Spacer(Modifier.width(4.dp))
            }

            // Detail status icons on the right
            if (user.isRecording) {
                Icon(
                    Icons.Default.RadioButtonChecked,
                    contentDescription = stringResource(R.string.status_recording),
                    modifier = Modifier.size(IconSize),
                    tint = AlertRed,
                )
                Spacer(Modifier.width(2.dp))
            }
            if (user.isAway) {
                Icon(
                    Icons.Default.NightsStay,
                    contentDescription = stringResource(R.string.status_away),
                    modifier = Modifier.size(IconSize),
                    // The colour of the away dot in the status sheet
                    tint = WhisperAmber,
                )
                Spacer(Modifier.width(2.dp))
            }
            
            // Inline whisper toggle. Whispering to yourself is meaningless, so
            // the own row never shows it.
            if (onToggleWhisper != null && !isSelf) {
                IconButton(
                    onClick = onToggleWhisper,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        AppIcons.Whisper,
                        contentDescription = stringResource(
                            if (isWhisperTarget) R.string.whisper_remove_target else R.string.whisper_add_target,
                        ),
                        modifier = Modifier.size(IconSize),
                        tint = if (isWhisperTarget) WhisperAmber
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            // Inline mute toggle button instead of long press menu
            if (onToggleMute != null && !isSelf) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        AppIcons.SpeakerOff,
                        contentDescription = stringResource(if (isLocallyMuted) R.string.unmute_user else R.string.mute_user),
                        modifier = Modifier.size(IconSize),
                        tint = if (isLocallyMuted) MutedOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            } else if (isLocallyMuted) {
                Icon(
                    AppIcons.SpeakerOff,
                    contentDescription = stringResource(R.string.user_muted),
                    modifier = Modifier.size(IconSize),
                    tint = MutedOrange,
                )
                Spacer(Modifier.width(2.dp))
            }
        }
    }
}

private val defaultAvatarColors = listOf(
    Color(0xFF5C6BC0), // indigo
    Color(0xFF26A69A), // teal
    Color(0xFFEF5350), // red
    Color(0xFFAB47BC), // purple
    Color(0xFF42A5F5), // blue
    Color(0xFFFF7043), // deep orange
    Color(0xFF66BB6A), // green
    Color(0xFFEC407A), // pink
)

/** The background of the letter shown for a member without an avatar. */
internal fun avatarColorFor(nickname: String): Color =
    defaultAvatarColors[nickname.hashCode().absoluteValue % defaultAvatarColors.size]

@Composable
private fun StatusIcon(icon: ImageVector, description: String) {
    Icon(
        icon,
        contentDescription = description,
        modifier = Modifier
            .padding(start = 6.dp)
            .size(IconSize),
        tint = AlertRed,
    )
}

@Composable
private fun AvatarWithRing(
    avatar: ImageBitmap?,
    nickname: String,
    isTalking: Boolean,
    isRecording: Boolean,
    isWhisperTalking: Boolean = false,
) {
    val ringActive = isTalking || isRecording
    // Amber marks a whisper talker, matching the floating bubble's ring, so a
    // private voice is never read as plain channel talk.
    val ringColor = when {
        isRecording -> AlertRed
        isTalking && isWhisperTalking -> WhisperAmber
        isTalking -> TalkingBlue
        else -> Color.Transparent
    }

    val ringModifier = if (ringActive) Modifier.border(
        width = 2.dp,
        color = ringColor,
        shape = CircleShape,
    ) else Modifier

    Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .then(ringModifier),
                contentScale = ContentScale.Crop,
            )
        } else {
            val bgColor = avatarColorFor(nickname)
            val letter = nickname.firstOrNull()?.uppercase() ?: "?"
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .then(ringModifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
