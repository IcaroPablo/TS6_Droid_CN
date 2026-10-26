package dev.tsdroid.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import dev.tsdroid.background.CustomBackgroundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AnimeWallpaperState {

    private const val TAG = "AnimeWallpaper"

    /** Edge of the square reduction the theme colour is measured from. */
    private const val COLOR_SAMPLE = 48

    val currentUrl = mutableStateOf<String?>(null)
    val customBitmap = mutableStateOf<ImageBitmap?>(null)
    val dominantColor = mutableStateOf<Color?>(null)

    /**
     * Latest decoded wallpaper (online or cached file). Used as the blurred backdrop of the
     * liquid-glass surfaces, so they never have to decode the image a second time.
     */
    val wallpaperBitmap = mutableStateOf<ImageBitmap?>(null)

    /** True once the pipeline has looked everywhere, whether or not it found an image. */
    val settled = mutableStateOf(false)

    /** The image the background is actually painting right now, if any. */
    val activeBitmap: ImageBitmap?
        get() = customBitmap.value ?: wallpaperBitmap.value

    /**
     * True once there is an image to paint.
     *
     * This is what the splash waits on: the app is only let in when the wallpaper behind it is
     * already decoded, because entering earlier means watching it arrive.
     */
    val paintReady: Boolean
        get() = activeBitmap != null

    /**
     * The pipeline runs in a scope of its own, not in the caller's.
     *
     * Neither the splash nor the activity that started it lives long enough to be trusted with it:
     * the splash hands over before a slow download has finished, and a rotation rebuilds the whole
     * activity. Cancelling the fetch with either of them used to leave the session with no wallpaper
     * at all, because the "already fetched" flag stayed set while the work it guarded was gone.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var started = false

    /**
     * Starts the wallpaper pipeline — the user's own background, or a wallpaper left in the cache
     * by earlier builds — at most once per process.
     *
     * [wanted] is the wallpaper setting. With it off nothing is decoded: the app never paints a
     * wallpaper then.
     */
    fun ensureFetched(context: Context, wanted: Boolean) {
        if (started || !wanted) return
        started = true
        // Held across the whole fetch: the pipeline must not keep a destroyed activity alive.
        val appContext = context.applicationContext
        scope.launch {
            WallpaperCacheManager.init(appContext)
            refreshCustomBackground(appContext)
            if (customBitmap.value == null) {
                useCachedWallpaper()
            }
            settled.value = true
        }
    }

    /**
     * Re-reads the user's own background image, if there is one.
     *
     * The decode is off the main thread: it is a full-size JPEG and it runs while the splash is on
     * screen, which is the one place a stalled frame is impossible to miss.
     */
    suspend fun refreshCustomBackground(context: Context) {
        val customFile = CustomBackgroundManager.getActiveBackground(context)
        val bitmap = customFile?.let { file ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
        }
        if (bitmap != null) {
            // Published before it is recorded as the custom background: `paintReady`, which the splash
            // hands over on, is set in the same step as the theme colour, so the app cannot open on a
            // palette that is still to arrive.
            publishWallpaper(bitmap)
            customBitmap.value = bitmap.asImageBitmap()
        } else {
            // No background of their own — or one that no longer decodes. Cleared rather than left
            // in place, so `ensureFetched` falls through to the online wallpaper instead of painting
            // a stale image over a file the user has since deleted.
            customBitmap.value = null
        }
    }

    /**
     * One of the wallpapers earlier builds downloaded, picked at random so consecutive starts do
     * not all show the same picture. Nothing is downloaded any more: the app talks to no server but
     * the user's own (see the README), so without a custom background or such a leftover the
     * background stays plain.
     */
    private suspend fun useCachedWallpaper() {
        withContext(Dispatchers.IO) {
            WallpaperCacheManager.getRandomCachedFile()?.let { cached ->
                BitmapFactory.decodeFile(cached.absolutePath)?.let { bitmap ->
                    currentUrl.value = "file://${cached.absolutePath}"
                    publishWallpaper(bitmap)
                }
            }
        }
    }

    /**
     * Publishes [bitmap] as the wallpaper and measures the colour the theme is seeded with.
     *
     * Both happen off the main thread. The measurement also no longer copies the whole image first:
     * a custom background is decoded on the way into the app, and that copy plus a `getPixel` call
     * per sample was megabytes of allocation and thousands of JNI round trips on a frame that was
     * only just starting up.
     */
    private suspend fun publishWallpaper(bitmap: Bitmap) {
        val color = withContext(Dispatchers.Default) { averageColorOf(bitmap) }
        wallpaperBitmap.value = bitmap.asImageBitmap()
        if (color != null) dominantColor.value = color
    }

    /** Mean colour of the opaque pixels of [bitmap], measured from a [COLOR_SAMPLE]² reduction. */
    private fun averageColorOf(bitmap: Bitmap): Color? {
        val scaled = Bitmap.createScaledBitmap(bitmap, COLOR_SAMPLE, COLOR_SAMPLE, true)
        val pixels = IntArray(COLOR_SAMPLE * COLOR_SAMPLE)
        scaled.getPixels(pixels, 0, COLOR_SAMPLE, 0, 0, COLOR_SAMPLE, COLOR_SAMPLE)
        // `createScaledBitmap` hands back the source itself when it is already the right size.
        if (scaled !== bitmap) scaled.recycle()

        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (pixel in pixels) {
            if (android.graphics.Color.alpha(pixel) > 128) {
                r += android.graphics.Color.red(pixel)
                g += android.graphics.Color.green(pixel)
                b += android.graphics.Color.blue(pixel)
                count++
            }
        }
        if (count == 0) return null
        return Color(
            red = (r.toFloat() / count / 255f).coerceIn(0f, 1f),
            green = (g.toFloat() / count / 255f).coerceIn(0f, 1f),
            blue = (b.toFloat() / count / 255f).coerceIn(0f, 1f),
        )
    }

    fun extractDominantColor(result: ImageResult) {
        if (result !is SuccessResult) return
        // Coil can hand back any Drawable (bitmap, crossfade, wrapper). Requiring a
        // BitmapDrawable bailed out silently, which left `wallpaperBitmap` empty: every glass
        // surface then fell back to its opaque material while the wallpaper still displayed.
        val bitmap = runCatching { result.drawable.toBitmap() }.getOrNull() ?: return
        scope.launch { publishWallpaper(bitmap) }
    }
}

