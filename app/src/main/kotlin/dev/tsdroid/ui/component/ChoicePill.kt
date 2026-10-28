package dev.tsdroid.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import dev.tsdroid.ui.theme.Shapes

/** Height of every button-like control: buttons, choice pills, the chat input's actions. */
val ControlHeight = 40.dp

/**
 * One choice out of a few (a setting's options, the chat's two conversations, a private
 * conversation): as tall as a button, with the button's label style and corner. Selected, it is
 * filled; otherwise it is outlined.
 */
@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown as a badge after the label */
    unread: Int = 0,
) {
    val fill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "choiceFill",
    )
    val outline by animateColorAsState(
        if (selected) Color.Transparent else LocalContentColor.current.copy(alpha = 0.25f),
        label = "choiceOutline",
    )
    Row(
        modifier = modifier
            .height(ControlHeight)
            .clip(Shapes.Small)
            .background(fill)
            .border(1.dp, outline, Shapes.Small)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else LocalContentColor.current,
            maxLines = 1,
        )
        if (unread > 0) {
            Spacer(Modifier.width(6.dp))
            Badge { Text("$unread") }
        }
    }
}
