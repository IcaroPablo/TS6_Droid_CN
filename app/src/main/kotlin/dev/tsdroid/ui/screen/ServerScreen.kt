package dev.tsdroid.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tsdroid.bridge.AudioBridge
import dev.tsdroid.data.PrivateConversations
import dev.tsdroid.han.R
import dev.tsdroid.service.WhisperManager
import dev.tsdroid.service.WhisperRouting
import dev.tsdroid.service.WhisperTargets
import dev.tsdroid.ui.component.AnimeBackground
import dev.tsdroid.ui.component.AwayStatusSheet
import dev.tsdroid.ui.component.ChannelPasswordDialog
import dev.tsdroid.ui.component.ChannelTree
import dev.tsdroid.ui.component.ChatInput
import dev.tsdroid.ui.component.ChatView
import dev.tsdroid.ui.component.ChoicePill
import dev.tsdroid.ui.component.FileManagerDialog
import dev.tsdroid.ui.component.FoldableIsland
import dev.tsdroid.ui.component.GlassCard
import dev.tsdroid.ui.component.GlassMenuItem
import dev.tsdroid.ui.component.Island
import dev.tsdroid.ui.component.IslandHeaderHeight
import dev.tsdroid.ui.component.NicknameDialog
import dev.tsdroid.ui.component.ServerIcon
import dev.tsdroid.ui.component.ServerInfoContent
import dev.tsdroid.ui.component.ShareTarget
import dev.tsdroid.ui.component.StreamTiles
import dev.tsdroid.ui.component.StreamViewer
import dev.tsdroid.ui.component.UserPanelSheet
import dev.tsdroid.ui.component.VoiceBar
import dev.tsdroid.ui.component.VoiceBarState
import dev.tsdroid.ui.component.WhisperListSheet
import dev.tsdroid.ui.component.parseSpacer
import dev.tsdroid.ui.component.rememberAppearance
import dev.tsdroid.ui.component.rememberGlassBackdrop
import dev.tsdroid.ui.component.rememberUploadPicker
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.Spacing
import dev.tsdroid.ui.theme.WhisperAmber
import dev.tsdroid.viewmodel.ChatMessage
import dev.tsdroid.viewmodel.DownloadState
import dev.tsdroid.viewmodel.FileAttachment
import dev.tsdroid.viewmodel.ServerViewModel
import dev.tslib.Channel
import dev.tslib.ConnectionState
import dev.tslib.User
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val streams by viewModel.streams.collectAsStateWithLifecycle()
    // Each member's streams; a member can share several screens or windows
    val streamsByUser = remember(streams) {
        streams.values.groupBy { it.clientId }.mapValues { (_, list) -> list.sortedBy { it.name } }
    }
    val streamSessions by viewModel.streamSessions.collectAsStateWithLifecycle()
    val streamLandscape by viewModel.streamLandscape.collectAsStateWithLifecycle()
    val channelIcons by viewModel.channelIcons.collectAsStateWithLifecycle()
    val userAvatars by viewModel.avatars.collectAsStateWithLifecycle()
    val serverInfo by viewModel.serverInfo.collectAsStateWithLifecycle()
    val channelMessages by viewModel.channelMessages.collectAsStateWithLifecycle()
    val privateMessages by viewModel.privateMessages.collectAsStateWithLifecycle()
    val isPttMode by viewModel.isPttMode.collectAsStateWithLifecycle()
    val userVolumes by viewModel.userVolumes.collectAsStateWithLifecycle()
    val isOutputMuted by viewModel.isOutputMuted.collectAsStateWithLifecycle()
    val isMicMuted by viewModel.isMicMuted.collectAsStateWithLifecycle()
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

    // The channel list and the chat docked under it can each be folded to their header
    var channelsOpen by rememberSaveable { mutableStateOf(true) }
    var chatOpen by rememberSaveable { mutableStateOf(false) }
    // The chat's share of the room when both are open, set by dragging the border between them
    var chatFraction by rememberSaveable { mutableFloatStateOf(0.55f) }
    // Whether the channels island shows the streams being watched rather than the channel list
    var showStream by rememberSaveable { mutableStateOf(false) }
    var draggingBorder by remember { mutableStateOf(false) }
    var chatTab by remember { mutableIntStateOf(0) }
    // Shared by the chat panel and the stream viewer's chat, and kept across
    // the stream viewer's rotation, which recreates the activity
    var messageText by rememberSaveable { mutableStateOf("") }
    // Conversations are identified by the peer's permanent uid, not by the session
    // client id: a reconnect used to reassign the client id and split one person
    // into several conversation chips.
    var pmTargetUid by remember { mutableStateOf<String?>(null) }
    var showServerInfo by remember { mutableStateOf(false) }
    var serverMenuOpen by remember { mutableStateOf(false) }

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
    // 状态 (online / away / do-not-disturb), the desktop client's status menu.
    val awayStatus by viewModel.awayStatus.collectAsStateWithLifecycle()
    val awayMessage by viewModel.awayMessage.collectAsStateWithLifecycle()
    var awaySheetOpen by remember { mutableStateOf(false) }


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

    // Back closes what is open on top of the channel tree before it leaves the
    // server screen; the chat is drawn over the file manager, so it goes first
    BackHandler(enabled = fileManagerOpen) {
        // In a folder, back goes up to its parent first
        if (currentFilePath != "/") viewModel.navigateUp() else viewModel.closeFileManager()
    }
    BackHandler(enabled = serverMenuOpen) { serverMenuOpen = false }
    // A folded chat must not keep the keyboard up for its hidden input
    val focusManager = LocalFocusManager.current
    LaunchedEffect(chatOpen) {
        if (!chatOpen) focusManager.clearFocus()
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

    // The server renames a client whose nickname is taken (ushummu → ushummu1). Say so once, when
    // our own name first shows up, or the user cannot tell which row is theirs.
    val selfNickname = users.firstOrNull { it.id == viewModel.myClientId }?.nickname
    var nicknameChecked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selfNickname) {
        if (selfNickname == null || nicknameChecked) return@LaunchedEffect
        nicknameChecked = true
        val requested = viewModel.requestedNickname
        if (requested != null && requested != selfNickname) {
            Toast.makeText(
                context,
                context.getString(R.string.nickname_taken, requested, selfNickname),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val serverAddressLabel by viewModel.serverAddressLabel.collectAsStateWithLifecycle()

    // Backdrop for the liquid-glass surfaces on this screen: only when the wallpaper is actually
    // painted behind them, exactly like ConnectionScreen does.
    val glassWallpaper = rememberGlassBackdrop(animeBackground)

    // Per-user panel (volume / mute / info) — opened by long-pressing a row
    var userPanelUserId by remember { mutableStateOf<Int?>(null) }

    // The voice controls, the same bar here and in the stream viewer
    val voiceState = VoiceBarState(
        isMicMuted = isMicMuted,
        isOutputMuted = isOutputMuted,
        isPushToTalk = isPttMode,
        onToggleMic = viewModel::toggleMicMute,
        onPushToTalk = viewModel::setPushToTalk,
        onToggleOutput = viewModel::toggleOutputMute,
        onToggleMode = viewModel::toggleVoiceMode,
        whisperReady = whisperHasTargets,
        // The empty check happens in the ViewModel, at press time
        onWhisperHold = {
            if (!viewModel.beginWhisperHold()) {
                Toast.makeText(context, context.getString(R.string.whisper_need_targets), Toast.LENGTH_SHORT).show()
            }
        },
        onWhisperRelease = { viewModel.endWhisperHold() },
        awayStatus = awayStatus,
        onOpenStatus = { awaySheetOpen = true },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimeBackground(enabled = animeBackground)

        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = Spacing.Gap, end = Spacing.Gap, top = Spacing.Gap),
            ) {
                ServerBar(
                    serverName = serverInfo?.name ?: stringResource(R.string.server),
                    serverIcon = serverInfo?.iconId?.takeIf { it != 0L }?.let { channelIcons[it] },
                    infoOpen = showServerInfo,
                    onToggleInfo = { showServerInfo = !showServerInfo },
                    whisperActive = whisperHasTargets,
                    onHome = onBackToHome,
                    onWhisperList = { whisperSheetOpen = true },
                    onMenu = { serverMenuOpen = true },
                    wallpaper = glassWallpaper,
                ) {
                    ServerInfoContent(
                        info = serverInfo,
                        address = serverAddressLabel,
                        clientsOnline = users.size,
                        channelCount = channels.size,
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .padding(start = Spacing.Inner, end = Spacing.Inner, bottom = Spacing.Inner),
                    )
                }
                Spacer(Modifier.height(Spacing.Gap))
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
            VoiceBar(
                state = voiceState,
                wallpaper = glassWallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(Spacing.Gap),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // The bars already clear the status and navigation bars: the
                // panels below must not do it a second time, and the keyboard
                // only has to push them up by what it covers beyond the bottom bar
                .consumeWindowInsets(padding)
                // Shrinks above the keyboard, so the chat input stays in view
                // without the window sliding the top bar off the screen
                .imePadding()
                .padding(horizontal = Spacing.Gap),
        ) {
            // Two islands: the channels and the chat. Each folds down to its header; their heights
            // are worked out here and animated, so folding one hands its room to the other. With
            // both open, the handle between them moves the border.
            val imeVisible = WindowInsets.isImeVisible
            val density = LocalDensity.current
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // What the two islands share: everything but the gap between them
            val shared = (maxHeight - Spacing.Gap).coerceAtLeast(IslandHeaderHeight * 2)
            // Neither island can be dragged smaller than its header and a few rows
            val minFraction = ((IslandHeaderHeight + 72.dp) / shared).coerceAtMost(0.5f)
            val fraction = (if (imeVisible) maxOf(chatFraction, 0.75f) else chatFraction)
                .coerceIn(minFraction, 1f - minFraction)
            val chatShare = shared * fraction
            val channelsTarget = when {
                channelsOpen && chatOpen -> shared - chatShare
                channelsOpen -> shared - IslandHeaderHeight
                else -> IslandHeaderHeight
            }
            val chatTarget = when {
                chatOpen && channelsOpen -> chatShare
                chatOpen -> shared - IslandHeaderHeight
                else -> IslandHeaderHeight
            }
            // Folding animates; dragging follows the finger
            val heightSpec = if (draggingBorder) snap() else spring<Dp>(stiffness = Spring.StiffnessMediumLow)
            val channelsHeight by animateDpAsState(channelsTarget, heightSpec, label = "channelsHeight")
            val chatHeight by animateDpAsState(chatTarget, heightSpec, label = "chatHeight")
            Column(modifier = Modifier.fillMaxSize()) {
            val watching = streamSessions.isNotEmpty()
            FoldableIsland(
                title = stringResource(R.string.server_channels),
                icon = AppIcons.Channel,
                expanded = channelsOpen,
                onToggle = { channelsOpen = !channelsOpen },
                // While a stream is watched, it shares this island with the channel list
                actions = if (watching) {
                    {
                        ChoicePill(
                            label = stringResource(R.string.channels_list),
                            selected = !showStream,
                            onClick = { showStream = false },
                        )
                        ChoicePill(
                            label = stringResource(R.string.stream_tab),
                            selected = showStream,
                            onClick = { showStream = true },
                        )
                    }
                } else {
                    null
                },
                wallpaper = glassWallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(channelsHeight),
                summary = {
                    Text(
                        text = stringResource(R.string.people_online, users.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                    )
                },
            ) {
            if (watching && showStream) {
                val watched = streamSessions.map { it.stream.id }.toSet()
                StreamTiles(
                    sessions = streamSessions,
                    nameOf = { id -> users.find { it.id == id }?.nickname ?: "" },
                    addable = if (streamSessions.size < ServerViewModel.MAX_WATCHED_STREAMS) {
                        streams.values.filter { it.id !in watched }
                    } else {
                        emptyList()
                    },
                    onAdd = { viewModel.watchStream(it.id) },
                    onVolumeChange = viewModel::setStreamVolume,
                    onRetryWithTeamSpeakStun = viewModel::retryStreamWithTeamSpeakStun,
                    onCloseStream = { viewModel.stopWatching(it) },
                    onFullscreen = viewModel::toggleStreamLandscape,
                    onCloseAll = { viewModel.stopWatching() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = Spacing.Gap, end = Spacing.Gap, bottom = Spacing.Gap),
                )
            } else
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
                onUserClick = { user -> userPanelUserId = user.id },
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
                streamingUserIds = streamsByUser.keys,
                // One stream opens at once; with several, the member's panel lists them
                onWatchStream = { user ->
                    val own = streamsByUser[user.id].orEmpty()
                    if (own.size == 1) viewModel.watchStream(own[0].id) else userPanelUserId = user.id
                },
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
                    .padding(start = Spacing.Inner, end = Spacing.Inner, bottom = Spacing.Inner),
            )
            }
            Spacer(Modifier.height(Spacing.Gap))
            ChatPanel(
                recipientName = pmTarget?.nickname,
                expanded = chatOpen,
                onToggleExpanded = { chatOpen = !chatOpen },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chatHeight),
                chatTab = chatTab,
                onTabChange = { chatTab = it },
                wallpaper = glassWallpaper,
                channelMessages = channelMessages,
                privateMessages = pmTargetUid?.let { uid ->
                    privateMessages[uid] ?: emptyList()
                } ?: privateMessages.values.flatten().sortedBy { it.timestamp },
                latestMessage = (channelMessages + privateMessages.values.flatten())
                    .maxByOrNull { it.timestamp },
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
            if (channelsOpen && chatOpen) {
                BorderHandle(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = channelsHeight + Spacing.Gap / 2 - BorderHandleHeight / 2),
                    onDragStarted = { draggingBorder = true },
                    onDrag = { deltaPx ->
                        val delta = with(density) { deltaPx.toDp() }
                        chatFraction = (fraction - delta / shared).coerceIn(minFraction, 1f - minFraction)
                    },
                    onDragStopped = { draggingBorder = false },
                )
            }
            }

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
                        channelName = users.firstOrNull { it.id == viewModel.myClientId }
                            ?.let { self -> channels.find { it.id == self.channelId }?.name },
                        wallpaper = glassWallpaper,
                        files = fileList,
                        isLoading = fileManagerLoading,
                        users = users,
                        selfId = viewModel.myClientId,
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

        }

        if (serverMenuOpen) {
            ServerMenu(
                wallpaper = glassWallpaper,
                onFiles = {
                    serverMenuOpen = false
                    viewModel.toggleFileManager()
                },
                onDisconnect = {
                    serverMenuOpen = false
                    viewModel.disconnect()
                },
                onDismiss = { serverMenuOpen = false },
            )
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
        onChangeStatus = {
            userPanelUserId = null
            awaySheetOpen = true
        },
        wallpaper = glassWallpaper,
        streams = panelUser?.let { streamsByUser[it.id] }.orEmpty(),
        onWatchStream = { stream ->
            viewModel.watchStream(stream.id)
            userPanelUserId = null
        },
    )

    // Streams are watched in the channels island; a new one brings it forward, and the island
    // goes back to the channel list when the last one closes
    val watchingCount = streamSessions.size
    var previousWatchingCount by remember { mutableIntStateOf(watchingCount) }
    LaunchedEffect(watchingCount) {
        if (watchingCount > previousWatchingCount) {
            showStream = true
            channelsOpen = true
        }
        if (watchingCount == 0) showStream = false
        previousWatchingCount = watchingCount
    }

    // Full screen, in landscape, when the island's full-screen button asks for it. The choice
    // lives in the view model: rotating recreates the activity.
    val fullscreen = streamSessions.isNotEmpty() && streamLandscape
    if (fullscreen) {
        val watched = streamSessions.map { it.stream.id }.toSet()
        StreamViewer(
            sessions = streamSessions,
            nameOf = { id -> users.find { it.id == id }?.nickname ?: "" },
            addable = if (streamSessions.size < ServerViewModel.MAX_WATCHED_STREAMS) {
                streams.values.filter { it.id !in watched }
            } else {
                emptyList()
            },
            onVolumeChange = viewModel::setStreamVolume,
            onAdd = { viewModel.watchStream(it.id) },
            onRetryWithTeamSpeakStun = viewModel::retryStreamWithTeamSpeakStun,
            onCloseStream = { viewModel.stopWatching(it) },
            voice = VoiceBarState(
                isMicMuted = voiceState.isMicMuted,
                isOutputMuted = voiceState.isOutputMuted,
                isPushToTalk = voiceState.isPushToTalk,
                onToggleMic = voiceState.onToggleMic,
                onPushToTalk = voiceState.onPushToTalk,
                onToggleOutput = voiceState.onToggleOutput,
                onToggleMode = voiceState.onToggleMode,
                whisperReady = voiceState.whisperReady,
                onWhisperHold = voiceState.onWhisperHold,
                onWhisperRelease = voiceState.onWhisperRelease,
                // The status sheet opens in the main window, behind the viewer
                awayStatus = voiceState.awayStatus,
            ),
            onRotate = viewModel::toggleStreamLandscape,
            // Back to the island, where the streams keep playing
            onClose = viewModel::toggleStreamLandscape,
        )
    }
    LaunchedEffect(fullscreen) {
        context.findActivity()?.requestedOrientation =
            if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // 状态 sheet (online / away / do-not-disturb), opened from the bottom bar's
    // dot or from the own row's panel.
    if (awaySheetOpen) {
        AwayStatusSheet(
            status = awayStatus,
            message = awayMessage,
            onSelect = { viewModel.setAwayStatus(it) },
            onMessageChange = { viewModel.setAwayMessage(it) },
            onDismiss = { awaySheetOpen = false },
            wallpaper = glassWallpaper,
        )
    }

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
    /** Whom the private tab writes to, if anyone is chosen */
    recipientName: String?,
    /** Unfolded; folded, only the header with the latest message shows */
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    chatTab: Int,
    onTabChange: (Int) -> Unit,
    wallpaper: ImageBitmap? = null,
    channelMessages: List<ChatMessage>,
    privateMessages: List<ChatMessage>,
    /** Shown on the folded header */
    latestMessage: ChatMessage?,
    messageText: String,
    onMessageChange: (String) -> Unit,
    pmTarget: User?,
    pmConversationUsers: List<Pair<String, String>>,
    onSelectPmUser: (String) -> Unit,
    onClearPmTarget: () -> Unit,
    onSend: () -> Unit,
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
    val pickUpload = rememberUploadPicker(onUploadFile)
    val unread = unreadChannel + unreadPrivateTotal
    // An island like the channel list above it, folding the same way. Folded, its header carries
    // the latest message and the unread count.
    FoldableIsland(
        title = stringResource(R.string.chat),
        icon = AppIcons.Sms,
        expanded = expanded,
        onToggle = onToggleExpanded,
        modifier = modifier,
        wallpaper = wallpaper,
        summary = {
            Text(
                text = latestMessage?.let { "${it.sender}: ${it.text}" } ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (unread > 0) {
                Spacer(Modifier.width(8.dp))
                Badge { Text("$unread") }
            }
        },
        // Unfolded, the header holds the two conversations instead of a tab row of its own
        // The header spaces them by the island gap, like the channels island's pills
        actions = {
            ChoicePill(
                label = stringResource(R.string.tab_channel),
                selected = chatTab == 0,
                unread = unreadChannel,
                onClick = { onTabChange(0) },
            )
            ChoicePill(
                label = stringResource(R.string.tab_private),
                selected = chatTab == 1,
                unread = unreadPrivateTotal,
                onClick = { onTabChange(1) },
            )
        },
    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = Spacing.Gap, end = Spacing.Gap, bottom = Spacing.Gap),
            ) {
                // PM conversation selector
                if (chatTab == 1 && pmConversationUsers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = Spacing.Gap),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Gap),
                    ) {
                        ChoicePill(
                            label = stringResource(R.string.filter_all),
                            selected = pmTarget == null,
                            onClick = onClearPmTarget,
                        )
                        // One pill per conversation peer (keyed by uid)
                        pmConversationUsers.forEach { (peerKey, nickname) ->
                            ChoicePill(
                                label = nickname,
                                selected = pmTarget?.uid == peerKey,
                                onClick = { onSelectPmUser(peerKey) },
                                unread = unreadPrivatePerUser[peerKey] ?: 0,
                            )
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
                // vertically; a swipe of 48dp switches, anything shorter does nothing.
                var swipeDrag by remember { mutableFloatStateOf(0f) }
                val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
                ChatView(
                    messages = messages,
                    showLinkThumbnails = showLinkThumbnails,
                    autoLoadImages = autoLoadImages,
                    onDownload = onDownload,
                    emptyText = when {
                        chatTab == 0 -> stringResource(R.string.chat_empty_channel)
                        recipientName != null -> stringResource(R.string.chat_empty_private_with, recipientName)
                        else -> stringResource(R.string.chat_empty_private)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta -> swipeDrag += delta },
                            onDragStopped = {
                                val travelled = swipeDrag
                                swipeDrag = 0f
                                if (travelled < -swipeThreshold && chatTab == 0) onTabChange(1)
                                else if (travelled > swipeThreshold && chatTab == 1) onTabChange(0)
                            },
                        ),
                )

                // While whispering, a banner above the input says who the text goes to
                ChatInput(
                    text = messageText,
                    onTextChange = onMessageChange,
                    placeholder = when {
                        isWhisperActive && whisperTargetName != null ->
                            stringResource(R.string.whisper_placeholder, whisperTargetName)
                        chatTab == 0 -> stringResource(R.string.message_channel_placeholder)
                        pmTarget == null -> stringResource(R.string.message_private_no_recipient)
                        else -> stringResource(R.string.message_private_placeholder, pmTarget.nickname)
                    },
                    onSend = onSend,
                    whisperTargetName = whisperTargetName.takeIf { isWhisperActive },
                    enabled = chatTab == 0 || pmTarget != null || isWhisperActive,
                    onAttach = pickUpload.takeIf { canUploadFiles },
                    // A file goes to the channel or to the chosen person, never to whisper targets
                    attachEnabled = chatTab == 0 || pmTarget != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
    }
}



/**
 * The top bar's menu, hung under its ⋯ button. Drawn in place like the bookmark menu on the home
 * screen: a DropdownMenu is a window of its own, with no wallpaper behind it to frost.
 */
@Composable
private fun ServerMenu(
    wallpaper: ImageBitmap?,
    onFiles: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val menuAppear = rememberAppearance()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Swallows the dismissing tap and keeps what is behind it out of reach.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        GlassCard(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = Spacing.Gap + ServerBarHeight, end = Spacing.Gap)
                .width(200.dp)
                .graphicsLayer {
                    alpha = menuAppear.value
                    scaleX = 0.9f + 0.1f * menuAppear.value
                    scaleY = 0.9f + 0.1f * menuAppear.value
                    // Grows out of the corner its button is in
                    transformOrigin = TransformOrigin(1f, 0f)
                },
            wallpaper = wallpaper,
            shape = Shapes.Medium,
            elevation = 6.dp,
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            Column {
                GlassMenuItem(AppIcons.File, stringResource(R.string.file_manager), onClick = onFiles)
                GlassMenuItem(
                    icon = AppIcons.Exit,
                    label = stringResource(R.string.disconnect),
                    destructive = true,
                    onClick = onDisconnect,
                )
            }
        }
    }
}

