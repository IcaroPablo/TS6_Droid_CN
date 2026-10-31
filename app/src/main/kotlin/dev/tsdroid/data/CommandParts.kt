package dev.tsdroid.data

/**
 * Plugin commands cut to the length a server passes on, and put back together on arrival.
 *
 * A TeamSpeak server drops a plugin command whose data is longer than 1024 characters, without a
 * word to either side (measured on the group's server: 1024 arrived, 1025 did not). A history
 * reply runs to a few kB, and a busy channel's request past that limit too, so most of them never
 * arrived. A longer command now goes as numbered parts under a name of its own, each part
 * `<key>:<index>:<count>:<slice>`; clients that do not know the parts ignore them, as they ignore
 * any command they do not know.
 */
object CommandParts {
    /** The longest command data a server passes on. Counted in UTF-8 bytes, never fewer than characters */
    const val MAX_BYTES = 1024

    /** Room kept in each part for its `<key>:<index>:<count>:` */
    private const val HEADER_BYTES = 32

    /** More parts than this is no command of ours: a part claiming so is ignored */
    private const val MAX_PARTS = 256

    fun fits(data: String): Boolean = data.toByteArray(Charsets.UTF_8).size <= MAX_BYTES

    /** [data] cut into parts that each fit, tagged with [key] so the receiver keeps them apart. */
    fun split(data: String, key: Int): List<String> {
        val slices = mutableListOf<String>()
        val budget = MAX_BYTES - HEADER_BYTES
        var start = 0
        var bytes = 0
        var index = 0
        while (index < data.length) {
            // A surrogate pair stays whole: half of one is not a character on either side
            val width = if (Character.isHighSurrogate(data[index]) && index + 1 < data.length) 2 else 1
            val charBytes = data.substring(index, index + width).toByteArray(Charsets.UTF_8).size
            if (bytes + charBytes > budget) {
                slices += data.substring(start, index)
                start = index
                bytes = 0
            }
            bytes += charBytes
            index += width
        }
        slices += data.substring(start)
        return slices.mapIndexed { i, slice -> "$key:$i:${slices.size}:$slice" }
    }

    /**
     * Puts each sender's parts back together. A command whose parts stop coming (the sender left)
     * is dropped once newer ones have filled the room kept for them.
     */
    class Assembler(private val maxPending: Int = 16) {
        private val pending = LinkedHashMap<String, Array<String?>>()

        /** The whole command once [part] from [senderId] was its last missing part, else null. */
        fun add(senderId: Int, part: String): String? {
            val fields = part.split(':', limit = 4)
            if (fields.size != 4) return null
            val key = fields[0]
            val index = fields[1].toIntOrNull() ?: return null
            val count = fields[2].toIntOrNull() ?: return null
            if (count !in 1..MAX_PARTS || index !in 0 until count) return null
            val id = "$senderId:$key"
            val slots = pending.getOrPut(id) { arrayOfNulls(count) }
            if (slots.size != count) return null
            slots[index] = fields[3]
            if (slots.any { it == null }) {
                while (pending.size > maxPending) pending.remove(pending.keys.first())
                return null
            }
            pending.remove(id)
            return slots.joinToString("")
        }
    }
}
