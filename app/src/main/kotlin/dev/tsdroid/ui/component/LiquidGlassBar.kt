package dev.tsdroid.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.tsdroid.ui.theme.Shapes

/**
 * The frosted surface every panel and card is built from: the wallpaper actually behind the pane,
 * blurred, with the reference's inset bottom shadow, a specular rim and a soft drop shadow on top.
 *
 * When [wallpaper] is null — the anime background is switched off, or nothing has been decoded yet
 * — the pane paints its own `backgroundColor` instead, so a card still reads as a surface.
 */
@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    wallpaper: ImageBitmap? = null,
    shape: Shape = Shapes.Standard,
    elevation: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable BoxScope.(contentColor: Color) -> Unit,
) {
    // Without a wallpaper there is nothing to frost: the pane is a flat surface with a hairline
    // edge. No shadow, no blurred backdrop and no full-size gradient layers, which a pane the size
    // of the screen would otherwise redraw on every frame of every animation inside it.
    if (wallpaper == null) {
        val contentColor = MaterialTheme.colorScheme.onSurface
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(contentPadding),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content(contentColor)
            }
        }
        return
    }
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
            // Deliberately not matchParentSize(): the content must be able to size this panel.
            // The backdrop is painted in the draw phase, which never takes part in measurement, so
            // a pane can no longer be inflated by its own backdrop.
            modifier = Modifier,
        ) { contentColor ->
            Box(modifier = Modifier.padding(contentPadding)) {
                content(contentColor)
            }
        }
    }
}

/**
 * Size of the composition root: the layout the full-screen wallpaper fills, and therefore the
 * space a pane's backdrop position is measured in.
 */
private fun LayoutCoordinates.rootLayoutSize(): IntSize {
    var current: LayoutCoordinates = this
    while (true) {
        current = current.parentLayoutCoordinates ?: return current.size
    }
}

/**
 * The frosted material itself: the wallpaper behind the pane, blurred, with a legibility veil, the
 * reference's inset bottom shadow and a specular rim on top.
 */
@Composable
private fun LiquidGlassSurface(
    wallpaper: ImageBitmap?,
    shape: Shape,
    modifier: Modifier = Modifier,
    /**
     * Painted behind the content when there is no wallpaper to blur — and only then.
     *
     * Opaque on purpose, and a container token rather than `surface`: the page behind a pane *is*
     * `surface`, so a translucent `surface` fill composites to exactly the background colour and the
     * pane stops reading as a surface at all. With the anime background switched off, that left the
     * cards and the floating bar as hollow outlines with their own shadow showing through the middle.
     * `Color.Transparent` leaves only the rim, for a pane that must stay fully see-through.
     */
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.(contentColor: Color) -> Unit,
) {
    val dark = isSystemInDarkTheme()

    // Where this pane sits inside the root: the offset the backdrop is positioned from. Only ever
    // read in the draw phase below, so scrolling — which changes it every frame — redraws the pane
    // instead of recomposing it.
    var paneLeft by remember { mutableStateOf(0f) }
    var paneTop by remember { mutableStateOf(0f) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    // One blurred copy of the wallpaper, shared by every pane and rebuilt only when the wallpaper or
    // the root changes — never once per pane, and never per frame.
    val frosted = rememberFrostedWallpaper(wallpaper, rootSize)
    // Whether anything is painted behind this pane at all. The blurred copy may still be building;
    // until it arrives the pane simply stays transparent over the wallpaper the background paints.
    val hasBackdrop = wallpaper != null

    // Milky veil over the blurred backdrop — this is the frosted finish. Skipped for a pane that owns
    // no background at all and must stay see-through.
    val veil = when {
        backgroundColor == Color.Transparent -> Color.Transparent
        !hasBackdrop -> Color.Transparent
        dark -> Color.White.copy(alpha = 0.30f)
        else -> Color.White.copy(alpha = 0.46f)
    }
    // Vibrancy: choose the label colour from what the pane actually shows. The wallpaper's mean
    // luminance is measured once, while the blurred copy is built; the veil is nearly white, so it
    // is folded in before the decision.
    val effectiveLuma = frosted?.let { it.meanLuma * (1f - veil.alpha) + veil.alpha }
    val lightBackdrop = effectiveLuma?.let { it >= 0.5f } ?: !dark
    val contentColor = when {
        !hasBackdrop -> MaterialTheme.colorScheme.onSurface
        lightBackdrop -> Color.Black.copy(alpha = 0.88f)
        else -> Color.White.copy(alpha = 0.96f)
    }
    // The sheen and the inset shading only read as material over a backdrop; on the flat fallback
    // they would be a gradient artefact on a solid card.
    val topHighlight = when {
        !hasBackdrop -> 0f
        lightBackdrop -> 0.12f
        else -> 0.24f
    }
    val insetShadow = when {
        !hasBackdrop -> 0f
        lightBackdrop -> 0.07f
        else -> 0.14f
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                paneLeft = position.x
                paneTop = position.y
                rootSize = coordinates.rootLayoutSize()
            }
            .clip(shape)
            // The backdrop is painted here, in the draw phase, clipped to the pane's own size. Its
            // position comes from the pane's draw space instead of from a sized-and-offset child, so
            // it never takes part in measurement: a pane can neither be inflated by its own backdrop
            // nor end up with a strip of bare wallpaper inside it.
            .drawWithContent {
                val image = frosted?.image
                val root = rootSize
                if (image != null && root.width > 0 && root.height > 0) {
                    // The blurred copy is the whole wallpaper as the background lays it out, at half
                    // size, so drawing it over the root puts it exactly behind the pane.
                    clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
                        // Move the origin from this pane's top-left corner to the root's, which is
                        // where the wallpaper itself is painted from.
                        translate(left = -paneLeft, top = -paneTop) {
                            drawImage(
                                image = image,
                                dstSize = root,
                                filterQuality = FilterQuality.Low,
                            )
                        }
                    }
                } else if (wallpaper == null) {
                    // Nothing behind the pane at all (the wallpaper switch is off). A see-through
                    // pane would be an invisible ghost on the plain theme surface, so the pane falls
                    // back to a material of its own and still reads as a surface. Neutral near-white
                    // / near-black rather than surfaceContainer: the latter is a fixed
                    // lavender-tinted M3 baseline (#F3EDF7) and reads as a muddy purple block.
                    drawRect(color = backgroundColor)
                }
                // With a wallpaper but no blurred copy yet, nothing is painted: the wallpaper the
                // background is already drawing *is* the backdrop, so the pane is merely not frosted
                // for the frame or two the blur takes to build.
                drawContent()
            },
    ) {

        // Legibility veil over the frosted backdrop — light enough that the wallpaper still reads as
        // texture instead of as a flat tint.
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
