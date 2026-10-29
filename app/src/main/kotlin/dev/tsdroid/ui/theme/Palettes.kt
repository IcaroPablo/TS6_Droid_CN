package dev.tsdroid.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The colour schemes the user can pick in Settings. [DEFAULT] is the app's own, derived from the
 * wallpaper's colour when there is one; the others are well-known editor themes, each with a light
 * variant where the theme has one (Dracula is dark only).
 */
enum class AppColorScheme(val key: String, val label: String) {
    DEFAULT("default", ""),
    GRUVBOX("gruvbox", "Gruvbox"),
    DRACULA("dracula", "Dracula"),
    CATPPUCCIN("catppuccin", "Catppuccin"),
    TOKYO_NIGHT("tokyo_night", "Tokyo Night");

    companion object {
        fun fromKey(key: String?): AppColorScheme = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * The few colours a theme is made of: [page] is the background behind the islands, [island] the
 * islands themselves, [inset] the darker areas set into them (a channel, the chat's messages),
 * [raised] and [highest] the controls laid on them.
 */
private class Palette(
    val page: Color,
    val inset: Color,
    val island: Color,
    val raised: Color,
    val highest: Color,
    val text: Color,
    val subtext: Color,
    val comment: Color,
    val accent: Color,
    val secondary: Color,
    val tertiary: Color,
    val red: Color,
)

private val gruvboxDark = Palette(
    page = Color(0xFF1D2021), inset = Color(0xFF1D2021), island = Color(0xFF282828),
    raised = Color(0xFF3C3836), highest = Color(0xFF504945),
    text = Color(0xFFEBDBB2), subtext = Color(0xFFA89984), comment = Color(0xFF928374),
    accent = Color(0xFFFABD2F), secondary = Color(0xFF83A598), tertiary = Color(0xFF8EC07C),
    red = Color(0xFFFB4934),
)

private val gruvboxLight = Palette(
    page = Color(0xFFEBDBB2), inset = Color(0xFFF2E5BC), island = Color(0xFFFBF1C7),
    raised = Color(0xFFEBDBB2), highest = Color(0xFFD5C4A1),
    text = Color(0xFF3C3836), subtext = Color(0xFF665C54), comment = Color(0xFF928374),
    accent = Color(0xFFB57614), secondary = Color(0xFF076678), tertiary = Color(0xFF427B58),
    red = Color(0xFF9D0006),
)

private val dracula = Palette(
    page = Color(0xFF191A21), inset = Color(0xFF21222C), island = Color(0xFF282A36),
    raised = Color(0xFF343746), highest = Color(0xFF44475A),
    text = Color(0xFFF8F8F2), subtext = Color(0xFFBFBFBF), comment = Color(0xFF6272A4),
    accent = Color(0xFFBD93F9), secondary = Color(0xFFFF79C6), tertiary = Color(0xFF8BE9FD),
    red = Color(0xFFFF5555),
)

private val catppuccinMocha = Palette(
    page = Color(0xFF11111B), inset = Color(0xFF181825), island = Color(0xFF1E1E2E),
    raised = Color(0xFF313244), highest = Color(0xFF45475A),
    text = Color(0xFFCDD6F4), subtext = Color(0xFFA6ADC8), comment = Color(0xFF6C7086),
    accent = Color(0xFFCBA6F7), secondary = Color(0xFF89B4FA), tertiary = Color(0xFF94E2D5),
    red = Color(0xFFF38BA8),
)

private val catppuccinLatte = Palette(
    page = Color(0xFFDCE0E8), inset = Color(0xFFE6E9EF), island = Color(0xFFEFF1F5),
    raised = Color(0xFFCCD0DA), highest = Color(0xFFBCC0CC),
    text = Color(0xFF4C4F69), subtext = Color(0xFF6C6F85), comment = Color(0xFF9CA0B0),
    accent = Color(0xFF8839EF), secondary = Color(0xFF1E66F5), tertiary = Color(0xFF179299),
    red = Color(0xFFD20F39),
)

private val tokyoNight = Palette(
    page = Color(0xFF13131A), inset = Color(0xFF16161E), island = Color(0xFF1A1B26),
    raised = Color(0xFF292E42), highest = Color(0xFF414868),
    text = Color(0xFFC0CAF5), subtext = Color(0xFFA9B1D6), comment = Color(0xFF565F89),
    accent = Color(0xFF7AA2F7), secondary = Color(0xFFBB9AF7), tertiary = Color(0xFF7DCFFF),
    red = Color(0xFFF7768E),
)

private val tokyoNightDay = Palette(
    page = Color(0xFFD0D5E3), inset = Color(0xFFD8DCE8), island = Color(0xFFE1E2E7),
    raised = Color(0xFFC4C8DA), highest = Color(0xFFB4B9D0),
    text = Color(0xFF3760BF), subtext = Color(0xFF6172B0), comment = Color(0xFF848CB5),
    accent = Color(0xFF2E7DE9), secondary = Color(0xFF9854F1), tertiary = Color(0xFF007197),
    red = Color(0xFFF52A65),
)

/** The scheme to paint with, or null for [AppColorScheme.DEFAULT], which the theme derives itself. */
fun paletteColorScheme(scheme: AppColorScheme, dark: Boolean): ColorScheme? {
    val palette = when (scheme) {
        AppColorScheme.DEFAULT -> return null
        AppColorScheme.GRUVBOX -> if (dark) gruvboxDark else gruvboxLight
        AppColorScheme.DRACULA -> dracula
        AppColorScheme.CATPPUCCIN -> if (dark) catppuccinMocha else catppuccinLatte
        AppColorScheme.TOKYO_NIGHT -> if (dark) tokyoNight else tokyoNightDay
    }
    // Dracula has no light variant, so it is always dark
    return palette.toColorScheme(dark || scheme == AppColorScheme.DRACULA)
}

private fun Palette.toColorScheme(dark: Boolean): ColorScheme {
    // Containers are the accent (or red) washed into the island, so they stay in the theme's mood
    val accentContainer = lerp(island, accent, 0.28f)
    val errorContainer = lerp(island, red, 0.35f)
    return if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = island,
            primaryContainer = accentContainer, onPrimaryContainer = text,
            inversePrimary = accent,
            secondary = secondary, onSecondary = island,
            secondaryContainer = highest, onSecondaryContainer = text,
            tertiary = tertiary, onTertiary = island,
            tertiaryContainer = lerp(island, tertiary, 0.28f), onTertiaryContainer = text,
            error = red, onError = island,
            errorContainer = errorContainer, onErrorContainer = text,
            background = page, onBackground = text,
            surface = page, onSurface = text,
            surfaceVariant = raised, onSurfaceVariant = subtext,
            outline = comment, outlineVariant = highest,
            inverseSurface = text, inverseOnSurface = island,
            surfaceDim = page, surfaceBright = highest,
            surfaceContainerLowest = page, surfaceContainerLow = inset,
            surfaceContainer = island, surfaceContainerHigh = raised, surfaceContainerHighest = highest,
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = island,
            primaryContainer = accentContainer, onPrimaryContainer = text,
            inversePrimary = accent,
            secondary = secondary, onSecondary = island,
            secondaryContainer = highest, onSecondaryContainer = text,
            tertiary = tertiary, onTertiary = island,
            tertiaryContainer = lerp(island, tertiary, 0.28f), onTertiaryContainer = text,
            error = red, onError = island,
            errorContainer = errorContainer, onErrorContainer = text,
            background = page, onBackground = text,
            surface = page, onSurface = text,
            surfaceVariant = raised, onSurfaceVariant = subtext,
            outline = comment, outlineVariant = highest,
            inverseSurface = text, inverseOnSurface = island,
            surfaceDim = page, surfaceBright = island,
            surfaceContainerLowest = island, surfaceContainerLow = inset,
            surfaceContainer = island, surfaceContainerHigh = raised, surfaceContainerHighest = highest,
        )
    }
}
