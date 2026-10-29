package dev.tsdroid.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tsdroid.han.R
import dev.tsdroid.service.WhisperTargets
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.InsetPadding
import dev.tsdroid.ui.theme.WhisperAmber
import dev.tslib.Channel
import dev.tslib.ServerGroup

/**
 * 密聊 list editor, the same idea as the desktop client's whisper list: pick the
 * clients, channels and server groups that should hear you, and see at a glance
 * what is currently selected.
 *
 * A group row shows how many members are online for orientation only — a group
 * is routed by the server as one target, so its size does not count against the
 * server's whisper-client limit.
 */
@Composable
fun WhisperListSheet(
    targets: WhisperTargets,
    groups: List<ServerGroup>,
    channels: List<Channel>,
    /** Whisper target key → nickname, for the selected members. */
    selectedMembers: List<Pair<String, String>>,
    membersOfGroup: (Long) -> Int,
    onToggleGroup: (Long) -> Unit,
    onToggleChannel: (Long) -> Unit,
    onRemoveMember: (String) -> Unit,
    onStop: () -> Unit,
    wallpaper: ImageBitmap? = null,
) {
    // Unfolded in place in the server's tab, under what opened it
    Column(modifier = Modifier.fillMaxWidth().padding(InsetPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.Whisper,
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                tint = WhisperAmber,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.whisper_list_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (!targets.isEmpty) {
                TextButton(onClick = onStop) {
                    Text(stringResource(R.string.whisper_clear_list))
                }
            }
        }

        Text(
            text = stringResource(R.string.whisper_list_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // ── 服务器组 ──────────────────────────────────────
            if (groups.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.whisper_section_groups)) }
                items(groups, key = { "grp_${it.id}" }) { group ->
                    val members = membersOfGroup(group.id)
                    CheckRow(
                        checked = targets.hasGroup(group.id),
                        title = group.name,
                        detail = stringResource(R.string.whisper_group_members, members),
                        enabled = targets.hasGroup(group.id) || members > 0,
                        onClick = { onToggleGroup(group.id) },
                    )
                }
            } else {
                item { SectionHeader(stringResource(R.string.whisper_section_groups)) }
                item {
                    Text(
                        text = stringResource(R.string.whisper_groups_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            // ── 频道 ──────────────────────────────────────────
            if (channels.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.whisper_section_channels)) }
                items(channels, key = { "ch_${it.id}" }) { channel ->
                    CheckRow(
                        checked = targets.hasChannel(channel.id),
                        title = channel.name,
                        detail = null,
                        enabled = true,
                        onClick = { onToggleChannel(channel.id) },
                    )
                }
            }

            // ── 已在列表里的成员 ──────────────────────────────
            item { SectionHeader(stringResource(R.string.whisper_section_members)) }
            if (selectedMembers.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.whisper_no_members),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            } else {
                items(selectedMembers, key = { "mem_${it.first}" }) { (key, nickname) ->
                    CheckRow(
                        checked = true,
                        title = nickname,
                        detail = null,
                        enabled = true,
                        onClick = { onRemoveMember(key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun CheckRow(
    checked: Boolean,
    title: String,
    detail: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onClick() }, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
