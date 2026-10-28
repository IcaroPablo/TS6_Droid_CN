package dev.tsdroid.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tsdroid.han.R
import dev.tsdroid.service.TsConnectionService
import dev.tsdroid.ui.component.AnimeBackground
import dev.tsdroid.ui.component.AnimeWallpaperState
import dev.tsdroid.ui.component.ChannelTree
import dev.tsdroid.ui.component.CompactTopBar
import dev.tsdroid.ui.component.GlassCard
import dev.tsdroid.ui.component.GlassDialog
import dev.tsdroid.ui.component.GlassMenuItem
import dev.tsdroid.ui.component.Island
import dev.tsdroid.ui.component.LiquidGlassNavBar
import dev.tsdroid.ui.component.LiquidGlassNavBarClearance
import dev.tsdroid.ui.component.LiquidGlassNavItem
import dev.tsdroid.ui.component.LiquidGlassPanel
import dev.tsdroid.ui.component.ServerIcon
import dev.tsdroid.ui.component.rememberAppearance
import dev.tsdroid.ui.component.rememberGlassBackdrop
import dev.tsdroid.ui.icon.AppIcons
import dev.tsdroid.ui.theme.IconSize
import dev.tsdroid.ui.theme.OnlineGreen
import dev.tsdroid.ui.theme.Shapes
import dev.tsdroid.ui.theme.Spacing
import dev.tsdroid.viewmodel.ConnectionViewModel
import dev.tslib.ConnectionState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    onConnected: () -> Unit,
    onNavigateToAbout: () -> Unit,
    /** What startup read, so the first frame already paints the wallpaper the splash prepared. */
    initialAnimeBackground: Boolean,
    viewModel: ConnectionViewModel = viewModel(),
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

    // Frosted bookmark menu: which bookmark it belongs to, and the icon it hangs from — both in root
    // pixels, because the menu is positioned by hand inside this composition.
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    var menuAnchorRight by remember { mutableFloatStateOf(0f) }
    var menuAnchorBottom by remember { mutableFloatStateOf(0f) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // Both overlays live inside this screen instead of a window of their own, so they have to claim
    // the back gesture themselves: a ModalBottomSheet and a DropdownMenu used to do it for us, and
    // without it back would leave the screen with a half-filled form still pinned open.
    BackHandler(enabled = showBottomSheet) {
        showBottomSheet = false
        viewModel.cancelEdit()
    }
    BackHandler(enabled = menuIndex != null) { menuIndex = null }

    // The two tabs are pages: swipe left/right to switch them, and the floating bar follows. Tapping
    // the bar animates the pager, swiping the pager moves the selection — both directions, one state.
    val pagerState = rememberPagerState(pageCount = { 2 })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { selectedTab = it }
    }

    // The connection sheet can be dragged down to dismiss it, like the sheet it replaces.
    val sheetOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(showBottomSheet) { if (!showBottomSheet) sheetOffset.snapTo(0f) }

    // True while an in-place overlay (the sheet or the bookmark menu) floats above the tab content:
    // the Scaffold is composed below the floating bar, so it has to outrank it while one is open.
    // Dialogs do not need this — they render through GlassOverlayHost at the root of the app.
    val overlayOpen = showBottomSheet || menuIndex != null

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
        var attempts = 0
        while (TsConnectionService.instance == null && attempts < 50) {
            delay(100)
            attempts++
        }
        val service = TsConnectionService.instance ?: return@LaunchedEffect
        service.tsClient.state.collect { viewModel.observeServiceState(it) }
    }
    val sessionRunning = connectionState == ConnectionState.CONNECTED
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            // The root's own size, in pixels: the frosted menu below is positioned against it.
            .onGloballyPositioned { rootSize = it.size },
    ) {
        AnimeBackground(enabled = animeBackground)

        // Floating tab bar: laid over the content instead of taking a Scaffold.bottomBar slot, so it
        // reserves no height and the content area keeps the whole screen. zIndex keeps it above the
        // Scaffold (drawn later) even though it is declared first.
        // Home and Settings are the two pages; between them sit two actions: back into the running
        // session (only while there is one) and adding a server
        val homeItem = LiquidGlassNavItem(AppIcons.Home, stringResource(R.string.tab_home))
        val serverItem = LiquidGlassNavItem(AppIcons.Server, stringResource(R.string.nav_server))
        val addItem = LiquidGlassNavItem(Icons.Default.Add, stringResource(R.string.nav_add))
        val settingsItem = LiquidGlassNavItem(AppIcons.Settings, stringResource(R.string.tab_settings))
        val navItems = listOfNotNull(homeItem, serverItem.takeIf { sessionRunning }, addItem, settingsItem)
        LiquidGlassNavBar(
            items = navItems,
            selectedIndex = navItems.indexOf(if (selectedTab == 0) homeItem else settingsItem),
            onSelect = { index ->
                when (navItems[index]) {
                    serverItem -> onConnected()
                    addItem -> showBottomSheet = true
                    else -> {
                        val page = if (navItems[index] == homeItem) 0 else 1
                        selectedTab = page
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
                }
            },
            // Frosted like every other surface: it used to be transparent so the wallpaper showed
            // straight through, which left the items floating on the photo with no bar at all.
            wallpaper = rememberGlassBackdrop(animeBackground),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(1f),
        )

        Scaffold(
            // The overlays above are composed inside this Scaffold, and the floating tab bar is its
            // sibling at zIndex 1: while an overlay is open the Scaffold has to outrank the bar,
            // otherwise the bar is drawn over the sheet and covers its buttons.
            modifier = Modifier.zIndex(if (overlayOpen) 2f else 0f),
            containerColor = Color.Transparent,
            topBar = {
                CompactTopBar(
                    title = if (selectedTab == 0) stringResource(R.string.app_name)
                    else stringResource(R.string.tab_settings),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            // The tabs are real pages now: swipe between them, and the bar keeps up in both
            // directions. The old hand-rolled alpha/translation swap is gone with it.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                // Keep the off-screen tab composed: its scroll position and expansion state would
                // otherwise reset every time the user swipes away and back.
                beyondViewportPageCount = 1,
            ) { page ->
                if (page == 0) {
                    Box(modifier = Modifier.fillMaxSize()) {
                    if (bookmarks.isEmpty() && connectingAddress == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.no_connection),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = Spacing.Gap)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Gap),
                        ) {
                            if (connectingAddress != null && bookmarks.none { it.address == connectingAddress }) {
                                ConnectingCard(wallpaper = rememberGlassBackdrop(animeBackground))
                            }
                            bookmarks.forEachIndexed { index, bookmark ->
                                    Island(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // The connected / connecting line comes and goes smoothly
                                            .animateContentSize(),
                                        wallpaper = rememberGlassBackdrop(animeBackground),
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
                                                if (isActive || isConnectingHere) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isActive) OnlineGreen
                                                                    else LocalContentColor.current.copy(alpha = 0.5f),
                                                                ),
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = stringResource(
                                                                if (isActive) R.string.bookmark_connected else R.string.connecting,
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = LocalContentColor.current.copy(alpha = 0.72f),
                                                        )
                                                    }
                                                }
                                            }
                                            FilledTonalButton(
                                                shape = Shapes.Small,
                                                onClick = {
                                                    when {
                                                        isActive -> onConnected()
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
                                                    Text(stringResource(if (isActive) R.string.open_session else R.string.connect))
                                                }
                                            }
                                            var anchorRight by remember { mutableFloatStateOf(0f) }
                                            var anchorBottom by remember { mutableFloatStateOf(0f) }
                                            IconButton(
                                                onClick = {
                                                    // The menu itself is drawn at the root of this screen,
                                                    // further down: a DropdownMenu lives in its own
                                                    // window, where there is no wallpaper to frost.
                                                    menuIndex = index
                                                    menuAnchorRight = anchorRight
                                                    menuAnchorBottom = anchorBottom
                                                },
                                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                                    val position = coordinates.positionInRoot()
                                                    anchorRight = position.x + coordinates.size.width
                                                    anchorBottom = position.y + coordinates.size.height
                                                },
                                            ) {
                                                Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(IconSize))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(LiquidGlassNavBarClearance))
                        }
                    }

                } else {
                    SettingsPage(
                        onNavigateToAbout = onNavigateToAbout,
                        autoReconnect = autoReconnect,
                        onAutoReconnectChange = { viewModel.setAutoReconnect(it) },
                    )
                }
            }

            deleteConfirmIndex?.let { idx ->
                val bookmarkName = bookmarks.getOrNull(idx)?.let { it.serverName ?: it.name } ?: ""
                GlassDialog(
                    onDismissRequest = { deleteConfirmIndex = null },
                    title = stringResource(R.string.remove),
                    wallpaper = rememberGlassBackdrop(animeBackground),
                    body = {
                        Text(
                            text = stringResource(R.string.confirm_delete, bookmarkName),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmIndex = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.removeBookmark(idx)
                            deleteConfirmIndex = null
                        }) {
                            Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            }

            if (showChannelPicker) {
                GlassDialog(
                    onDismissRequest = { viewModel.showChannelPicker.value = false },
                    title = stringResource(R.string.select_channel),
                    wallpaper = rememberGlassBackdrop(animeBackground),
                    body = {
                        ChannelTree(
                            channels = browsedChannels,
                            users = emptyList(),
                            onChannelClick = { viewModel.selectChannel(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .height(400.dp),
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.showChannelPicker.value = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            // Bookmark menu, drawn here rather than as a DropdownMenu: a popup lives in its own window,
            // where there is no wallpaper behind it to frost. Same material as the cards, hung from
            // the icon it belongs to.
            menuIndex?.let { index ->
                val menuAppear = rememberAppearance()
                Box(modifier = Modifier.fillMaxSize().zIndex(3f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Swallows the dismissing tap and keeps what is behind it out of reach.
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { menuIndex = null },
                    )
                    GlassCard(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            // A fixed width, hung from the icon's right edge: without it the rows
                            // would fill the whole screen, because a card measures itself from its
                            // widest child.
                            .width(156.dp)
                            .offset {
                                IntOffset(
                                    x = (menuAnchorRight - rootSize.width).toInt(),
                                    y = menuAnchorBottom.toInt() + with(density) { 4.dp.roundToPx() },
                                )
                            }
                            // Grows out of the corner its button is in
                            .graphicsLayer {
                                alpha = menuAppear.value
                                scaleX = 0.9f + 0.1f * menuAppear.value
                                scaleY = 0.9f + 0.1f * menuAppear.value
                                transformOrigin = TransformOrigin(1f, 0f)
                            },
                        wallpaper = rememberGlassBackdrop(animeBackground),
                        shape = Shapes.Medium,
                        elevation = 6.dp,
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        // A Column, not two more children of the card's Box: two children of a Box are
                        // two children stacked on top of each other, which painted the labels over
                        // one another.
                        Column {
                            GlassMenuItem(Icons.Default.Edit, stringResource(R.string.edit)) {
                                menuIndex = null
                                // The list can have changed under the menu; never index into it blindly.
                                bookmarks.getOrNull(index)?.let { bookmark ->
                                    viewModel.editBookmark(bookmark, index)
                                    showBottomSheet = true
                                }
                            }
                            GlassMenuItem(
                                icon = Icons.Default.Delete,
                                label = stringResource(R.string.remove),
                                destructive = true,
                            ) {
                                menuIndex = null
                                deleteConfirmIndex = index
                            }
                        }
                    }
                }
            }

            // Manual connection / edit bookmark. A ModalBottomSheet is a window of its own, so the
            // wallpaper behind it could only be shown sharp; this panel is frosted like the cards and
            // merely shaped like a sheet. Dismissing is the scrim's job instead of a drag.
            if (showBottomSheet) {
                Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.32f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                showBottomSheet = false
                                viewModel.cancelEdit()
                            },
                    )
                    LiquidGlassPanel(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .offset { IntOffset(0, sheetOffset.value.roundToInt()) }
                            // Draggable downwards to dismiss, the way a sheet behaves; the scrim and
                            // the back gesture also close it.
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    scope.launch {
                                        // Only downwards: the panel is anchored to the bottom edge.
                                        sheetOffset.snapTo((sheetOffset.value + delta).coerceAtLeast(0f))
                                    }
                                },
                                onDragStopped = { velocity ->
                                    val draggedFar = sheetOffset.value > with(density) { 96.dp.toPx() }
                                    if (draggedFar || velocity > 1200f) {
                                        showBottomSheet = false
                                        viewModel.cancelEdit()
                                        sheetOffset.snapTo(0f)
                                    } else {
                                        sheetOffset.animateTo(0f, spring())
                                    }
                                },
                            )
                            // Keeps the panel above the keyboard while a field is being typed in.
                            .imePadding(),
                        wallpaper = rememberGlassBackdrop(animeBackground),
                        shape = Shapes.Sheet,
                        elevation = 10.dp,
                        contentPadding = PaddingValues(0.dp),
                    ) { contentColor ->
                        CompositionLocalProvider(LocalContentColor provides contentColor) {
                            val isEditing = editingIndex >= 0
                            val glassTextFieldColors = OutlinedTextFieldDefaults.colors(
                                // Translucent: an opaque field container on a frosted panel reads as a
                                // sticker laid on top of it instead of as part of the material.
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.26f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.44f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 24.dp),
                            ) {
                                // The handle is the drag target, and it sits outside the scrolling area so
                                // that a drag on it can never be swallowed by the field list's own scroll.
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(top = 8.dp, bottom = 6.dp)
                                        .width(36.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(LocalContentColor.current.copy(alpha = 0.3f)),
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // fill = false: the panel wraps its content, and only when the
                                        // keyboard squeezes it do the fields start to scroll.
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        stringResource(if (isEditing) R.string.edit_bookmark else R.string.manual_connection),
                                        style = MaterialTheme.typography.titleMedium,
                                    )

                                    OutlinedTextField(
                                    value = address,
                                    onValueChange = { viewModel.address.value = it },
                                    label = { Text(stringResource(R.string.server_address)) },
                                    placeholder = { Text(stringResource(R.string.server_address_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    maxLines = 1,
                                    enabled = !isConnecting,
                                    colors = glassTextFieldColors,
                                )

                                    OutlinedTextField(
                                    value = nickname,
                                    onValueChange = { viewModel.nickname.value = it },
                                    label = { Text(stringResource(R.string.nickname)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    maxLines = 1,
                                    enabled = !isConnecting,
                                    colors = glassTextFieldColors,
                                )

                                    OutlinedTextField(
                                    value = password,
                                    onValueChange = { viewModel.password.value = it },
                                    label = { Text(stringResource(R.string.password_optional)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    maxLines = 1,
                                    enabled = !isConnecting,
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = glassTextFieldColors,
                                )

                                    OutlinedTextField(
                                    value = channel,
                                    onValueChange = { viewModel.channel.value = it },
                                    label = { Text(stringResource(R.string.channel_optional)) },
                                    placeholder = { Text(stringResource(R.string.default_channel)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    maxLines = 1,
                                    enabled = !isConnecting,
                                    trailingIcon = {
                                        if (isBrowsing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.height(24.dp).width(24.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            IconButton(onClick = { viewModel.browseChannels() }, enabled = !isConnecting) {
                                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.browse_channels), modifier = Modifier.size(IconSize))
                                            }
                                        }
                                    },
                                    colors = glassTextFieldColors,
                                )

                                }
                                // Outside the scrolling block, so the actions never scroll out of reach.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        shape = Shapes.Small,
                                        onClick = {
                                            showBottomSheet = false
                                            viewModel.connect(onConnected)
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isConnecting,
                                    ) {
                                        if (isConnecting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.height(20.dp).width(20.dp),
                                                strokeWidth = 2.dp,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(stringResource(if (isConnecting) R.string.connecting else R.string.connect))
                                    }

                                    FilledTonalButton(shape = Shapes.Small, onClick = {
                                        viewModel.saveBookmark()
                                        showBottomSheet = false
                                    }) {
                                        if (!isEditing) {
                                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(IconSize))
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(stringResource(if (isEditing) R.string.save else R.string.add_bookmark))
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

/** Stands in for a bookmark card while a connection typed by hand is being made. */
@Composable
private fun ConnectingCard(wallpaper: ImageBitmap?) {
    Island(modifier = Modifier.fillMaxWidth(), wallpaper = wallpaper) {
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

