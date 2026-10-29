package dev.tsdroid.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tsdroid.bridge.TsFileEntry
import dev.tsdroid.han.R
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.InsetFill
import dev.tsdroid.ui.theme.InsetPadding
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.Spacing
import dev.tslib.Channel
import dev.tslib.User
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

sealed class ShareTarget {
    data object Channel : ShareTarget()

    /** [peerKey] is the peer's permanent uid, not the session client id. */
    data class PrivateMessage(val peerKey: String, val nickname: String) : ShareTarget()
}

/** What unfolds under a file's row: its actions, or one of them in progress. */
private enum class EntryPanel { ACTIONS, RENAME, SHARE }

/** How long Delete waits for its second tap. */
private const val DELETE_CONFIRM_MS = 3000L

/**
 * The Files tab of the server's Channels section: a toolbar with the folder it shows and what can be
 * done there, then the files. Nothing here opens anything outside the section: a file's actions,
 * renaming it, choosing whom to send it to and naming a new folder all unfold in place.
 */
@Composable
fun FileManager(
    currentPath: String,
    /** The channel whose files these are: TeamSpeak keeps files per channel. */
    channelName: String?,
    files: List<TsFileEntry>,
    isLoading: Boolean,
    users: List<User>,
    /** Left out of the people a file can be sent to */
    selfId: Int?,
    permissionHints: Long = 0L,
    onNavigateToFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onCreateDirectory: (String) -> Unit,
    onUploadFile: (String, ByteArray) -> Unit,
    onShareFile: (ShareTarget, String, Long) -> Unit,
    onPreviewImage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Permission flags — write operations require explicit permission bits.
    // When permissionHints == 0 (server didn't send hints), hide write buttons.
    // Download/browse are always allowed (CommandError toast handles denials).
    val canUpload = (permissionHints and Channel.PERM_FILE_UPLOAD) != 0L
    val canDelete = (permissionHints and Channel.PERM_FILE_DELETE) != 0L
    val canRename = (permissionHints and Channel.PERM_FILE_RENAME) != 0L
    val canCreateDir = (permissionHints and Channel.PERM_FILE_DIRECTORY_CREATE) != 0L
    val hasHints = permissionHints != 0L
    val canDownload = !hasHints || (permissionHints and Channel.PERM_FILE_DOWNLOAD) != 0L

    var newFolderOpen by remember { mutableStateOf(false) }
    // The one entry whose panel is unfolded, and what the panel shows
    var openEntry by remember { mutableStateOf<String?>(null) }
    var panel by remember { mutableStateOf(EntryPanel.ACTIONS) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(DELETE_CONFIRM_MS)
            confirmDelete = false
        }
    }
    // Another folder has other entries: nothing stays unfolded across it
    LaunchedEffect(currentPath) {
        openEntry = null
        newFolderOpen = false
    }

    val pickUpload = rememberUploadPicker(onUploadFile)
    val shareUsers = remember(users, selfId) {
        users.filter { it.id != selfId && it.clientType.toInt() == 0 }
    }

    // Sort: folders first, then files, alphabetically
    val sortedFiles = remember(files) {
        files.sortedWith(compareBy<TsFileEntry> { it.isFile }.thenBy { it.name.lowercase() })
    }

    Column(modifier = modifier.fillMaxSize()) {
        // "/" alone did not say whose files these are
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = InsetPadding, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentPath != "/") {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(IconSize),
                    )
                }
            }
            Text(
                text = when {
                    channelName == null -> currentPath
                    currentPath == "/" -> stringResource(R.string.files_in_channel, channelName)
                    else -> channelName + currentPath
                },
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (canCreateDir) {
                IconButton(onClick = { newFolderOpen = !newFolderOpen }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.new_folder), modifier = Modifier.size(IconSize))
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh), modifier = Modifier.size(IconSize))
            }
            if (canUpload) {
                IconButton(onClick = pickUpload) {
                    Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.upload), modifier = Modifier.size(IconSize))
                }
            }
        }
        // A new folder is named under the toolbar that asked for it
        AnimatedVisibility(
            visible = newFolderOpen,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            NameEditor(
                label = stringResource(R.string.enter_folder_name),
                confirmText = stringResource(R.string.create),
                onConfirm = { name ->
                    onCreateDirectory(name)
                    newFolderOpen = false
                },
                onDismiss = { newFolderOpen = false },
            )
        }

        // The files' own area, set into the island like the chat's messages
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(Shapes.Standard)
                .background(InsetFill),
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                sortedFiles.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_files),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalContentColor.current.copy(alpha = 0.78f),
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sortedFiles, key = { it.name }) { entry ->
                            val open = openEntry == entry.name
                            Column(modifier = Modifier.animateItem()) {
                                FileEntryRow(
                                    entry = entry,
                                    canDownload = canDownload,
                                    // Download and share are for files; folders only have the rest
                                    hasMenu = (entry.isFile && canDownload) || canRename || canDelete,
                                    onFolderClick = { onNavigateToFolder(entry.name) },
                                    onDownload = { onDownload(entry.name) },
                                    onPreviewImage = { onPreviewImage(entry.name) },
                                    onToggleMenu = {
                                        openEntry = if (open) null else entry.name
                                        panel = EntryPanel.ACTIONS
                                        confirmDelete = false
                                    },
                                )
                                AnimatedVisibility(
                                    visible = open,
                                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                                ) {
                                    val close = { openEntry = null }
                                    when (panel) {
                                        EntryPanel.ACTIONS -> EntryActions(
                                            canDownload = entry.isFile && canDownload,
                                            canRename = canRename,
                                            canDelete = canDelete,
                                            confirmDelete = confirmDelete,
                                            onDownload = {
                                                close()
                                                onDownload(entry.name)
                                            },
                                            onShare = { panel = EntryPanel.SHARE },
                                            onRename = { panel = EntryPanel.RENAME },
                                            onDelete = {
                                                if (confirmDelete) {
                                                    confirmDelete = false
                                                    close()
                                                    onDelete(entry.name)
                                                } else {
                                                    confirmDelete = true
                                                }
                                            },
                                        )
                                        EntryPanel.RENAME -> NameEditor(
                                            label = stringResource(R.string.enter_new_name),
                                            initialValue = entry.name,
                                            confirmText = stringResource(R.string.rename),
                                            onConfirm = { newName ->
                                                if (newName != entry.name) onRename(entry.name, newName)
                                                close()
                                            },
                                            onDismiss = close,
                                        )
                                        EntryPanel.SHARE -> ShareTargets(
                                            users = shareUsers,
                                            onSelect = { target ->
                                                close()
                                                onShareFile(target, entry.name, entry.size)
                                            },
                                            onDismiss = close,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A file's actions, unfolded under its row: a first tap on Delete waits for a second. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryActions(
    canDownload: Boolean,
    canRename: Boolean,
    canDelete: Boolean,
    confirmDelete: Boolean,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InsetPadding)
            .padding(bottom = Spacing.Gap),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Gap, Alignment.End),
    ) {
        if (canDelete) {
            TextButton(shape = Shapes.Standard, onClick = onDelete) {
                Text(
                    stringResource(if (confirmDelete) R.string.tap_again else R.string.delete_file),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (canRename) {
            TextButton(shape = Shapes.Standard, onClick = onRename) {
                Text(stringResource(R.string.rename))
            }
        }
        if (canDownload) {
            TextButton(shape = Shapes.Standard, onClick = onShare) {
                Text(stringResource(R.string.share))
            }
            TextButton(shape = Shapes.Standard, onClick = onDownload) {
                Text(stringResource(R.string.download))
            }
        }
    }
}

/** Whom a file is sent to: the channel, or one person in a private message. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareTargets(
    users: List<User>,
    onSelect: (ShareTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InsetPadding)
            .padding(bottom = Spacing.Gap),
        verticalArrangement = Arrangement.spacedBy(Spacing.Gap),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Gap),
            verticalArrangement = Arrangement.spacedBy(Spacing.Gap),
        ) {
            ChoicePill(
                label = stringResource(R.string.share_in_channel),
                selected = false,
                onClick = { onSelect(ShareTarget.Channel) },
            )
            users.forEach { user ->
                ChoicePill(
                    label = user.nickname,
                    selected = false,
                    onClick = { onSelect(ShareTarget.PrivateMessage(user.uid, user.nickname)) },
                )
            }
        }
        TextButton(
            shape = Shapes.Standard,
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

/** A name typed in place: a new folder's, or a file's new one. */
@Composable
private fun NameEditor(
    label: String,
    initialValue: String = "",
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val name = text.trim()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InsetPadding)
            .padding(bottom = Spacing.Gap),
        verticalArrangement = Arrangement.spacedBy(Spacing.Gap),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            shape = Shapes.Standard,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Gap, Alignment.End),
        ) {
            TextButton(shape = Shapes.Standard, onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                shape = Shapes.Standard,
                onClick = { onConfirm(name) },
                enabled = name.isNotEmpty(),
            ) {
                Text(confirmText)
            }
        }
    }
}

