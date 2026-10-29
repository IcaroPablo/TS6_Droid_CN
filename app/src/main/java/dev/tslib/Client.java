package dev.tslib;

/**
 * A TeamSpeak 3 client connection.
 *
 * <p>The client is <strong>not thread-safe</strong>. All method calls must
 * happen from the same thread that created the instance.
 *
 * <pre>{@code
 * try (Client client = new Client("localhost:9987", identity, "JavaBot")) {
 *     client.waitConnected();
 *     client.sendServerMessage("Hello!");
 * }
 * }</pre>
 */
public class Client implements AutoCloseable {
    private final Identity identity;
    private long nativePtr;

    /**
     * Connect to a TeamSpeak server.
     *
     * @param address  server address (host:port)
     * @param identity the identity to authenticate with
     * @param nickname display name on the server
     * @throws TsLibException on connection failure
     */
    public Client(String address, Identity identity, String nickname) {
        this(address, identity, nickname, null, null);
    }

    /**
     * Connect to a TeamSpeak server with optional password and channel.
     *
     * <p>The identity is borrowed for the lifetime of this client: it stays valid on the
     * native side even if {@link Identity#close()} is called first, and is only released
     * once this client is closed. That removes the dangling-pointer hazard of holding a
     * raw native pointer across an explicit identity close.
     *
     * @param address  server address (host:port)
     * @param identity the identity to authenticate with
     * @param nickname display name on the server
     * @param password server password (may be {@code null})
     * @param channel  default channel to join (may be {@code null})
     * @throws TsLibException on connection failure
     */
    public Client(String address, Identity identity, String nickname,
                  String password, String channel) {
        this.identity = identity;
        long ptr = nativeCreate(address, identity.borrow(),
                                nickname, password, channel);
        if (ptr == 0) {
            // Give the borrow back even when the connection never came up, otherwise the
            // identity could never be destroyed.
            identity.release();
            throw new TsLibException("Failed to connect to " + address);
        }
        this.nativePtr = ptr;
    }

    /**
     * Wait for the connection to be fully established.
     * Blocks until initial state synchronization is complete.
     *
     * @throws TsLibException on error
     */
    public void waitConnected() {
        checkNotClosed();
        nativeWaitConnected(nativePtr);
    }

    /**
     * Process pending events from the server.
     *
     * @return array of events (may be empty)
     * @throws TsLibException on error
     */
    public Event[] processEvents() {
        checkNotClosed();
        return nativeProcessEvents(nativePtr);
    }

    /**
     * Register a low-latency audio callback. Once set, received voice frames
     * are delivered synchronously on a native pump thread instead of via
     * {@code audio_received} events, which is what lets a receiver tell a
     * whisper frame from a channel-broadcast one. Pass {@code null} to
     * unregister.
     *
     * @param sink receiver or null
     * @throws TsLibException on error
     */
    public void setAudioSink(AudioSink sink) {
        checkNotClosed();
        nativeSetAudioSink(nativePtr, sink);
    }

    /**
     * Disconnect from the server.
     *
     * @throws TsLibException on error
     */
    public void disconnect() {
        checkNotClosed();
        nativeDisconnect(nativePtr);
    }

    /**
     * Whether the client is currently connected.
     */
    public boolean isConnected() {
        checkNotClosed();
        return nativeIsConnected(nativePtr);
    }

    /**
     * Get the current connection state.
     *
     * @return one of the {@link ConnectionState} constants
     */
    public int getState() {
        checkNotClosed();
        return nativeGetState(nativePtr);
    }

    /**
     * The connection's quality: {@code {rttMs, rttDevMs, packetLoss, packetLossIn,
     * bytesRecvPerSec, bytesSentPerSec, lossObservedTotal}}.
     *
     * @return the figures, or null while not connected
     */
    public double[] getNetworkStats() {
        checkNotClosed();
        return nativeGetNetworkStats(nativePtr);
    }

    /**
     * Get our client ID on the server.
     *
     * @return the client ID, or {@code null} if not yet assigned
     */
    public Integer getClientId() {
        checkNotClosed();
        return nativeGetClientId(nativePtr);
    }

    /**
     * Get our current channel ID.
     *
     * @return the channel ID, or {@code null} if not in a channel
     */
    public Long getChannelId() {
        checkNotClosed();
        return nativeGetChannelId(nativePtr);
    }

