package app.skerry.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Modal overlay scrim: full-screen dimming with the card centered (or at [contentAlignment]). The
 * card inside [content] must consume its own clicks ([consumeClicks]), or tapping it would fall
 * through to the scrim. Every modal that uses this has its own explicit close control (a corner
 * ✕ or a Cancel button), so a scrim click deliberately does NOT dismiss — only Esc does (via
 * [onDismiss]); a stray click must never discard half-filled modal state.
 */
@Composable
fun ModalScrim(
    onDismiss: () -> Unit,
    scrimColor: Color = D.modalScrim,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val noop = remember { MutableInteractionSource() }
    DisposableEffect(Unit) {
        ModalPresence.opened()
        onDispose { ModalPresence.closed() }
    }
    // Esc handling: the scrim listens on key bubble-up (onKeyEvent), so a focused field or a
    // nested modal handles the event first. The scrim claims focus only while nothing inside the
    // modal holds it — otherwise it would steal focus from an auto-focused field.
    val escFocus = remember { FocusRequester() }
    var subtreeFocused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(scrimColor)
            .clickable(interactionSource = noop, indication = null, onClick = {}) // consume, never dismiss
            .onFocusChanged { subtreeFocused = it.hasFocus }
            .focusRequester(escFocus)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss(); true
                } else {
                    false
                }
            }
            .focusable(),
        contentAlignment = contentAlignment,
        content = content,
    )
    // Re-keyed on focus state, not one-shot: when the focused child is disposed (a protocol
    // switch removing the field, a nested modal closing) Compose clears focus to no one, key
    // events stop dispatching through this subtree, and Esc would go dead for good.
    LaunchedEffect(subtreeFocused) {
        if (subtreeFocused) return@LaunchedEffect
        withFrameNanos {} // let an auto-focused field inside claim focus first
        if (!subtreeFocused) runCatching { escFocus.requestFocus() }
    }
}

/**
 * Count of open [ModalScrim]s, as observable state. A scrim takes keyboard focus for Esc handling,
 * and closing the modal disposes the focused node — Compose then clears focus to no one. Widgets
 * that own the keyboard otherwise (the desktop terminal) watch for the count returning to zero to
 * re-claim focus, since nothing else restores it.
 */
object ModalPresence {
    var openCount by mutableStateOf(0)
        private set

    internal fun opened() {
        openCount++
    }

    internal fun closed() {
        openCount--
    }
}

/** Consumes clicks on a modal card (no-op clickable, no indication) so taps don't reach the scrim. */
@Composable
fun Modifier.consumeClicks(): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
