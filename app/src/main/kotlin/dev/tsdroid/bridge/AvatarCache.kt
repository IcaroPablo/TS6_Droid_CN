package dev.tsdroid.bridge

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.tsdroid.data.DeviceCipher
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Avatars by uid: in memory, and on disk encrypted (see [DeviceCipher]) under hashed
 * names, since a list of friends' uids is itself worth hiding.
 */
class AvatarCache(private val cacheDir: File) {

    companion object {
        private const val TAG = "AvatarCache"
    }

    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()
    private val loading = ConcurrentHashMap<String, Boolean>()
    // Asked on every member list refresh (twice a second): a failed avatar waits its turn.
    private val retries = RetryBackoff()
    private val avatarsDir = File(cacheDir, "avatars_enc").also { it.mkdirs() }
    private val cipher = DeviceCipher()

    fun getAvatar(uid: String): ImageBitmap? = memoryCache[uid]

    /** False while a failed download of [uid]'s avatar waits for its next attempt, or was given up. */
    fun mayLoad(uid: String): Boolean = retries.canTry(uid)

    /** Clear memory cache for specific UIDs so next loadAvatar forces re-download */
    fun clearMemoryCache(vararg uids: String) {
        for (uid in uids) {
            memoryCache.remove(uid)
            loading.remove(uid)
            retries.reset(uid)
        }
        Log.d(TAG, "Cleared memory cache for ${uids.size} UID(s)")
    }

    /**
     * Convert a TS3 base64 UID to the avatar file path used by the server.
     *
     * The server stores avatars at `/avatar_{encoded}` where each byte of the
     * decoded UID is encoded as two characters in [a-p] (nibble + 'a').
     */
    fun uidToAvatarPath(base64Uid: String): String {
        val bytes = Base64.decode(base64Uid, Base64.DEFAULT)
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            sb.append(('a' + (unsigned shr 4)))
            sb.append(('a' + (unsigned and 0x0F)))
        }
        return "/avatar_$sb"
    }

    suspend fun loadAvatar(uid: String, tsClient: TsClient) {
        if (uid.isEmpty()) return
        if (memoryCache.containsKey(uid)) return
        if (!mayLoad(uid)) return
        if (loading.putIfAbsent(uid, true) != null) return

        try {
            // Disk I/O and the JNI download must not run on the caller's
            // (main) dispatcher — both call sites collect from the UI side
            withContext(Dispatchers.IO) {
                val diskFile = File(avatarsDir, DeviceCipher.hashedName(uid))
                var bytes: ByteArray? = null
                var fromDisk = false

                // Try disk cache first
                if (diskFile.exists() && diskFile.length() > 0) {
                    bytes = try {
                        cipher.readFile(diskFile)
                    } catch (e: Exception) {
                        Log.w(TAG, "Unreadable avatar on disk", e)
                        ByteArray(0)
                    }
                    fromDisk = true
                    Log.d(TAG, "Read avatar from disk for $uid (${bytes.size} bytes)")
                }

                // Try decoding if we have disk data
                if (fromDisk && bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        memoryCache[uid] = bitmap.asImageBitmap()
                        Log.d(TAG, "Loaded avatar from disk cache for $uid")
                        return@withContext
                    }
                    Log.w(TAG, "Corrupt avatar on disk for $uid, deleting and re-downloading")
                    diskFile.delete()
                    bytes = null
                }

                // Download from server using UID-based path
                val path = uidToAvatarPath(uid)
                Log.d(TAG, "Downloading avatar for $uid at path $path")
                bytes = when (val result = tsClient.download(0L, path)) {
                    is TsClient.Download.Ok -> result.data
                    is TsClient.Download.Failed -> {
                        retries.failed(uid, result.permanent)
                        return@withContext
                    }
                }
                if (bytes.isEmpty()) {
                    retries.failed(uid, permanent = true)
                    return@withContext
                }

                Log.d(TAG, "Downloaded avatar for $uid: ${bytes.size} bytes")

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    cipher.writeFile(diskFile, bytes)
                    memoryCache[uid] = bitmap.asImageBitmap()
                    retries.succeeded(uid)
                    Log.i(TAG, "Avatar loaded for $uid")
                } else {
                    Log.w(TAG, "Failed to decode downloaded avatar (${bytes.size} bytes)")
                    retries.failed(uid, permanent = true)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load avatar", e)
            retries.failed(uid, permanent = false)
        } finally {
            loading.remove(uid)
        }
    }
}