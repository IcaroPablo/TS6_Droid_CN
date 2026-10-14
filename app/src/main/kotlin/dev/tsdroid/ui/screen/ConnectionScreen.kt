package dev.tsdroid.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tsdroid.han.R
import dev.tslib.ConnectionState
import dev.tsdroid.service.TsConnectionService
import dev.tsdroid.ui.component.AnimeBackground
import dev.tsdroid.ui.component.AnimeWallpaperState
import dev.tsdroid.ui.component.ChannelTree
import dev.tsdroid.ui.component.CompactTopBar
import dev.tsdroid.ui.component.GlassCard
import dev.tsdroid.ui.component.LiquidGlassNavBar
import dev.tsdroid.ui.component.LiquidGlassNavItem
import dev.tsdroid.ui.component.rememberGlassBackdrop
import dev.tsdroid.viewmodel.ConnectionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    onConnected: () -> Unit,
    onNavigateToAbout: () -> Unit,
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    val isConnecting = connectionState == ConnectionState.CONNECTING
    val context = LocalContext.current
    val settingsStore = remember { dev.tsdroid.data.SettingsStore(context) }
    val animeBackground by settingsStore.animeBackground.collectAsStateWithLifecycle(initialValue = true)

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
    // it ends — the server screen is gone by then. Tracking it here keeps the card below honest.
    var sessionName by remember { mutableStateOf<String?>(null) }
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
        launch { service.tsClient.state.collect { viewModel.observeServiceState(it) } }
        launch {
            service.tsClient.serverInfo.collect { info ->
                sessionName = info?.name ?: service.tsClient.serverAddress
            }
        }
    }
    val sessionRunning = connectionState == ConnectionState.CONNECTED

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimeBackground(enabled = animeBackground)

        // Floating tab bar: laid over the content instead of taking a Scaffold.bottomBar slot, so it
        // reserves no height and the content area keeps the whole screen. zIndex keeps it above the
        // Scaffold (drawn later) even though it is declared first.
        LiquidGlassNavBar(
            items = listOf(
                LiquidGlassNavItem(Icons.Default.Home, stringResource(R.string.tab_home)),
                LiquidGlassNavItem(Icons.Default.Settings, stringResource(R.string.tab_settings)),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            // Frosted like every other surface: it used to be transparent so the wallpaper showed
            // straight through, which left the items floating on the photo with no bar at all.
            wallpaper = rememberGlassBackdrop(animeBackground),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(1f),
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    CompactTopBar(
                        title = if (selectedTab == 0) stringResource(R.string.app_name)
                        else stringResource(R.string.tab_settings),
                    )
                    // A session is running but the user is here, so give them the way back into it:
                    // a connection in the service is not visible anywhere else.
                    if (sessionRunning) {
                        RunningSessionCard(
                            name = sessionName,
                            onReturn = onConnected,
                            wallpaper = rememberGlassBackdrop(animeBackground),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (selectedTab == 0) {
                    GlassCard(
                        modifier = Modifier
                            .size(56.dp)
                            .clickable { showBottomSheet = true },
                        wallpaper = rememberGlassBackdrop(animeBackground),
                        shape = CircleShape,
                        elevation = 6.dp,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.manual_connection),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Tab 0: Home
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (selectedTab == 0) 1f else 0f }
                        .then(
                            if (selectedTab == 0) Modifier else Modifier.graphicsLayer {
                                translationY = -size.height
                            }
                        ),
                ) {
                    if (bookmarks.isEmpty()) {
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
                                .padding(horizontal = 16.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            bookmarks.forEachIndexed { index, bookmark ->
                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        wallpaper = rememberGlassBackdrop(animeBackground),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val icon = if (bookmark.iconId != 0L) bookmarkIcons[bookmark.iconId] else null
                                            if (icon != null) {
                                                Image(
                                                    bitmap = icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp),
                                                    contentScale = ContentScale.Fit,
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    bookmark.serverName ?: bookmark.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                )
                                                Text(
                                                    bookmark.address,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = LocalContentColor.current.copy(alpha = 0.72f),
                                                )
                                            }
                                            FilledTonalButton(
                                                onClick = { viewModel.connectBookmark(bookmark, onConnected) },
                                                enabled = !isConnecting,
                                            ) {
                                                Text(stringResource(R.string.connect))
                                            }
                                            Box {
                                                var menuExpanded by remember { mutableStateOf(false) }
                                                IconButton(onClick = { menuExpanded = true }) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = null)
                                                }
                                                DropdownMenu(
                                                    expanded = menuExpanded,
                                                    onDismissRequest = { menuExpanded = false },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.edit)) },
                                                        onClick = {
                                                            menuExpanded = false
                                                            viewModel.editBookmark(bookmark, index)
                                                            showBottomSheet = true
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.remove)) },
                                                        onClick = {
                                                            menuExpanded = false
                                                            deleteConfirmIndex = index
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                // Tab 1: Settings (always in composition, hidden when not selected)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (selectedTab == 1) 1f else 0f }
                        .then(
                            if (selectedTab == 1) Modifier else Modifier.graphicsLayer {
                                translationY = size.height
                            }
                        ),
                ) {
                    SettingsPage(
                        onNavigateToAbout = onNavigateToAbout,
                        autoReconnect = autoReconnect,
                        onAutoReconnectChange = { viewModel.setAutoReconnect(it) },
                    )
                }
            }

            deleteConfirmIndex?.let { idx ->
                val bookmarkName = bookmarks.getOrNull(idx)?.let { it.serverName ?: it.name } ?: ""
                AlertDialog(
                    onDismissRequest = { deleteConfirmIndex = null },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = { Text(stringResource(R.string.remove)) },
                    text = { Text(stringResource(R.string.confirm_delete, bookmarkName)) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.removeBookmark(idx)
                            deleteConfirmIndex = null
                        }) {
                            Text(stringResource(R.string.remove))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmIndex = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            if (showChannelPicker) {
                Dialog(onDismissRequest = { viewModel.showChannelPicker.value = false }) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 0.dp,
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                stringResource(R.string.select_channel),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Spacer(Modifier.height(16.dp))
                            ChannelTree(
                                channels = browsedChannels,
                                users = emptyList(),
                                onChannelClick = { viewModel.selectChannel(it) },
                                modifier = Modifier.fillMaxWidth().height(400.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            TextButton(
                                onClick = { viewModel.showChannelPicker.value = false },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            }

            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showBottomSheet = false
                        viewModel.cancelEdit()
                    },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    val isEditing = editingIndex >= 0
                    val glassTextFieldColors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(if (isEditing) R.string.edit_bookmark else R.string.manual_connection),
                            style = MaterialTheme.typography.titleLarge,
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
                                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.browse_channels))
                                    }
                                }
                            },
                            colors = glassTextFieldColors,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        sheetState.hide()
                                        showBottomSheet = false
                                    }
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

                            FilledTonalButton(onClick = {
                                viewModel.saveBookmark()
                                scope.launch {
                                    sheetState.hide()
                                    showBottomSheet = false
                                }
                            }) {
                                Icon(Icons.Default.Star, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(if (isEditing) R.string.save else R.string.add_bookmark))
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * The way back into a session that is still running in the background.
 *
 * Leaving the server screen no longer disconnects, so the connection has to be visible from here:
 * otherwise the only thing the user could do about a live session would be to start another one.
 */
@Composable
private fun RunningSessionCard(
    name: String?,
    onReturn: () -> Unit,
    wallpaper: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.clickable(onClick = onReturn),
        wallpaper = wallpaper,
        elevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.session_running),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = name ?: stringResource(R.string.session_running_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
            Text(
                text = stringResource(R.string.back_to_session),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
