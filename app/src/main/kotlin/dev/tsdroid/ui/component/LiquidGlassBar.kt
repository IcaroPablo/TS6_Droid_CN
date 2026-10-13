package dev.tsdroid.ui.component

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A rendered glass backdrop plus the average luminance of what it shows, which is used to pick
 * light or dark labels (the same vibrancy behaviour iOS uses).
 */
/**
 * How long a pane waits for its geometry to settle before recomputing the refracted backdrop.
 * Scrolling changes a pane's position every frame; without this the SDF render would restart on
 * each frame and the pane would never show a stable image.
 */
private const val GLASS_SETTLE_MS = 90L

/**
 * Identifies one refracted pane: the wallpaper instance plus the exact pixels and radius the lens
 * was computed for.
 */
private data class GlassCacheKey(
    val wallpaperId: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val outWidth: Int,
    val outHeight: Int,
    val radius: Float,
)

/**
 * Remembers already-rendered panes so revisiting a screen does not re-run the lens.
 *
 * Without this, returning to the settings tab (or scrolling back to a spot already seen) recomputed
 * every pane's SDF render from scratch, which is why the glass appeared to rebuild itself on each
 * visit. Bounded, because every entry holds a pane-sized ARGB bitmap.
 */
private object GlassBackdropCache {
    private const val MAX_ENTRIES = 14

    private val entries = object : LinkedHashMap<GlassCacheKey, GlassBackdrop>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<GlassCacheKey, GlassBackdrop>,
        ): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: GlassCacheKey): GlassBackdrop? = entries[key]

    @Synchronized
    fun put(key: GlassCacheKey, value: GlassBackdrop) {
        entries[key] = value
    }
}

private data class GlassBackdrop(
    val image: ImageBitmap,
    val meanLuma: Float,
)

/**
 * One entry of [LiquidGlassNavBar].
 */
data class LiquidGlassNavItem(
    val icon: ImageVector,
    val label: String,
)

/**
 * Direct port of the iOS 26 "liquid glass" recipe used by
 * https://github.com/childrentime/liquid-glass:
 *
 * 1. the glass shape is a signed distance field (rounded rect / capsule);
 * 2. every pixel samples the backdrop at `center + (p - center) * scaled`, where `scaled` runs
 *    from 1 in the middle down to ~0.84 at the rim — the backdrop is therefore *refracted*
 *    (pulled towards the centre) in a band along the edge, like a thick glass lens;
 * 3. the result gets the same post chain: `contrast(1.2) brightness(1.05) saturate(1.1)`.
 *
 * The reference encodes that maths into a displacement map and hands it to SVG's
 * `feDisplacementMap` through `backdrop-filter`. Android has no backdrop filter, so the same
 * maths is applied on the CPU, once per wallpaper/geometry change, directly over the wallpaper
 * pixels sitting behind the pane. Doing it on the real backdrop also keeps the refracted image
 * aligned with the wallpaper around the pane, which is what makes it read as glass rather than
 * as a tinted panel.
 */
private object LiquidGlassRefraction {

    /** Reference constants: `smoothStep(0.8, 0, distanceToEdge - 0.15)`. */
    private const val EDGE_START = 0.8f
    private const val EDGE_OFFSET = 0.15f

    /**
     * The reference SDF ("0.3 / 0.2 / 0.6" box) puts its rim at ≈0.35 in its normalised
     * distance units; mapping our true capsule SDF onto the same range reproduces its strength.
     */
    private const val RIM_DISTANCE = 0.35f

    /**
     * The reference applies half of the computed displacement (`maxScale *= 0.5`); a touch
     * stronger here because a phone-sized bar is much smaller than the 300x200 demo.
     */
    private const val LENS_SCALE = 0.7f

    /**
     * The reference blurs the backdrop by only 0.25px (it demos a *clear* lens). A navigation bar
     * sits over the whole app, so a small amount of diffusion is kept for legibility.
     */
    private const val DIFFUSION_RADIUS_PX = 2

    private const val CONTRAST = 1.12f
    private const val BRIGHTNESS = 1.02f
    private const val SATURATION = 1.08f

    private fun smoothStep(a: Float, b: Float, t: Float): Float {
        val u = ((t - a) / (b - a)).coerceIn(0f, 1f)
        return u * u * (3f - 2f * u)
    }

