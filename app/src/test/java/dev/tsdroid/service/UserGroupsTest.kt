package dev.tsdroid.service

import dev.tslib.ChannelGroup
import dev.tslib.ServerGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The member panel names a member's groups from ids plus the server's group list,
 * and must not invent a name or hide a group while that list is missing, so the
 * mapping is pinned here.
 */
class UserGroupsTest {

    private fun group(id: Long, name: String) = ServerGroup(id, name)

    @Test
    fun `ids are resolved to the names the server sent`() {
        val order = UserGroups.displayOrder(
            listOf(6L, 9L),
            listOf(group(6, "Server Admin"), group(9, "Guest")),
        )
        assertEquals(listOf(9L to "Guest", 6L to "Server Admin"), order)
    }

    @Test
    fun `a group the server did not describe keeps its id`() {
        val order = UserGroups.displayOrder(listOf(12L), listOf(group(6, "Server Admin")))
        assertEquals(1, order.size)
        assertEquals(12L, order[0].first)
        assertNull(order[0].second)
    }

    @Test
    fun `named groups sort before unnamed ones, alphabetically`() {
        val order = UserGroups.displayOrder(
            listOf(3L, 7L, 1L, 5L),
            listOf(group(7, "zeta"), group(5, "Alpha")),
        )
        // Names first (case-insensitive), then the ids nobody named, ascending.
        assertEquals(listOf(5L, 7L, 1L, 3L), order.map { it.first })
        assertEquals(listOf("Alpha", "zeta", null, null), order.map { it.second })
    }

    @Test
    fun `repeated ids collapse`() {
        val order = UserGroups.displayOrder(
            listOf(6L, 6L),
            listOf(group(6, "Server Admin")),
        )
        assertEquals(listOf(6L to "Server Admin"), order)
    }

    @Test
    fun `a member with no groups shows nothing`() {
        assertEquals(emptyList<Pair<Long, String?>>(), UserGroups.displayOrder(emptyList(), emptyList()))
    }

    @Test
    fun `a blank group name is treated as unknown`() {
        val order = UserGroups.displayOrder(listOf(4L), listOf(group(4, "  ")))
        assertNull(order.single().second)
    }

    @Test
    fun `the channel group is named when the server described it`() {
        val resolved = UserGroups.channelGroupOf(
            5L,
            listOf(ChannelGroup(5, "Channel Admin"), ChannelGroup(6, "Operator")),
        )
        assertEquals(5L to "Channel Admin", resolved)
    }

    @Test
    fun `an undescribed channel group shows its id`() {
        val resolved = UserGroups.channelGroupOf(9L, listOf(ChannelGroup(5, "Channel Admin")))
        assertEquals(9L, resolved?.first)
        assertNull(resolved?.second)
    }

    @Test
    fun `an unknown channel group id is no row at all`() {
        // 0 means the book has not seen the client's channel group yet, which is
        // not the same as "the member holds no group".
        assertNull(UserGroups.channelGroupOf(0L, emptyList()))
        assertNull(UserGroups.channelGroupOf(-1L, emptyList()))
    }

    @Test
    fun `a blank channel group name is treated as unknown`() {
        val resolved = UserGroups.channelGroupOf(5L, listOf(ChannelGroup(5, "")))
        assertEquals(5L, resolved?.first)
        assertNull(resolved?.second)
    }
}
