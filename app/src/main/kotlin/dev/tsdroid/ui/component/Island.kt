package dev.tsdroid.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.InsetFill
import dev.tsdroid.ui.theme.InsetPadding
import dev.tsdroid.ui.theme.LocalInsetFill
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.Spacing

/*
 * Islands: the rounded sections every screen is built from, in the frosted material. One padding
 * inside, one gap between them (see Spacing), one corner (Shapes.Standard), and one header — so a
 * section looks the same on the home screen, in Settings, in the server screen and in a sheet.
 */

/** Height of an island's header row, folded or not: a control and a gap above and below it. */
val IslandHeaderHeight = ControlHeight + Spacing.Gap * 2

/**
 * An island's icon: a rounded square as tall as a control, so the header's gap surrounds it on
 * every side. A picture (a server's icon, someone's avatar) fills it; a glyph sits on a tile.
 */
val IslandIconSize = ControlHeight

/** The icon's corner: the standard one, which every other corner follows. */
val IslandIconShape = Shapes.Standard

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
        shape = Shapes.Standard,
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
 * An island that folds down to its header. Its height comes from its caller, which animates it;
 * folded, that is just the header's. The header shows [summary] while folded (the latest
 * message, a count), and [actions] while unfolded; tapping it anywhere else folds or unfolds.
 */
@Composable
fun FoldableIsland(
    title: String,
    icon: ImageVector?,
    /** An image in place of [icon]: whom a conversation is with */
    image: (@Composable () -> Unit)? = null,
    /** Read here rather than by the caller, so folding recomposes only this section */
    expanded: () -> Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    summary: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val nested = MaterialTheme.colorScheme.surfaceContainer
    Column(
        modifier = modifier
            .clip(Shapes.Standard)
            .background(InsetFill),
    ) {
        CompositionLocalProvider(LocalInsetFill provides nested) {
            IslandHeader(
                title = title,
                icon = icon,
                image = image,
                onClick = onToggle,
                expanded = expanded(),
                trailing = if (expanded()) actions else summary,
            )
            // The content stays composed while folded: the section's height (set by whoever lays
            // it out) shrinks to the header and clips it away. Building a chat's messages again
            // on every unfold, and tearing them down on every fold, was what made folding stutter.
            Column(modifier = Modifier.clipToBounds(), content = content)
        }
    }
}

/**
 * The header row of an island: icon and title, then whatever [trailing] holds, at the far end. With
 * [onClick] the whole row folds and unfolds the island. The row is a control's height plus a
 * gap above and below, so buttons in it sit the island's gap from its edges.
 */
@Composable
fun IslandHeader(
    title: String,
    icon: ImageVector?,
    /** A picture in place of [icon], sized [IslandIconSize]: a server's icon, someone's avatar */
    image: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    expanded: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IslandHeaderHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (image != null || icon != null) {
            Box(
                modifier = Modifier
                    .size(IslandIconSize)
                    .clip(IslandIconShape)
                    .then(if (image == null) Modifier.background(InsetFill) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    image()
                } else if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

/**
 * A section set into an island (a group of settings, a server): the island's inset colour and a
 * control's corner, with a header like an island's when it has a [title].
 */
@Composable
fun InsetSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = InsetFill
    val nested = MaterialTheme.colorScheme.surfaceContainer
    Column(
        modifier = modifier
            .clip(Shapes.Standard)
            .background(fill),
    ) {
        CompositionLocalProvider(LocalInsetFill provides nested) {
        if (title != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(InsetHeaderHeight)
                    .padding(horizontal = InsetPadding),
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
                Text(text = title, style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(
            modifier = Modifier.padding(
                start = if (padded) InsetPadding else 0.dp,
                end = if (padded) InsetPadding else 0.dp,
                top = if (padded && title == null) InsetPadding else 0.dp,
                bottom = if (padded) InsetPadding else 0.dp,
            ),
            content = content,
        )
        }
    }
}

/** Height of an [InsetSection]'s header row. */
private val InsetHeaderHeight = 44.dp

/**
 * The root island: the screen itself, so no margin, edge or corner of its own. It fills the screen
 * in the islands' colour (or lets the wallpaper show), and only its content keeps clear of the
 * system bars and the keyboard.
 */
@Composable
fun RootIsland(
    insets: PaddingValues,
    wallpaper: ImageBitmap?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        LocalGlassWallpaper provides wallpaper,
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .then(if (wallpaper == null) Modifier.background(MaterialTheme.colorScheme.surfaceContainer) else Modifier)
                .padding(insets)
                .consumeWindowInsets(insets)
                // Shrinks above the keyboard, so a field being typed in stays in view
                .imePadding(),
            content = content,
        )
    }
}
