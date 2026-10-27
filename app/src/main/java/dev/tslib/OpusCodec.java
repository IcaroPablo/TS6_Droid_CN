package dev.tslib;

/**
 * Opus encoder/decoder pair.
 *
 * <p>Not thread-safe.
 *
 * <pre>{@code
 * try (OpusCodec codec = new OpusCodec()) {
 *     byte[] encoded = codec.encode(pcmBytes);
 *     byte[] decoded = codec.decode(encoded);
 * }
 * }</pre>
 */
public class OpusCodec implements AutoCloseable {
    private long nativePtr;

    /**
     * Create an Opus codec with default configuration.
     *
     * @throws TsLibException if creation fails
     */
    public OpusCodec() {
        this(new AudioConfig());
    }

    /**
     * Create an Opus codec with the given configuration.
     *
     * @param config audio configuration
     * @throws TsLibException if creation fails
     */
    public OpusCodec(AudioConfig config) {
        this.nativePtr = nativeCreate(config.sampleRate, config.channels,
                                       config.bitrate, config.frameSizeMs);
        if (this.nativePtr == 0) {
            throw new TsLibException("Failed to create OpusCodec");
        }
    }

    /**
     * Encode PCM samples (16-bit little-endian bytes) to Opus.
     *
     * @param pcm raw PCM data
     * @return Opus-encoded packet
     * @throws TsLibException on encoding error
     */
    public byte[] encode(byte[] pcm) {
        checkNotClosed();
        return nativeEncode(nativePtr, pcm);
    }

    /**
     * Decode an Opus packet to PCM (16-bit little-endian bytes).
     *
     * @param data Opus-encoded data
     * @return decoded PCM samples
     * @throws TsLibException on decoding error
     */
    public byte[] decode(byte[] data) {
        checkNotClosed();
        // The codec pair shipped with the current libtslib_jni takes an explicit
        // FEC switch: false decodes the packet's own audio. The native signature
        // changed from the previous build, so passing two arguments would hand
        // the native side a garbage flag.
        return nativeDecode(nativePtr, data, false);
    }

    /**
     * Rebuild the frame lost just before {@code data} from the redundant copy
     * (FEC) the sender may have embedded in it; without one, this conceals the
     * frame as {@link #decodePlc()} does.
     *
     * @param data the Opus packet that follows the lost one
     * @return PCM for the lost frame
     * @throws TsLibException on decoding error
     */
    public byte[] decodeFec(byte[] data) {
        checkNotClosed();
        return nativeDecode(nativePtr, data, true);
    }

    /**
     * Conceal one lost frame (packet loss concealment).
     *
     * @return PCM for the lost frame
     * @throws TsLibException on decoding error
     */
    public byte[] decodePlc() {
        checkNotClosed();
        return nativeDecodePlc(nativePtr);
    }

    /**
     * Set the decoder complexity, 0 to 10 (default 0). 5 turns on deep PLC,
     * 6 adds LACE and 7 uses NoLACE instead, when the library has opus's
     * neural features.
     *
     * @throws TsLibException if the value is out of range
     */
    public void setDecoderComplexity(int complexity) {
        checkNotClosed();
        nativeSetDecoderComplexity(nativePtr, complexity);
    }

    /**
     * Get the audio configuration used by this codec.
     */
    public AudioConfig getConfig() {
        checkNotClosed();
        return nativeGetConfig(nativePtr);
    }

    @Override
    public void close() {
        if (nativePtr != 0) {
            nativeDestroy(nativePtr);
            nativePtr = 0;
        }
    }

    private void checkNotClosed() {
        if (nativePtr == 0) {
            throw new IllegalStateException("OpusCodec has been closed");
        }
    }

    // Native methods
    private static native long nativeCreate(int sampleRate, int channels,
                                             int bitrate, int frameSizeMs);
    private static native void nativeDestroy(long ptr);
    private static native byte[] nativeEncode(long ptr, byte[] pcm);
    private static native byte[] nativeDecode(long ptr, byte[] data, boolean fec);
    private static native byte[] nativeDecodePlc(long ptr);
    private static native void nativeSetDecoderComplexity(long ptr, int complexity);
    private static native AudioConfig nativeGetConfig(long ptr);
}
