package dev.tslib;

/**
 * A channel group on the connected server (immutable snapshot).
 *
 * <p>Like {@link ServerGroup}, only the identity and the display name are
 * exposed: a client's {@link User#channelGroup} is an id, and naming it is all
 * the member panel needs. The list is empty until the server answers the
 * {@code channelgrouplist} request issued on connect.
 */
public class ChannelGroup {
    public final long id;
    public final String name;

    public ChannelGroup(long id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return "ChannelGroup(id=" + id + ", name='" + name + "')";
    }
}
