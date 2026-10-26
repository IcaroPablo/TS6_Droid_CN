package dev.tsdroid.data

import android.content.Context
import android.util.Log
import java.io.File

private const val TAG = "PlaintextMigration"

/**
 * Encrypts what builds before on-device encryption kept in plaintext: the identity and
 * the chat history; drops the plaintext avatar and icon caches. Their content is unchanged, so each file is encrypted as it is
 * into its new name and the plaintext copy deleted once that is on disk. Where an
 * encrypted copy already exists it wins, and the plaintext one is only deleted. The
 * bookmark list goes with the bookmark store's own migration.
 *
 * Cheap when there is nothing left, so it simply runs on every start, before anything
 * reads these stores.
 */
fun migratePlaintextData(context: Context) {
    val cipher = DeviceCipher()
    val files = context.filesDir
    migrateFile(cipher, File(files, "identity.ini"), File(files, "identity.enc"))
    File(files, "identity.import.tmp").delete()
    File(files, "messages").listFiles { file -> file.name.endsWith(".json") }?.forEach { json ->
        migrateFile(cipher, json, File(json.parentFile, json.name.removeSuffix(".json") + ".enc"))
    }
    // Plaintext avatar and icon caches: downloaded again on demand, so dropped, not moved.
    File(context.cacheDir, "avatars").deleteRecursively()
    File(context.cacheDir, "icons").deleteRecursively()
}

private fun migrateFile(cipher: DeviceCipher, plain: File, encrypted: File) {
    if (!plain.exists()) return
    try {
        if (!encrypted.exists()) cipher.writeFile(encrypted, plain.readBytes())
        plain.delete()
    } catch (e: Exception) {
        // Leave the plaintext copy for the next start rather than lose it.
        Log.e(TAG, "Failed to encrypt ${plain.name}", e)
    }
}
