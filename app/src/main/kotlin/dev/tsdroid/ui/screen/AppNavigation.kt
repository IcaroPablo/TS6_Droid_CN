package dev.tsdroid.ui.screen

import androidx.compose.runtime.Composable

/**
 * The app is one island with tabs (servers, settings and the connected server), so there is only
 * one screen to show.
 *
 * [initialAnimeBackground] is what startup read from the settings before it let the app in. The
 * screen takes it as the initial value of its own wallpaper setting so the first frame it draws
 * already has the background the splash prepared, instead of an empty one waiting for DataStore.
 */
@Composable
fun AppNavigation(initialAnimeBackground: Boolean) {
    ConnectionScreen(initialAnimeBackground = initialAnimeBackground)
}
