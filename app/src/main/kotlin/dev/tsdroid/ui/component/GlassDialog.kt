package dev.tsdroid.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * The screen's overlay slot: at most one [GlassDialog] draws here at a time.
 *
 * A single slot is enough because every dialog in the app is modal — no screen opens a second one
 * while the first is still up.
 */
@Stable
class GlassOverlayHostState {
    internal var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    internal fun show(block: @Composable () -> Unit) {
        content = block
    }

    internal fun clear() {
        content = null
    }
}

@Composable
fun rememberGlassOverlayHostState(): GlassOverlayHostState = remember { GlassOverlayHostState() }

/**
 * Where [GlassDialog] draws itself.
 *
 * A dialog declared deep in the tree — inside a settings tab, inside a panel that slides over the
 * screen — would otherwise be clipped by whatever contains it, and the floating bar (a sibling of the
 * content, drawn above it) would sit on top of its scrim. Through this host the dialog is composed at
 * the screen root instead, wherever in the tree it was declared.
 */
val LocalGlassOverlayHost = compositionLocalOf<GlassOverlayHostState?> { null }

/**
 * Renders whatever [GlassDialog] currently wants on top. Put one at the root of a screen, after the
 * screen's content so that it draws last.
 */
@Composable
fun GlassOverlayHost(state: GlassOverlayHostState) {
    state.content?.invoke()
}

/**
 * A dialog drawn inside the screen it belongs to instead of in a window of its own.
 *
 * Android gives `Dialog` — and so `AlertDialog`, a `DropdownMenu` and a `ModalBottomSheet` — its own
 * window. The wallpaper is painted in the activity's window, so nothing inside a dialog window has a
 * backdrop to blur: those surfaces could only ever be opaque slabs. This one is the same frosted
 * material as the cards, over a dimming scrim that is part of the composition, which is what lets it
 * blur what is really behind it.
 *
 * Compose it inside an `if` to own its lifetime. It renders through [LocalGlassOverlayHost] when the
 * surrounding screen provides one — recommended, it then covers the whole screen, floating bar
 * included — and in place otherwise.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    wallpaper: ImageBitmap? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    zIndex: Float = 4f,
    /** Shown between the title and the buttons: a list, a tree, text fields — anything. */
    body: (@Composable ColumnScope.() -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
) {
    val host = LocalGlassOverlayHost.current
    if (host == null) {
        GlassDialogPanel(
            onDismissRequest = onDismissRequest,
            title = title,
            modifier = modifier,
            wallpaper = wallpaper,
            shape = shape,
            zIndex = zIndex,
            body = body,
            dismissButton = dismissButton,
            confirmButton = confirmButton,
        )
        return
    }

    // Hand this dialog to the screen's slot, and drop it again when the dialog leaves the composition.
    // The lambda keeps its identity while its captures are stable, so this does not churn the host.
    SideEffect {
        host.show {
            GlassDialogPanel(
                onDismissRequest = onDismissRequest,
                title = title,
                modifier = modifier,
                wallpaper = wallpaper,
                shape = shape,
                zIndex = zIndex,
                body = body,
                dismissButton = dismissButton,
                confirmButton = confirmButton,
            )
        }
    }
    DisposableEffect(host) { onDispose { host.clear() } }
}

@Composable
private fun GlassDialogPanel(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier,
    wallpaper: ImageBitmap?,
    shape: Shape,
    zIndex: Float,
    body: (@Composable ColumnScope.() -> Unit)?,
    dismissButton: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
) {
    // Without this a dialog the user cannot dismiss with the back gesture is a trap.
    BackHandler(enabled = true) { onDismissRequest() }

    Box(modifier = modifier.fillMaxSize().zIndex(zIndex)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )
        LiquidGlassPanel(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp),
            wallpaper = wallpaper,
            shape = shape,
            elevation = 10.dp,
            contentPadding = PaddingValues(0.dp),
        ) { contentColor ->
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(text = title, style = MaterialTheme.typography.headlineSmall)
                    body?.invoke(this)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
