package dev.tsdroid.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The chat's backdrop: line doodles in the app's icon style (the 67 dog among headsets, notes and
 * bolts), as WhatsApp and Telegram have, faint enough to stay under the messages. One tile is drawn
 * once, for the colour and density in use, and repeated by a shader: scrolling costs nothing.
 */
@Composable
fun Modifier.chatPattern(): Modifier {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = PATTERN_ALPHA)
    val density = LocalDensity.current
    val brush = remember(color, density) {
        ShaderBrush(ImageShader(drawTile(color, density), TileMode.Repeated, TileMode.Repeated))
    }
    return drawBehind { drawRect(brush) }
}

private const val PATTERN_ALPHA = 0.07f

/** The tile's edge, and the doodles' place in it, in dp */
private const val TILE_DP = 240

/** Each doodle on a 24-unit grid, outlined 2 units wide like the app's icons, plus filled parts */
private class Doodle(strokes: List<String>, fills: List<String> = emptyList()) {
    val strokes = strokes.map { PathParser().parsePathString(it).toPath() }
    val fills = fills.map { PathParser().parsePathString(it).toPath() }
}

private val Dog = Doodle(
    strokes = listOf(
        "M7 8c0-3 2.2-5 5-5s5 2 5 5v5c0 3.5-2.2 6-5 6s-5-2.5-5-6z",
        "M7 6.5C5 6.5 3 9 3.5 13c.3 2 1.8 2.5 3.5 1.5",
        "M17 6.5c2 0 4 2.5 3.5 6.5-.3 2-1.8 2.5-3.5 1.5",
        "M12 15.5v1.5",
        "M10.5 17.5c.8.6 2.2.6 3 0",
    ),
    fills = listOf(
        "M9.2 10a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0-2.2 0z",
        "M12.6 10a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0-2.2 0z",
        "M10.4 13.6h3.2l-1.6 1.8z",
    ),
)
private val Paw = Doodle(
    strokes = emptyList(),
    fills = listOf(
        "M12 13c-3 0-5 2.5-5 4.5 0 1.5 1.2 2.5 2.5 2.5 1 0 1.5-.5 2.5-.5s1.5.5 2.5.5c1.3 0 2.5-1 2.5-2.5 0-2-2-4.5-5-4.5z",
        "M4.7 10a1.8 2.1 0 1 0 3.6 0a1.8 2.1 0 1 0-3.6 0z",
        "M7.7 6.3a1.8 2.1 0 1 0 3.6 0a1.8 2.1 0 1 0-3.6 0z",
        "M12.7 6.3a1.8 2.1 0 1 0 3.6 0a1.8 2.1 0 1 0-3.6 0z",
        "M15.7 10a1.8 2.1 0 1 0 3.6 0a1.8 2.1 0 1 0-3.6 0z",
    ),
)
private val SixtySeven = Doodle(
    listOf(
        "M11.5 4C7.5 5 5 8.5 5 13.5c0 4 1.8 6.5 4.5 6.5s4.5-2.2 4.5-4.8-1.8-4.7-4.5-4.7S5 12.6 5 15",
        "M15 4h6l-4.5 16",
    ),
)
private val Headset = Doodle(
    listOf("M4 15v-3a8 8 0 0 1 16 0v3", "M4 14h3v6H5a1 1 0 0 1-1-1z", "M20 14h-3v6h2a1 1 0 0 0 1-1z"),
)
private val Mic = Doodle(
    listOf("M12 2a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V5a3 3 0 0 1 3-3z", "M19 10v1a7 7 0 0 1-14 0v-1", "M12 18v4", "M8 22h8"),
)
private val Note = Doodle(
    listOf("M9 18V5l11-2v13", "M3 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0z", "M14 16a3 3 0 1 0 6 0a3 3 0 1 0-6 0z"),
)
private val Bolt = Doodle(listOf("M13 2L4 14h7l-1 8 9-12h-7z"))
private val Star = Doodle(listOf("M12 3l2.6 5.6 6 .7-4.5 4.1 1.2 6L12 16.4 6.7 19.4l1.2-6L3.4 9.3l6-.7z"))
private val Bubble = Doodle(
    listOf("M21 11.5a8.4 8.4 0 0 1-9 8.4 9 9 0 0 1-3.8-.9L3 21l1.9-5.2A8.4 8.4 0 0 1 12 3a8.5 8.5 0 0 1 9 8.5z"),
)

/** A doodle at (x, y) in the tile, [size] across, turned by [degrees]; all in dp */
private class Placed(val doodle: Doodle, val x: Int, val y: Int, val size: Int, val degrees: Float)

// Spread by hand so the repeat does not show: each doodle keeps clear of its neighbours across
// the tile's edges too
private val Tile = listOf(
    Placed(Dog, 20, 18, 38, -12f), Placed(Note, 100, 12, 26, 10f), Placed(Bolt, 170, 30, 26, 18f),
    Placed(Paw, 214, 96, 24, -25f), Placed(SixtySeven, 58, 84, 34, 8f), Placed(Headset, 130, 82, 30, -8f),
    Placed(Star, 196, 160, 22, 14f), Placed(Mic, 16, 140, 26, 16f), Placed(Bubble, 86, 150, 28, -10f),
    Placed(Paw, 146, 196, 22, 20f), Placed(Dog, 150, 128, 30, 14f), Placed(Bolt, 44, 206, 22, -16f),
    Placed(Star, 112, 226, 16, 0f), Placed(Note, 214, 8, 18, -14f),
)

private fun drawTile(color: Color, density: Density): ImageBitmap {
    val edge = with(density) { TILE_DP.dp.toPx() }
    val bitmap = ImageBitmap(edge.toInt(), edge.toInt())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(edge, edge)) {
        val unit = density.density
        for (placed in Tile) {
            val half = placed.size * unit / 2
            translate(placed.x * unit, placed.y * unit) {
                rotate(placed.degrees, pivot = Offset(half, half)) {
                    // From the 24-unit grid to the doodle's size, the outline scaling with it
                    scale(placed.size / 24f * unit, pivot = Offset.Zero) {
                        val outline = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        for (path in placed.doodle.strokes) drawPath(path, color, style = outline)
                        for (path in placed.doodle.fills) drawPath(path, color)
                    }
                }
            }
        }
    }
    return bitmap
}
