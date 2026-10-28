package dev.tsdroid.ui.component

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tsdroid.data.PrivateConversations
import dev.tsdroid.han.R
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.Corners
import dev.tsdroid.ui.theme.InsetFill
import dev.tsdroid.ui.theme.InsetPadding
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.Spacing
import dev.tsdroid.ui.theme.WhisperAmber
import dev.tslib.Channel
import dev.tslib.ChannelTree as JChannelTree
import dev.tslib.User

sealed class TreeItem {
    data class ChannelNode(val channel: Channel, val depth: Int) : TreeItem()
    data class UserNode(val user: User, val depth: Int) : TreeItem()
}

@Composable
fun ChannelTree(
    channels: List<Channel>,
    users: List<User>,
    onChannelClick: (Long) -> Unit,
    onUserClick: ((User) -> Unit)? = null,
    onUserLongClick: ((User) -> Unit)? = null,
    onUserLongPress: ((User) -> Unit)? = null,
    onChannelLongPress: ((Channel) -> Unit)? = null,
    onWhisperClick: ((User) -> Unit)? = null,
    mutedUserIds: Set<Int> = emptySet(),
    whisperChannelIds: Set<Long> = emptySet(),
    whisperTargetKeys: Set<String> = emptySet(),
    whisperTalkingUserIds: Set<Int> = emptySet(),
    whisperModeActive: Boolean = false,
    channelIcons: Map<Long, ImageBitmap> = emptyMap(),
    userAvatars: Map<String, ImageBitmap> = emptyMap(),
    selfId: Int? = null,
    /** Members streaming in our channel, and what tapping their badge does. */
    streamingUserIds: Set<Int> = emptySet(),
    onWatchStream: ((User) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Filter nulls early — JNI arrays can contain null elements
    @Suppress("USELESS_CAST")
    val safeUsers = remember(users) { users.filterNotNull() }
    val treeItems = remember(channels, safeUsers) {
        val items = buildTreeItems(channels, safeUsers)
        Log.d("ChannelTree", "Built ${items.size} tree items (${items.count { it is TreeItem.ChannelNode }} channels, ${items.count { it is TreeItem.UserNode }} users) from ${channels.size} channels, ${safeUsers.size} users")
        items
    }
    val userCountByChannel = remember(safeUsers) {
        safeUsers.groupingBy { it.channelId }.eachCount()
    }
    val selfChannelId = safeUsers.firstOrNull { it.id == selfId }?.channelId

    // Each channel and its members share an area of their own: where each item sits in it decides
    // which of its corners are rounded. Spacer channels are decoration and get no area.
    val sections = remember(treeItems) { sectionPositions(treeItems) }

    LazyColumn(modifier = modifier) {
        itemsIndexed(treeItems, key = { _, item ->
            when (item) {
                is TreeItem.ChannelNode -> "ch_${item.channel.id}"
                is TreeItem.UserNode -> "usr_${item.user.id}"
            }
        }) { index, item ->
            val position = sections[index]
            // Members joining, leaving and moving between channels slide into place
            Box(
                modifier = Modifier
                    .animateItem()
                    // The island's gap between one channel's area and the next
                    .padding(top = if (position.first && index > 0) Spacing.Gap else 0.dp)
                    .then(
                        if (position.boxed) Modifier.background(InsetFill, position.shape()) else Modifier,
                    )
                    .padding(horizontal = InsetPadding),
            ) {
            when (item) {
                is TreeItem.ChannelNode -> ChannelRow(
                    channel = item.channel,
                    depth = item.depth,
                    userCount = userCountByChannel[item.channel.id] ?: 0,
                    onClick = { onChannelClick(item.channel.id) },
                    onLongClick = onChannelLongPress?.let { { it(item.channel) } },
                    isWhisperTarget = item.channel.id in whisperChannelIds,
                    isCurrent = item.channel.id == selfChannelId,
                    icon = channelIcons[item.channel.iconId],
                )
                is TreeItem.UserNode -> UserItem(
                    user = item.user,
                    avatar = item.user.uid?.let { userAvatars[it] },
                    // Under the channel's name rather than a further step in: the avatar starts where
                    // the name does (a channel row's icon and its gap are 32dp)
                    modifier = Modifier.fillMaxWidth().padding(start = ((item.depth - 1) * 24 + 28).dp),
                    onClick = onUserClick?.let { { it(item.user) } },
                    onLongClick = onUserLongPress?.let { { it(item.user) } },
                    onToggleMute = onUserLongClick?.let { { it(item.user) } },
                    onToggleWhisper = onWhisperClick?.let { { it(item.user) } },
                    isLocallyMuted = item.user.id in mutedUserIds,
                    isWhisperTarget = PrivateConversations.keyFor(item.user.uid, item.user.id) in whisperTargetKeys,
                    isWhisperTalking = item.user.id in whisperTalkingUserIds,
                    isWhisperModeActive = whisperModeActive,
                    isSelf = item.user.id == selfId,
                    isStreaming = item.user.id in streamingUserIds,
                    onWatchStream = onWatchStream?.let { { it(item.user) } },
                )
            }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: Channel,
    depth: Int,
    userCount: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isWhisperTarget: Boolean = false,
    /** The channel we are in, which stands out from the rest. */
    isCurrent: Boolean = false,
    icon: ImageBitmap? = null,
) {
    val spacer = remember(channel.name) { parseSpacer(channel.name) }

    if (spacer != null) {
        when (spacer.type) {
            SpacerType.REPEAT -> {
                HorizontalDivider(
                    modifier = Modifier.padding(
                        start = (depth * 24).dp, top = 4.dp, bottom = 4.dp, end = 8.dp,
                    ),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            else -> {
                val align = when (spacer.type) {
                    SpacerType.CENTER -> TextAlign.Center
                    SpacerType.RIGHT -> TextAlign.End
                    else -> TextAlign.Start
                }
                val hasText = spacer.displayText.isNotEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = (depth * 24).dp,
                            top = if (hasText) 4.dp else 2.dp,
                            bottom = if (hasText) 4.dp else 2.dp,
                            end = 8.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = spacer.displayText.ifEmpty { " " },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = align,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(
                    start = (depth * 24).dp,
                    top = 6.dp,
                    bottom = 6.dp,
                    end = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelIcon(
                icon = icon,
                userCount = userCount,
                maxClients = channel.maxClients,
                isCurrent = isCurrent,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                modifier = Modifier.weight(1f),
            )
            // Where newcomers land, marked as the desktop client marks it
            if (channel.isDefault) {
                Icon(
                    AppIcons.Home,
                    contentDescription = stringResource(R.string.channel_default),
                    modifier = Modifier.size(IconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
            }
            // Whole-channel whisper target, marked like the member rows
            if (isWhisperTarget) {
                Icon(
                    AppIcons.Whisper,
                    contentDescription = stringResource(R.string.whisper_channel_target),
                    modifier = Modifier.size(IconSize),
                    tint = WhisperAmber,
                )
                Spacer(Modifier.width(4.dp))
            }
            // A protected channel needs its password before the server accepts a
            // move, so the row says so before the tap.
            if (channel.hasPassword) {
                Icon(
                    AppIcons.Lock,
                    contentDescription = stringResource(R.string.channel_password_required),
                    modifier = Modifier.size(IconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
            }
            if (userCount > 0) {
                Text(
                    text = "($userCount)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Where an item sits in its channel's area. */
private data class SectionPosition(val boxed: Boolean, val first: Boolean, val last: Boolean) {
    fun shape(): Shape {
        val top = if (first) Corners.Small else 0.dp
        val bottom = if (last) Corners.Small else 0.dp
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }
}

/** A channel row opens an area; its members follow it in the same area. */
private fun sectionPositions(items: List<TreeItem>): List<SectionPosition> = items.mapIndexed { index, item ->
    val first = item is TreeItem.ChannelNode
    val last = items.getOrNull(index + 1).let { it == null || it is TreeItem.ChannelNode }
    val sectionChannel = items.subList(0, index + 1).last { it is TreeItem.ChannelNode } as TreeItem.ChannelNode
    SectionPosition(
        boxed = parseSpacer(sectionChannel.channel.name) == null,
        first = first,
        last = last,
    )
}

private fun buildTreeItems(channels: List<Channel>, users: List<User>): List<TreeItem> {
    if (channels.isEmpty()) return emptyList()

    // Filter out any null elements that may come from JNI
    @Suppress("USELESS_CAST")
    val safeChannels = channels.filterNotNull()
    @Suppress("USELESS_CAST")
    val safeUsers = users.filterNotNull()

    if (safeChannels.isEmpty()) return emptyList()

    val tree = JChannelTree.fromChannels(safeChannels.toTypedArray())
    val usersByChannel = safeUsers.groupBy { it.channelId }

    Log.d("ChannelTree", "usersByChannel keys: ${usersByChannel.keys}, channel ids: ${safeChannels.map { it.id }}")

    val items = mutableListOf<TreeItem>()

    fun addChannel(channel: Channel, depth: Int) {
        items.add(TreeItem.ChannelNode(channel, depth))
        // Add users in this channel
        usersByChannel[channel.id]?.forEach { user ->
            items.add(TreeItem.UserNode(user, depth + 1))
        }
        // Add sub-channels (filter nulls from JNI array)
        tree.getChildren(channel.id)?.filterNotNull()?.forEach { child ->
            addChannel(child, depth + 1)
        }
    }

    tree.roots?.filterNotNull()?.forEach { root -> addChannel(root, 0) }
    tree.close()
    return items
}