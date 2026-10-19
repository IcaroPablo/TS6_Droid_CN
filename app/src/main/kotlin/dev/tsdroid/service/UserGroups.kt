package dev.tsdroid.service

import dev.tslib.ChannelGroup
import dev.tslib.ServerGroup

/**
 * How a member's server groups are presented in the member panel.
 *
 * A member carries the ids of the groups it holds (`User.serverGroups`), and the
 * names come from the server's group list, which is requested once per
 * connection. Until that answer arrives - or for a group the server did not
 * describe - an id has no name, and the UI shows the id with a marker instead of
 * pretending the member is in no group.
 */
object UserGroups {

    /**
     * The member's groups in display order, as `(id, name?)`.
     *
     * Named groups come first, alphabetically (the group a server user recognises
     * is its name, not its number); groups without a known name follow, by id.
     * Duplicates are collapsed, because the same id twice means nothing to a
     * reader.
     */
    fun displayOrder(
        groupIds: List<Long>,
        known: List<ServerGroup>,
    ): List<Pair<Long, String?>> {
        val names: Map<Long, String> = known
            .filter { it.name.isNotBlank() }
            .associate { it.id to it.name }

        val named = mutableListOf<Pair<Long, String>>()
        val unnamed = mutableListOf<Long>()
        for (id in groupIds.distinct()) {
            val name = names[id]
            if (name != null) named += id to name else unnamed += id
        }

        named.sortWith(compareBy({ it.second.lowercase() }, { it.first }))
        unnamed.sort()
        return named + unnamed.map { it to null }
    }

    /**
     * A member's channel group as `(id, name?)`, or null when nothing is known.
     *
     * Unlike server groups a client holds exactly one channel group, and it is
     * the one it holds *in the channel it is currently in*. An id of 0 means the
     * book has not seen it yet, which is not the same as "no group" — hence null
     * rather than a row of zeroes.
     */
    fun channelGroupOf(
        channelGroupId: Long,
        known: List<ChannelGroup>,
    ): Pair<Long, String?>? {
        if (channelGroupId <= 0) return null
        val name = known.firstOrNull { it.id == channelGroupId }?.name?.takeIf { it.isNotBlank() }
        return channelGroupId to name
    }
}
