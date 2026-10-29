package dev.tsdroid.data

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Whether a TeamSpeak server answers, and how fast, without connecting to it.
 *
 * It sends the first packet of TeamSpeak's own handshake (the "TS3INIT1" init step 0, exactly what
 * a client sends when it starts connecting) and times the server's first reply. Nothing else is
 * sent, so the server never sees a client join; it only answers the opening packet and forgets it.
 */
object ServerProbe {
    private const val DEFAULT_PORT = 9987
    private const val TIMEOUT_MS = 2_000

    /** TeamSpeak counts the client version's timestamp from 2013-01-01. */
    private const val VERSION_EPOCH = 1_356_998_400L

    /** The round trip in milliseconds, or null when the server did not answer in time. */
    suspend fun ping(address: String): Long? = withContext(Dispatchers.IO) {
        val (host, port) = parse(address) ?: return@withContext null
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = TIMEOUT_MS
                val target = InetSocketAddress(host, port)
                val packet = init0()
                val reply = DatagramPacket(ByteArray(64), 64)
                val start = System.nanoTime()
                socket.send(DatagramPacket(packet, packet.size, target))
                socket.receive(reply)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                // Any reply that is a TeamSpeak init packet means the server is up
                if (reply.length >= 8 && String(reply.data, 0, 8, Charsets.US_ASCII) == "TS3INIT1") elapsed else null
            }
        }.getOrNull()
    }

    /** "host", "host:port", or "[v6]:port". */
    private fun parse(address: String): Pair<String, Int>? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("[")) {
            val end = trimmed.indexOf(']')
            if (end < 0) return null
            val port = trimmed.substring(end + 1).removePrefix(":").toIntOrNull() ?: DEFAULT_PORT
            return trimmed.substring(1, end) to port
        }
        val colon = trimmed.lastIndexOf(':')
        if (colon > 0 && trimmed.indexOf(':') == colon) {
            return trimmed.substring(0, colon) to (trimmed.substring(colon + 1).toIntOrNull() ?: DEFAULT_PORT)
        }
        return trimmed to DEFAULT_PORT
    }

    /** The client's first handshake packet, as tsproto builds it (OutC2SInit0). */
    private fun init0(): ByteArray {
        val now = System.currentTimeMillis() / 1000
        return ByteBuffer.allocate(13 + 21).apply {
            put("TS3INIT1".toByteArray(Charsets.US_ASCII))
            putShort(0x65)               // packet id
            putShort(0)                  // client id
            put(0x88.toByte())           // unencrypted, init
            putInt((now - VERSION_EPOCH).toInt())
            put(0)                       // step 0
            putInt(now.toInt())
            put(Random.nextBytes(4))
            put(ByteArray(8))            // reserved
        }.array()
    }
}
