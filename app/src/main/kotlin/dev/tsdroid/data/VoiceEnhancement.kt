package dev.tsdroid.data

/**
 * How much of opus's neural decoding runs on friends' voices. Each level
 * includes the ones before it and costs more CPU; all of it runs on the phone.
 */
enum class VoiceEnhancement(
    /** The opus decoder complexity that turns the level on. */
    val decoderComplexity: Int,
) {
    /** Plain opus decoding. */
    OFF(0),

    /** Deep PLC: lost audio is rebuilt by a neural network. Costs nothing until packets are lost. */
    FILL_GAPS(5),

    /** Deep PLC and LACE, which cleans up speech sent at a low bitrate. */
    CLEAN_UP(6),

    /** Deep PLC and NoLACE, a stronger and about four times costlier LACE. */
    STRONG_CLEAN_UP(7);

    fun toRaw(): String = when (this) {
        OFF -> "off"
        FILL_GAPS -> "fill_gaps"
        CLEAN_UP -> "clean_up"
        STRONG_CLEAN_UP -> "strong_clean_up"
    }

    companion object {
        fun from(raw: String?): VoiceEnhancement = when (raw) {
            "fill_gaps" -> FILL_GAPS
            "clean_up" -> CLEAN_UP
            "strong_clean_up" -> STRONG_CLEAN_UP
            else -> OFF
        }
    }
}