    /**
     * Get all channels on the server.
     *
     * @return array of channels
     */
    public Channel[] getChannels() {
        checkNotClosed();
        return nativeGetChannels(nativePtr);
    }

    /**
     * Get all connected users.
     *
     * @return array of users
     */
    public User[] getUsers() {
        checkNotClosed();
        return nativeGetUsers(nativePtr);
    }

    /**
     * Get a specific channel by ID.
     *
     * @param id channel ID
     * @return the channel, or {@code null} if not found
     */
    public Channel getChannel(long id) {
        checkNotClosed();
        return nativeGetChannel(nativePtr, id);
    }

    /**
     * Get a specific user by ID.
     *
     * @param id user (client) ID
     * @return the user, or {@code null} if not found
     */
    public User getUser(int id) {
        checkNotClosed();
        return nativeGetUser(nativePtr, id);
    }

    /**
     * Get server information.
     *
     * @return server info snapshot
     */
    public ServerInfo getServerInfo() {
        checkNotClosed();
        return nativeGetServerInfo(nativePtr);
    }

    /**
     * Send a message to the entire server.
     *
     * @param msg message text (may contain BBCode)
     * @throws TsLibException on error
     */
    public void sendServerMessage(String msg) {
        checkNotClosed();
        nativeSendServerMessage(nativePtr, msg);
    }

    /**
     * Send a message to the current channel.
     *
     * @param msg message text
     * @throws TsLibException on error
     */
    public void sendChannelMessage(String msg) {
        checkNotClosed();
        nativeSendChannelMessage(nativePtr, msg);
    }

    /**
     * Send a plugin command: hidden data for clients that know {@code name}; others ignore it.
     *
     * @param name what the data is, so a receiver can tell its own commands apart
     * @param data the payload
     * @param targetId the client to send it to, or -1 for everyone on the server
     * @throws TsLibException on error
     */
    public void sendPluginCommand(String name, String data, int targetId) {
        checkNotClosed();
        nativeSendPluginCommand(nativePtr, name, data, targetId);
    }

    /**
     * Send a private message to a user.
     *
     * @param userId target user ID
     * @param msg    message text
     * @throws TsLibException on error
     */
    public void sendPrivateMessage(int userId, String msg) {
        checkNotClosed();
        nativeSendPrivateMessage(nativePtr, userId, msg);
    }

    /**
     * Set the whisper (密聊) target set for outgoing voice. While the set is
     * non-empty, every packet captured via {@link #sendAudio(byte[], int)} is
     * sent as a whisper packet to exactly these clients, channels and server
     * groups instead of the current channel — TS3 semantics: whispering and
     * channel talk are mutually exclusive.
     *
     * <p>Server group targets are resolved on the native side for every packet,
     * so a member who joins the group later starts receiving without this
     * method being called again.
     *
     * @param clients  target client ids (empty to clear)
     * @param channels target channel ids (empty to clear)
     * @param groups   target server group ids (empty to clear)
     * @throws TsLibException on error
     */
    public void setWhisperTargets(int[] clients, long[] channels, long[] groups) {
        checkNotClosed();
        nativeSetWhisperTargets(nativePtr, clients, channels, groups);
    }

    /**
     * Server groups known to this connection, sorted by id.
     *
     * <p>Empty until the server answers the group list request that is sent
     * when the connection comes up.
     */
    public ServerGroup[] getServerGroups() {
        checkNotClosed();
        return nativeGetServerGroups(nativePtr);
    }

    /**
     * Channel groups known to this connection, sorted by id.
     *
     * <p>Empty until the server answers the group list request that is sent
     * when the connection comes up.
     */
    public ChannelGroup[] getChannelGroups() {
        checkNotClosed();
        return nativeGetChannelGroups(nativePtr);
    }

    /**
     * Move to a different channel.
     *
     * @param channelId target channel ID
     * @throws TsLibException on error
     */
    public void moveToChannel(long channelId) {
        moveToChannel(channelId, null);
    }

    /**
     * Move to a different channel, optionally passing its password.
     *
     * @param channelId target channel ID
     * @param password  channel password (may be {@code null})
     * @throws TsLibException on error
     */
    public void moveToChannel(long channelId, String password) {
        checkNotClosed();
        nativeMoveToChannel(nativePtr, channelId, password);
    }

