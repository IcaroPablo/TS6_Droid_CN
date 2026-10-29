package dev.tsdroid.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.tsdroid.data.AppLog
import dev.tsdroid.han.R
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.InsetFill
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The in-app log, unfolded in the Settings tab.
 *
 * Reads this process's own logcat tail (see [AppLog]) so a user without adb can
 * still hand over the reason a session went silent. Refreshing is manual and
 * happens on open, so nothing runs while the viewer is closed.
 */
@Composable
fun LogViewer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        AppLog.refresh()
        loading = false
    }

    // Keep the newest line in view as lines arrive.
    LaunchedEffect(AppLog.lines.size) {
        if (AppLog.lines.isNotEmpty()) {
            listState.animateScrollToItem(AppLog.lines.size - 1)
        }
    }

    // Unfolded in place in the Settings tab: the actions, then the lines in a box of its own height
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.diag_log_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    scope.launch {
                        loading = true
                        AppLog.refresh()
                        loading = false
                    }
                }
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.diag_log_refresh),
                    modifier = Modifier.size(IconSize),
                )
            }
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(AppLog.lines.joinToString("\n")))
                    Toast.makeText(context, R.string.common_copied, Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.common_copy),
                    modifier = Modifier.size(IconSize),
                )
            }
            IconButton(onClick = { AppLog.clear() }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.diag_log_clear),
                    modifier = Modifier.size(IconSize),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LogHeight)
                .clip(Shapes.Standard)
                .background(InsetFill),
        ) {
            if (AppLog.lines.isEmpty()) {
                Text(
                    text = stringResource(
                        if (loading) R.string.diag_log_loading else R.string.diag_log_empty
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.Gap),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(AppLog.lines) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

/** Height of the box the log's lines scroll in. */
private val LogHeight = 360.dp