    private fun roundedRectSdf(x: Float, y: Float, halfW: Float, halfH: Float, radius: Float): Float {
        val qx = abs(x) - halfW + radius
        val qy = abs(y) - halfH + radius
        val outerX = max(qx, 0f)
        val outerY = max(qy, 0f)
        return min(max(qx, qy), 0f) + sqrt(outerX * outerX + outerY * outerY) - radius
    }

    /**
     * Renders the refracted backdrop for one glass pane.
     *
     * @param wallpaper image the app paints full screen (see [AnimeWallpaperState.activeBitmap])
     * @param paneRect pane rect in window pixels
     * @param windowSize window size in pixels (how the wallpaper is laid out, ContentScale.Crop)
     * @param outSize pane size in pixels, i.e. the output bitmap size
     * @param cornerRadiusPx pane corner radius in pixels
     * @param surfaceArgb app surface colour, used to match the wallpaper's on-screen alpha
     * @param backdropAlpha alpha the background paints the wallpaper with
     */
    fun render(
        wallpaper: ImageBitmap,
        paneRect: Rect,
        windowSize: IntSize,
        outSize: IntSize,
        cornerRadiusPx: Float,
        surfaceArgb: Int,
        backdropAlpha: Float,
    ): GlassBackdrop? {
        if (outSize.width <= 0 || outSize.height <= 0) return null
        if (windowSize.width <= 0 || windowSize.height <= 0) return null

        val source = try {
            wallpaper.asAndroidBitmap()
        } catch (_: Throwable) {
            return null
        }
        val imgW = source.width
        val imgH = source.height
        if (imgW <= 0 || imgH <= 0) return null

        // How the wallpaper is laid out on screen: ContentScale.Crop, centred.
        val cropScale = max(windowSize.width.toFloat() / imgW, windowSize.height.toFloat() / imgH)
        val originX = (windowSize.width - imgW * cropScale) / 2f
        val originY = (windowSize.height - imgH * cropScale) / 2f

        // Only the wallpaper region that can be sampled for this pane needs to be pulled in.
        val margin = 2f
        val regionLeft = floor((paneRect.left - originX) / cropScale - margin).toInt().coerceIn(0, imgW - 1)
        val regionTop = floor((paneRect.top - originY) / cropScale - margin).toInt().coerceIn(0, imgH - 1)
        val regionRight = ceil((paneRect.right - originX) / cropScale + margin).toInt().coerceIn(regionLeft + 1, imgW)
        val regionBottom = ceil((paneRect.bottom - originY) / cropScale + margin).toInt().coerceIn(regionTop + 1, imgH)
        val regionW = regionRight - regionLeft
        val regionH = regionBottom - regionTop

        val pixels = IntArray(regionW * regionH)
        try {
            source.getPixels(pixels, 0, regionW, regionLeft, regionTop, regionW, regionH)
        } catch (_: Throwable) {
            return null
        }

        val outW = outSize.width
        val outH = outSize.height
        val centerX = outW / 2f
        val centerY = outH / 2f
        val halfW = outW / 2f
        val halfH = outH / 2f
        val radius = min(cornerRadiusPx, min(halfW, halfH))

        // Pane origin inside the sampled wallpaper region.
        val paneOriginX = (paneRect.left - originX) / cropScale - regionLeft
        val paneOriginY = (paneRect.top - originY) / cropScale - regionTop

        val surfaceR = AndroidColor.red(surfaceArgb) / 255f
        val surfaceG = AndroidColor.green(surfaceArgb) / 255f
        val surfaceB = AndroidColor.blue(surfaceArgb) / 255f
        val surfaceWeight = (1f - backdropAlpha).coerceIn(0f, 1f)

        val out = IntArray(outW * outH)
        for (oy in 0 until outH) {
            val py = oy + 0.5f - centerY
            for (ox in 0 until outW) {
                val px = ox + 0.5f - centerX

                // 1. distance to the rim, mapped onto the reference's normalised range
                val sdf = roundedRectSdf(px, py, halfW, halfH, radius)
                val rim = RIM_DISTANCE * (1f + sdf / halfH) // 0 in the middle, ≈RIM at the edge

                // 2. the closer to the rim, the stronger the refraction
                val displacement = smoothStep(EDGE_START, 0f, rim - EDGE_OFFSET)
                val scaled = smoothStep(0f, 1f, displacement)

                // 3. sample the backdrop pulled towards the centre
                val pull = 1f - LENS_SCALE * (1f - scaled)
                val sampleX = px * pull + centerX
                val sampleY = py * pull + centerY

                val color = sampleBilinear(
                    pixels = pixels,
                    w = regionW,
                    h = regionH,
                    x = paneOriginX + sampleX / cropScale,
                    y = paneOriginY + sampleY / cropScale,
                )

                // 4. reference post chain: contrast(1.2) brightness(1.05) saturate(1.1)
                var r = AndroidColor.red(color) / 255f
                var g = AndroidColor.green(color) / 255f
                var b = AndroidColor.blue(color) / 255f
                r = ((r - 0.5f) * CONTRAST + 0.5f) * BRIGHTNESS
                g = ((g - 0.5f) * CONTRAST + 0.5f) * BRIGHTNESS
                b = ((b - 0.5f) * CONTRAST + 0.5f) * BRIGHTNESS
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = luma + (r - luma) * SATURATION
                g = luma + (g - luma) * SATURATION
                b = luma + (b - luma) * SATURATION

                // The background paints the wallpaper with an alpha; match that here.
                r = r * backdropAlpha + surfaceR * surfaceWeight
                g = g * backdropAlpha + surfaceG * surfaceWeight
                b = b * backdropAlpha + surfaceB * surfaceWeight

                out[oy * outW + ox] = AndroidColor.argb(
                    255,
                    (r.coerceIn(0f, 1f) * 255f).toInt(),
                    (g.coerceIn(0f, 1f) * 255f).toInt(),
                    (b.coerceIn(0f, 1f) * 255f).toInt(),
                )
            }
        }
        if (DIFFUSION_RADIUS_PX > 0) boxBlur(out, outW, outH, DIFFUSION_RADIUS_PX)

        var lumaSum = 0.0
        for (pixel in out) {
            lumaSum += (0.2126 * AndroidColor.red(pixel) +
                0.7152 * AndroidColor.green(pixel) +
                0.0722 * AndroidColor.blue(pixel)) / 255.0
        }
        val meanLuma = (lumaSum / out.size).toFloat()

        val bitmap = try {
            Bitmap.createBitmap(out, outW, outH, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            return null
        }
        return GlassBackdrop(bitmap.asImageBitmap(), meanLuma)
    }

    /** Separable box blur, enough to take the digital edge off the refracted backdrop. */
    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val window = radius * 2 + 1
        val tmp = IntArray(pixels.size)

        for (y in 0 until h) {
            val row = y * w
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (i in -radius..radius) {
                val c = pixels[row + i.coerceIn(0, w - 1)]
                rSum += AndroidColor.red(c)
                gSum += AndroidColor.green(c)
                bSum += AndroidColor.blue(c)
            }
            for (x in 0 until w) {
                tmp[row + x] = AndroidColor.argb(255, rSum / window, gSum / window, bSum / window)
                val leaving = pixels[row + (x - radius).coerceIn(0, w - 1)]
                val entering = pixels[row + (x + radius + 1).coerceIn(0, w - 1)]
                rSum += AndroidColor.red(entering) - AndroidColor.red(leaving)
                gSum += AndroidColor.green(entering) - AndroidColor.green(leaving)
                bSum += AndroidColor.blue(entering) - AndroidColor.blue(leaving)
            }
        }

        for (x in 0 until w) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (i in -radius..radius) {
                val c = tmp[i.coerceIn(0, h - 1) * w + x]
                rSum += AndroidColor.red(c)
                gSum += AndroidColor.green(c)
                bSum += AndroidColor.blue(c)
            }
            for (y in 0 until h) {
                pixels[y * w + x] = AndroidColor.argb(255, rSum / window, gSum / window, bSum / window)
                val leaving = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
                val entering = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                rSum += AndroidColor.red(entering) - AndroidColor.red(leaving)
                gSum += AndroidColor.green(entering) - AndroidColor.green(leaving)
                bSum += AndroidColor.blue(entering) - AndroidColor.blue(leaving)
            }
        }
    }

    /** Bilinear sample with edge clamping — the reference's `blur(0.25px)` tap. */
    private fun sampleBilinear(pixels: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val cx = x.coerceIn(0f, (w - 1).toFloat())
        val cy = y.coerceIn(0f, (h - 1).toFloat())
        val x0 = floor(cx).toInt()
        val y0 = floor(cy).toInt()
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val fx = cx - x0
        val fy = cy - y0

        val c00 = pixels[y0 * w + x0]
        val c10 = pixels[y0 * w + x1]
        val c01 = pixels[y1 * w + x0]
        val c11 = pixels[y1 * w + x1]

        val w00 = (1f - fx) * (1f - fy)
        val w10 = fx * (1f - fy)
        val w01 = (1f - fx) * fy
        val w11 = fx * fy

        val r = AndroidColor.red(c00) * w00 + AndroidColor.red(c10) * w10 +
            AndroidColor.red(c01) * w01 + AndroidColor.red(c11) * w11
        val g = AndroidColor.green(c00) * w00 + AndroidColor.green(c10) * w10 +
            AndroidColor.green(c01) * w01 + AndroidColor.green(c11) * w11
        val b = AndroidColor.blue(c00) * w00 + AndroidColor.blue(c10) * w10 +
            AndroidColor.blue(c01) * w01 + AndroidColor.blue(c11) * w11
        return AndroidColor.argb(
            255,
            r.toInt().coerceIn(0, 255),
            g.toInt().coerceIn(0, 255),
            b.toInt().coerceIn(0, 255),
        )
    }
}

