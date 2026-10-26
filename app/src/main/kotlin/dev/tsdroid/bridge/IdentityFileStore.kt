package dev.tsdroid.bridge

import android.content.Context
import dev.tsdroid.data.DeviceCipher
import dev.tslib.Identity
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Access to the persistent TeamSpeak identity, kept encrypted at filesDir/identity.enc
 * (see [DeviceCipher]). The content is the library's identity format, so an export
 * stays interchangeable with other clients; only the copy on the device is encrypted.
 * All methods do blocking IO/JNI — call from a background dispatcher.
 */
class IdentityFileStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, "identity.enc")

    private val cipher = DeviceCipher()

    fun exists(): Boolean = file.exists()

    /**
     * The stored identity, or null when there is none. Throws when an identity exists but cannot be decrypted: the caller must not
     * mistake that for "no identity" and replace the user's key with a new one.
     */
    fun load(): Identity? = readText()?.let { Identity.fromString(it) }

    /** The stored identity, or a new one saved for next time on first use. */
    fun loadOrCreate(): Identity {
        load()?.let { return it }
        val identity = Identity()
        write(identity.exportString())
        return identity
    }

    /** @return the UID of the stored identity, or null if none exists/it is invalid */
    fun readUniqueId(): String? {
        val identity = try {
            load()
        } catch (_: Throwable) {
            null
        } ?: return null
        return try {
            identity.uniqueId
        } catch (_: Throwable) {
            null
        } finally {
            identity.close()
        }
    }

    /**
     * Write the identity, in plaintext, to [out] — a file the user picked to take the
     * key elsewhere. @return false when no identity exists
     */
    fun exportTo(out: OutputStream): Boolean {
        val text = readText() ?: return false
        out.write(text.toByteArray(Charsets.UTF_8))
        return true
    }

    /**
     * Validate [input] as an identity, then replace the stored one. The new
     * identity takes effect on the next connection.
     *
     * @return true when a valid identity was imported
     */
    fun importFrom(input: InputStream): Boolean {
        val data = input.use { it.readBytes().toString(Charsets.UTF_8) }
        if (data.isBlank()) return false
        val identity = try {
            Identity.fromString(data)
        } catch (_: Throwable) {
            return false
        }
        val uid = try {
            identity.uniqueId
        } catch (_: Throwable) {
            null
        } finally {
            identity.close()
        }
        if (uid.isNullOrBlank()) return false

        return try {
            write(data)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readText(): String? {
        if (!file.exists()) return null
        return cipher.readFile(file).toString(Charsets.UTF_8)
    }

    private fun write(text: String) {
        cipher.writeFile(file, text.toByteArray(Charsets.UTF_8))
    }
}
