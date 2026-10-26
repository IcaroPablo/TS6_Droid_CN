package dev.tsdroid.bridge

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dev.tsdroid.data.DeviceCipher
import java.io.File

/**
 * Files downloaded from a server (chat images, attachments, file browser).
 *
 * With [encrypted] on (the default) they are kept in app-private storage, encrypted
 * like the rest of the app's data (see [DeviceCipher]); opening one in another app
 * goes through a short-lived decrypted copy in the private cache, shared by
 * [FileProvider] and deleted on the next start ([clearOpenedCopies]). With it off
 * they go to the public Documents/TeleSpeak/{serverHost}/ folder, where the gallery
 * and file managers see them.
 *
 * Lookups check both places, so switching the setting keeps earlier downloads
 * reachable; new downloads follow the current setting.
 */
class FileCache(private val context: Context, private val encrypted: () -> Boolean) {

    companion object {
        private const val TAG = "FileCache"
        private const val BASE_FOLDER = "TeleSpeak"
        private const val PRIVATE_DIR = "attachments"
        private const val OPENED_DIR = "opened"

        /** Deletes the decrypted copies handed to other apps during an earlier run. */
        fun clearOpenedCopies(context: Context) {
            File(context.cacheDir, OPENED_DIR).deleteRecursively()
        }
    }

    private val contentResolver = context.contentResolver
    private val cipher = DeviceCipher()

    /** Returns the file from the local cache, or null if it is not there. */
    fun get(serverHost: String, relativePath: String): ByteArray? {
        val file = privateFile(serverHost, relativePath)
        if (file.exists()) {
            return try {
                cipher.readFile(file)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decrypt cached file $relativePath", e)
                null
            }
        }
        val uri = findPublicUri(serverHost, relativePath) ?: return null
        return try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached file $relativePath", e)
            null
        }
    }

    /** Stores the bytes, encrypted or in the public folder depending on the setting. Overwrites. */
    fun put(serverHost: String, relativePath: String, data: ByteArray) {
        if (encrypted()) {
            try {
                cipher.writeFile(privateFile(serverHost, relativePath), data)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache $relativePath", e)
            }
        } else {
            putPublic(serverHost, relativePath, data)
        }
    }

    fun exists(serverHost: String, relativePath: String): Boolean {
        return privateFile(serverHost, relativePath).exists() || findPublicUri(serverHost, relativePath) != null
    }

    /** A content URI another app can open, or null if the file is not cached. */
    fun getUri(serverHost: String, relativePath: String): Uri? {
        val file = privateFile(serverHost, relativePath)
        if (!file.exists()) return findPublicUri(serverHost, relativePath)
        return try {
            val opened = File(File(context.cacheDir, OPENED_DIR), relativePath.substringAfterLast('/'))
            opened.parentFile?.mkdirs()
            opened.writeBytes(cipher.readFile(file))
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", opened)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open cached file $relativePath", e)
            null
        }
    }

    private fun privateFile(serverHost: String, relativePath: String): File {
        // Neither part comes from us: keep them from climbing out of the folder.
        val safeHost = serverHost.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safePath = relativePath.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")
        return File(File(File(context.filesDir, PRIVATE_DIR), safeHost), "$safePath.enc")
    }

    private fun publicRelativePath(serverHost: String, relativePath: String): String {
        val subDir = relativePath.substringBeforeLast('/', "")
        return buildString {
            append("Documents/$BASE_FOLDER/")
            append(serverHost)
            if (subDir.isNotEmpty()) {
                append("/")
                append(subDir)
            }
        }
    }

    private fun putPublic(serverHost: String, relativePath: String, data: ByteArray) {
        try {
            // Delete existing file if any
            findPublicUri(serverHost, relativePath)?.let {
                contentResolver.delete(it, null, null)
            }

            val fileName = relativePath.substringAfterLast('/')
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"

            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, publicRelativePath(serverHost, relativePath))
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }

            val collection = MediaStore.Files.getContentUri("external")
            val uri = contentResolver.insert(collection, values) ?: run {
                Log.w(TAG, "Failed to insert file into MediaStore: $relativePath")
                return
            }

            contentResolver.openOutputStream(uri)?.use { it.write(data) }

            values.clear()
            values.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache $relativePath", e)
        }
    }

    private fun findPublicUri(serverHost: String, relativePath: String): Uri? {
        val fileName = relativePath.substringAfterLast('/')
        val relPath = publicRelativePath(serverHost, relativePath) + "/"

        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, relPath)

        return try {
            contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    ContentUris.withAppendedId(collection, id)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query MediaStore for $relativePath", e)
            null
        }
    }
}
