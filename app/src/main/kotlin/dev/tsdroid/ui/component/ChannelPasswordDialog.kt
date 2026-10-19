package dev.tsdroid.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.tsdroid.han.R

/**
 * Asks for the password of a protected channel.
 *
 * TS3 refuses a move into a password-protected channel without one, and the
 * desktop client prompts for it; without this the tap on such a channel only
 * produced a server error. The password is never stored: it is handed to the one
 * move it belongs to, exactly like the desktop client does unless the user asked
 * it to be remembered.
 */
@Composable
fun ChannelPasswordDialog(
    channelName: String,
    wallpaper: ImageBitmap? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.channel_password_title, channelName),
        wallpaper = wallpaper,
        body = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.channel_password_label)) },
                singleLine = true,
                // A channel password is a shared secret typed once, not a field
                // the user needs to proof-read on screen.
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.26f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.44f),
                ),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty(),
            ) {
                Text(stringResource(R.string.channel_join))
            }
        },
    )
}
