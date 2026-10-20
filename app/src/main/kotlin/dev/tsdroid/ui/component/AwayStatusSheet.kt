package dev.tsdroid.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tsdroid.data.AwayStatus
import dev.tsdroid.han.R
import dev.tsdroid.ui.icon.AppIcons

/** Longest away message the sheet accepts; nobody reads a novel next to a name. */
const val AWAY_MESSAGE_MAX_LENGTH = 100

/** Colour of one status: green online, amber away, red do-not-disturb. */
fun awayStatusColor(status: AwayStatus): Color = when (status) {
    AwayStatus.ONLINE -> Color(0xFF4CAF50)
    AwayStatus.AWAY -> Color(0xFFFFB300)
    AwayStatus.DND -> Color(0xFFE53935)
}

/**
 * 状态 sheet — the desktop client's status menu.
 *
 * Online / away / do-not-disturb are the three states TeamSpeak can actually
 * express (`client_away` plus an away message); the hint and the do-not-disturb
 * row spell out the one local extra, namely that DND closes the microphone.
 */
@Composable
fun AwayStatusSheet(
    status: AwayStatus,
    message: String,
    onSelect: (AwayStatus) -> Unit,
    onMessageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    wallpaper: ImageBitmap? = null,
) {
    GlassBottomSheet(onDismissRequest = onDismiss, wallpaper = wallpaper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (status == AwayStatus.DND) {
                    AppIcons.DoNotDisturb
                } else {
                    AppIcons.Away
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = awayStatusColor(status),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.away_status_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = stringResource(R.string.away_status_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        AwayStatus.values().forEach { entry ->
            StatusRow(entry = entry, selected = entry == status, onClick = { onSelect(entry) })
        }

        if (status != AwayStatus.ONLINE) {
            // Draft state: `clientupdate` goes out once per edit, not per key.
            var draft by remember(status, message) { mutableStateOf(message) }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(AWAY_MESSAGE_MAX_LENGTH) },
                label = { Text(stringResource(R.string.away_message_label)) },
                placeholder = { Text(status.defaultMessage.orEmpty()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = {
                        onMessageChange(draft)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.away_message_save))
                    }
                },
            )
        }
    }
}

@Composable
private fun StatusRow(entry: AwayStatus, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(awayStatusColor(entry), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(entry.titleRes()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = stringResource(entry.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

private fun AwayStatus.titleRes(): Int = when (this) {
    AwayStatus.ONLINE -> R.string.away_status_online
    AwayStatus.AWAY -> R.string.away_status_away
    AwayStatus.DND -> R.string.away_status_dnd
}

private fun AwayStatus.descriptionRes(): Int = when (this) {
    AwayStatus.ONLINE -> R.string.away_status_online_desc
    AwayStatus.AWAY -> R.string.away_status_away_desc
    AwayStatus.DND -> R.string.away_status_dnd_desc
}
