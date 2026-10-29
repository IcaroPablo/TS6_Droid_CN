package dev.tsdroid.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tsdroid.data.ServerProbe
import dev.tsdroid.han.R
import dev.tsdroid.service.TsConnectionService
import dev.tsdroid.ui.component.AnimeBackground
import dev.tsdroid.ui.component.ChannelTree
import dev.tsdroid.ui.component.ControlHeight
import dev.tsdroid.ui.component.InsetSection
import dev.tsdroid.ui.component.IslandHeader
import dev.tsdroid.ui.component.IslandIconSize
import dev.tsdroid.ui.component.IslandTab
import dev.tsdroid.ui.component.IslandTabs
import dev.tsdroid.ui.component.RootIsland
import dev.tsdroid.ui.component.ServerIcon
import dev.tsdroid.ui.component.rememberGlassBackdrop
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.InsetPadding
import dev.tsdroid.ui.theme.OnlineGreen
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.Spacing
import dev.tsdroid.viewmodel.ConnectionViewModel
import dev.tsdroid.viewmodel.ServerViewModel
import dev.tslib.ConnectionState
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** How often a connected server's latency is read, and a disconnected one is probed. */
private const val ACTIVE_LATENCY_MS = 3_000L
private const val PROBE_INTERVAL_MS = 15_000L

/** How long a first tap on Disconnect waits for the second. */
private const val DISCONNECT_CONFIRM_MS = 3_000L

/** The page of the server's tab, after Servers and Settings. */
private const val SERVER_TAB = 2

