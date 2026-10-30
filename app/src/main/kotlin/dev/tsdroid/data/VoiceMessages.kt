package dev.tsdroid.data

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Voice messages: recorded audio sent like any other file, so a PC client sees an ordinary Ogg
 * file it can play. What tells TeleSpeak it is a voice message is its name,
 * `Voice message 2026-10-29 18-25-31 12s.ogg`, which also carries its length.
 */
object VoiceMessages {
    private val NAME = Regex("""^Voice message \d{4}-\d{2}-\d{2} \d{2}-\d{2}-\d{2} (\d+)s\.ogg$""")
    private val AUDIO_EXTENSIONS = setOf("ogg", "opus", "oga", "mp3", "m4a", "aac", "wav", "flac")

    /** The longest voice message: recording stops and it is sent there */
    const val MAX_DURATION_MS = 5 * 60 * 1000L

    /** Shorter than this is a slip of the finger, not a message */
    const val MIN_DURATION_MS = 500L

    /** A voice message's file name; no colons, which Windows refuses in a file name */
    fun fileName(recordedAt: Long, durationMs: Long): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date(recordedAt))
        val seconds = ((durationMs + 500) / 1000).coerceAtLeast(1)
        return "Voice message $time ${seconds}s.ogg"
    }

    /** Whether [fileName] is a voice message: the name TeleSpeak gives them. */
    fun isVoiceMessage(fileName: String): Boolean = NAME.matches(fileName.substringAfterLast('/'))

    /** The length a voice message's name gives, in milliseconds; null for any other file. */
    fun durationOf(fileName: String): Long? =
        NAME.matchEntire(fileName.substringAfterLast('/'))?.groupValues?.get(1)?.toLongOrNull()?.times(1000)

    /** Audio TeleSpeak plays in the chat itself: voice messages, and audio files anyone sent. */
    fun isAudioFile(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
}

/**
 * Opus packets in an Ogg file (RFC 7845), the format of voice messages: what Android's player
 * and every desktop player read.
 */
object OggOpus {
    /** Samples per second Opus counts in, whatever was recorded */
    private const val GRANULE_RATE = 48000

    /** Samples the decoder drops at the start: the encoder's lookahead at 48 kHz */
    private const val PRE_SKIP = 312

    /** Packets per page: about a second of 20 ms packets, well under a page's 255 segments */
    private const val PACKETS_PER_PAGE = 50

    /** [packets] of [frameSamples] samples each, mono, as an Ogg Opus file. */
    fun write(packets: List<ByteArray>, frameSamples: Int, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val serial = (System.nanoTime() and 0x7fffffff).toInt()
        var sequence = 0
        fun page(content: List<ByteArray>, granule: Long, flags: Int) {
            writePage(out, content, granule, flags, serial, sequence++)
        }

        val head = ByteArrayOutputStream().apply {
            write("OpusHead".toByteArray())
            write(1) // version
            write(1) // channels
            writeLe(PRE_SKIP.toLong(), 2)
            writeLe(sampleRate.toLong(), 4)
            writeLe(0, 2) // output gain
            write(0) // channel mapping family: mono or stereo, no table
        }.toByteArray()
        page(listOf(head), 0, flags = 0x02) // beginning of stream

        val vendor = "TeleSpeak".toByteArray()
        val tags = ByteArrayOutputStream().apply {
            write("OpusTags".toByteArray())
            writeLe(vendor.size.toLong(), 4)
            write(vendor)
            writeLe(0, 4) // no comments
        }.toByteArray()
        page(listOf(tags), 0, flags = 0)

        // The granule position counts 48 kHz samples up to the page's last packet
        val granulePerPacket = frameSamples.toLong() * GRANULE_RATE / sampleRate
        val pages = packets.chunked(PACKETS_PER_PAGE)
        var granule = 0L
        pages.forEachIndexed { index, chunk ->
            granule += granulePerPacket * chunk.size
            page(chunk, granule, flags = if (index == pages.lastIndex) 0x04 else 0) // last: end of stream
        }
        return out.toByteArray()
    }

    private fun writePage(
        out: ByteArrayOutputStream,
        packets: List<ByteArray>,
        granule: Long,
        flags: Int,
        serial: Int,
        sequence: Int,
    ) {
        // Each packet is laced as runs of 255 ended by a shorter segment
        val lacing = ByteArrayOutputStream()
        for (packet in packets) {
            var left = packet.size
            while (left >= 255) {
                lacing.write(255)
                left -= 255
            }
            lacing.write(left)
        }
        val page = ByteArrayOutputStream().apply {
            write("OggS".toByteArray())
            write(0) // version
            write(flags)
            writeLe(granule, 8)
            writeLe(serial.toLong(), 4)
            writeLe(sequence.toLong(), 4)
            writeLe(0, 4) // checksum, filled in below
            write(lacing.size())
            write(lacing.toByteArray())
            packets.forEach { write(it) }
        }.toByteArray()
        val crc = crc(page)
        for (i in 0 until 4) page[22 + i] = (crc ushr (8 * i)).toByte()
        out.write(page)
    }

    private fun ByteArrayOutputStream.writeLe(value: Long, bytes: Int) {
        for (i in 0 until bytes) write(((value ushr (8 * i)) and 0xff).toInt())
    }

    /** Ogg's checksum: CRC-32 with polynomial 0x04c11db7, not reflected, starting from 0. */
    private val CRC_TABLE = IntArray(256) { index ->
        var r = index shl 24
        repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04c11db7 else r shl 1 }
        r
    }

    private fun crc(data: ByteArray): Int {
        var crc = 0
        for (b in data) crc = (crc shl 8) xor CRC_TABLE[((crc ushr 24) xor (b.toInt() and 0xff)) and 0xff]
        return crc
    }
}