/** Height of the server bar's row. */
private val ServerBarHeight = 52.dp

/**
 * The server screen's top bar, as an island: home, the server's icon and name, the whisper list and
 * the menu. Tapping the name unfolds the server's details inside the same island, and tapping it
 * again folds them away.
 */
@Composable
private fun ServerBar(
    serverName: String,
    serverIcon: ImageBitmap?,
    infoOpen: Boolean,
    onToggleInfo: () -> Unit,
    whisperActive: Boolean,
    onHome: () -> Unit,
    onWhisperList: () -> Unit,
    onMenu: () -> Unit,
    wallpaper: ImageBitmap?,
    info: @Composable () -> Unit,
) {
    val chevronTurn by animateFloatAsState(if (infoOpen) 180f else 0f, label = "serverInfoChevron")
    Island(modifier = Modifier.fillMaxWidth(), wallpaper = wallpaper, padded = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ServerBarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leaving the server screen is not disconnecting: the session keeps running in
            // the service, so it can be picked up again from the home screen.
            IconButton(onClick = onHome) {
                Icon(AppIcons.Home, contentDescription = stringResource(R.string.back_to_home), modifier = Modifier.size(IconSize))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(Shapes.Small)
                    .clickable(onClick = onToggleInfo)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerIcon(name = serverName, icon = serverIcon, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = serverName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.server_info),
                    modifier = Modifier
                        .size(IconSize)
                        .rotate(chevronTurn),
                )
            }
            // 密聊: amber while a target set is live, and the way into the whisper list editor
            IconButton(onClick = onWhisperList) {
                Icon(
                    AppIcons.Whisper,
                    contentDescription = stringResource(R.string.whisper_list_title),
                    tint = if (whisperActive) WhisperAmber else LocalContentColor.current,
                    modifier = Modifier.size(IconSize),
                )
            }
            // Files and disconnect are occasional: they share a menu instead of unlabelled icons,
            // and disconnecting takes a second tap like it does in the floating window
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options), modifier = Modifier.size(IconSize))
            }
        }
        AnimatedVisibility(
            visible = infoOpen,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            info()
        }
    }
}

/** How tall the strip that takes a drag on the border between two islands is. */
private val BorderHandleHeight = 24.dp

/**
 * The border between the channels and the chat: drag it to share the room differently. It reaches
 * a little into both islands so a finger finds it; the visible part is a short bar in the gap.
 */
@Composable
private fun BorderHandle(
    modifier: Modifier,
    onDragStarted: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BorderHandleHeight)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState(onDrag),
                onDragStarted = { onDragStarted() },
                onDragStopped = { onDragStopped() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(LocalContentColor.current.copy(alpha = 0.35f)),
        )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.Gap),
        shape = Shapes.Medium,
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
                modifier = Modifier.size(IconSize),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

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

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
