package dev.tsdroid.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.Spacing

/*
 * Islands: the rounded sections every screen is built from, in the frosted material. One padding
 * inside, one gap between them (see Spacing), one corner (Shapes.Medium), and one header — so a
 * section looks the same on the home screen, in Settings, in the server screen and in a sheet.
 */

/** Height of an island's header row, folded or not: a control and a gap above and below it. */
val IslandHeaderHeight = ControlHeight + Spacing.Gap * 2

/**
 * A plain island. Its content gets the standard inner padding, unless [padded] is false for
 * content that runs to the edges itself (a list whose rows are tappable edge to edge).
 */
@Composable
fun Island(
    modifier: Modifier = Modifier,
    wallpaper: ImageBitmap? = LocalGlassWallpaper.current,
    title: String? = null,
    icon: ImageVector? = null,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    LiquidGlassPanel(
        modifier = modifier,
        wallpaper = wallpaper,
        shape = Shapes.Medium,
        elevation = 1.dp,
        contentPadding = PaddingValues(0.dp),
    ) { contentColor ->
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalGlassWallpaper provides wallpaper,
        ) {
            Column {
                if (title != null) IslandHeader(title = title, icon = icon)
                Column(
                    modifier = Modifier.padding(
                        start = if (padded) Spacing.Inner else 0.dp,
                        end = if (padded) Spacing.Inner else 0.dp,
                        // Under a header, the header's own height already spaces the content
                        top = if (padded && title == null) Spacing.Inner else 0.dp,
                        bottom = if (padded) Spacing.Inner else 0.dp,
                    ),
                    content = content,
                )
            }
        }
    }
}

/**
 * An island that folds down to its header. The header shows [summary] while folded (the latest
 * message, a count), and [actions] while unfolded; tapping it anywhere else folds or unfolds.
 */
@Composable
fun FoldableIsland(
    title: String,
    icon: ImageVector?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    wallpaper: ImageBitmap? = LocalGlassWallpaper.current,
    summary: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    LiquidGlassPanel(
        modifier = modifier,
        wallpaper = wallpaper,
        shape = Shapes.Medium,
        elevation = 1.dp,
        contentPadding = PaddingValues(0.dp),
    ) { contentColor ->
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalGlassWallpaper provides wallpaper,
        ) {
            Column {
                IslandHeader(
                    title = title,
                    icon = icon,
                    onClick = onToggle,
                    expanded = expanded,
                    trailing = if (expanded) actions else summary,
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    Column(content = content)
                }
            }
        }
    }
}

/**
 * The header row of an island: icon and title, the arrow right after them when it folds (as on the
 * server bar), then whatever [trailing] holds, at the far end. The row is a control's height plus a
 * gap above and below, so buttons in it sit the island's gap from its edges.
 */
@Composable
fun IslandHeader(
    title: String,
    icon: ImageVector?,
    onClick: (() -> Unit)? = null,
    expanded: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val chevronTurn by animateFloatAsState(if (expanded) 180f else 0f, label = "islandChevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IslandHeaderHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = Spacing.Inner, end = Spacing.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(IconSize)
                    .rotate(chevronTurn),
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Gap, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailing?.invoke(this)
        }
    }
}
