package dev.tsdroid.ui.component

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.tsdroid.han.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The largest file the app uploads. */
private const val MAX_UPLOAD_BYTES = 10L * 1024 * 1024

/**
 * A file picker for uploads: the returned function opens it, and [onPicked]
 * gets the chosen file's name and contents. A file over 10 MB is refused
 * with a message.
 */
@Composable
fun rememberUploadPicker(onPicked: (name: String, data: ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Reading up to 10 MB must not happen on the main thread
            when (val file = withContext(Dispatchers.IO) { readUpload(context, uri) }) {
                is Upload.File -> latestOnPicked(file.name, file.data)
                Upload.TooLarge -> Toast.makeText(context, R.string.file_too_large, Toast.LENGTH_SHORT).show()
                // Unreadable content: nothing to send
                null -> {}
            }
        }
    }
    return { launcher.launch("*/*") }
}

private sealed class Upload {
    class File(val name: String, val data: ByteArray) : Upload()
    data object TooLarge : Upload()
}

private fun readUpload(context: Context, uri: Uri): Upload? = try {
    var name = "file"
    var size = -1L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
            if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
        }
    }
    if (size > MAX_UPLOAD_BYTES) {
        Upload.TooLarge
    } else {
        val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        when {
            data == null -> null
            data.size > MAX_UPLOAD_BYTES -> Upload.TooLarge
            else -> Upload.File(name, data)
        }
    }
} catch (_: Exception) {
    null
}

/**
 * A file size in the UI language's units (the hardcoded "Ko"/"Mo"/"Go" were
 * French-only).
 */
fun formatFileSize(context: Context, bytes: Long): String {
    if (bytes < 1024) return context.getString(R.string.file_size_bytes, bytes)
    val kb = bytes / 1024.0
    if (kb < 1024) return context.getString(R.string.file_size_kb, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return context.getString(R.string.file_size_mb, mb)
    return context.getString(R.string.file_size_gb, mb / 1024.0)
}