/**
 * The iOS 26 liquid-glass material: a genuinely refracted backdrop, the reference's inset bottom
 * shadow, a specular rim and a soft drop shadow. Deliberately **no** milky tint and only a hint
 * of diffusion — the refraction is what makes it read as glass.
 */
@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    wallpaper: ImageBitmap? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable BoxScope.(contentColor: Color) -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.25f),
            )
            .clip(shape),
    ) {
        LiquidGlassSurface(
            wallpaper = wallpaper,
            shape = shape,
            cornerRadius = cornerRadius,
            // Deliberately not matchParentSize(): the content must be able to size this panel,
            // and a matchParentSize child contributes nothing to its parent's measurement — the
            // whole card then collapsed to zero height. The paint layers inside the surface fill
            // it via matchParentSize instead, so the glass still covers the measured content.
            modifier = Modifier,
        ) { contentColor ->
            Box(modifier = Modifier.padding(contentPadding)) {
                content(contentColor)
            }
        }
    }
}

/**
 * A compact glass surface for list rows and grouped settings: the same refracting material as
 * [LiquidGlassPanel], with a lighter shadow so a long list does not read as a pile of slabs.
 *
 * The backdrop-derived colour is published as `LocalContentColor`, so any inner [Text] or [Icon]
 * that does not set an explicit colour adapts to whatever is actually behind the glass instead of
 * assuming a light background.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    wallpaper: ImageBitmap? = null,
    shape: Shape = RoundedCornerShape(22.dp),
    cornerRadius: Dp = 22.dp,
    elevation: Dp = 3.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    LiquidGlassPanel(
        modifier = modifier,
        wallpaper = wallpaper,
        shape = shape,
        cornerRadius = cornerRadius,
        elevation = elevation,
        contentPadding = contentPadding,
    ) { contentColor ->
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * iOS 26 style floating navigation bar: a glass capsule whose backdrop is really refracted,
 * with a selection pill that glides between items on a spring.
 */