/** How much the launcher foreground is enlarged so its picture fills an icon slot. */
private const val LAUNCHER_ART_SCALE = 432f / 320f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    /** What startup read, so the first frame already paints the wallpaper the splash prepared. */
    initialAnimeBackground: Boolean,
    viewModel: ConnectionViewModel = viewModel(),
    /** The session's own model, shared with the server's tab */
    serverViewModel: ServerViewModel = viewModel(),
) {
    val address by viewModel.address.collectAsStateWithLifecycle()
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val bookmarkIcons by viewModel.bookmarkIcons.collectAsStateWithLifecycle()
    val autoReconnect by viewModel.autoReconnect.collectAsStateWithLifecycle()
    val editingIndex by viewModel.editingIndex.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val browsedChannels by viewModel.browsedChannels.collectAsStateWithLifecycle()
    val isBrowsing by viewModel.isBrowsing.collectAsStateWithLifecycle()
    val showChannelPicker by viewModel.showChannelPicker.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteConfirmIndex by remember { mutableStateOf<Int?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    // The server's details, unfolded by tapping the header while it names the server
    val serverInfoOpen = rememberSaveable { mutableStateOf(false) }
    val serverInfo by serverViewModel.serverInfo.collectAsStateWithLifecycle()
    val serverIcons by serverViewModel.channelIcons.collectAsStateWithLifecycle()

    // The bookmark whose actions are unfolded under it
    var menuIndex by remember { mutableStateOf<Int?>(null) }

    // Both overlays live inside this screen instead of a window of their own, so they have to claim
    // the back gesture themselves: a ModalBottomSheet and a DropdownMenu used to do it for us, and
    // without it back would leave the screen with a half-filled form still pinned open.
    BackHandler(enabled = showBottomSheet) {
        showBottomSheet = false
        viewModel.cancelEdit()
    }
    BackHandler(enabled = menuIndex != null) { menuIndex = null }
    // A first tap on Remove waits a few seconds for the second
    LaunchedEffect(deleteConfirmIndex) {
        if (deleteConfirmIndex != null) {
            delay(DISCONNECT_CONFIRM_MS)
            deleteConfirmIndex = null
        }
    }

    // The app is one island with tabs: servers, settings, and the server while connected to one.
    // Each tab is a page; tapping a tab animates the pager, swiping it moves the selection.
    // An automatic reconnect keeps the server's tab, which shows its progress
    var reconnecting by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        pageCount = { if (connectionState == ConnectionState.CONNECTED || reconnecting) 3 else 2 },
    )
    // Connecting opens the server's tab; it appears once the session is up, so the request waits
    var serverTabRequested by remember { mutableStateOf(false) }
    val onConnected: () -> Unit = { serverTabRequested = true }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { selectedTab = it }
    }

    // The connection sheet can be dragged down to dismiss it, like the sheet it replaces.
    val scope = rememberCoroutineScope()


    val isConnecting = connectionState == ConnectionState.CONNECTING
    val context = LocalContext.current
    val settingsStore = remember { dev.tsdroid.data.SettingsStore(context) }
    val animeBackground by settingsStore.animeBackground
        .collectAsStateWithLifecycle(initialValue = initialAnimeBackground)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
        // Only once per process: coming back here while a session runs must stay here, not bounce
        // straight back into the server screen.
        viewModel.resumeExistingConnectionOnce(onConnected)
    }

    LaunchedEffect(autoReconnect) {
        if (autoReconnect) {
            viewModel.tryAutoReconnect(onConnected)
        }
    }

    // A session that is still running lives in the service, which is the only thing that knows when
    // it ends — the server screen is gone by then. Tracking it here keeps the pill above honest.
    LaunchedEffect(Unit) {
        // Coming back here after disconnecting: the service has already stopped itself, so the
        // "connected" state this screen still holds from before is stale. Clear it before waiting.
        if (TsConnectionService.instance?.hasActiveConnection() != true) {
            viewModel.observeServiceState(ConnectionState.DISCONNECTED)
        }
        // Follows each session's service: a new connection after a disconnect is a new one
        TsConnectionService.instances.collectLatest { service ->
            if (service == null) {
                // Stopping, it lets go of itself before its client reports the end
                reconnecting = false
                viewModel.observeServiceState(ConnectionState.DISCONNECTED)
                return@collectLatest
            }
            launch { service.reconnectStatus.collect { reconnecting = it.active } }
            service.tsClient.state.collect { viewModel.observeServiceState(it) }
        }
    }
    val sessionRunning = connectionState == ConnectionState.CONNECTED || reconnecting
    LaunchedEffect(serverTabRequested, sessionRunning) {
        if (serverTabRequested && sessionRunning) {
            pagerState.animateScrollToPage(SERVER_TAB)
            // Only once there: clearing the request restarts this effect, which would cancel the
            // scroll halfway (or before it moved at all, on a slower phone)
            serverTabRequested = false
        }
    }
    // Back leaves the server or settings tab for the server list before it leaves the app
    BackHandler(enabled = selectedTab != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }
    // Which bookmark the running session (or the attempt in progress) belongs to, so its card can
    // say so instead of offering to connect again.
    val activeAddress = if (sessionRunning) TsConnectionService.instance?.tsClient?.serverAddress else null
    val connectingAddress = if (isConnecting) address.trim() else null

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Adding or editing a server: the form unfolds in the server list, where the add button or the
    // bookmark being edited is, rather than over it
    @Composable
    fun ServerForm() {
        val isEditing = editingIndex >= 0
        val fieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            unfocusedBorderColor = LocalContentColor.current.copy(alpha = 0.3f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        )
        @Composable
        fun Field(
            value: String,
            onChange: (String) -> Unit,
            label: Int,
            placeholder: Int? = null,
            secret: Boolean = false,
            trailing: (@Composable () -> Unit)? = null,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(stringResource(label)) },
                placeholder = placeholder?.let { { Text(stringResource(it)) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConnecting,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = trailing,
                shape = Shapes.Standard,
                colors = fieldColors,
            )
        }
        InsetSection(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(if (isEditing) R.string.edit_bookmark else R.string.manual_connection),
            icon = AppIcons.Server,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Gap)) {
                Field(address, { viewModel.address.value = it }, R.string.server_address, R.string.server_address_placeholder)
                Field(nickname, { viewModel.nickname.value = it }, R.string.nickname)
                Field(password, { viewModel.password.value = it }, R.string.password_optional, secret = true)
                Field(
                    channel, { viewModel.channel.value = it }, R.string.channel_optional, R.string.default_channel,
                    trailing = {
                        if (isBrowsing) {
                            CircularProgressIndicator(modifier = Modifier.size(IconSize), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { viewModel.browseChannels() }, enabled = !isConnecting) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.browse_channels), modifier = Modifier.size(IconSize))
                            }
                        }
                    },
                )
                // The server's channels, to pick one from, unfold under the field
                AnimatedVisibility(
                    visible = showChannelPicker,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    ChannelTree(
                        channels = browsedChannels,
                        users = emptyList(),
                        onChannelClick = { viewModel.selectChannel(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Gap, Alignment.End),
                ) {
                    TextButton(
                        shape = Shapes.Standard,
                        onClick = {
                            showBottomSheet = false
                            viewModel.showChannelPicker.value = false
                            viewModel.cancelEdit()
                        },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    FilledTonalButton(shape = Shapes.Standard, onClick = {
                        viewModel.saveBookmark()
                        showBottomSheet = false
                    }) {
                        Text(stringResource(if (isEditing) R.string.save else R.string.add_bookmark))
                    }
                    Button(
                        shape = Shapes.Standard,
                        onClick = {
                            showBottomSheet = false
                            viewModel.connectFromForm(onConnected)
                        },
                        enabled = !isConnecting,
                    ) {
                        Text(stringResource(R.string.connect))
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimeBackground(enabled = animeBackground)

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            // The whole home screen is one island: the app's name and its two tabs, servers and
            // settings, in the header, and the tab's content below. Swiping between the tabs works
            // too, and the header follows.
            RootIsland(insets = padding, wallpaper = rememberGlassBackdrop(animeBackground)) {
            // The header names what the tab shows: the app, or the server on the server's tab
            val bookmarkName = bookmarks.firstOrNull { it.address == activeAddress }
                ?.let { it.serverName ?: it.name } ?: stringResource(R.string.server)
            val onServerTab = selectedTab == SERVER_TAB && sessionRunning
            val serverName = serverInfo?.name?.takeIf { it.isNotBlank() } ?: bookmarkName
            IslandHeader(
                title = if (onServerTab) serverName else stringResource(R.string.app_name),
                icon = null,
                image = {
                    if (onServerTab) {
                        ServerIcon(
                            name = serverName,
                            icon = serverInfo?.iconId?.takeIf { it != 0L }?.let { serverIcons[it] },
                            size = IslandIconSize,
                            // The header's icon slot rounds it
                            shape = RectangleShape,
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(IslandIconSize)
                                // An adaptive icon's foreground: the picture is the middle 320px of
                                // its 432px, the rest a transparent margin for the launcher's mask
                                .graphicsLayer {
                                    scaleX = LAUNCHER_ART_SCALE
                                    scaleY = LAUNCHER_ART_SCALE
                                },
                        )
                    }
                },
                onClick = if (onServerTab) {
                    { serverInfoOpen.value = !serverInfoOpen.value }
                } else null,
                trailing = {
                    IslandTabs(
                        tabs = listOfNotNull(
                            IslandTab(stringResource(R.string.tab_servers)),
                            IslandTab(stringResource(R.string.tab_settings)),
                            IslandTab(bookmarkName).takeIf { sessionRunning },
                        ),
                        selected = selectedTab,
                        onSelect = { page ->
                            selectedTab = page
                            scope.launch { pagerState.animateScrollToPage(page) }
                        },
                    )
                },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // The server tab is full of horizontal gestures (sliders, the chat's tabs, the
                // stream), so it is left by its tab rather than by a swipe
                userScrollEnabled = selectedTab != SERVER_TAB,
                // Every tab stays composed: building the server's again on each return took about
                // 300 ms. The price is that it keeps up with voice events while another tab shows.
                beyondViewportPageCount = 2,
            ) { page ->
                if (page == 0) {
                    Box(modifier = Modifier.fillMaxSize()) {
                    run {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = Spacing.Gap, end = Spacing.Gap, bottom = Spacing.Gap),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Gap),
                        ) {
                            if (connectingAddress != null && bookmarks.none { it.address == connectingAddress }) {
                                ConnectingCard(wallpaper = rememberGlassBackdrop(animeBackground))
                            }
                            bookmarks.forEachIndexed { index, bookmark ->
                                    InsetSection(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // The connected / connecting line comes and goes smoothly
                                            .animateContentSize(),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            ServerIcon(
                                                name = bookmark.serverName ?: bookmark.name,
                                                icon = if (bookmark.iconId != 0L) bookmarkIcons[bookmark.iconId] else null,
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            // Deliberately no address line: the home list is the screen users
                                            // screenshot most, and a server's address is exactly what should not
                                            // travel with that screenshot. The name (and the server's own icon
                                            // above) is enough to pick the right entry.
                                            val isActive = bookmark.address == activeAddress
                                            val isConnectingHere = bookmark.address == connectingAddress
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = bookmark.serverName ?: bookmark.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                )
                                                // How the server is doing: the connection's own round
                                                // trip while connected, a handshake probe otherwise
                                                var latencyMs by remember(bookmark.address) { mutableStateOf<Long?>(null) }
                                                var probed by remember(bookmark.address) { mutableStateOf(false) }
                                                LaunchedEffect(bookmark.address, isActive, selectedTab) {
                                                    // Only while the list is on screen
                                                    if (selectedTab != 0) return@LaunchedEffect
                                                    while (true) {
                                                        latencyMs = if (isActive) {
                                                            TsConnectionService.instance?.tsClient?.roundTripMs?.roundToLong()
                                                        } else {
                                                            ServerProbe.ping(bookmark.address)
                                                        }
                                                        probed = true
                                                        delay(if (isActive) ACTIVE_LATENCY_MS else PROBE_INTERVAL_MS)
                                                    }
                                                }
                                                val up = isActive || latencyMs != null
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                when {
                                                                    isConnectingHere || !probed -> LocalContentColor.current.copy(alpha = 0.5f)
                                                                    up -> OnlineGreen
                                                                    else -> MaterialTheme.colorScheme.error
                                                                },
                                                            ),
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    val state = when {
                                                        isConnectingHere -> stringResource(R.string.connecting)
                                                        isActive -> stringResource(R.string.bookmark_connected)
                                                        !probed -> stringResource(R.string.server_checking)
                                                        up -> stringResource(R.string.server_up)
                                                        else -> stringResource(R.string.server_down)
                                                    }
                                                    Text(
                                                        text = latencyMs?.takeIf { !isConnectingHere }
                                                            ?.let { stringResource(R.string.server_status_latency, state, it) } ?: state,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = LocalContentColor.current.copy(alpha = 0.72f),
                                                    )
                                                }
                                            }
                                            // The connected server is reached through its tab; here
                                            // its button disconnects, after a second tap
                                            var confirmDisconnect by remember { mutableStateOf(false) }
                                            LaunchedEffect(confirmDisconnect) {
                                                if (confirmDisconnect) {
                                                    delay(DISCONNECT_CONFIRM_MS)
                                                    confirmDisconnect = false
                                                }
                                            }
                                            FilledTonalButton(
                                                shape = Shapes.Standard,
                                                colors = if (isActive) {
                                                    ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                                    )
                                                } else {
                                                    ButtonDefaults.filledTonalButtonColors()
                                                },
                                                onClick = {
                                                    when {
                                                        isActive && confirmDisconnect -> {
                                                            confirmDisconnect = false
                                                            serverViewModel.disconnect()
                                                        }
                                                        isActive -> confirmDisconnect = true
                                                        isConnectingHere -> {}
                                                        else -> viewModel.connectBookmark(bookmark, onConnected)
                                                    }
                                                },
                                                // Not greyed out while it shows this bookmark's progress,
                                                // or the spinner would be greyed out with it
                                                enabled = isActive || isConnectingHere || !isConnecting,
                                            ) {
                                                if (isConnectingHere) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp,
                                                        // The button's text colour: the default
                                                        // accent disappears into the tonal button
                                                        color = LocalContentColor.current,
                                                    )
                                                } else {
                                                    Text(
                                                        stringResource(
                                                            when {
                                                                isActive && confirmDisconnect -> R.string.tap_again
                                                                isActive -> R.string.disconnect
                                                                else -> R.string.connect
                                                            },
                                                        ),
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { menuIndex = if (menuIndex == index) null else index },
                                            ) {
                                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options), modifier = Modifier.size(IconSize))
                                            }
                                        }
                                        // The bookmark's own actions unfold under it
                                        AnimatedVisibility(
                                            visible = menuIndex == index,
                                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = Spacing.Gap),
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.Gap, Alignment.End),
                                            ) {
                                                TextButton(
                                                    shape = Shapes.Standard,
                                                    onClick = {
                                                        if (deleteConfirmIndex == index) {
                                                            viewModel.removeBookmark(index)
                                                            deleteConfirmIndex = null
                                                            menuIndex = null
                                                        } else {
                                                            deleteConfirmIndex = index
                                                        }
                                                    },
                                                ) {
                                                    Text(
                                                        stringResource(if (deleteConfirmIndex == index) R.string.tap_again else R.string.remove),
                                                        color = MaterialTheme.colorScheme.error,
                                                    )
                                                }
                                                FilledTonalButton(
                                                    shape = Shapes.Standard,
                                                    onClick = {
                                                        menuIndex = null
                                                        viewModel.editBookmark(bookmark, index)
                                                        showBottomSheet = true
                                                    },
                                                ) {
                                                    Text(stringResource(R.string.edit))
                                                }
                                            }
                                        }
                                    }
                                    // Editing a bookmark opens its form right under it
                                    AnimatedVisibility(
                                        visible = showBottomSheet && editingIndex == index,
                                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                                    ) {
                                        ServerForm()
                                    }
                                }
                            if (bookmarks.isEmpty() && connectingAddress == null) {
                                Text(
                                    stringResource(R.string.no_connection),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(InsetPadding),
                                )
                            }
                            // Adding a server, at the end of the list it adds to: the button opens
                            // the form in its place
                            AnimatedVisibility(
                                visible = showBottomSheet && editingIndex < 0,
                                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                            ) {
                                ServerForm()
                            }
                            if (!(showBottomSheet && editingIndex < 0)) FilledTonalButton(
                                onClick = {
                                    viewModel.cancelEdit()
                                    showBottomSheet = true
                                },
                                shape = Shapes.Standard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(ControlHeight),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(IconSize))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.add_server))
                            }
                        }
                    }
                    }

                } else if (page == 1) {
                    SettingsPage(
                        autoReconnect = autoReconnect,
                        onAutoReconnectChange = { viewModel.setAutoReconnect(it) },
                    )
                } else {
                    ServerScreen(
                        onBackToHome = { scope.launch { pagerState.animateScrollToPage(0) } },
                        initialAnimeBackground = initialAnimeBackground,
                        infoOpen = serverInfoOpen,
                    )
                }
            }
            }

        }
    }
}

/** Stands in for a bookmark card while a connection typed by hand is being made. */
@Composable
private fun ConnectingCard(wallpaper: ImageBitmap?) {
    InsetSection(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.connecting), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