    /**
     * Re-synchronize the local state from the server.
     *
     * @throws TsLibException on error
     */
    public void syncState() {
        checkNotClosed();
        nativeSyncState(nativePtr);
    }

    /**
     * Change our nickname for this session only.
     *
     * <p>Sent as {@code clientupdate client_nickname}. The server keeps it for
     * the lifetime of the connection: the identity file and the next connect are
     * untouched, which is what "temporary" means here.
     *
     * @param nickname the new display name
     * @throws TsLibException on error
     */
    public void setNickname(String nickname) {
        checkNotClosed();
        nativeSetNickname(nativePtr, nickname);
    }

    /**
     * Set our own away state, i.e. the desktop client's "status" menu entry.
     *
     * <p>Sent as {@code clientupdate client_away=1} plus an optional
     * {@code client_away_message}. Away is only a marker: voice and text keep
     * working exactly as before, the server merely tells everybody else that we
     * are away. Passing {@code false} clears the flag and the message.
     *
     * @param away    true to go away, false to come back online
     * @param message free-form away message, may be null or blank for none
     * @throws TsLibException on error
     */
    public void setAway(boolean away, String message) {
        checkNotClosed();
        nativeSetAway(nativePtr, away, message);
    }

    /**
     * Notify the server of our input muted state.
     *
     * @param muted true if microphone is muted
     * @throws TsLibException on error
     */
    public void setInputMuted(boolean muted) {
        checkNotClosed();
        nativeSetInputMuted(nativePtr, muted);
    }

    /**
     * Notify the server of our output (speaker) muted state.
     *
     * @param muted true if speakers are muted
     * @throws TsLibException on error
     */
    public void setOutputMuted(boolean muted) {
        checkNotClosed();
        nativeSetOutputMuted(nativePtr, muted);
    }

    /**
     * Send encoded audio data to the server.
     *
     * @param data  encoded audio data (e.g. Opus)
     * @param codec audio codec ID (see {@link AudioCodec} constants: 4 = Opus Voice, 5 = Opus Music)
     * @throws TsLibException on error
     */
    public void sendAudio(byte[] data, int codec) {
        checkNotClosed();
        nativeSendAudio(nativePtr, data, codec);
    }

    /**
     * Initiate a file download from the server.
     * The result will be delivered as a {@code file_downloaded} or
     * {@code file_transfer_failed} event via {@link #processEvents()}.
     *
     * @param channelId channel containing the file (0 for icons)
     * @param path      virtual path on the server (e.g. "/icon_12345")
     * @throws TsLibException on error
     */
    public void downloadFile(long channelId, String path) {
        checkNotClosed();
        nativeDownloadFile(nativePtr, channelId, path);
    }

    /**
     * Initiate a file upload to the server.
     * The result will be delivered as a {@code file_uploaded} or
     * {@code file_transfer_failed} event via {@link #processEvents()}.
     *
     * @param channelId channel to upload to
     * @param path      virtual path on the server (e.g. "/myfile.png")
     * @param data      file content bytes
     * @param overwrite whether to overwrite existing file
     * @throws TsLibException on error
     */
    public void uploadFile(long channelId, String path, byte[] data, boolean overwrite) {
        checkNotClosed();
        nativeUploadFile(nativePtr, channelId, path, data, overwrite);
    }

    /**
     * Request the file list for a channel directory.
     * The result will be delivered as a {@code file_list_received} event.
     */
    public void listFiles(long channelId, String path) {
        checkNotClosed();
        nativeListFiles(nativePtr, channelId, path);
    }

    /**
     * Query effective permissions for the current user in a channel.
     * The result will be delivered as a {@code channel_permissions_updated} event.
     */
    public void queryChannelPermissions(long channelId) {
        checkNotClosed();
        nativeQueryChannelPermissions(nativePtr, channelId);
    }

    /**
     * Delete a file on the server.
     */
    public void deleteFile(long channelId, String name) {
        checkNotClosed();
        nativeDeleteFile(nativePtr, channelId, name);
    }

    /**
     * Rename a file on the server.
     */
    public void renameFile(long channelId, String oldName, String newName) {
        checkNotClosed();
        nativeRenameFile(nativePtr, channelId, oldName, newName);
    }