@Composable
fun LiquidGlassNavBar(
    items: List<LiquidGlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    wallpaper: ImageBitmap? = null,
    barHeight: Dp = 64.dp,
    /** Width given to each item. The capsule hugs the items instead of filling the window. */
    itemWidth: Dp = 92.dp,
    horizontalMargin: Dp = 16.dp,
    topMargin: Dp = 8.dp,
    bottomMargin: Dp = 12.dp,
) {
    if (items.isEmpty()) return
    val dark = isSystemInDarkTheme()
    val capsule = CircleShape

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = horizontalMargin,
                end = horizontalMargin,
                top = topMargin,
                bottom = bottomMargin,
            ),
        // Centred, and only as wide as its items: a two-item bar reads as a compact capsule
        // rather than a rail spanning the whole window.
        contentAlignment = Alignment.Center,
    ) {
        // Hug the items, but never exceed the available width: as entries are added the slot
        // shrinks so the capsule stays inside the window instead of being clipped.
        val capsuleWidth = minOf(itemWidth * items.size, maxWidth)
        val slotWidth = capsuleWidth / items.size
        Box(
            modifier = Modifier
                .width(capsuleWidth)
                .height(barHeight)
                .shadow(
                    elevation = 10.dp,
                    shape = capsule,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.22f),
                    spotColor = Color.Black.copy(alpha = 0.25f),
                )
                .clip(capsule),
        ) {
            val pillInset = 6.dp

            LiquidGlassSurface(
                wallpaper = wallpaper,
                shape = capsule,
                cornerRadius = barHeight / 2,
                modifier = Modifier.matchParentSize(),
            ) { contentColor ->
                // Selection pill — a brighter sliver of the same glass.
                val halo = if (contentColor.luminance() > 0.5f) {
                    Color.Black.copy(alpha = 0.45f)
                } else {
                    Color.White.copy(alpha = 0.65f)
                }
                val pillOffset by animateDpAsState(
                    targetValue = slotWidth * selectedIndex + pillInset,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "liquidGlassPill",
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = pillOffset)
                        .width(slotWidth - pillInset * 2)
                        .height(barHeight - 12.dp)
                        .clip(capsule)
                        // A theme-tinted pill instead of the old white-alpha overlay: on the neutral
                        // near-white bar a translucent white pill would be invisible.
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .border(
                            width = 0.75.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = capsule,
                        ),
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        val tint by animateColorAsState(
                            targetValue = if (selected) contentColor
                            else contentColor.copy(alpha = 0.72f),
                            animationSpec = tween(durationMillis = 200),
                            label = "liquidGlassTint$index",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.08f else 1f,
                            animationSpec = spring(
                                dampingRatio = 0.6f,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "liquidGlassScale$index",
                        )
                        val interaction = remember { MutableInteractionSource() }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .selectable(
                                    selected = selected,
                                    interactionSource = interaction,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { onSelect(index) },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = item.label,
                                color = tint,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    shadow = Shadow(
                                        color = halo,
                                        offset = Offset.Zero,
                                        blurRadius = 4f,
                                    ),
                                ),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Size of the root layout, which is the window (and therefore the wallpaper) size.
 */
private fun LayoutCoordinates.rootLayoutSize(): IntSize {
    var current: LayoutCoordinates = this
    while (true) {
        current = current.parentLayoutCoordinates ?: return current.size
    }
}

/**
 * The glass material itself: refracted wallpaper (computed off the main thread), a barely-there
 * legibility veil, the reference's inset bottom shadow and a specular rim.
 */
@Composable
private fun LiquidGlassSurface(
    wallpaper: ImageBitmap?,
    shape: Shape,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(contentColor: Color) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }

    // Window size (how the full-screen wallpaper is laid out) + this pane's rect, both in
    // pixels: measured from the layout tree, so the refracted backdrop lines up with the
    // wallpaper painted around the bar.
    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    var paneRect by remember { mutableStateOf(Rect.Zero) }
    var paneSize by remember { mutableStateOf(IntSize.Zero) }

    val backdrop by produceState<GlassBackdrop?>(
        initialValue = null,
        wallpaper,
        paneRect,
        paneSize,
        windowSize,
        surfaceColor,
    ) {
        val source = wallpaper
        if (source == null) {
            // Nothing to refract at all (background off): drop the stale frame.
            value = null
            return@produceState
        }
        if (paneSize.width <= 0 || paneSize.height <= 0) return@produceState
        // Deliberately NOT `value = null` here. Clearing it on every key change meant each scroll
        // frame and each entrance animation dropped the pane back to the flat fallback until the
        // new bitmap arrived — the glass visibly vanished while scrolling and re-appeared a moment
        // after the finger stopped. Keeping the previous frame means the pane always shows glass,
        // and the newly rendered one simply replaces it.
        // The settle delay coalesces the geometry changes a scroll produces, so the SDF render
        // runs once for the resting position instead of once per frame. It is skipped for the
        // very first frame of a pane so nothing is delayed on initial appearance.
        if (value != null) kotlinx.coroutines.delay(GLASS_SETTLE_MS)
        val key = GlassCacheKey(
            wallpaperId = System.identityHashCode(source),
            left = paneRect.left.toInt(),
            top = paneRect.top.toInt(),
            right = paneRect.right.toInt(),
            bottom = paneRect.bottom.toInt(),
            outWidth = paneSize.width,
            outHeight = paneSize.height,
            radius = cornerRadiusPx,
        )
        GlassBackdropCache.get(key)?.let { cached ->
            // This exact pane was rendered before (returning to a tab, scrolling back): reuse it
            // instead of running the lens again, so nothing visibly rebuilds.
            value = cached
            return@produceState
        }
        val computed = withContext(Dispatchers.Default) {
            LiquidGlassRefraction.render(
                wallpaper = source,
                paneRect = paneRect,
                windowSize = windowSize,
                outSize = paneSize,
                cornerRadiusPx = cornerRadiusPx,
                surfaceArgb = surfaceColor.toArgb(),
                // 1.0 = genuinely clear glass. AnimeBackground paints the wallpaper at full
                // opacity, so nothing may be mixed in behind the refraction: any value below 1
                // blends the (near-white) surface colour into every pane, which is exactly what
                // made the glass read as a translucent white slab instead of glass.
                backdropAlpha = 1.0f,
            )
        }
        if (computed != null) GlassBackdropCache.put(key, computed)
        // Keep the previous frame if this one failed, rather than dropping to the flat fallback.
        value = computed ?: value
    }

    // Vibrancy: choose the label colour from what the glass actually shows, then pick overlays
    // that keep that contrast. Every overlay stays neutral (never theme-tinted), otherwise the
    // glass picks up a colour cast and reads as a dirty gradient.
    val rendered = backdrop
    val lightBackdrop = (rendered?.meanLuma ?: 1f) >= 0.5f
    val contentColor = when {
        rendered == null -> MaterialTheme.colorScheme.onSurface
        lightBackdrop -> Color.Black.copy(alpha = 0.88f)
        else -> Color.White.copy(alpha = 0.96f)
    }
    val veil = when {
        rendered == null -> Color.Transparent
        lightBackdrop -> Color.Black.copy(alpha = 0.05f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    // Liquid glass is gone and the surfaces are opaque, so the lens-era sheen and inset shading would
    // only read as a gradient artefact on a flat card. They stay for the (now unused) refracted path.
    val topHighlight = when {
        rendered == null -> 0f
        lightBackdrop -> 0.12f
        else -> 0.24f
    }
    val insetShadow = if (rendered == null) 0f else if (lightBackdrop) 0.07f else 0.14f

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                paneSize = coordinates.size
                paneRect = Rect(
                    offset = coordinates.positionInWindow(),
                    size = Size(
                        coordinates.size.width.toFloat(),
                        coordinates.size.height.toFloat(),
                    ),
                )
                windowSize = coordinates.rootLayoutSize()
            }
            .clip(shape),
    ) {
        if (rendered != null) {
            Image(
                bitmap = rendered.image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
            )
        } else if (wallpaper == null) {
            // Nothing to refract at all (the wallpaper switch is off). A see-through pane would be
            // an invisible ghost on the plain theme surface — cards and the bar would lose every
            // edge. Fall back to a defined frosted material so they still read as surfaces.
            // Neutral near-white / near-black rather than surfaceContainer. The latter is a fixed
            // lavender-tinted M3 baseline (#F3EDF7) which, laid over a saturated wallpaper as a
            // large opaque slab, reads as a muddy purple block and clashes with the wallpaper's own
            // dominant colour. `surface` is neutral, so the cards read as clean cards on a photo.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface),
            )
        } else {
            // A wallpaper exists but its first refraction has not landed yet. Stay almost invisible
            // so this frame already reads as glass rather than as a downgrade: the pane sits
            // directly on the wallpaper, so showing it unrefracted is already glass-like.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (dark) Color.White.copy(alpha = 0.05f)
                        else Color.White.copy(alpha = 0.08f)
                    ),
            )
        }

        // Legibility veil — very light, the refraction must stay visible.
        if (veil != Color.Transparent) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(veil),
            )
        }

        // Reference inset shadow: `0 -10px 25px inset rgba(0, 0, 0, 0.15)`.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = insetShadow),
                    )
                ),
        )

        // Specular rim: a light band along the top edge only.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = topHighlight),
                        0.16f to Color.Transparent,
                    )
                ),
        )
        // Hairline edge: bright on dark backdrops, gently dark on light ones.
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 0.75.dp,
                    // No backdrop at all counts as a light one, so the frosted fallback gets the
                    // darker hairline instead of an invisible white one.
                    brush = if (lightBackdrop) {
                        Brush.linearGradient(
                            0f to Color.Black.copy(alpha = 0.12f),
                            0.55f to Color.Black.copy(alpha = 0.04f),
                            1f to Color.Black.copy(alpha = 0.08f),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        )
                    } else {
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = if (dark) 0.30f else 0.50f),
                            0.55f to Color.White.copy(alpha = 0.06f),
                            1f to Color.White.copy(alpha = if (dark) 0.16f else 0.24f),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        )
                    },
                    shape = shape,
                ),
        )

        content(contentColor)
    }
}
