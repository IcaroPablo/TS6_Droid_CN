package dev.tslib;

import java.util.Arrays;
import java.util.Objects;

/**
 * A TeamSpeak user (immutable snapshot).
 */
public class User {
    public final int id;
    public final String uid;
    public final long databaseId;
    public final long channelId;
    public final String nickname;
    public final byte clientType;
    public final boolean isTalking;
    public final boolean isInputMuted;
    public final boolean isOutputMuted;
    public final boolean hasInputHardware;
    public final boolean hasOutputHardware;
    public final boolean isAway;
    public final boolean isRecording;
    public final boolean isPrioritySpeaker;
    public final boolean isChannelCommander;
    public final boolean isTalker;
    public final int talkPower;
    public final String awayMessage;
    public final long[] serverGroups;
    public final long channelGroup;
    public final String platform;
    public final String version;
    public final String country;
    public final String description;
    public final String avatarId;
    public final long iconId;

    public User(int id, String uid, long databaseId, long channelId,
                String nickname, byte clientType, boolean isTalking,
                boolean isInputMuted, boolean isOutputMuted,
                boolean hasInputHardware, boolean hasOutputHardware,
                boolean isAway, boolean isRecording,
                boolean isPrioritySpeaker, boolean isChannelCommander,
                boolean isTalker, int talkPower, String awayMessage,
                long[] serverGroups, long channelGroup,
                String platform, String version,
                String country, String description,
                String avatarId, long iconId) {
        this.id = id;
        this.uid = uid;
        this.databaseId = databaseId;
        this.channelId = channelId;
        this.nickname = nickname;
        this.clientType = clientType;
        this.isTalking = isTalking;
        this.isInputMuted = isInputMuted;
        this.isOutputMuted = isOutputMuted;
        this.hasInputHardware = hasInputHardware;
        this.hasOutputHardware = hasOutputHardware;
        this.isAway = isAway;
        this.isRecording = isRecording;
        this.isPrioritySpeaker = isPrioritySpeaker;
        this.isChannelCommander = isChannelCommander;
        this.isTalker = isTalker;
        this.talkPower = talkPower;
        this.awayMessage = awayMessage;
        this.serverGroups = serverGroups;
        this.channelGroup = channelGroup;
        this.platform = platform;
        this.version = version;
        this.country = country;
        this.description = description;
        this.avatarId = avatarId;
        this.iconId = iconId;
    }

    public User(int id, String uid, long databaseId, long channelId,
                String nickname, byte clientType, boolean isTalking,
                boolean isInputMuted, boolean isOutputMuted,
                boolean hasInputHardware, boolean hasOutputHardware,
                boolean isAway, boolean isRecording,
                boolean isPrioritySpeaker, boolean isChannelCommander,
                boolean isTalker, int talkPower, String awayMessage,
                long[] serverGroups, long channelGroup,
                String platform, String version,
                String country, String description,
                String avatarId) {
        this(id, uid, databaseId, channelId, nickname, clientType, isTalking,
             isInputMuted, isOutputMuted, hasInputHardware, hasOutputHardware,
             isAway, isRecording, isPrioritySpeaker, isChannelCommander,
             isTalker, talkPower, awayMessage, serverGroups, channelGroup,
             platform, version, country, description, avatarId, 0L);
    }

    /**
     * Whether this is a server query client.
     */
    public boolean isQuery() {
        return clientType == 1;
    }

    @Override
    public String toString() {
        return "User(id=" + id + ", nickname='" + nickname + "')";
    }

    /**
     * Value equality: the client publishes a fresh snapshot several times a second, and an
     * unchanged one must compare equal so nothing downstream redraws for it.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User other = (User) o;
        return id == other.id
                && Objects.equals(uid, other.uid)
                && databaseId == other.databaseId
                && channelId == other.channelId
                && Objects.equals(nickname, other.nickname)
                && clientType == other.clientType
                && isTalking == other.isTalking
                && isInputMuted == other.isInputMuted
                && isOutputMuted == other.isOutputMuted
                && hasInputHardware == other.hasInputHardware
                && hasOutputHardware == other.hasOutputHardware
                && isAway == other.isAway
                && isRecording == other.isRecording
                && isPrioritySpeaker == other.isPrioritySpeaker
                && isChannelCommander == other.isChannelCommander
                && isTalker == other.isTalker
                && talkPower == other.talkPower
                && Objects.equals(awayMessage, other.awayMessage)
                && Arrays.equals(serverGroups, other.serverGroups)
                && channelGroup == other.channelGroup
                && Objects.equals(platform, other.platform)
                && Objects.equals(version, other.version)
                && Objects.equals(country, other.country)
                && Objects.equals(description, other.description)
                && Objects.equals(avatarId, other.avatarId)
                && iconId == other.iconId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uid, databaseId, channelId, nickname, clientType, isTalking, isInputMuted, isOutputMuted, hasInputHardware, hasOutputHardware, isAway, isRecording, isPrioritySpeaker, isChannelCommander, isTalker, talkPower, awayMessage, Arrays.hashCode(serverGroups), channelGroup, platform, version, country, description, avatarId, iconId);
    }
}
