package dev.tsdroid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shared sizes, so screens stop picking their own: every rounded shape, gap and inner padding in
 * the app is one of these. Text sizes are the six roles described on the app's typography.
 */
object Corners {
    /** Chips, badges, menu rows and other small pieces */
    val Small = 8.dp

    /** Islands, cards, menus and text fields */
    val Medium = 14.dp

    /** Dialogs, sheets and the floating bars */
    val Large = 24.dp
}

object Shapes {
    val Small = RoundedCornerShape(Corners.Small)
    val Medium = RoundedCornerShape(Corners.Medium)
    val Large = RoundedCornerShape(Corners.Large)
    val Sheet = RoundedCornerShape(topStart = Corners.Large, topEnd = Corners.Large)
}

object Spacing {
    /** Between two islands, and between an island and the edge of the screen */
    val Gap = 6.dp

    /** Inside an island, from its edge to its content */
    val Inner = 16.dp
}

/** The size of every icon in the app, in buttons, rows, headers and bars alike. */
val IconSize = 20.dp
