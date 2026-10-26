package dev.tsdroid.bridge

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.tsdroid.data.DeviceCipher
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Server and channel icons: in memory, and on disk encrypted (see [DeviceCipher]). */
class IconCache(private val cacheDir: File) {

    companion object {
        private const val TAG = "IconCache"
    }

    private val memoryCache = ConcurrentHashMap<Long, ImageBitmap>()
    private val loading = ConcurrentHashMap<Long, Boolean>()
    private val iconsDir = File(cacheDir, "icons_enc").also { it.mkdirs() }
    private val cipher = DeviceCipher()
    private val retries = RetryBackoff()

    private fun diskFile(iconId: Long) = File(iconsDir, DeviceCipher.hashedName(iconId.toString()))

    /** The icon from the disk cache only, or null; blocking, call off the main thread. */
    fun readCached(iconId: Long): ImageBitmap? {
        val file = diskFile(iconId)
        if (!file.exists()) return null
        return try {
            val bytes = cipher.readFile(file)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable icon on disk: $iconId", e)
            null
        }
    }

    fun getIcon(iconId: Long): ImageBitmap? = memoryCache[iconId]

    fun hasCached(iconId: Long): Boolean =
        memoryCache.containsKey(iconId) || diskFile(iconId).exists()

    suspend fun loadIcon(iconId: Long, tsClient: TsClient) {
        if (iconId == 0L) return
        if (memoryCache.containsKey(iconId)) return
        // Asked on every channel list refresh (twice a second): a failed icon waits its turn.
        if (!retries.canTry(iconId)) return
        if (loading.putIfAbsent(iconId, true) != null) return

        try {
            // Try disk cache first
            val diskFile = diskFile(iconId)
            val bytes = if (diskFile.exists() && diskFile.length() > 0) {
                cipher.readFile(diskFile)
            } else {
                // Download from server: icons are on channel 0, path /icon_{id}
                when (val result = tsClient.download(0L, "/icon_$iconId")) {
                    is TsClient.Download.Ok -> result.data.also {
                        if (it.isNotEmpty()) cipher.writeFile(diskFile, it)
                    }
                    is TsClient.Download.Failed -> {
                        retries.failed(iconId, result.permanent)
                        return
                    }
                }
            }

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                memoryCache[iconId] = bitmap.asImageBitmap()
                retries.succeeded(iconId)
            } else {
                Log.w(TAG, "Failed to decode icon $iconId (${bytes.size} bytes)")
                diskFile.delete()
                retries.failed(iconId, permanent = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load icon $iconId", e)
            retries.failed(iconId, permanent = false)
        } finally {
            loading.remove(iconId)
        }
    }
}
