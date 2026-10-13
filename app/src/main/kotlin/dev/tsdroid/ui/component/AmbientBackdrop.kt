package dev.tsdroid.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The bitmap a surface samples for its frosted backdrop.
 *
 * Returns the wallpaper that is actually painted behind the panes, so each surface can pull in its
 * own patch of it, blur it heavily and composite a milky veil on top — the "milk glass" look. `null`
 * when the wallpaper is switched off, because there is then nothing behind the panes to soften.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun rememberGlassBackdrop(wallpaperEnabled: Boolean): ImageBitmap? =
    if (wallpaperEnabled) AnimeWallpaperState.activeBitmap else null
