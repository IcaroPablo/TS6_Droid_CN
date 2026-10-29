package dev.tsdroid.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.AlertRed
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.Shapes

/*
 * Server and channel icons. The server's own pictures come over file transfer, so until one has
 * been downloaded (or while the file-transfer port cannot be reached) these draw a fallback that
 * still tells servers and channels apart.
 */

/**
 * Server and channel icons are at most 32px on the server and drawn two or three times that: scaled
 * up blocky, each pixel a crisp square, rather than smoothed into a blur.
 */
private val IconFilter = FilterQuality.None

/** The server's icon, or its initials on a colour picked from its name, like member avatars. */
@Composable
fun ServerIcon(
    name: String,
    icon: ImageBitmap?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    shape: Shape = Shapes.Standard,
) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            // Rounded like every other picture and section; cropped so the corners are the image's
            contentScale = ContentScale.Crop,
            filterQuality = IconFilter,
            modifier = modifier
                .size(size)
                .clip(shape),
        )
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(avatarColorFor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            // Scales with the icon: two letters fill about half of it
            fontSize = (size.value * 0.4f).sp,
            maxLines = 1,
        )
    }
}

/** Up to two letters: the first of the first two words, or the first two of a single word. */
private fun initialsOf(name: String): String {
    val words = name.split(' ', '-', '_', '.').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }.uppercase()
}

/**
 * A channel's icon: the server's picture when it has one, otherwise the channel glyph coloured by
 * the channel's state, as the desktop client does — red when full, the accent colour when people
 * are in it or we are, dimmed when empty.
 */
@Composable
fun ChannelIcon(
    icon: ImageBitmap?,
    userCount: Int,
    maxClients: Int,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = IconFilter,
            modifier = modifier
                .size(24.dp)
                .clip(Shapes.Standard),
        )
        return
    }
    val full = maxClients in 1..userCount
    Icon(
        AppIcons.Channel,
        contentDescription = null,
        modifier = modifier.size(IconSize),
        tint = when {
            full -> AlertRed
            isCurrent || userCount > 0 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
    )
}
