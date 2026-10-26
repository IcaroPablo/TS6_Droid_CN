package dev.tsdroid.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts what the app keeps on the device (chat history, the TeamSpeak identity,
 * server bookmarks) with AES-256-GCM.
 *
 * The key lives in the Android Keystore and cannot be exported, so only this app on
 * this device can read the data: a copied file, a backup or another app gets
 * ciphertext. The key needs no user authentication, because messages keep arriving
 * (and are saved) while the phone is locked.
 *
 * Format: [MAGIC] (4 bytes), IV (12 bytes), ciphertext with the GCM tag.
 */
class DeviceCipher(private val keyProvider: () -> SecretKey = ::keystoreKey) {

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val iv = cipher.iv
        check(iv.size == IV_SIZE) { "unexpected IV size ${iv.size}" }
        return MAGIC + iv + cipher.doFinal(plain)
    }

    /** Throws if the data is not ours, was tampered with, or the key is gone. */
    fun decrypt(data: ByteArray): ByteArray {
        require(isEncrypted(data)) { "not encrypted by this app" }
        val iv = data.copyOfRange(MAGIC.size, MAGIC.size + IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(data, MAGIC.size + IV_SIZE, data.size - MAGIC.size - IV_SIZE)
    }

    fun readFile(file: File): ByteArray = decrypt(file.readBytes())

    /**
     * Write aside and rename, so a crash mid-write cannot leave half a file behind
     * (which, encrypted, would lose all of it).
     */
    fun writeFile(file: File, plain: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(encrypt(plain))
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("rename to ${file.name} failed")
        }
    }

    companion object {
        /**
         * A file name for [name] that does not reveal it (a friend's uid, say): its
         * SHA-256 in hex.
         */
        fun hashedName(name: String): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(name.encodeToByteArray())
                .joinToString("") { "%02x".format(it) }

        private const val KEY_ALIAS = "telespeak_device"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
        private val MAGIC = byteArrayOf('T'.code.toByte(), 'S'.code.toByte(), 'H'.code.toByte(), 1)

        fun isEncrypted(data: ByteArray): Boolean =
            data.size >= MAGIC.size + IV_SIZE && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

        @Synchronized
        private fun keystoreKey(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return generator.generateKey()
        }
    }
}
