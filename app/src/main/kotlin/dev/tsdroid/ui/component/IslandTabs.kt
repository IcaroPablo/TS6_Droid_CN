package dev.tsdroid.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.tsdroid.ui.theme.InsetFill
import dev.tsdroid.ui.theme.Shapes

/** One view an island can switch to, with an optional unread count. */
data class IslandTab(val label: String, val badge: Int = 0)

/** How far the selected tab sits inside the tab group's track. */
private val TrackInset = 3.dp

/**
 * The views of one island (home's servers and settings, the channel list and the streams, the
 * channel chat and private messages), as a group in its header: a track in the island's inset
 * colour with the current view filled. Every island that switches views uses this, so switching
 * looks and works the same everywhere.
 */
@Composable
fun IslandTabs(
    tabs: List<IslandTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(ControlHeight)
            .clip(Shapes.Standard)
            .background(InsetFill)
            .padding(TrackInset),
        horizontalArrangement = Arrangement.spacedBy(TrackInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selected
            val fill by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                label = "islandTab",
            )
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(Shapes.Standard)
                    .background(fill)
                    .clickable { onSelect(index) }
                    .semantics {
                        role = Role.Tab
                        this.selected = isSelected
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else LocalContentColor.current.copy(alpha = 0.72f),
                    maxLines = 1,
                )
                if (tab.badge > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge { Text("${tab.badge}") }
                }
            }
        }
    }
}
