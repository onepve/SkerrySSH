package app.skerry.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import app.skerry.ui.host.FolderBounds
import app.skerry.ui.host.HostDrop
import app.skerry.ui.host.hostDropTarget
import app.skerry.ui.theme.Skerry
import kotlinx.coroutines.CancellationException

private val MOUSE_DRAG_DEAD_ZONE = 6.dp

private class DragTrackingState(val startPosition: Offset) {
    var dragging = false
    var accumulated = Offset.Zero

    fun onDelta(
        change: PointerInputChange,
        threshold: Float,
        onStart: (Offset) -> Unit,
        onMove: (PointerInputChange, Offset) -> Unit,
    ) {
        val delta = change.positionChange()
        if (dragging) {
            if (delta != Offset.Zero) {
                change.consume()
                onMove(change, delta)
            }
        } else {
            accumulated += delta
            if (accumulated.getDistance() >= threshold) {
                dragging = true
                change.consume()
                onStart(startPosition)
                onMove(change, accumulated)
            }
        }
    }
}

suspend fun PointerInputScope.detectDeadZoneDragGestures(
    onStart: (Offset) -> Unit,
    onMove: (PointerInputChange, Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val threshold =
        if (down.type == PointerType.Mouse) MOUSE_DRAG_DEAD_ZONE.toPx() else viewConfiguration.touchSlop
    val tracker = DragTrackingState(down.position)
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || (change.isConsumed && !change.changedToUpIgnoreConsumed())) {
                if (tracker.dragging) onCancel()
                break
            }
            if (change.changedToUpIgnoreConsumed()) {
                if (tracker.dragging) onEnd()
                break
            }
            tracker.onDelta(change, threshold, onStart, onMove)
        }
    } catch (e: CancellationException) {
        if (tracker.dragging) onCancel()
        throw e
    }
}

/**
 * Drag state for item lists partitioned into folders (Snippets, Runbooks, Keychain).
 */
@Stable
class ListDragState {
    var draggingId by mutableStateOf<String?>(null)
        private set

    var activeDrop by mutableStateOf<HostDrop?>(null)
        private set

    private var pointerY = 0f
    private val itemBounds = HashMap<String, Rect>()
    private val folderRange = HashMap<String, Rect>()

    val isDragging: Boolean get() = draggingId != null

    fun setItemBounds(id: String, rect: Rect) { itemBounds[id] = rect }
    fun setFolderRange(name: String, rect: Rect) { folderRange[name] = rect }

    fun startDrag(id: String, localOffsetY: Float) {
        draggingId = id
        pointerY = (itemBounds[id]?.top ?: 0f) + localOffsetY
    }

    fun dragBy(deltaY: Float) {
        pointerY += deltaY
    }

    fun <T> folderBounds(folders: List<Folder<T>>, keyOf: (T) -> String): List<FolderBounds> =
        folders.mapNotNull { folder ->
            val range = folderRange[folder.name] ?: return@mapNotNull null
            FolderBounds(
                group = if (folder.name == UNGROUPED_FOLDER) null else folder.name,
                top = range.top,
                bottom = range.bottom,
                otherHostCentersY = folder.items
                    .filter { keyOf(it) != draggingId }
                    .mapNotNull { itemBounds[keyOf(it)]?.let { b -> (b.top + b.bottom) / 2f } }
            )
        }

    fun <T> refreshDrop(folders: List<Folder<T>>, keyOf: (T) -> String) {
        val next = hostDropTarget(folderBounds(folders, keyOf), pointerY)
        if (next != activeDrop) activeDrop = next
    }

    fun <T> currentDrop(folders: List<Folder<T>>, keyOf: (T) -> String): HostDrop? =
        hostDropTarget(folderBounds(folders, keyOf), pointerY)

    fun endDrag() {
        draggingId = null
        activeDrop = null
    }
}

fun Modifier.itemBoundsAnchor(state: ListDragState, id: String): Modifier =
    onGloballyPositioned { state.setItemBounds(id, it.boundsInWindow()) }

fun Modifier.folderRangeAnchor(state: ListDragState, name: String): Modifier =
    onGloballyPositioned { state.setFolderRange(name, it.boundsInWindow()) }

fun <T> Modifier.draggableItemRow(
    state: ListDragState,
    id: String,
    folders: () -> List<Folder<T>>,
    keyOf: (T) -> String,
    longPress: Boolean = false,
    onDrop: (HostDrop) -> Unit,
): Modifier = pointerInput(id, longPress) {
    var moved = false
    val onStart = { offset: Offset ->
        moved = false
        state.startDrag(id, offset.y)
        state.refreshDrop(folders(), keyOf)
    }
    val onMove = { change: PointerInputChange, amount: Offset ->
        change.consume()
        moved = true
        state.dragBy(amount.y)
        state.refreshDrop(folders(), keyOf)
    }
    val onEnd = {
        if (moved) state.currentDrop(folders(), keyOf)?.let(onDrop)
        state.endDrag()
    }
    val onCancel = { state.endDrag() }
    if (longPress) {
        detectDragGesturesAfterLongPress(onDragStart = onStart, onDrag = onMove, onDragEnd = onEnd, onDragCancel = onCancel)
    } else {
        detectDeadZoneDragGestures(onStart, onMove, onEnd, onCancel)
    }
}

@Composable
fun ListDropLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 2.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(Skerry.colors.cyan),
    )
}
