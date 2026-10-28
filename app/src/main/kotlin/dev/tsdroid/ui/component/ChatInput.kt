package dev.tsdroid.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.tsdroid.han.R
import dev.tsdroid.ui.icon.AppIcons
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
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        AppIcons.Whisper,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = WhisperAmber,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.whisper_banner, whisperTargetName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onAttach != null) {
                IconButton(onClick = onAttach, enabled = attachEnabled) {
                    Icon(AppIcons.File, contentDescription = stringResource(R.string.attach_file))
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    // Light glass: the panel's refraction shows through, the border keeps
                    // the focus affordance, and every text colour follows the glass.
                    focusedTextColor = LocalContentColor.current,
                    unfocusedTextColor = LocalContentColor.current,
                    disabledTextColor = LocalContentColor.current.copy(alpha = 0.38f),
                    focusedContainerColor = LocalContentColor.current.copy(alpha = 0.10f),
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = LocalContentColor.current.copy(alpha = 0.35f),
                    disabledBorderColor = LocalContentColor.current.copy(alpha = 0.15f),
                    focusedPlaceholderColor = LocalContentColor.current.copy(alpha = 0.55f),
                    unfocusedPlaceholderColor = LocalContentColor.current.copy(alpha = 0.55f),
                    disabledPlaceholderColor = LocalContentColor.current.copy(alpha = 0.30f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            IconButton(onClick = onSend, enabled = canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
            }
        }
    }
}
