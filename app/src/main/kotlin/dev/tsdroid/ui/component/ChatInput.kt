package dev.tsdroid.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tsdroid.han.R
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.WhisperAmber

/**
 * The box chat messages are written in, shared by the chat panel and the
 * stream viewer's chat. While whispering a banner above it says who the text
 * goes to: like the voice, it goes to the whisper targets.
 *
 * Its colours follow [LocalContentColor], so it reads on the glass panels as
 * on a plain surface.
 */
@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    /** Whom the whisper targets are, while whispering */
    whisperTargetName: String?,
    modifier: Modifier = Modifier,
    /** False when there is nobody to write to yet */
    enabled: Boolean = true,
    /** Opens the file picker; no attach button without it */
    onAttach: (() -> Unit)? = null,
    /** False when there is nobody to send a file to */
    attachEnabled: Boolean = enabled,
) {
    val canSend = enabled && text.isNotBlank()
    Column(modifier = modifier) {
        if (whisperTargetName != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = Shapes.Standard,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        AppIcons.Whisper,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize),
                        tint = WhisperAmber,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.whisper_banner, whisperTargetName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        // One filled field with the attach and send buttons inside it, instead of an outlined box
        // beside loose icons. Its corner is a control's: nested inside the island by the gap.
        var focused by remember { mutableStateOf(false) }
        val fill by animateColorAsState(
            LocalContentColor.current.copy(alpha = if (focused) 0.14f else 0.08f),
            label = "chatInputFill",
        )
        val contentColor = LocalContentColor.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ControlHeight)
                .clip(Shapes.Standard)
                .background(fill),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onAttach != null) {
                IconButton(onClick = onAttach, enabled = attachEnabled, modifier = Modifier.size(ControlHeight)) {
                    Icon(
                        AppIcons.File,
                        contentDescription = stringResource(R.string.attach_file),
                        modifier = Modifier.size(IconSize),
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
                    .onFocusChanged { focused = it.isFocused },
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                decorationBox = { field ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor.copy(alpha = if (enabled) 0.55f else 0.30f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        field()
                    }
                },
            )
            // Lights up once there is something to send
            IconButton(onClick = onSend, enabled = canSend, modifier = Modifier.size(ControlHeight)) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.send),
                    modifier = Modifier.size(IconSize),
                    tint = if (canSend) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.38f),
                )
            }
        }
    }
}
