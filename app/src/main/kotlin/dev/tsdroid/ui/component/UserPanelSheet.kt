package dev.tsdroid.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tsdroid.bridge.LiveStream
import dev.tsdroid.han.R
import dev.tsdroid.service.UserGroups
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.Shapes
import dev.tslib.ChannelGroup
import dev.tslib.ServerGroup
import dev.tslib.User
import kotlin.math.roundToInt

@Composable
fun UserPanelSheet(
    user: User?,
    avatar: ImageBitmap?,
    volumeDb: Float,
    isMuted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onVolumeCommit: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onPrivateMessage: () -> Unit,
    onDismiss: () -> Unit,
    /** True while this member is on the whisper target list. */
    isWhisperTarget: Boolean = false,
    onToggleWhisper: (() -> Unit)? = null,
    /**
     * True when the panel describes the local user. The own row can be opened to
     * read its groups, but volume, mute, private message and whisper are all
     * meaningless towards yourself, so they are left out.
     */
    isSelf: Boolean = false,
    /** Server groups the connection knows (id + name), for naming [user]'s groups. */
    knownServerGroups: List<ServerGroup> = emptyList(),
    /** Channel groups the connection knows (id + name), for [User.getChannelGroup]. */
    knownChannelGroups: List<ChannelGroup> = emptyList(),
    /** Self panel only: open the temporary-nickname dialog. */
    onChangeNickname: (() -> Unit)? = null,
    /** Self panel only: open the status sheet (在线 / 离开 / 勿扰). */
    onChangeStatus: (() -> Unit)? = null,
    /** Backdrop for the frosted panel, so the sheet matches the cards behind it. */
    wallpaper: ImageBitmap? = null,
    /** [user]'s streams in our channel; a member can share several screens or windows. */
    streams: List<LiveStream> = emptyList(),
    onWatchStream: ((LiveStream) -> Unit)? = null,
) {
    if (user == null) return

    // Friends open this panel to turn someone up or down, so the controls come first and the
    // technical details (unique ID, groups, versions) fold away underneath. Your own panel exists to
    // read those details, so it shows them straight away.
    var detailsOpen by remember(user.id) { mutableStateOf(isSelf) }

    GlassBottomSheet(onDismissRequest = onDismiss, wallpaper = wallpaper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelAvatar(user.nickname, avatar)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = statusLine(user),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelf) {
                Text(
                    text = stringResource(R.string.user_panel_self),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        user.awayMessage?.takeIf { it.isNotBlank() }?.let { away ->
            Spacer(Modifier.height(6.dp))
            UserInfoRow(stringResource(R.string.away_message_label), away)
        }

        if (isSelf) {
            Spacer(Modifier.height(6.dp))
            // Renaming is a self action (clientupdate on our own client), so it
            // belongs here rather than on someone else's panel.
            if (onChangeNickname != null) {
                TextButton(onClick = onChangeNickname) {
                    Text(stringResource(R.string.self_change_nickname))
                }
            }
            // Same reasoning for the status: only your own client can set it.
            if (onChangeStatus != null) {
                TextButton(onClick = onChangeStatus) {
                    Text(stringResource(R.string.away_status_change))
                }
            }
        } else {
            if (onWatchStream != null) {
                streams.forEach { stream ->
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        shape = Shapes.Small,
                        onClick = { onWatchStream(stream) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (stream.name.isBlank()) stringResource(R.string.stream_watch)
                            else stringResource(R.string.stream_watch_named, streamDisplayName(stream.name))
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )

            VolumeSlider(
                initialDb = volumeDb,
                defaultLabel = stringResource(R.string.volume_default),
                onVolumeChange = onVolumeChange,
                onVolumeCommit = onVolumeCommit,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(if (isMuted) R.string.unmute_user else R.string.mute_user),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = isMuted, onCheckedChange = { onToggleMute() })
            }

            // 密聊 switch: the panel doorway next to the inline button on the row,
            // with a label that spells out what whispering does to the microphone.
            if (onToggleWhisper != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.whisper_switch_label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = isWhisperTarget, onCheckedChange = { onToggleWhisper() })
                }
            }

            Spacer(Modifier.height(8.dp))
            FilledTonalButton(shape = Shapes.Small, onClick = onPrivateMessage, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.private_message))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { detailsOpen = !detailsOpen }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.user_details),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            // Turns like an island's chevron
            val turn by animateFloatAsState(if (detailsOpen) 0f else -90f, label = "detailsChevron")
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(IconSize).rotate(turn),
            )
        }
        AnimatedVisibility(
            visible = detailsOpen,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column { UserDetails(user, knownServerGroups, knownChannelGroups) }
        }
    }
}

