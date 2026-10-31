package dev.tsdroid.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tsdroid.bridge.AudioBridge
import dev.tsdroid.data.IslandLayout
import dev.tsdroid.data.PrivateConversations
import dev.tsdroid.han.R
import dev.tsdroid.service.WhisperManager
import dev.tsdroid.service.WhisperRouting
import dev.tsdroid.service.WhisperTargets
import dev.tsdroid.ui.component.AwayStatusSheet
import dev.tsdroid.ui.component.ChannelPasswordEditor
import dev.tsdroid.ui.component.ChannelTree
import dev.tsdroid.ui.component.ChatInput
import dev.tsdroid.ui.component.ChatView
import dev.tsdroid.ui.component.ChoicePill
import dev.tsdroid.ui.component.FileManager
import dev.tsdroid.ui.component.FoldableIsland
import dev.tsdroid.ui.component.InsetSection
import dev.tsdroid.ui.component.IslandHeaderHeight
import dev.tsdroid.ui.component.IslandIconSize
import dev.tsdroid.ui.component.IslandTab
import dev.tsdroid.ui.component.IslandTabs
import dev.tsdroid.ui.component.NicknameEditor
import dev.tsdroid.ui.component.ServerInfoContent
import dev.tsdroid.ui.component.ShareTarget
import dev.tsdroid.ui.component.StreamTiles
import dev.tsdroid.ui.component.StreamViewer
import dev.tsdroid.ui.component.UserAvatar
import dev.tsdroid.ui.component.UserPanelSheet
import dev.tsdroid.ui.component.VoiceBar
import dev.tsdroid.ui.component.VoiceBarState
import dev.tsdroid.ui.component.WhisperListSheet
import dev.tsdroid.ui.component.parseSpacer
import dev.tsdroid.ui.component.rememberGlassBackdrop
import dev.tsdroid.ui.component.rememberUploadPicker
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.InsetFill
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.Spacing
import dev.tsdroid.viewmodel.ChatMessage
import dev.tsdroid.viewmodel.DownloadState
import dev.tsdroid.viewmodel.FileAttachment
import dev.tsdroid.viewmodel.ServerViewModel
import dev.tslib.Channel
import dev.tslib.User
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServerScreen(
    onBackToHome: () -> Unit,
    /** What startup read, so this screen opens on the wallpaper instead of an empty frame. */
    initialAnimeBackground: Boolean,
    /** The server's details unfolded; the app's header, which shows the server, toggles it */
    infoOpen: MutableState<Boolean>,
    /**
     * Whether this is the page on screen. The pager keeps it composed behind the other tabs, so
     * what this screen does on Back must check it; read in a lambda, so a page change only
     * recomposes the readers.
     */
    isShown: () -> Boolean = { true },
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
    val voiceRecordingSince by viewModel.voiceRecordingSince.collectAsStateWithLifecycle()
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
    var channelsView by rememberSaveable { mutableStateOf(ChannelsView.LIST) }

    var showServerInfo by infoOpen

    // The screen never ends up with only headers: folding the last open island unfolds the
    // channels instead
    fun keepAnIslandOpen() {
        if (!channelsOpen && !chatOpen) channelsOpen = true
    }

    // The islands come back the way they were left: restored once when the screen opens, without
    // animating into place, then saved whenever they change
    // The saved layout is read once per visit of the server: coming back to its tab, the tab's
    // own saved state already holds it, and reading it again showed the sections unfolding
    var layoutLoaded by rememberSaveable { mutableStateOf(false) }
    var layoutRestored by remember { mutableStateOf(layoutLoaded) }
    LaunchedEffect(Unit) {
        if (!layoutLoaded) {
            val saved = viewModel.loadIslandLayout()
            channelsOpen = saved.channelsOpen
            chatOpen = saved.chatOpen
            showServerInfo = saved.infoOpen
            chatFraction = saved.chatFraction
            keepAnIslandOpen()
            layoutLoaded = true
            withFrameNanos {}
        }
        layoutRestored = true
        snapshotFlow { IslandLayout(channelsOpen, chatOpen, showServerInfo, chatFraction) }
            .collectLatest { layout ->
                // A drag moves the border on every frame; save once it has settled
                delay(400)
                viewModel.saveIslandLayout(layout)
            }
    }
    var chatTab by rememberSaveable { mutableIntStateOf(0) }
    // Shared by the chat panel and the stream viewer's chat, and kept across
    // the stream viewer's rotation, which recreates the activity
    var messageText by rememberSaveable { mutableStateOf("") }
    // Conversations are identified by the peer's permanent uid, not by the session
    // client id: a reconnect used to reassign the client id and split one person
    // into several conversation chips.
    var pmTargetUid by rememberSaveable { mutableStateOf<String?>(null) }


    // 密聊 target list lives in the manager singleton; reading its Compose state
    // here is what repaints the indicators when a target is added or dropped.
    val whisperTargets = WhisperManager.targets
    val whisperHasTargets = whisperTargets.isNotEmpty
    var whisperSheetOpen by rememberSaveable { mutableStateOf(false) }
    // Channel waiting for its password (TS3 refuses the move without it).
    var channelPasswordTarget by remember { mutableStateOf<dev.tslib.Channel?>(null) }
    // Temporary-nickname dialog (own row's panel → 修改临时昵称).
    var nicknameDialogOpen by remember { mutableStateOf(false) }
    // 状态 (online / away / do-not-disturb), the desktop client's status menu.
    val awayStatus by viewModel.awayStatus.collectAsStateWithLifecycle()
    val awayMessage by viewModel.awayMessage.collectAsStateWithLifecycle()
    var awaySheetOpen by rememberSaveable { mutableStateOf(false) }


    // Build PM conversation list (uid → display name) from the message map + users list
    // Derived: the users list changes whenever someone talks, the names hardly ever
    val context = LocalContext.current
    val pmConversationUsers: List<Pair<String, String>> by remember {
        derivedStateOf { privateMessages.keys.map { peerKey ->
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
        } }
    }


    // Worked out only when the messages change, not on every recomposition (one per talk event)
    val allPrivateMessages = remember(privateMessages) {
        privateMessages.values.flatten().sortedBy { it.timestamp }
    }
    val latestMessage = remember(channelMessages, allPrivateMessages) {
        listOfNotNull(channelMessages.lastOrNull(), allPrivateMessages.lastOrNull()).maxByOrNull { it.timestamp }
    }

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

    // This screen's own body reads none of the chat's or the panels' state: the effects below
    // follow it through snapshotFlow, and the sections that show it read it themselves, each in
    // its own scope (see OwnScope). A tab switch, a fold, an opened panel or someone talking then
    // recomposes those, not the whole screen.

    // The chat's Files tab: back goes up a folder, and from the top back to the channel's chat
    @Composable
    fun FilesBack() = OwnScope {
        BackHandler(enabled = chatTab == FILES_TAB && isShown()) {
            if (currentFilePath != "/") viewModel.navigateUp() else chatTab = 0
        }
    }
    FilesBack()
    // The file list is loaded while its tab is shown, and dropped when it is left
    LaunchedEffect(Unit) {
        snapshotFlow { chatTab == FILES_TAB }.collect { showFiles ->
            if (showFiles && !viewModel.fileManagerOpen.value) viewModel.toggleFileManager()
            if (!showFiles && viewModel.fileManagerOpen.value) viewModel.closeFileManager()
        }
    }
    // Hidden inputs must not keep the keyboard up: a fold hides them all, and going to or from
    // the Files tab hides the one in use (the chat's input, or a folder or rename field). Both
    // stay composed while hidden, so nothing else takes their focus away.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        var wasFiles: Boolean? = null
        snapshotFlow { chatOpen to (chatTab == FILES_TAB) }.collect { (open, files) ->
            if (!open || (wasFiles != null && files != wasFiles)) focusManager.clearFocus()
            wasFiles = files
        }
    }

    // Sync chat state to ViewModel for unread tracking
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(chatOpen, chatTab, pmTargetUid) }
            .collect { (open, tab, peer) -> viewModel.setChatState(open, tab, peer) }
    }

    DisposableEffect(Unit) {
        viewModel.bindToService()
        onDispose {}
    }
    // Going home never drops the connection; only the disconnect button does that.
    // A session that ends takes this tab away with it: the home island only shows it while connected

    // The floating window is no longer driven from here: it belongs to the service, which shows it
    // when the app goes to the background. See TsConnectionService.setAppInForeground.

    // The server renames a client whose nickname is taken (ushummu → ushummu1). Say so once, when
    // our own name first shows up, or the user cannot tell which row is theirs.
    LaunchedEffect(Unit) {
        // Our client id is not snapshot state: reading the connection state too re-runs this when
        // a (re)connect hands us a new id, not only when the user list happens to change
        snapshotFlow {
            connectionState
            users.firstOrNull { it.id == viewModel.myClientId }?.nickname
        }.collect { selfNickname ->
            if (selfNickname == null) return@collect
            val requested = viewModel.requestedNickname
            if (requested != null && requested != selfNickname && viewModel.takeNicknameNotice()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.nickname_taken, requested, selfNickname),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    val serverAddressLabel by viewModel.serverAddressLabel.collectAsStateWithLifecycle()

    // Backdrop for the liquid-glass surfaces on this screen: only when the wallpaper is actually
    // painted behind them, exactly like ConnectionScreen does.
    val glassWallpaper = rememberGlassBackdrop(animeBackground)

    // Per-user panel (volume / mute / info) — opened by long-pressing a row
    var userPanelUserId by rememberSaveable { mutableStateOf<Int?>(null) }

    // What unfolds in place in the tab: above the voice bar (the status and the whisper list),
    // under a member (their panel) and under a protected channel (its password)
    val whisperListPanel: @Composable () -> Unit = {
        LaunchedEffect(Unit) { viewModel.refreshGroupLists() }
        WhisperListSheet(
            targets = whisperTargets,
            groups = serverGroups,
            // Spacer channels are decoration, not destinations: nobody sits in them.
            channels = channels.filter { parseSpacer(it.name) == null },
            selectedMembers = whisperTargets.clients.map { key ->
                key to (users.firstOrNull { PrivateConversations.keyFor(it.uid, it.id) == key }?.nickname
                    ?: context.getString(R.string.whisper_target_offline))
            },
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
            )
    }
    val statusPanel: @Composable () -> Unit = {
        AwayStatusSheet(
            status = awayStatus,
            message = awayMessage,
            onSelect = { viewModel.setAwayStatus(it) },
            onMessageChange = { viewModel.setAwayMessage(it) },
            onDismiss = { awaySheetOpen = false },
        )
    }
    // The panel names the member's groups, and the answers to both group list
    // requests may not have arrived yet when it opens.
    LaunchedEffect(Unit) {
        snapshotFlow { userPanelUserId }.collect { if (it != null) viewModel.refreshGroupLists() }
    }
    val memberPanel: @Composable (User) -> Unit = { panelUser ->
        UserPanelSheet(
            user = panelUser,
            avatar = panelUser?.uid?.let { userAvatars[it] },
            // The member this panel shows, not userPanelUserId: a panel folding away is still
            // drawn while that already names the next member, and must not act on them
            volumeDb = userVolumes[panelUser.id] ?: 0f,
            isMuted = panelUser.id in mutedUserIds,
            onVolumeChange = { db -> viewModel.setUserVolumeDb(panelUser.id, db, commit = false) },
            onVolumeCommit = { db -> viewModel.setUserVolumeDb(panelUser.id, db, commit = true) },
            onToggleMute = { viewModel.toggleMuteUser(panelUser.id) },
            onPrivateMessage = {
                // Open the conversation under the peer's uid, matching every other
                // entry point into private chat.
                users.find { it.id == panelUser.id }?.let { pmTargetUid = it.uid }
                chatTab = 1
                chatOpen = true
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
            onChangeNickname = { nicknameDialogOpen = !nicknameDialogOpen },
            onChangeStatus = {
                whisperSheetOpen = false
                awaySheetOpen = true
            },
            streams = panelUser?.let { streamsByUser[it.id] }.orEmpty(),
            onWatchStream = { stream ->
                viewModel.watchStream(stream.id)
                userPanelUserId = null
            },
        )

        // Temporary nickname: applies to this connection only, and the identity file
        // is never touched (see ServerViewModel.changeTemporaryNickname).
        if (nicknameDialogOpen && panelUser.id == viewModel.myClientId) {
            NicknameEditor(
                currentNickname = panelUser.nickname,
                onConfirm = { nickname -> viewModel.changeTemporaryNickname(nickname) },
                onDismiss = { nicknameDialogOpen = false },
            )
        }
    }
    // A protected channel needs its password before the server accepts the move
    val channelPasswordPanel: @Composable (Channel) -> Unit = { channel ->
        ChannelPasswordEditor(
            channelName = channel.name,
            onConfirm = { password ->
                viewModel.moveToChannel(channel.id, password)
                channelPasswordTarget = null
            },
            onDismiss = { channelPasswordTarget = null },
        )
    }

    // The voice controls, the same bar here and in the stream viewer
    // Built where the bar is drawn: a mute or a mode change then recomposes the bar alone
    fun voiceState() = VoiceBarState(
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
        whisperHolding = WhisperManager.isHolding,
        onWhisperList = {
            awaySheetOpen = false
            whisperSheetOpen = !whisperSheetOpen
        },
        awayStatus = awayStatus,
        onOpenStatus = {
            whisperSheetOpen = false
            awaySheetOpen = !awaySheetOpen
        },
    )

    // The chat reads its tab, its conversation and its text here, so they recompose it alone
    @Composable
    fun ChatSection(modifier: Modifier) = OwnScope {
        val pmTarget by remember { derivedStateOf { pmTargetUid?.let { uid -> users.find { it.uid == uid } } } }
        ChatPanel(
            recipientName = pmTarget?.nickname
                ?: pmConversationUsers.firstOrNull { it.first == pmTargetUid }?.second,
            recipientAvatar = pmTargetUid?.let { userAvatars[it] },
            expanded = { chatOpen },
            onToggleExpanded = {
                chatOpen = !chatOpen
                keepAnIslandOpen()
            },
            modifier = modifier,
            chatTab = chatTab,
            onTabChange = { chatTab = it },
            files = {
        FileManager(
            modifier = Modifier.padding(start = Spacing.Gap, end = Spacing.Gap, bottom = Spacing.Gap),
            currentPath = currentFilePath,
            channelName = users.firstOrNull { it.id == viewModel.myClientId }
                ?.let { self -> channels.find { it.id == self.channelId }?.name },
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
            onPreviewImage = { fileName ->
                viewModel.previewImageFile(fileName)
            },
        )
            },
            wallpaper = glassWallpaper,
            channelMessages = channelMessages,
            privateMessages = pmTargetUid?.let { uid ->
                privateMessages[uid] ?: emptyList()
            } ?: allPrivateMessages,
            latestMessage = latestMessage,
            messageText = messageText,
            onMessageChange = { messageText = it },
            pmTarget = pmTarget,
            pmConversationUsers = pmConversationUsers,
            onSelectPmUser = { uid -> pmTargetUid = uid },
            onClearPmTarget = { pmTargetUid = null },
            onSend = {
                when {
                    // While whispering the microphone is on the target
                    // set, so the text follows the voice: it goes to
                    // those same people as private messages.
                    whisperHasTargets -> viewModel.sendWhisperMessage(messageText)
                    chatTab == 0 -> viewModel.sendChannelMessage(messageText)
                    else -> pmTargetUid?.let { viewModel.sendPrivateMessage(it, messageText) }
                }
                messageText = ""
            },
            unreadChannel = unreadChannel,
            unreadPrivateTotal = unreadPrivate.values.sum(),
            unreadPrivatePerUser = unreadPrivate,
            showLinkThumbnails = showLinkThumbnails,
            autoLoadImages = autoLoadImages,
            canUploadFiles = (channelPermissions and dev.tslib.Channel.PERM_FILE_UPLOAD) != 0L,
            onUploadFile = { fileName, data ->
                viewModel.uploadAndSendFile(fileName, data, chatTab == 1, pmTargetUid)
            },
            onDownload = { attachment -> viewModel.downloadAttachment(attachment) },
            isWhisperActive = whisperHasTargets,
            whisperTargetName = whisperTargetLabel(whisperTargets, users, channels, serverGroups),
            voiceRecordingSince = voiceRecordingSince,
            onStartVoiceMessage = { viewModel.startVoiceMessage(chatTab == 1, pmTargetUid) },
            onFinishVoiceMessage = { send -> viewModel.finishVoiceMessage(send) },
        )
    }

    @Composable
    fun VoiceSection() = OwnScope {
        // Back folds an open panel away before it leaves the tab
        BackHandler(enabled = (awaySheetOpen || whisperSheetOpen) && isShown()) {
            awaySheetOpen = false
            whisperSheetOpen = false
        }
        VoiceBar(
            state = voiceState(),
            modifier = Modifier.fillMaxWidth(),
            panel = when {
                awaySheetOpen -> statusPanel
                whisperSheetOpen -> whisperListPanel
                else -> null
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The server is a tab of the app's one island: its bar, the channels and the chat, and the
        // voice bar are sections of it, the island's gap apart
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Spacing.Gap, end = Spacing.Gap, bottom = Spacing.Gap),
            verticalArrangement = Arrangement.spacedBy(Spacing.Gap),
        ) {
            // The server's details, unfolded from the app's header, which names the server
            AnimatedVisibility(
                visible = showServerInfo,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                InsetSection(modifier = Modifier.fillMaxWidth()) {
                    ServerInfoContent(
                        info = serverInfo,
                        address = serverAddressLabel,
                        clientsOnline = users.size,
                        channelCount = channels.size,
                        modifier = Modifier.heightIn(max = 320.dp),
                    )
                }
            }
            // Connection / microphone health, so a silent failure is never invisible again
            if (reconnectStatus.active) {
                StatusBanner(
                    text = stringResource(R.string.reconnect_in_progress, maxOf(reconnectStatus.attempt, 1)),
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Two islands: the channels and the chat. Each folds down to its header, and with both
            // open the handle between them moves the border. The chat's height is worked out in the
            // layout phase from the fold animations and the border's position, and the channels
            // take the rest: a drag or a fold then only measures the two islands again instead of
            // recomposing the whole screen on every frame.
            // The keyboard's height is read in the layout phase, through this, never here
            val imeInsets = WindowInsets.ime
            val density = LocalDensity.current
            // Driven from the fold states without reading them here: folding then recomposes only
            // the section that folds, and the heights follow in the layout phase
            // Starting where the sections are, without subscribing to them here
            val channelsProgress = remember {
                Animatable(if (Snapshot.withoutReadObservation { channelsOpen }) 1f else 0f)
            }
            val chatProgress = remember {
                Animatable(if (Snapshot.withoutReadObservation { chatOpen }) 1f else 0f)
            }
            LaunchedEffect(Unit) {
                launch { snapshotFlow { channelsOpen }.collect { foldTo(channelsProgress, it, layoutRestored) } }
                snapshotFlow { chatOpen }.collect { foldTo(chatProgress, it, layoutRestored) }
            }
            val headerPx = with(density) { IslandHeaderHeight.toPx() }
            val gapPx = with(density) { Spacing.Gap.toPx() }
            val minRowsPx = with(density) { 72.dp.toPx() }
            // What the two islands share: everything but the gap between them. Measured in the
            // layout phase into a plain field: the keyboard opening or closing then lays the
            // islands out again without recomposing them, as BoxWithConstraints did
            val shared = remember { FloatArray(1) }
            fun sharedPx(): Float = shared[0]
            // Neither island can be dragged smaller than its header and a few rows
            fun minFraction(): Float = ((headerPx + minRowsPx) / sharedPx()).coerceAtMost(0.5f)
            fun chatFractionNow(): Float {
                val imeVisible = imeInsets.getBottom(density) > 0
                return (if (imeVisible) maxOf(chatFraction, 0.75f) else chatFraction)
                    .coerceIn(minFraction(), 1f - minFraction())
            }
            // Read only while measuring and placing, never while composing
            fun chatOpenHeightPx(): Float {
                val sharedPx = sharedPx()
                return lerp(sharedPx - headerPx, sharedPx * chatFractionNow(), channelsProgress.value)
            }
            fun chatHeightPx(): Int = lerp(headerPx, chatOpenHeightPx(), chatProgress.value).roundToInt()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        shared[0] = (constraints.maxHeight - gapPx).coerceAtLeast(headerPx * 2)
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
            val watching = streamSessions.isNotEmpty()
            FoldableIsland(
                title = stringResource(R.string.server_channels),
                icon = AppIcons.Channel,
                expanded = { channelsOpen },
                onToggle = {
                    channelsOpen = !channelsOpen
                    keepAnIslandOpen()
                },
                // The channel list and the streams being watched are the section's tabs, shown
                // only while there is a stream to switch to
                actions = if (!watching) null else {
                    {
                    val views = listOfNotNull(
                        ChannelsView.LIST,
                        ChannelsView.STREAM.takeIf { watching },
                    )
                    val labels = mapOf(
                        ChannelsView.LIST to stringResource(R.string.channels_list),
                        ChannelsView.STREAM to stringResource(R.string.stream_tab),
                    )
                    IslandTabs(
                        tabs = views.map { IslandTab(labels.getValue(it)) },
                        selected = views.indexOf(channelsView).coerceAtLeast(0),
                        onSelect = { channelsView = views[it] },
                    )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                summary = {
                    Text(
                        text = stringResource(R.string.people_online, users.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                    )
                },
            ) {
            if (watching && channelsView == ChannelsView.STREAM) {
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
                // A member's panel unfolds under their row; a second tap folds it
                onUserClick = { user -> userPanelUserId = if (userPanelUserId == user.id) null else user.id },
                expandedUserId = { userPanelUserId },
                userPanel = memberPanel,
                expandedChannelId = { channelPasswordTarget?.id },
                channelPanel = channelPasswordPanel,
                onUserLongClick = { user -> viewModel.toggleMuteUser(user.id) },
                onUserLongPress = { user ->
                    // The own row opens the same panel to read your own server
                    // groups; the interactive sections hide themselves for self.
                    userPanelUserId = if (userPanelUserId == user.id) null else user.id
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
                    .padding(start = Spacing.Gap, end = Spacing.Gap, bottom = Spacing.Gap),
            )
            }
            Spacer(Modifier.height(Spacing.Gap))
            ChatSection(
                modifier = Modifier
                    .fillMaxWidth()
                    // While the chat folds or unfolds it keeps the open size and is cut to the
                    // animated one: its messages are then measured once, not on every frame. Cut
                    // in the island's own shape, so the bottom corners stay round while it moves.
                    .clip(Shapes.Standard)
                    .layout { measurable, constraints ->
                        val height = chatHeightPx()
                        val measured = if (chatProgress.isRunning) chatOpenHeightPx().roundToInt() else height
                        val placeable = measurable.measure(constraints.copy(minHeight = measured, maxHeight = measured))
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    },
            )
            }
            // Its own scope, so the fold states it reads recompose only the handle
            @Composable
            fun Handle() = OwnScope {
                if (!(channelsOpen && chatOpen)) return@OwnScope
                val handleHalfPx = with(density) { (BorderHandleHeight / 2).toPx() }
                BorderHandle(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            val y = sharedPx() - chatHeightPx() + gapPx / 2 - handleHalfPx
                            IntOffset(0, y.roundToInt())
                        },
                    onDrag = { deltaPx ->
                        chatFraction = (chatFractionNow() - deltaPx / sharedPx()).coerceIn(minFraction(), 1f - minFraction())
                    },
                )
            }
            Handle()
            }


        }
            VoiceSection()
        }


    // Streams are watched in the channels island; a new one brings it forward, and the island
    // goes back to the channel list when the last one closes
    val watchingCount = streamSessions.size
    var previousWatchingCount by remember { mutableIntStateOf(watchingCount) }
    LaunchedEffect(watchingCount) {
        if (watchingCount > previousWatchingCount) {
            channelsView = ChannelsView.STREAM
            channelsOpen = true
        }
        if (watchingCount == 0 && channelsView == ChannelsView.STREAM) channelsView = ChannelsView.LIST
        previousWatchingCount = watchingCount
    }

    // Full screen, in landscape, when the island's full-screen button asks for it. The choice
    // lives in the view model: rotating recreates the activity.
    val fullscreen = streamSessions.isNotEmpty() && streamLandscape
    if (fullscreen) {
        val voiceState = voiceState()
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
                whisperHolding = voiceState.whisperHolding,
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
    recipientAvatar: ImageBitmap?,
    /** Unfolded; folded, only the header with the latest message shows */
    expanded: () -> Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    chatTab: Int,
    onTabChange: (Int) -> Unit,
    /** The Files tab: TeamSpeak keeps files per channel, so they sit next to its chat */
    files: @Composable () -> Unit,
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
    /** When the voice message being recorded started; null while none is */
    voiceRecordingSince: Long? = null,
    onStartVoiceMessage: () -> Unit = {},
    onFinishVoiceMessage: (send: Boolean) -> Unit = {},
) {
    val pickUpload = rememberUploadPicker(onUploadFile)
    val unread = unreadChannel + unreadPrivateTotal
    // An island like the channel list above it, folding the same way. Folded, its header carries
    // the latest message and the unread count.
    // On the private tab the header is the conversation: whom it is with, by avatar and name
    val recipient = recipientName?.takeIf { chatTab == 1 }
    FoldableIsland(
        title = recipient ?: stringResource(R.string.chat),
        icon = AppIcons.Sms,
        image = recipient?.let {
            { UserAvatar(avatar = recipientAvatar, nickname = it, size = IslandIconSize, shape = RectangleShape) }
        },
        expanded = expanded,
        onToggle = onToggleExpanded,
        modifier = modifier,
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
            IslandTabs(
                tabs = listOf(
                    IslandTab(stringResource(R.string.tab_channel), badge = unreadChannel),
                    IslandTab(stringResource(R.string.tab_private), badge = unreadPrivateTotal),
                    IslandTab(stringResource(R.string.files_tab)),
                ),
                selected = chatTab,
                onSelect = onTabChange,
            )
        },
    ) {
            // Each tab stays built once shown, and only the current one is placed: switching back
            // then shows a tab instead of building all of its messages again in one frame
            val builtTabs = remember { BooleanArray(3) }
            builtTabs[chatTab] = true
            fun built(tab: Int) = builtTabs[tab]
            Box(Modifier.fillMaxSize()) {
            if (built(FILES_TAB)) Box(Modifier.fillMaxSize().placedIf(chatTab == FILES_TAB)) { files() }
            if (built(0) || built(1)) Column(
                modifier = Modifier
                    .fillMaxSize()
                    .placedIf(chatTab != FILES_TAB)
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

                // Swipe left/right to switch between the channel and the private tabs, like the app's
                // other tab hosts. Only horizontal drags are claimed, so the list still scrolls
                // vertically; a swipe of 48dp switches, anything shorter does nothing.
                var swipeDrag by remember { mutableFloatStateOf(0f) }
                val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // The messages' own area, set into the island like a channel's
                        .clip(Shapes.Standard)
                        .background(InsetFill)
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
                ) {
                    // The channel's messages and the private ones, each with its own scroll position
                    for (tab in 0..1) {
                        if (!built(tab)) continue
                        key(tab) { ChatView(
                            messages = if (tab == 0) channelMessages else privateMessages,
                            showLinkThumbnails = showLinkThumbnails,
                            autoLoadImages = autoLoadImages,
                            onDownload = onDownload,
                            emptyText = when {
                                tab == 0 -> stringResource(R.string.chat_empty_channel)
                                recipientName != null -> stringResource(R.string.chat_empty_private_with, recipientName)
                                else -> stringResource(R.string.chat_empty_private)
                            },
                            // Remembered: a new modifier would recompose the hidden list as well
                            modifier = remember(chatTab == tab) { Modifier.placedIf(chatTab == tab) },
                        ) }
                    }
                }

                Spacer(Modifier.height(Spacing.Gap))
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
                    // A voice message is a file: it needs the same right to upload, and goes where
                    // a file would, never to whisper targets
                    recordingSince = voiceRecordingSince,
                    onStartRecording = onStartVoiceMessage.takeIf { canUploadFiles && !isWhisperActive },
                    onFinishRecording = onFinishVoiceMessage,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            }
    }
}



/** Folds or unfolds a section: animated, or at once while its saved layout is being restored. */
private suspend fun foldTo(progress: Animatable<Float, AnimationVector1D>, open: Boolean, animate: Boolean) {
    val target = if (open) 1f else 0f
    if (animate) progress.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) else progress.snapTo(target)
}

/** The chat's tab that shows the channel's files, after Channel and Private. */
private const val FILES_TAB = 2

/** What the server's Channels section shows. */
private enum class ChannelsView { LIST, STREAM }

/** How tall the strip that takes a drag on the border between two islands is. */
private val BorderHandleHeight = 24.dp

/** Measured, but placed (and so drawn and touchable) only when [placed]: kept ready to show. */
private fun Modifier.placedIf(placed: Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
}

/**
 * Runs [content] in a recompose scope of its own. A local composable function has none: the
 * state it reads recomposes the function it is declared in.
 */
@Composable
private fun OwnScope(content: @Composable () -> Unit) = content()

/**
 * The border between the channels and the chat: drag it to share the room differently. It reaches
 * a little into both islands so a finger finds it; the visible part is a short bar in the gap.
 */
@Composable
private fun BorderHandle(
    modifier: Modifier,
    onDrag: (Float) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BorderHandleHeight)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState(onDrag),
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
            .fillMaxWidth(),
        shape = Shapes.Standard,
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
