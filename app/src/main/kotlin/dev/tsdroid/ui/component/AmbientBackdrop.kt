package dev.tsdroid.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The backdrop the app's surfaces refract.
 *
 * Liquid glass has been removed in favour of a solid Material 3 design: the refraction could not be
 * made to read like iOS on this platform, and a solid, opaque surface is both calmer and cheaper.
 * This therefore always returns `null`, which makes every surface paint an opaque theme colour and
 * lets the refraction step short-circuit instead of computing an image nobody shows.
 *
 * Kept as the single choke point for the backdrop argument so the call sites did not have to change.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun rememberGlassBackdrop(wallpaperEnabled: Boolean): ImageBitmap? = null