@Composable
private fun FileEntryRow(
    entry: TsFileEntry,
    canDownload: Boolean = true,
    /** Whether the row has any action for its ⋮ button to unfold */
    hasMenu: Boolean,
    onFolderClick: () -> Unit,
    onDownload: () -> Unit,
    onPreviewImage: () -> Unit,
    onToggleMenu: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!entry.isFile) onFolderClick()
                else if (isImageFile(entry.name)) onPreviewImage()
                else if (canDownload) onDownload()
            }
            .padding(start = InsetPadding, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            !entry.isFile -> Icons.Default.Folder
            isImageFile(entry.name) -> Icons.Default.Image
            else -> AppIcons.File
        }
        val tint = if (!entry.isFile) MaterialTheme.colorScheme.primary
            else LocalContentColor.current.copy(alpha = 0.78f)
        Icon(
            icon,
            contentDescription = if (!entry.isFile) stringResource(R.string.folder) else null,
            tint = tint,
            modifier = Modifier.size(IconSize),
        )

        Spacer(Modifier.width(12.dp))

        // Name + details
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.isFile) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = formatFileSize(context, entry.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                    Text(
                        text = formatTimestamp(entry.datetime),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
            }
        }

        if (hasMenu) {
            IconButton(onClick = onToggleMenu) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    modifier = Modifier.size(IconSize),
                )
            }
        }
    }
}

private fun isImageFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
}

/** Locale-default short date + time instead of a hardcoded French day/month order. */
private fun formatTimestamp(unixSeconds: Long): String {
    return try {
        DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
            Locale.getDefault(),
        ).format(Date(unixSeconds * 1000))
    } catch (_: Exception) {
        ""
    }
}
