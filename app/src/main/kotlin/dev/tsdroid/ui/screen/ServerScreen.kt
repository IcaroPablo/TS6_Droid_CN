package dev.tsdroid.ui.screen

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tsdroid.data.PrivateConversations
import dev.tsdroid.han.R
import dev.tsdroid.bridge.AudioBridge
import dev.tsdroid.bridge.MicMode
import dev.tsdroid.service.WhisperManager
import dev.tsdroid.service.WhisperRouting
import dev.tsdroid.service.WhisperTargets
import dev.tslib.Channel
import dev.tslib.ConnectionState
import dev.tslib.User
import dev.tsdroid.ui.component.AnimeBackground
import dev.tsdroid.ui.component.AnimeWallpaperState
import dev.tsdroid.ui.component.ChannelTree
import dev.tsdroid.ui.component.ChannelPasswordDialog
import dev.tsdroid.ui.component.NicknameDialog
import dev.tsdroid.ui.component.ChatView
import dev.tsdroid.ui.component.CompactTopBar
import dev.tsdroid.ui.component.FileManagerDialog
import dev.tsdroid.ui.component.GlassCard
import dev.tsdroid.ui.component.LiquidGlassPanel
import dev.tsdroid.ui.component.LocalGlassWallpaper
import dev.tsdroid.ui.component.ShareTarget
import dev.tsdroid.ui.component.ServerInfoSheet
import dev.tsdroid.ui.component.UserPanelSheet
import dev.tsdroid.ui.component.WhisperListSheet
import dev.tsdroid.ui.component.parseSpacer
import dev.tsdroid.ui.component.rememberGlassBackdrop
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.viewmodel.ChatMessage
import dev.tsdroid.viewmodel.DownloadState
import dev.tsdroid.viewmodel.FileAttachment
import dev.tsdroid.viewmodel.ServerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    onBackToHome: () -> Unit,
    onNavigateToAbout: () -> Unit,
    /** What startup read, so this screen opens on the wallpaper instead of an empty frame. */
    initialAnimeBackground: Boolean,
    viewModel: ServerViewModel = viewModel(),
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val channelIcons by viewModel.channelIcons.collectAsStateWithLifecycle()
    val userAvatars by viewModel.avatars.collectAsStateWithLifecycle()
    val serverInfo by viewModel.serverInfo.collectAsStateWithLifecycle()
    val channelMessages by viewModel.channelMessages.collectAsStateWithLifecycle()
    val privateMessages by viewModel.privateMessages.collectAsStateWithLifecycle()
    val isPttMode by viewModel.isPttMode.collectAsStateWithLifecycle()
    val micMode by viewModel.micMode.collectAsStateWithLifecycle()
    val userVolumes by viewModel.userVolumes.collectAsStateWithLifecycle()
    val isOutputMuted by viewModel.isOutputMuted.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val unreadChannel by viewModel.unreadChannel.collectAsStateWithLifecycle()
    val unreadPrivate by viewModel.unreadPrivate.collectAsStateWithLifecycle()
    val audioGain by viewModel.audioGain.collectAsStateWithLifecycle()
    val showLinkThumbnails by viewModel.showLinkThumbnails.collectAsStateWithLifecycle()
    val autoLoadImages by viewModel.autoLoadImages.collectAsStateWithLifecycle()
    val animeBackground by viewModel.animeBackground
        .collectAsStateWithLifecycle(initialValue = initialAnimeBackground)
    val noiseSuppression by viewModel.noiseSuppression.collectAsStateWithLifecycle()
    val mutedUserIds by viewModel.mutedUserIds.collectAsStateWithLifecycle()
    val whisperTalkingUserIds by viewModel.whisperTalkingUserIds.collectAsStateWithLifecycle()
    val serverGroups by viewModel.serverGroups.collectAsStateWithLifecycle()

    // Channel groups (id + name) for the member panel's channel-group row.
    val channelGroups by viewModel.channelGroups.collectAsStateWithLifecycle()
    val fileManagerOpen by viewModel.fileManagerOpen.collectAsStateWithLifecycle()
    val fileList by viewModel.fileList.collectAsStateWithLifecycle()
    val previewImageBitmap by viewModel.previewImageBitmap.collectAsStateWithLifecycle()
    val previewImageName by viewModel.previewImageName.collectAsStateWithLifecycle()
    val currentFilePath by viewModel.currentFilePath.collectAsStateWithLifecycle()
    val fileManagerLoading by viewModel.fileManagerLoading.collectAsStateWithLifecycle()
    val channelPermissions by viewModel.currentChannelPermissions.collectAsStateWithLifecycle()
    val reconnectStatus by viewModel.reconnectStatus.collectAsStateWithLifecycle()
    val micStatus by viewModel.micStatus.collectAsStateWithLifecycle()

    var chatOpen by remember { mutableStateOf(false) }
    var chatEverOpened by remember { mutableStateOf(false) }
    var chatTab by remember { mutableIntStateOf(0) }
    var messageText by remember { mutableStateOf("") }
    // Conversations are identified by the peer's permanent uid, not by the session
    // client id: a reconnect used to reassign the client id and split one person
    // into several conversation chips.
    var pmTargetUid by remember { mutableStateOf<String?>(null) }
    var showServerInfo by remember { mutableStateOf(false) }

    // Resolve pmTarget User from users list
    val pmTarget = pmTargetUid?.let { uid -> users.find { it.uid == uid } }

    // 密聊 target list lives in the manager singleton; reading its Compose state
    // here is what repaints the indicators when a target is added or dropped.
    val whisperTargets = WhisperManager.targets
    val whisperHasTargets = whisperTargets.isNotEmpty
    val whisperLabel = whisperTargetLabel(whisperTargets, users, channels, serverGroups)
    var whisperSheetOpen by remember { mutableStateOf(false) }
    // Channel waiting for its password (TS3 refuses the move without it).
    var channelPasswordTarget by remember { mutableStateOf<dev.tslib.Channel?>(null) }
    // Temporary-nickname dialog (own row's panel → 修改临时昵称).
    var nicknameDialogOpen by remember { mutableStateOf(false) }


    // Build PM conversation list (uid → display name) from the message map + users list
    val context = LocalContext.current
    val pmConversationUsers: List<Pair<String, String>> = remember(privateMessages, users) {
        privateMessages.keys.map { peerKey ->
            val name = users.find { it.uid == peerKey }?.nickname
                ?: privateMessages[peerKey]?.lastOrNull { !it.isMe }?.sender
                // A conversation that never resolved to a uid (the peer was not in
                // the user list yet) keeps the old generic label.
                ?: if (PrivateConversations.isLegacy(peerKey)) {
                    context.getString(
                        R.string.user_fallback,
                        PrivateConversations.clientIdOf(peerKey) ?: 0,
                    )
                } else {
                    peerKey
                }
            peerKey to name
        }
    }

    val totalUnreadPrivate = unreadPrivate.values.sum()

    // One entry point for the whisper toggle, shared by the member row's inline
    // button and the long-press panel, so the feedback toast is identical.
    val toggleWhisperWithFeedback: (User) -> Unit = { user ->
        val toggled = viewModel.toggleWhisper(user)
        val key = PrivateConversations.keyFor(user.uid, user.id)
        val message = when {
            viewModel.whisperTargets.hasClient(key) ->
                context.getString(R.string.whisper_member_added, user.nickname)
            toggled ->
                context.getString(R.string.whisper_member_removed, user.nickname)
            else ->
                context.getString(R.string.whisper_list_full, WhisperRouting.MAX_TARGETS)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val whisperSelectedMembers = whisperTargets.clients.map { key ->
        key to (users.firstOrNull { PrivateConversations.keyFor(it.uid, it.id) == key }?.nickname
            ?: context.getString(R.string.whisper_target_offline))
    }

    // Sync chat state to ViewModel for unread tracking
    LaunchedEffect(chatOpen, chatTab, pmTargetUid) {
        viewModel.setChatState(chatOpen, chatTab, pmTargetUid)
    }

    DisposableEffect(Unit) {
        viewModel.bindToService()
        onDispose {}
    }
    // Leave this screen — home button or a session that ended on its own. Going home never drops the
    // connection; only the disconnect button above does that.
    // While the service is auto-reconnecting we stay here and show a banner instead.
    LaunchedEffect(connectionState, reconnectStatus.active) {
        if (connectionState == ConnectionState.DISCONNECTED && !reconnectStatus.active) {
            onBackToHome()
        }
    }
    if (connectionState == ConnectionState.DISCONNECTED && !reconnectStatus.active) return

    // The floating window is no longer driven from here: it belongs to the service, which shows it
    // when the app goes to the background. See TsConnectionService.setAppInForeground.

    val totalUnread = unreadChannel + totalUnreadPrivate
    val serverAddressLabel by viewModel.serverAddressLabel.collectAsStateWithLifecycle()

    // Backdrop for the liquid-glass surfaces on this screen: only when the wallpaper is actually
    // painted behind them, exactly like ConnectionScreen does.
    val glassWallpaper = rememberGlassBackdrop(animeBackground)

    // Per-user panel (volume / mute / info) — opened by long-pressing a row
    var userPanelUserId by remember { mutableStateOf<Int?>(null) }

    if (showServerInfo) {
        ServerInfoSheet(
            info = serverInfo,
            address = serverAddressLabel,
            wallpaper = glassWallpaper,
            onDismiss = { showServerInfo = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimeBackground(enabled = animeBackground)

        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            Column {
                CompactTopBar(
                    title = serverInfo?.name ?: stringResource(R.string.server),
                    // Leaving the server screen is not disconnecting: the session keeps running in
                    // the service, so it can be picked up again from the home screen.
                    navigationIcon = {
                        IconButton(onClick = onBackToHome) {
                            Icon(
                                AppIcons.Home,
                                contentDescription = stringResource(R.string.back_to_home),
                            )
                        }
                    },
                    actions = {
                        // 密聊: green while a target set is live, and the way into
                        // the whisper list editor (desktop's whisper list).
                        IconButton(onClick = { whisperSheetOpen = true }) {
                            Icon(
                                AppIcons.Whisper,
                                contentDescription = stringResource(R.string.whisper_list_title),
                                tint = if (whisperHasTargets) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { showServerInfo = true }) {
                            Icon(AppIcons.Info, contentDescription = stringResource(R.string.server_info))
                        }
                        IconButton(onClick = { viewModel.toggleFileManager() }) {
                            Icon(AppIcons.File, contentDescription = stringResource(R.string.file_manager))
                        }
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(AppIcons.Exit, contentDescription = stringResource(R.string.disconnect))
                        }
                    },
                )
                // Connection / microphone health, so a silent failure is never invisible again
                if (reconnectStatus.active) {
                    StatusBanner(
                        text = stringResource(
                            R.string.reconnect_in_progress,
                            maxOf(reconnectStatus.attempt, 1),
                        ),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else if (micStatus == AudioBridge.MicStatus.UNAVAILABLE) {
                    StatusBanner(
                        text = stringResource(R.string.mic_unavailable_warning),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // The controls used to float straight on the wallpaper with no panel at all, so
                // their contrast was pure luck. They now sit on a floating glass bar.
                LiquidGlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    wallpaper = glassWallpaper,
                    shape = RoundedCornerShape(28.dp),
                    elevation = 6.dp,
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Chat FAB with badge
                        Box {
                            IconButton(onClick = {
                                chatOpen = !chatOpen
                            }) {
                                Icon(
                                    AppIcons.Sms,
                                    contentDescription = stringResource(R.string.chat),
                                    tint = Color(0xFF4CAF50),
                                )
                            }
                            if (totalUnread > 0) {
                                Badge(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-4).dp, y = 4.dp),
                                ) {
                                    Text("$totalUnread")
                                }
                            }
                        }

                        // Hold-to-whisper microphone, immediately left of the main
                        // one: a second push-to-talk that only reaches the whisper
                        // list, so nothing has to be switched on beforehand.
                        WhisperHoldMic(
                            enabled = whisperHasTargets,
                            // The empty check happens in the ViewModel, at press
                            // time: this gesture handler is created once and would
                            // otherwise keep a stale "no targets" answer forever.
                            onHold = {
                                if (!viewModel.beginWhisperHold()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.whisper_need_targets),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onRelease = { viewModel.endWhisperHold() },
                        )

                        // Center button: PTT or Mute depending on MODE (not mute state)
                        if (isPttMode) {
                            // PTT mode: hold to talk — a glass circle whose pressed state is carried
                            // by the icon tint and the rim, not by an opaque fill.
                            var isPressed by remember { mutableStateOf(false) }
                            GlassCard(
                                modifier = Modifier
                                    .size(72.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                isPressed = true
                                                viewModel.setPushToTalk(true)
                                                tryAwaitRelease()
                                                viewModel.setPushToTalk(false)
                                                isPressed = false
                                            },
                                        )
                                    },
                                wallpaper = glassWallpaper,
                                shape = CircleShape,
                                elevation = 4.dp,
                            ) {
                                val pttTint = if (isPressed) MaterialTheme.colorScheme.primary
                                    else LocalContentColor.current.copy(alpha = 0.88f)
                                val pttRim = if (isPressed) MaterialTheme.colorScheme.primary
                                    else LocalContentColor.current.copy(alpha = 0.30f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(
                                            width = if (isPressed) 2.dp else 1.dp,
                                            color = pttRim,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            AppIcons.MicOn,
                                            contentDescription = stringResource(R.string.push_to_talk),
                                            modifier = Modifier.size(28.dp),
                                            tint = pttTint,
                                        )
                                        Text(
                                            stringResource(R.string.ptt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = pttTint,
                                        )
                                    }
                                }
                            }
                        } else {
                            // Voice activity mode: click to mute and go back to PTT. The error
                            // colour is carried by the icon tint and the rim.
                            GlassCard(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clickable { viewModel.toggleVoiceMode() },
                                wallpaper = glassWallpaper,
                                shape = CircleShape,
                                elevation = 4.dp,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(
                                            width = 1.5.dp,
                                            color = MaterialTheme.colorScheme.error,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            AppIcons.MicOff,
                                            contentDescription = stringResource(R.string.mute_mic),
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                        Text(
                                            stringResource(R.string.mute),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }

                        // Toggle voice mode (PTT →Voice Activity)
                        IconButton(onClick = { viewModel.toggleVoiceMode() }) {
                            Icon(
                                if (isPttMode) AppIcons.MicOff else AppIcons.MicOn,
                                // The three-way mode lives in Settings; here the
                                // label only has to say what a tap will do.
                                contentDescription = stringResource(
                                    when {
                                        isPttMode -> R.string.unmute_mic
                                        micMode == MicMode.VAD -> R.string.mic_mode_vad
                                        else -> R.string.mute_mic
                                    }
                                ),
                                tint = if (isPttMode) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                            )
                        }

                        // Toggle Output Mute (Deafen)
                        IconButton(onClick = { viewModel.toggleOutputMute() }) {
                            Icon(
                                if (isOutputMuted) AppIcons.SpeakerOff else AppIcons.SpeakerOn,
                                contentDescription = stringResource(if (isOutputMuted) R.string.notif_unmute else R.string.notif_mute),
                                tint = if (isOutputMuted) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Channel tree —full screen
            ChannelTree(
                channels = channels,
                users = users,
                onChannelClick = { channelId ->
                    // A protected channel needs its password before the server
                    // will accept the move; asking here keeps the tap from ending
                    // in a bare server error.
                    val channel = channels.find { it.id == channelId }
                    if (channel?.hasPassword == true) {
                        channelPasswordTarget = channel
                    } else {
                        viewModel.moveToChannel(channelId)
                    }
                },
                onUserClick = { user ->
                    pmTargetUid = user.uid
                    chatTab = 1
                    chatOpen = true
                },
                onUserLongClick = { user -> viewModel.toggleMuteUser(user.id) },
                onUserLongPress = { user ->
                    // The own row opens the same panel to read your own server
                    // groups; the interactive sections hide themselves for self.
                    userPanelUserId = user.id
                },
                onChannelLongPress = { channel ->
                    // Long-press a channel to whisper to everyone in it (TS3 whisper
                    // list semantics: whole channels are targets too).
                    val toggled = viewModel.toggleChannelWhisper(channel.id)
                    val message = when {
                        viewModel.whisperTargets.hasChannel(channel.id) ->
                            context.getString(R.string.whisper_channel_added, channel.name)
                        toggled ->
                            context.getString(R.string.whisper_channel_removed, channel.name)
                        else ->
                            context.getString(
                                R.string.whisper_list_full,
                                WhisperRouting.MAX_TARGETS,
                            )
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
                onWhisperClick = { user -> toggleWhisperWithFeedback(user) },
                mutedUserIds = mutedUserIds,
                whisperChannelIds = whisperTargets.channels.toSet(),
                whisperTargetKeys = whisperTargets.clients.toSet(),
                whisperTalkingUserIds = whisperTalkingUserIds,
                whisperModeActive = WhisperManager.isRouted,
                channelIcons = channelIcons,
                userAvatars = userAvatars,
                selfId = viewModel.myClientId,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
            )

            // File manager —slides up from bottom, fills content area
            val fileManagerProgress by animateFloatAsState(
                targetValue = if (fileManagerOpen) 0f else 1f,
                animationSpec = tween(300),
                label = "fileManager",
            )
            if (fileManagerOpen || fileManagerProgress < 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = size.height * fileManagerProgress },
                ) {
                    FileManagerDialog(
                        currentPath = currentFilePath,
                        wallpaper = glassWallpaper,
                        files = fileList,
                        isLoading = fileManagerLoading,
                        users = users,
                        permissionHints = channelPermissions,
                        onNavigateToFolder = { viewModel.navigateToFolder(it) },
                        onNavigateUp = { viewModel.navigateUp() },
                        onRefresh = { viewModel.refreshFileList() },
                        onDownload = { viewModel.downloadFileFromManager(it) },
                        onDelete = { viewModel.deleteFileInChannel(it) },
                        onRename = { old, new -> viewModel.renameFileInChannel(old, new) },
                        onCreateDirectory = { viewModel.createDirectoryInChannel(it) },
                        onUploadFile = { name, data -> viewModel.uploadFileToChannel(name, data) },
                        onShareFile = { target, name, size ->
                            when (target) {
                                is ShareTarget.Channel -> viewModel.shareFile(null, name, size)
                                is ShareTarget.PrivateMessage -> viewModel.shareFile(target.peerKey, name, size)
                            }
                        },
                        onDismiss = { viewModel.closeFileManager() },
                        onPreviewImage = { fileName ->
                            viewModel.previewImageFile(fileName)
                        },
                    )
                }
            }

            // Chat panel —slides up from bottom, fills content area
            // Once opened, stay composed so re-opening is instant (no recomposition)
            if (chatOpen) chatEverOpened = true
            val chatProgress by animateFloatAsState(
                targetValue = if (chatOpen) 0f else 1f,
                animationSpec = tween(300),
                label = "chat",
            )
            if (chatEverOpened) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .graphicsLayer { translationY = size.height * chatProgress },
                ) {
                    ChatPanel(
                        chatTab = chatTab,
                        onTabChange = { chatTab = it },
                        wallpaper = glassWallpaper,
                        channelMessages = channelMessages,
                        privateMessages = pmTargetUid?.let { uid ->
                            privateMessages[uid] ?: emptyList()
                        } ?: privateMessages.values.flatten().sortedBy { it.timestamp },
                        messageText = messageText,
                        onMessageChange = { messageText = it },
                        pmTarget = pmTarget,
                        pmConversationUsers = pmConversationUsers,
                        onSelectPmUser = { uid -> pmTargetUid = uid },
                        onClearPmTarget = { pmTargetUid = null },
                        onSend = {
                            when {
                                // While密聊 is live the microphone is on the target
                                // set, so the text follows the voice: it goes to
                                // those same people as private messages.
                                whisperHasTargets -> viewModel.sendWhisperMessage(messageText)
                                chatTab == 0 -> viewModel.sendChannelMessage(messageText)
                                else -> pmTargetUid?.let { viewModel.sendPrivateMessage(it, messageText) }
                            }
                            messageText = ""
                        },
                        onClose = { chatOpen = false },
                        unreadChannel = unreadChannel,
                        unreadPrivateTotal = totalUnreadPrivate,
                        unreadPrivatePerUser = unreadPrivate,
                        showLinkThumbnails = showLinkThumbnails,
                        autoLoadImages = autoLoadImages,
                        canUploadFiles = (channelPermissions and dev.tslib.Channel.PERM_FILE_UPLOAD) != 0L,
                        onUploadFile = { fileName, data ->
                            viewModel.uploadAndSendFile(fileName, data, chatTab == 1, pmTargetUid)
                        },
                        onDownload = { attachment -> viewModel.downloadAttachment(attachment) },
                        isWhisperActive = whisperHasTargets,
                        whisperTargetName = whisperLabel,
                    )
                }
            }
        }
    }

    // 密聊 list editor — desktop-equivalent target picker (clients / channels / groups)
    if (whisperSheetOpen) {
        LaunchedEffect(Unit) { viewModel.refreshGroupLists() }
        WhisperListSheet(
            targets = whisperTargets,
            groups = serverGroups,
            // Spacer channels are decoration, not destinations: nobody sits in them.
            channels = channels.filter { parseSpacer(it.name) == null },
            selectedMembers = whisperSelectedMembers,
            membersOfGroup = { groupId -> viewModel.onlineMembersOfGroup(groupId) },
            onToggleGroup = { groupId ->
                // A group is one entry, so the cap only refuses a full list.
                if (!viewModel.toggleGroupWhisper(groupId)) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.whisper_list_full, WhisperRouting.MAX_TARGETS),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onToggleChannel = { channelId ->
                if (!viewModel.toggleChannelWhisper(channelId)) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.whisper_list_full, WhisperRouting.MAX_TARGETS),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onRemoveMember = { key ->
                users.firstOrNull { PrivateConversations.keyFor(it.uid, it.id) == key }
                    ?.let { toggleWhisperWithFeedback(it) }
            },
            onStop = { viewModel.stopWhisper() },
            onDismiss = { whisperSheetOpen = false },
            wallpaper = glassWallpaper,
        )
    }

    // Temporary nickname: applies to this connection only, and the identity file
    // is never touched (see ServerViewModel.changeTemporaryNickname).
    if (nicknameDialogOpen) {
        val selfName = users.firstOrNull { it.id == viewModel.myClientId }?.nickname ?: ""
        NicknameDialog(
            currentNickname = selfName,
            wallpaper = glassWallpaper,
            onConfirm = { nickname -> viewModel.changeTemporaryNickname(nickname) },
            onDismiss = { nicknameDialogOpen = false },
        )
    }

    // Password prompt for a protected channel: without it the tap only produced
    // a server error and the client stayed where it was.
    channelPasswordTarget?.let { channel ->
        ChannelPasswordDialog(
            channelName = channel.name,
            wallpaper = glassWallpaper,
            onConfirm = { password ->
                viewModel.moveToChannel(channel.id, password)
                channelPasswordTarget = null
            },
            onDismiss = { channelPasswordTarget = null },
        )
    }

    // Per-user panel (volume / mute / info) — long-press a user row
    val panelUser = users.find { it.id == userPanelUserId }

    // The panel names the member's groups, and the answers to both group list
    // requests may not have arrived yet when it opens.
    LaunchedEffect(userPanelUserId) {
        if (userPanelUserId != null) viewModel.refreshGroupLists()
    }
    UserPanelSheet(
        user = panelUser,
        avatar = panelUser?.uid?.let { userAvatars[it] },
        volumeDb = userPanelUserId?.let { userVolumes[it] } ?: 0f,
        isMuted = userPanelUserId != null && userPanelUserId in mutedUserIds,
        onVolumeChange = { db -> userPanelUserId?.let { viewModel.setUserVolumeDb(it, db, commit = false) } },
        onVolumeCommit = { db -> userPanelUserId?.let { viewModel.setUserVolumeDb(it, db, commit = true) } },
        onToggleMute = { userPanelUserId?.let { viewModel.toggleMuteUser(it) } },
        onPrivateMessage = {
            val id = userPanelUserId
            if (id != null) {
                // Open the conversation under the peer's uid, matching every other
                // entry point into private chat.
                users.find { it.id == id }?.let { pmTargetUid = it.uid }
                chatTab = 1
                chatOpen = true
            }
            userPanelUserId = null
        },
        onDismiss = { userPanelUserId = null },
        isWhisperTarget = panelUser?.let {
            viewModel.whisperTargets.hasClient(PrivateConversations.keyFor(it.uid, it.id))
        } ?: false,
        onToggleWhisper = { panelUser?.let { toggleWhisperWithFeedback(it) } },
        isSelf = panelUser != null && panelUser.id == viewModel.myClientId,
        knownServerGroups = serverGroups,
        knownChannelGroups = channelGroups,
        onChangeNickname = {
            // Close the panel first: two glass overlays stacked read as a bug.
            userPanelUserId = null
            nicknameDialogOpen = true
        },
        wallpaper = glassWallpaper,
    )

    // Image preview overlay (bitmap is pre-decoded off the main thread)
    if (previewImageBitmap != null) {
        Dialog(onDismissRequest = { viewModel.closePreview() }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .clipToBounds()
                    .clickable { viewModel.closePreview() },
                contentAlignment = Alignment.Center,
            ) {
                previewImageBitmap?.let { bitmap ->
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = previewImageName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPanel(
    chatTab: Int,
    onTabChange: (Int) -> Unit,
    wallpaper: ImageBitmap? = null,
    channelMessages: List<ChatMessage>,
    privateMessages: List<ChatMessage>,
    messageText: String,
    onMessageChange: (String) -> Unit,
    pmTarget: User?,
    pmConversationUsers: List<Pair<String, String>>,
    onSelectPmUser: (String) -> Unit,
    onClearPmTarget: () -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    unreadChannel: Int,
    unreadPrivateTotal: Int,
    unreadPrivatePerUser: Map<String, Int>,
    showLinkThumbnails: Boolean,
    autoLoadImages: Boolean = true,
    canUploadFiles: Boolean = true,
    onUploadFile: (String, ByteArray) -> Unit = { _, _ -> },
    onDownload: ((FileAttachment) -> StateFlow<DownloadState>)? = null,
    isWhisperActive: Boolean = false,
    whisperTargetName: String? = null,
) {
    val context = LocalContext.current
    val uploadScope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploadScope.launch {
            var fileName: String? = null
            var data: ByteArray? = null
            var tooLarge = false
            // Reading (up to 10 MB) must not happen on the main thread
            withContext(Dispatchers.IO) {
                try {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME) ?: -1
                    val sizeIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.SIZE) ?: -1
                    cursor?.moveToFirst()
                    fileName = if (nameIndex >= 0) cursor?.getString(nameIndex) ?: "file" else "file"
                    val fileSize = if (sizeIndex >= 0) cursor?.getLong(sizeIndex) ?: -1L else -1L
                    cursor?.close()
                    if (fileSize > 10_485_760) {
                        tooLarge = true
                        return@withContext
                    }
                    val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                        ?: return@withContext
                    if (bytes.size > 10_485_760) {
                        tooLarge = true
                    } else {
                        data = bytes
                    }
                } catch (_: Exception) {
                }
            }
            when {
                data != null && fileName != null -> onUploadFile(fileName!!, data!!)
                tooLarge -> Toast.makeText(context, R.string.file_too_large, Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Full-screen glass panel: this used to be a near-opaque white slab, the cheapest-looking
    // surface in the app. `contentColor` is derived from the wallpaper actually behind the glass,
    // so the children keep their contrast on any backdrop; nothing else about them changes.
    LiquidGlassPanel(
        modifier = Modifier.fillMaxSize(),
        wallpaper = wallpaper,
        shape = RoundedCornerShape(0.dp),
        elevation = 0.dp,
        contentPadding = PaddingValues(0.dp),
    ) { contentColor ->
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalGlassWallpaper provides wallpaper,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(8.dp),
            ) {
                // Header: tabs + close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabRow(
                        selectedTabIndex = chatTab,
                        modifier = Modifier.weight(1f),
                        containerColor = Color.Transparent,
                    ) {
                        Tab(
                            selected = chatTab == 0,
                            onClick = { onTabChange(0) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.tab_channel))
                                    if (unreadChannel > 0) {
                                        Spacer(Modifier.width(4.dp))
                                        Badge { Text("$unreadChannel") }
                                    }
                                }
                            },
                        )
                        Tab(
                            selected = chatTab == 1,
                            onClick = { onTabChange(1) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.tab_private))
                                    if (unreadPrivateTotal > 0) {
                                        Spacer(Modifier.width(4.dp))
                                        Badge { Text("$unreadPrivateTotal") }
                                    }
                                }
                            },
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                // PM conversation selector
                if (chatTab == 1 && pmConversationUsers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // "All" chip — the selected container is a small glass pill, not an
                        // opaque primary/secondary container fill.
                        GlassFilterChip(selected = pmTarget == null, wallpaper = wallpaper) {
                            val chipColor = if (pmTarget == null) LocalContentColor.current
                                else LocalContentColor.current.copy(alpha = 0.78f)
                            FilterChip(
                                selected = pmTarget == null,
                                onClick = { onClearPmTarget() },
                                label = {
                                    Text(stringResource(R.string.filter_all), color = chipColor)
                                },
                                leadingIcon = if (pmTarget == null) {
                                    {
                                        Icon(
                                            AppIcons.Sms,
                                            null,
                                            Modifier.size(16.dp),
                                            tint = chipColor,
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.Transparent,
                                ),
                            )
                        }
                        // One chip per conversation peer (keyed by uid)
                        pmConversationUsers.forEach { (peerKey, nickname) ->
                            val isSelected = pmTarget?.uid == peerKey
                            val userUnread = unreadPrivatePerUser[peerKey] ?: 0
                            GlassFilterChip(selected = isSelected, wallpaper = wallpaper) {
                                val chipColor = if (isSelected) LocalContentColor.current
                                    else LocalContentColor.current.copy(alpha = 0.78f)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectPmUser(peerKey) },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(nickname, color = chipColor)
                                            if (userUnread > 0) {
                                                Spacer(Modifier.width(4.dp))
                                                Badge { Text("$userUnread") }
                                            }
                                        }
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                Icons.Default.Person,
                                                null,
                                                Modifier.size(16.dp),
                                                tint = chipColor,
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.Transparent,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Messages
                val messages = when (chatTab) {
                    0 -> channelMessages
                    1 -> privateMessages
                    else -> emptyList()
                }
                // Swipe left/right to switch between the channel and the private tabs, like the app's
                // other tab hosts. Only horizontal drags are claimed, so the list still scrolls
                // vertically; a swipe past a third of the width switches, anything shorter springs
                // back.
                var swipeDrag by remember { mutableFloatStateOf(0f) }
                ChatView(
                    messages = messages,
                    showLinkThumbnails = showLinkThumbnails,
                    autoLoadImages = autoLoadImages,
                    onDownload = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta -> swipeDrag += delta },
                            onDragStopped = {
                                val travelled = swipeDrag
                                swipeDrag = 0f
                                val threshold = 120f
                                if (travelled < -threshold && chatTab == 0) onTabChange(1)
                                else if (travelled > threshold && chatTab == 1) onTabChange(0)
                            },
                        ),
                )

                // 密聊 banner: the microphone is on a target set, not the channel,
                // so this has to be visible right above the input at all times.
                if (isWhisperActive && whisperTargetName != null) {
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
                                tint = Color(0xFF4CAF50),
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

                // Message input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (canUploadFiles) {
                        IconButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            enabled = chatTab == 0 || pmTarget != null,
                        ) {
                            Icon(AppIcons.File, contentDescription = stringResource(R.string.attach_file))
                        }
                    }
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = onMessageChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                when {
                                    isWhisperActive && whisperTargetName != null ->
                                        stringResource(R.string.whisper_placeholder, whisperTargetName)
                                    chatTab == 0 -> stringResource(R.string.message_channel_placeholder)
                                    else -> stringResource(R.string.message_private_placeholder, pmTarget?.nickname ?: "?")
                                }
                            )
                        },
                        singleLine = true,
                        enabled = chatTab == 0 || pmTarget != null || isWhisperActive,
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
                    IconButton(
                        onClick = onSend,
                        enabled = messageText.isNotBlank() &&
                            (chatTab == 0 || pmTarget != null || isWhisperActive),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                    }
                }
            }
        }
    }
}

/**
 * Selected filter chips sit on a small glass pill instead of the previous opaque
 * primary/secondary container fill. The chip's own container stays transparent, and the chip's
 * label/icon colours are set explicitly to `LocalContentColor` at the call site, so the selected
 * state reads as "brighter label on glass" and the unselected one stays flat on the panel glass.
 */
@Composable
private fun GlassFilterChip(
    selected: Boolean,
    wallpaper: ImageBitmap?,
    chip: @Composable () -> Unit,
) {
    if (selected) {
        GlassCard(
            wallpaper = wallpaper,
            shape = RoundedCornerShape(8.dp),
            elevation = 2.dp,
        ) { chip() }
    } else {
        chip()
    }
}

/** Inline status strip used for reconnect / microphone health warnings. */
@Composable
private fun StatusBanner(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Hold-to-whisper microphone for the bottom bar, sitting next to the main one.
 *
 * It is a second push-to-talk, not a mode switch: while held the microphone is
 * open and routed to the whisper list; on release both go back. The normal
 * microphone keeps talking to the channel, so the two never share a hidden
 * state — the amber tint and the small whisper mark are the only difference,
 * and either button works without preparing anything first.
 */
@Composable
private fun WhisperHoldMic(
    enabled: Boolean,
    onHold: () -> Unit,
    onRelease: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val tint = if (enabled) WhisperAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    // `pointerInput(Unit)` keeps the lambda it was created with, so the newest
    // callbacks have to be read through these references on every press.
    val latestHold by rememberUpdatedState(onHold)
    val latestRelease by rememberUpdatedState(onRelease)

    Box(
        modifier = Modifier
            .size(52.dp)
            .background(
                if (pressed) WhisperAmber.copy(alpha = 0.18f) else Color.Transparent,
                CircleShape,
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        latestHold()
                        tryAwaitRelease()
                        latestRelease()
                        pressed = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons.MicOn,
            contentDescription = stringResource(R.string.whisper_hold_mic),
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
        Icon(
            AppIcons.Whisper,
            contentDescription = null,
            modifier = Modifier
                .size(13.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-9).dp, y = (-9).dp),
            tint = tint,
        )
    }
}

/** 密聊 (whisper) accent colour, shared by the member ring and the whisper mic. */
private val WhisperAmber = Color(0xFFFFB300)

/**
 * Display names for the current密聊 target set, or null while there is none.
 *
 * A client target is resolved through the same key private conversations use, so
 * it keeps its name across the target's reconnects; one that just went offline
 * falls back to a generic label instead of disappearing, because it is still on
 * the list and will be re-applied when it returns. Groups are named from the
 * server's group list and fall back to their id while that list has not arrived.
 */
@Composable
private fun whisperTargetLabel(
    targets: WhisperTargets,
    users: List<User>,
    channels: List<Channel>,
    groups: List<dev.tslib.ServerGroup>,
): String? {
    if (targets.isEmpty) return null
    val offline = stringResource(R.string.whisper_target_offline)
    val names = buildList {
        targets.clients.forEach { key ->
            val user = users.firstOrNull { PrivateConversations.keyFor(it.uid, it.id) == key }
            add(user?.nickname ?: offline)
        }
        targets.channels.forEach { id ->
            add(channels.firstOrNull { it.id == id }?.name ?: "#$id")
        }
        targets.groups.forEach { id ->
            val group = groups.firstOrNull { it.id == id }
            add(group?.name ?: stringResource(R.string.whisper_group_fallback, id))
        }
    }
    return names.joinToString(", ")
}