    /**
     * Create a directory on the server.
     */
    public void createDirectory(long channelId, String dirname) {
        checkNotClosed();
        nativeCreateDirectory(nativePtr, channelId, dirname);
    }

    /**
     * Ask to watch a TeamSpeak 6 stream. The streamer's answer arrives as a
     * {@code stream_join_response} event, with its WebRTC offer when it accepts.
     */
    public void joinStream(int clientId, String streamId) {
        checkNotClosed();
        nativeJoinStream(nativePtr, clientId, streamId);
    }

    /**
     * Stop watching a stream.
     */
    public void leaveStream(int clientId, String streamId) {
        checkNotClosed();
        nativeLeaveStream(nativePtr, clientId, streamId);
    }

    /**
     * Send WebRTC signalling (an answer or an ICE candidate, as TeamSpeak's
     * {@code {"cmd":…,"args":…}} JSON) to a streamer.
     */
    public void sendStreamSignaling(int clientId, String streamId, String json) {
        checkNotClosed();
        nativeSendStreamSignaling(nativePtr, clientId, streamId, json);
    }

    @Override
    public void close() {
        if (nativePtr != 0) {
            nativeDestroy(nativePtr);
            nativePtr = 0;
            // Hand the identity back; it destroys itself here if it was already closed.
            identity.release();
        }
    }

    private void checkNotClosed() {
        if (nativePtr == 0) {
            throw new IllegalStateException("Client has been closed");
        }
    }

    // Native methods
    private static native long nativeCreate(String address, long identityPtr,
                                             String nickname, String password,
                                             String channel);
    private static native void nativeDestroy(long ptr);
    private static native void nativeWaitConnected(long ptr);
    private static native Event[] nativeProcessEvents(long ptr);
    private static native void nativeSetAudioSink(long ptr, AudioSink sink);
    private static native void nativeDisconnect(long ptr);
    private static native boolean nativeIsConnected(long ptr);
    private static native int nativeGetState(long ptr);
    private static native double[] nativeGetNetworkStats(long ptr);
    private static native Integer nativeGetClientId(long ptr);
    private static native Long nativeGetChannelId(long ptr);
    private static native Channel[] nativeGetChannels(long ptr);
    private static native User[] nativeGetUsers(long ptr);
    private static native Channel nativeGetChannel(long ptr, long id);
    private static native User nativeGetUser(long ptr, int id);
    private static native ServerInfo nativeGetServerInfo(long ptr);
    private static native void nativeSendServerMessage(long ptr, String msg);
    private static native void nativeSendChannelMessage(long ptr, String msg);
    private static native void nativeSendPluginCommand(long ptr, String name, String data, int targetId);
    private static native void nativeSendPrivateMessage(long ptr, int userId, String msg);
    private static native void nativeSetWhisperTargets(long ptr, int[] clients, long[] channels, long[] groups);
    private static native ServerGroup[] nativeGetServerGroups(long ptr);
    private static native ChannelGroup[] nativeGetChannelGroups(long ptr);
    private static native void nativeMoveToChannel(long ptr, long channelId, String password);
    private static native void nativeSyncState(long ptr);
    private static native void nativeSetInputMuted(long ptr, boolean muted);
    private static native void nativeSetOutputMuted(long ptr, boolean muted);
    private static native void nativeSetNickname(long ptr, String nickname);
    private static native void nativeSetAway(long ptr, boolean away, String message);
    private static native void nativeSendAudio(long ptr, byte[] data, int codec);
    private static native void nativeDownloadFile(long ptr, long channelId, String path);
    private static native void nativeUploadFile(long ptr, long channelId, String path, byte[] data, boolean overwrite);
    private static native void nativeListFiles(long ptr, long channelId, String path);
    private static native void nativeQueryChannelPermissions(long ptr, long channelId);
    private static native void nativeDeleteFile(long ptr, long channelId, String name);
    private static native void nativeRenameFile(long ptr, long channelId, String oldName, String newName);
    private static native void nativeCreateDirectory(long ptr, long channelId, String dirname);
    private static native void nativeJoinStream(long ptr, int clientId, String streamId);
    private static native void nativeLeaveStream(long ptr, int clientId, String streamId);
    private static native void nativeSendStreamSignaling(long ptr, int clientId, String streamId, String json);
}
