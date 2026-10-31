package dev.tsdroid.data

/**
 * Who a chat message mentions, as "@name". A listener leaves the ones who are offline a note in
 * their mailbox (see ChannelHistory), so a mention reaches them when they next connect.
 */
object Mentions {
    /**
     * The uids of the [people] (uid to nickname) that [text] mentions. At each "@" the longest
     * name that follows wins, so "@Ushummu (honor)" is not taken for "@Ushummu"; and a name must
     * end there, so "@ushummu1" is not "@ushummu". Case is ignored, as people type it.
     */
    fun mentioned(text: String, people: Map<String, String>): Set<String> {
        if ('@' !in text || people.isEmpty()) return emptySet()
        // Longest first: the first that matches at a position is the longest there
        val byLength = people.entries.filter { it.value.isNotBlank() }.sortedByDescending { it.value.length }
        val found = mutableSetOf<String>()
        var at = text.indexOf('@')
        while (at >= 0) {
            val start = at + 1
            byLength.firstOrNull { (_, name) ->
                text.regionMatches(start, name, 0, name.length, ignoreCase = true) &&
                    text.getOrNull(start + name.length)?.isLetterOrDigit() != true
            }?.let { found += it.key }
            at = text.indexOf('@', start)
        }
        return found
    }
}
