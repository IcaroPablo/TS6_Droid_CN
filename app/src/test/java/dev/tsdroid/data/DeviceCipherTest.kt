package dev.tsdroid.data

import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The file format and its integrity check, with a software key standing in for the
 * Keystore one (which only exists on a device).
 */
class DeviceCipherTest {

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val cipher = DeviceCipher { key }
    private val plain = """{"channel":[{"s":"Ana","t":"oi"}]}""".encodeToByteArray()

    @Test
    fun `round trip gives the plaintext back`() {
        assertArrayEquals(plain, cipher.decrypt(cipher.encrypt(plain)))
    }

    @Test
    fun `output is marked and does not contain the plaintext`() {
        val data = cipher.encrypt(plain)
        assertTrue(DeviceCipher.isEncrypted(data))
        assertFalse(data.decodeToString(throwOnInvalidSequence = false).contains("oi"))
        assertFalse(DeviceCipher.isEncrypted(plain))
    }

    @Test
    fun `every save uses a fresh IV`() {
        assertNotEquals(cipher.encrypt(plain).toList(), cipher.encrypt(plain).toList())
    }

    @Test(expected = Exception::class)
    fun `a tampered file is rejected`() {
        val data = cipher.encrypt(plain)
        data[data.size - 1] = (data[data.size - 1].toInt() xor 1).toByte()
        cipher.decrypt(data)
    }

    @Test(expected = Exception::class)
    fun `another key cannot read it`() {
        val other = newKey()
        DeviceCipher { other }.decrypt(cipher.encrypt(plain))
    }

    @Test
    fun `files are written encrypted and read back`() {
        val dir = Files.createTempDirectory("cipher").toFile()
        try {
            val file = File(dir, "sub/history.enc")
            cipher.writeFile(file, plain)
            assertTrue(DeviceCipher.isEncrypted(file.readBytes()))
            assertArrayEquals(plain, cipher.readFile(file))
            // No temporary file is left next to it.
            assertArrayEquals(arrayOf("history.enc"), file.parentFile!!.list())
        } finally {
            dir.deleteRecursively()
        }
    }
}
