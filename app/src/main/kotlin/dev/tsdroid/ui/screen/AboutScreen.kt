package dev.tsdroid.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.tsdroid.han.R
import dev.tsdroid.ui.theme.Shapes

private const val REPO_URL = "https://github.com/IcaroPablo/TS6_Droid_CN"
private const val UPSTREAM_URL = "https://github.com/YUAXI/TS6_Droid_CN"

/** About TeleSpeak, unfolded in the Settings tab: what it is, where its source lives, whom it comes from, and its license. */
@Composable
fun AboutContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(Shapes.Standard),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AboutText(stringResource(R.string.about_tagline))
        AboutSection(stringResource(R.string.about_source_title)) {
            AboutText(stringResource(R.string.about_source_text))
            AboutLink(REPO_URL)
        }
        AboutSection(stringResource(R.string.about_credits_heading)) {
            AboutText(stringResource(R.string.about_credits_text))
            AboutLink(UPSTREAM_URL)
        }
        AboutSection(stringResource(R.string.about_license_heading)) {
            AboutText(stringResource(R.string.about_license_text))
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    content()
}

@Composable
private fun AboutText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall)
}

/** A link the user opens on purpose; the app itself never contacts these sites. */
@Composable
private fun AboutLink(url: String) {
    val context = LocalContext.current
    Text(
        text = url.removePrefix("https://"),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyLarge,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .padding(top = 8.dp)
            .clickable { openUrl(context, url) }
            .padding(vertical = 4.dp),
    )
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
    }
}
