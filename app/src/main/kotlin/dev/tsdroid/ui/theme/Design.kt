package dev.tsdroid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared sizes, so screens stop picking their own: every rounded shape, gap and inner padding in
 * the app is one of these. Text sizes are the six roles described on the app's typography.
 */
object Corners {
    /** The one corner radius: islands, sections, controls, tabs and island icons alike */
    val Standard = 4.dp
}

object Shapes {
    val Standard = RoundedCornerShape(Corners.Standard)
}

object Spacing {
    /** Between two islands, and between an island and the edge of the screen */
    val Gap = 4.dp

    /** Inside an island, from its edge to its content */
    val Inner = 14.dp
}

/** The size of every icon in the app, in buttons, rows, headers and bars alike. */
val IconSize = 20.dp

/**
 * An area set into an island (a channel and its members, the chat's messages): a darker fill, the
 * island's gap from its edges and a control's corner. Content inside it is padded so it lines up
 * with the island's own content.
 */
val InsetFill: Color
    @Composable get() = LocalInsetFill.current ?: MaterialTheme.colorScheme.surfaceContainerLow

/**
 * The colour of an area set into the surface it is drawn on. On an island it is the darker inset
 * colour; inside a section, which already has that colour, areas take the island's colour instead
 * so they still stand apart. Null means "on an island".
 */
val LocalInsetFill = compositionLocalOf<Color?> { null }
val InsetPadding = Spacing.Inner - Spacing.Gap
