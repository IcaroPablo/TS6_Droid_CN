package dev.tsdroid.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.tsdroid.han.R
import dev.tsdroid.ui.theme.generateColorScheme

/**
 * About page. It is deliberately plain white paper — the wallpaper is never drawn here — so the
 * long license text stays on a neutral, high-contrast background.
 *
 * Two things follow from that white surface and are handled here rather than left to the theme:
 * the app's *light* palette is pinned (a dark system theme would otherwise paint pale text on
 * white), and the system-bar icons are switched to their dark variants (or they would vanish into
 * the white until the user leaves the page).
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val systemInDarkTheme = isSystemInDarkTheme()
    val lightScheme = remember { generateColorScheme(Color(0xFF6750A4), isDark = false) }

    DisposableEffect(view, activity) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            // Hand the bars back to the rest of the app, which lets them follow the system theme.
            controller?.isAppearanceLightStatusBars = !systemInDarkTheme
            controller?.isAppearanceLightNavigationBars = !systemInDarkTheme
        }
    }

    MaterialTheme(colorScheme = lightScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .navigationBarsPadding(),
        ) {
            AboutContent(onBack = onBack)
        }
    }
}

@Composable
private fun AboutContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val repoUrl = "https://github.com/YUAXI/TS6_Droid_CN"
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.about_back),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { onBack() }.padding(8.dp)
            )
            Spacer(modifier = Modifier.width(24.dp))
            Text(
                text = stringResource(R.string.about_software),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(text = stringResource(R.string.about_credits_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_credits_desc),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = stringResource(R.string.about_enhancement_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_enhancement_desc),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = stringResource(R.string.about_license_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_license_desc),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = stringResource(R.string.about_repo_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_repo_desc),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.about_repo_link),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl))
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        )

        Spacer(modifier = Modifier.height(28.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        Spacer(modifier = Modifier.height(28.dp))

        // Fixed text where a contributor list used to be fetched from GitHub on every visit:
        // the app talks to no one but the user's own server (see the README).
        Text(text = stringResource(R.string.about_fork_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.about_fork_desc),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(28.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        Spacer(modifier = Modifier.height(28.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.about_warning_title),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.about_warning_desc),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
