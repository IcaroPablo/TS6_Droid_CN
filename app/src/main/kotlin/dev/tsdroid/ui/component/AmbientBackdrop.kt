package dev.tsdroid.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale

/**
 * The backdrop used when the anime wallpaper is switched off.
 *
 * Glass is refraction: a pane can only bend what is actually behind it, so over a flat surface every
 * pane collapses into a flat card no matter how the material is tuned. Painting a soft, non-flat
 * gradient here gives the glass something to refract, which is what keeps the liquid-glass look
 * available regardless of the wallpaper setting.
 *
 * The gradient is derived from the active colour scheme, so it follows the app's dynamic theme (the
 * palette extracted from the wallpaper, or the default seed when there has never been one).
 */
@Composable
fun rememberAmbientBackdrop(): ImageBitmap {
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    // Rebuild only when the palette actually changes.
    return remember(scheme, dark) { buildAmbientGradient(scheme, dark) }
}

/**
 * The bitmap the glass should refract: the wallpaper when it is available, otherwise the ambient
 * gradient. Never null, so no pane ever has to fall back to a flat, glass-less material — this also
 * covers the window between launch and the wallpaper finishing its download.
 */
@Composable
fun rememberGlassBackdrop(wallpaperEnabled: Boolean): ImageBitmap {
    val wallpaper = if (wallpaperEnabled) AnimeWallpaperState.activeBitmap else null
    return wallpaper ?: rememberAmbientBackdrop()
}

/** Paints [rememberAmbientBackdrop] full screen, laid out exactly as the glass assumes. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    Image(
        bitmap = rememberAmbientBackdrop(),
        contentDescription = null,
        // ContentScale.Crop is what LiquidGlassRefraction.render() maps against, so the painted
        // background and the refracted pane stay aligned pixel for pixel.
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

private const val AMBIENT_WIDTH = 96
private const val AMBIENT_HEIGHT = 192

private fun buildAmbientGradient(scheme: ColorScheme, dark: Boolean): ImageBitmap {
    // Three stops washed heavily towards the surface: an ambient backdrop must be felt, not read.
    val start = scheme.primary
        .copy(alpha = if (dark) 0.34f else 0.20f)
        .compositeOver(scheme.surface)
    val middle = scheme.secondaryContainer
        .copy(alpha = if (dark) 0.30f else 0.38f)
        .compositeOver(scheme.surface)
    val end = scheme.tertiary
        .copy(alpha = if (dark) 0.26f else 0.16f)
        .compositeOver(scheme.surface)

    val pixels = IntArray(AMBIENT_WIDTH * AMBIENT_HEIGHT)
    val maxX = (AMBIENT_WIDTH - 1).toFloat()
    val maxY = (AMBIENT_HEIGHT - 1).toFloat()
    for (y in 0 until AMBIENT_HEIGHT) {
        for (x in 0 until AMBIENT_WIDTH) {
            // Diagonal ramp: the stops sit in opposite corners so a pane placed anywhere on screen
            // has some gradient behind it to bend.
            val t = (x / maxX) * 0.5f + (y / maxY) * 0.5f
            val color: Color = if (t < 0.5f) {
                lerp(start, middle, t * 2f)
            } else {
                lerp(middle, end, (t - 0.5f) * 2f)
            }
            pixels[y * AMBIENT_WIDTH + x] = color.toArgb()
        }
    }
    return Bitmap
        .createBitmap(pixels, AMBIENT_WIDTH, AMBIENT_HEIGHT, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}
