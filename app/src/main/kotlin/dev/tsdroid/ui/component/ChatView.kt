package dev.tsdroid.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tsdroid.ui.theme.Spacing
import dev.tsdroid.viewmodel.ChatMessage
import dev.tsdroid.viewmodel.DownloadState
import dev.tsdroid.viewmodel.FileAttachment
import kotlinx.coroutines.flow.StateFlow

private const val INITIAL_PAGE = 15
private const val PAGE_SIZE = 20

@Composable
fun ChatView(
    messages: List<ChatMessage>,
    showLinkThumbnails: Boolean = false,
    autoLoadImages: Boolean = true,
    onDownload: ((FileAttachment) -> StateFlow<DownloadState>)? = null,
    /** Shown in the middle while there are no messages, so an empty chat does not look broken. */
    emptyText: String? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var visibleCount by remember { mutableIntStateOf(INITIAL_PAGE) }

    // reverseLayout=true: index 0 = bottom (newest), high index = top (oldest)
    val displayMessages = remember(messages, visibleCount) {
        messages.takeLast(visibleCount.coerceAtMost(messages.size)).asReversed()
    }

    // Auto-scroll to bottom on new messages: when already near the bottom, or
    // when the new message is one's own
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && (listState.firstVisibleItemIndex <= 3 || messages.last().isMe)) {
            listState.animateScrollToItem(0)
        }
    }

    // Load more when scrolling near the top (high indices in reversed layout)
    val totalMessages by rememberUpdatedState(messages.size)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to info.totalItemsCount
        }.collect { (lastVisible, totalItems) ->
            if (totalItems > 0 && lastVisible >= totalItems - 3 && visibleCount < totalMessages) {
                visibleCount = (visibleCount + PAGE_SIZE).coerceAtMost(totalMessages)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            // Messages sit the standard gap in from the area's edges and from each other
            contentPadding = PaddingValues(Spacing.Gap),
            verticalArrangement = Arrangement.spacedBy(Spacing.Gap),
            reverseLayout = true,
        ) {
            items(displayMessages, key = { it.id }) { message ->
                // A new message slides in instead of popping up
                Box(modifier = Modifier.animateItem()) {
                    MessageBubble(
                        message = message,
                        showLinkThumbnails = showLinkThumbnails,
                        autoLoadImages = autoLoadImages,
                        onDownload = onDownload,
                    )
                }
            }
        }
        if (messages.isEmpty() && emptyText != null) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            )
        }
    }
}
