package dev.tsdroid.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tsdroid.data.NicknameRules
import dev.tsdroid.han.R
import dev.tsdroid.ui.theme.InsetPadding
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.Spacing

/**
 * Renames the local client for the current session.
 *
 * This is the desktop client's "Self > Change nickname": the server keeps the new
 * name until the connection ends, and the identity file is not touched, so a
 * reconnect comes back with the nickname the session started with. The input is
 * checked against the same rules as the connect form rather than sending a name
 * the server would only reject.
 */
@Composable
fun NicknameEditor(
    currentNickname: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentNickname) }
    val normalized = NicknameRules.normalize(text)
    val tooShort = normalized.isNotEmpty() && normalized.length < NicknameRules.MIN_LENGTH
    val valid = NicknameRules.isValid(normalized)

    // Unfolded in place, under what asked for it
    Column(modifier = Modifier.fillMaxWidth().padding(InsetPadding)) {
        Text(text = stringResource(R.string.self_change_nickname), style = MaterialTheme.typography.labelLarge)
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                // Cap the length here instead of letting the server truncate a
                // name the user cannot see past the edge of the field.
                onValueChange = { text = it.take(NicknameRules.MAX_LENGTH) },
                label = { Text(stringResource(R.string.nickname)) },
                singleLine = true,
                isError = tooShort,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = Shapes.Standard,
            )
            Text(
                text = if (tooShort) {
                    stringResource(R.string.error_nickname_too_short)
                } else {
                    stringResource(R.string.nickname_temporary_hint)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (tooShort) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.Gap),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Gap, Alignment.End),
        ) {
            TextButton(onClick = onDismiss, shape = Shapes.Standard) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                shape = Shapes.Standard,
                onClick = {
                    onConfirm(normalized)
                    onDismiss()
                },
                enabled = valid,
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
