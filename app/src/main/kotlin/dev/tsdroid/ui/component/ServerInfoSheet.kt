package dev.tsdroid.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tsdroid.han.R
import dev.tslib.ServerInfo

/**
 * Read-only server details, unfolded as an island from the server's name in the top bar.
 *
 * Everything shown here comes from the [ServerInfo] snapshot the client already
 * refreshes with every state update, so it needs no extra polling and no
 * native call of its own.
 */
@Composable
fun ServerInfoContent(
    info: ServerInfo?,
    address: String?,
    /** Counted from the lists the app keeps: the server's own counters are not always filled in */
    clientsOnline: Int,
    channelCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        if (info == null) {
            Text(
                text = stringResource(R.string.server_platform_version) + ": —",
                style = MaterialTheme.typography.bodyLarge,
            )
            return@Column
        }

        address?.takeIf { it.isNotBlank() }?.let {
            InfoRow(stringResource(R.string.server_address), it)
        }
        InfoRow(
            stringResource(R.string.server_platform_version),
            "${info.platform} / ${info.version}",
        )
        InfoRow(
            stringResource(R.string.server_clients),
            stringResource(
                R.string.server_clients_format,
                clientsOnline,
                info.maxClients,
            ),
        )
        InfoRow(stringResource(R.string.server_channels), channelCount.toString())
        if (info.uptime > 0L) {
            InfoRow(stringResource(R.string.server_uptime), formatUptime(info.uptime))
        }

        val welcome = stripBbCode(info.welcomeMessage)
        if (welcome.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.server_welcome),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = welcome,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        // Label and value in two columns, so the values line up
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(120.dp)
                .padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Server welcome messages are BBCode. The sheet shows them as plain text, so the
 * tags are dropped rather than displayed literally. Line breaks survive.
 */
internal fun stripBbCode(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        .replace(Regex("\\[/?[A-Za-z*][^\\]]*\\]"), "")
        .replace("\r\n", "\n")
        .trim()
}

/**
 * "3d 4h", "4h 12m", "12m 30s" — the two most significant units are enough to
 * read uptime at a glance, and shorter strings fit the row.
 */
internal fun formatUptime(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "—"
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