@Composable
fun AnimeBackground(enabled: Boolean) {
    if (!enabled) return

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AnimeWallpaperState.ensureFetched(context, wanted = true)
    }

    // Painted from the decoded bitmap, never from the URL. The splash does not hand over until this
    // bitmap is in memory, so the first frame behind it already shows the wallpaper; giving the URL
    // to Coil instead downloaded the same image a second time and faded it in over 600 ms, which is
    // the wallpaper arriving after the app that the splash is supposed to rule out.
    val wallpaper = AnimeWallpaperState.activeBitmap
    val url = AnimeWallpaperState.currentUrl.value

    // No fade when it is already there (the normal case — anything else is a late arrival the splash
    // gave up on, and that one fades in rather than snapping on under the UI).
    var appeared by remember { mutableStateOf(wallpaper != null) }
    val wallpaperAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "bgFadeIn",
    )
    LaunchedEffect(wallpaper) {
        if (wallpaper != null) appeared = true
    }

    // Coil is the last resort: a cached file (always local) whose image never became a bitmap.
    var fallbackLoaded by remember { mutableStateOf(false) }
    val fallbackAlpha by animateFloatAsState(
        targetValue = if (fallbackLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "bgFadeInFallback",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Painted at full strength: the glass panes refract exactly what is shown here, so
            // softening the wallpaper also washed out every pane that samples it.
            .alpha(1f)
    ) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = wallpaperAlpha },
            )
        } else if (url != null) {
            AsyncImage(
                // Software bitmaps only: the glass refraction reads pixels back out of this
                // image, and a hardware bitmap cannot be read that way.
                model = ImageRequest.Builder(context).data(url).allowHardware(false).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fallbackAlpha },
                onSuccess = { state ->
                    fallbackLoaded = true
                    AnimeWallpaperState.extractDominantColor(state.result)
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.03f),
                            Color.White.copy(alpha = 0.08f),
                        )
                    )
                )
        )
    }
}
