package dev.tsdroid.ui.component

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WallpaperCacheManager {
    private var cacheDir: File? = null
    private const val MAX_CACHE_SIZE_MB_DEFAULT = 100L

    fun init(context: Context) {
        cacheDir = File(context.cacheDir, "wallpaper_cache").also { it.mkdirs() }
    }

    fun getCacheDir(): File? = cacheDir

    fun getCachedFiles(): List<File> {
        return cacheDir?.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun getCachedFilesCount(): Int = getCachedFiles().size

    fun getCacheSizeBytes(): Long {
        return getCachedFiles().sumOf { it.length() }
    }

    fun getCacheSizeMB(): Float = getCacheSizeBytes() / (1024f * 1024f)

    fun getRandomCachedFile(): File? {
        val files = getCachedFiles()
        if (files.isEmpty()) return null
        return files.random()
    }

    fun clearCache() {
        cacheDir?.listFiles()?.forEach { it.delete() }
    }

    fun deleteFile(file: File) {
        if (file.parentFile == cacheDir) file.delete()
    }
}

object SettingsCacheSizeHelper {
    private const val KEY_MAX_CACHE_SIZE = "wallpaper_max_cache_size_mb"
    private const val MAX_CACHE_SIZE_DEFAULT = 100L

    fun getMaxCacheSize(context: Context): Long {
        val prefs = context.getSharedPreferences("wallpaper_cache_prefs", Context.MODE_PRIVATE)
        return prefs.getLong(KEY_MAX_CACHE_SIZE, MAX_CACHE_SIZE_DEFAULT)
    }

    fun setMaxCacheSize(context: Context, sizeMB: Long) {
        val prefs = context.getSharedPreferences("wallpaper_cache_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_MAX_CACHE_SIZE, sizeMB).apply()
    }
}

/**
 * Copies cached wallpapers into the device gallery (`Pictures/TeleSpeak`).
 *
 * The cache stores whatever bytes the server sent under a fixed `.jpg` name, and the anime
 * endpoints serve PNG as often as JPEG. The real type is therefore sniffed from the file header
 * before publishing: an image registered under the wrong MIME type shows up as a broken thumbnail
 * in most galleries.
 */
object WallpaperExporter {

    private const val ALBUM = "TeleSpeak"

    /** Writes [files] into the gallery; returns how many of them were saved. */
    suspend fun export(context: Context, files: List<File>): Int = withContext(Dispatchers.IO) {
        files.count { exportOne(context, it) }
    }

    /** Writes a single cached file into the gallery. */
    suspend fun export(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        exportOne(context, file)
    }

    private fun exportOne(context: Context, file: File): Boolean = try {
        val (extension, mimeType) = sniff(file)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "TS6_${stamp}_${file.nameWithoutExtension.take(6)}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            // Hidden until the bytes are in place, so no gallery ever sees a half-written image.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val copied = try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } != null
        } catch (_: Exception) {
            false
        }
        if (copied) {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        } else {
            context.contentResolver.delete(uri, null, null)
        }
        copied
    } catch (_: Exception) {
        false
    }

    /** Extension and MIME type of [file], read from its magic bytes. */
    private fun sniff(file: File): Pair<String, String> {
        val header = ByteArray(12)
        val read = try {
            file.inputStream().use { it.read(header) }
        } catch (_: Exception) {
            -1
        }
        return when {
            read >= 8 && matches(header, 0, 0x89, 0x50, 0x4E, 0x47) -> "png" to "image/png"
            read >= 3 && matches(header, 0, 0xFF, 0xD8, 0xFF) -> "jpg" to "image/jpeg"
            read >= 6 && matches(header, 0, 0x47, 0x49, 0x46, 0x38) -> "gif" to "image/gif"
            read >= 12 && matches(header, 0, 0x52, 0x49, 0x46, 0x46) &&
                matches(header, 8, 0x57, 0x45, 0x42, 0x50) -> "webp" to "image/webp"
            else -> "jpg" to "image/jpeg"
        }
    }

    private fun matches(bytes: ByteArray, offset: Int, vararg expected: Int): Boolean =
        expected.indices.all { (bytes[offset + it].toInt() and 0xFF) == expected[it] }
}
