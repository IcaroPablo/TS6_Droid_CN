package dev.tslib;

/**
 * A server group on the connected server (immutable snapshot).
 *
 * <p>Only the identity and the display name are exposed: the whisper list needs
 * to name a target, and the routing itself happens in the native layer. The
 * list is empty until the server answers the {@code servergrouplist} request
 * issued on connect.
 */
public class ServerGroup {
    public final long id;
    public final String name;

    public ServerGroup(long id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return "ServerGroup(id=" + id + ", name='" + name + "')";
    }
}
