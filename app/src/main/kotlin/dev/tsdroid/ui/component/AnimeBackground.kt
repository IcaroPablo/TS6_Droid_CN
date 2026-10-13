package dev.tsdroid.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object AnimeWallpaperState {
    val currentUrl = mutableStateOf<String?>(null)
    val customBitmap = mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    val dominantColor = mutableStateOf<Color?>(null)

    /**
     * Latest decoded wallpaper (online or cached file). Used as the blurred backdrop of the
     * liquid-glass surfaces, so they never have to decode the image a second time.
     */
    val wallpaperBitmap = mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)

    /** The image the background is actually painting right now, if any. */
    val activeBitmap: androidx.compose.ui.graphics.ImageBitmap?
        get() = customBitmap.value ?: wallpaperBitmap.value

    private var fetched = false

    suspend fun ensureFetched(context: Context) {
        if (fetched) return
        fetched = true
        WallpaperCacheManager.init(context)
        refreshCustomBackground(context)
        if (customBitmap.value == null) {
            fetchOnlineWallpaper(context)
        }
    }

    fun refreshCustomBackground(context: Context) {
        val customFile = CustomBackgroundManager.getActiveBackground(context)
        if (customFile != null) {
            val bm = BitmapFactory.decodeFile(customFile.absolutePath)
            if (bm != null) {
                customBitmap.value = bm.asImageBitmap()
                extractColorFromBitmap(bm)
            }
        } else {
            customBitmap.value = null
        }
    }

    private suspend fun fetchOnlineWallpaper(context: Context) {
        withContext(Dispatchers.IO) {
            var networkSuccess = false
            try {
                val url = URL("https://www.loliapi.com/acg/pe/")
                val conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val redirect = conn.getHeaderField("Location")
                val finalUrl = if (!redirect.isNullOrBlank()) redirect else conn.url.toString()
                conn.disconnect()
                currentUrl.value = finalUrl

                val imgConn = URL(finalUrl).openConnection() as HttpURLConnection
                imgConn.connectTimeout = 8000
                imgConn.readTimeout = 8000
                val bitmap = BitmapFactory.decodeStream(imgConn.inputStream)
                imgConn.disconnect()
                if (bitmap != null) {
                    extractColorFromBitmap(bitmap)
                    WallpaperCacheManager.saveToCache(context, finalUrl)
                    networkSuccess = true
                }
            } catch (_: Exception) {
            }
            if (!networkSuccess) {
                WallpaperCacheManager.getRandomCachedFile()?.let { cached ->
                    BitmapFactory.decodeFile(cached.absolutePath)?.let { bm ->
                        currentUrl.value = "file://${cached.absolutePath}"
                        extractColorFromBitmap(bm)
                    }
                }
            }
        }
    }

    private fun extractColorFromBitmap(bitmap: Bitmap) {
        wallpaperBitmap.value = bitmap.asImageBitmap()
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return
        val scaled = Bitmap.createScaledBitmap(mutable, 48, 48, true)
        var r = 0; var g = 0; var b = 0; var count = 0
        for (x in 0 until scaled.width) {
            for (y in 0 until scaled.height) {
                val pixel = scaled.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) > 128) {
                    r += android.graphics.Color.red(pixel)
                    g += android.graphics.Color.green(pixel)
                    b += android.graphics.Color.blue(pixel)
                    count++
                }
            }
        }
        if (count > 0) {
            dominantColor.value = Color(
                red = (r.toFloat() / count / 255f).coerceIn(0f, 1f),
                green = (g.toFloat() / count / 255f).coerceIn(0f, 1f),
                blue = (b.toFloat() / count / 255f).coerceIn(0f, 1f),
            )
        }
    }

    fun extractDominantColor(result: ImageResult) {
        if (result !is SuccessResult) return
        // Coil can hand back any Drawable (bitmap, crossfade, wrapper). Requiring a
        // BitmapDrawable bailed out silently, which left `wallpaperBitmap` empty: every glass
        // surface then fell back to its opaque material while the wallpaper still displayed.
        val bitmap = runCatching { result.drawable.toBitmap() }.getOrNull() ?: return
        extractColorFromBitmap(bitmap)
    }
}

@Composable
fun AnimeBackground(enabled: Boolean) {
    if (!enabled) return

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AnimeWallpaperState.ensureFetched(context)
    }

    val customBmp = AnimeWallpaperState.customBitmap.value
    val url = AnimeWallpaperState.currentUrl.value

    var imageLoaded by remember { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (imageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "bgFadeIn",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Painted at full strength: the glass panes refract exactly what is shown here, so
            // softening the wallpaper also washed out every pane that samples it.
            .alpha(1f)
    ) {
        if (customBmp != null) {
            imageLoaded = true
            androidx.compose.foundation.Image(
                bitmap = customBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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
                    .graphicsLayer { alpha = imageAlpha },
                onSuccess = { state ->
                    imageLoaded = true
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