/** "Online · Microphone off · Speakers off": the status the member list shows as icons, in words. */
@Composable
private fun statusLine(user: User): String = buildList {
    add(stringResource(if (user.isAway) R.string.away_status_away else R.string.away_status_online))
    if (user.isInputMuted || !user.hasInputHardware) add(stringResource(R.string.status_mic_off))
    if (user.isOutputMuted) add(stringResource(R.string.status_speakers_off))
}.joinToString(" · ")

@Composable
private fun UserDetails(
    user: User,
    knownServerGroups: List<ServerGroup>,
    knownChannelGroups: List<ChannelGroup>,
) {
    UserInfoRow(stringResource(R.string.user_uid), user.uid ?: "")
    UserInfoRow(stringResource(R.string.user_platform), user.platform ?: "")
    UserInfoRow(stringResource(R.string.user_version), user.version ?: "")
    UserInfoRow(stringResource(R.string.user_country), user.country ?: "")
    UserInfoRow(stringResource(R.string.user_talkpower), user.talkPower.toString())

    // 服务器组: the groups this member holds. Long-pressing the own row shows
    // the same list for yourself, which is how a user checks their own rights.
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.user_server_groups) +
            if (user.serverGroups.isNotEmpty()) " (${user.serverGroups.size})" else "",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(2.dp))
    val groups = UserGroups.displayOrder(user.serverGroups.toList(), knownServerGroups)
    if (groups.isEmpty()) {
        Text(
            text = stringResource(R.string.user_server_groups_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        // A member can hold a lot of groups; the sheet itself does not scroll,
        // so this list gets its own bounded scroll area instead of pushing the
        // controls off the screen.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            groups.forEach { (id, name) ->
                Text(
                    text = name ?: stringResource(R.string.user_server_group_unknown, id),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }

    // 频道组: exactly one, and it is the one held in the channel the member is
    // in right now — which is how the server reports it.
    UserGroups.channelGroupOf(user.channelGroup, knownChannelGroups)?.let { (id, name) ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.user_channel_group),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = name ?: stringResource(R.string.user_channel_group_unknown, id),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 1.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PanelAvatar(nickname: String, avatar: ImageBitmap?) {
    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            // The same colour as in the member list
            val bgColor = avatarColorFor(nickname)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = nickname.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun UserInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VolumeSlider(
    initialDb: Float,
    defaultLabel: String,
    onVolumeChange: (Float) -> Unit,
    onVolumeCommit: (Float) -> Unit,
) {
    var db by remember(initialDb) { mutableFloatStateOf(initialDb) }
    Column {
        Text(
            text = stringResource(R.string.user_volume) + " " + formatDb(db, defaultLabel),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = db,
            onValueChange = {
                db = it
                onVolumeChange(it)
            },
            onValueChangeFinished = { onVolumeCommit(db) },
            valueRange = -30f..15f,
            steps = 44,
        )
        if (db.roundToInt() != 0) {
            TextButton(onClick = {
                db = 0f
                onVolumeChange(0f)
                onVolumeCommit(0f)
            }) {
                Text(stringResource(R.string.volume_reset))
            }
        }
    }
}

private fun formatDb(db: Float, defaultLabel: String): String {
    val rounded = db.roundToInt()
    return if (rounded == 0) {
        "0 dB ($defaultLabel)"
    } else {
        (if (rounded > 0) "+$rounded" else "$rounded") + " dB"
    }
}
