package app.skerry.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.shtail_group_collapse
import app.skerry.ui.generated.resources.shtail_group_expand
import app.skerry.ui.generated.resources.shtail_group_rename
import app.skerry.ui.generated.resources.shtail_group_state_collapsed
import app.skerry.ui.generated.resources.shtail_group_state_expanded
import app.skerry.ui.theme.Skerry
import org.jetbrains.compose.resources.stringResource

/**
 * Folder header of a list section: chevron + folder icon + name + count, the whole row folding the
 * section on a click.
 */
@Composable
fun FolderSectionHeader(
    name: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    onEdit: (() -> Unit)? = null,
) {
    val label = folderLabel(name)
    val action = stringResource(
        if (collapsed) Res.string.shtail_group_expand else Res.string.shtail_group_collapse,
        label,
    )
    val state = stringResource(
        if (collapsed) Res.string.shtail_group_state_collapsed else Res.string.shtail_group_state_expanded,
    )

    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = action, role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state }
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Sym(if (collapsed) "chevron_right" else "expand_more", size = 16.sp, color = Skerry.colors.faint)
        Sym("folder_open", size = 15.sp, color = Skerry.colors.cyanBright)
        Txt(
            label,
            color = Skerry.colors.dim,
            size = 12.5.sp,
            weight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onEdit != null) {
            IconBtn(
                "edit",
                onClick = onEdit,
                box = 20,
                icon = 13.sp,
                tint = Skerry.colors.faint,
                tooltip = stringResource(Res.string.shtail_group_rename, label),
            )
        }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(Skerry.colors.card)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Txt(count.toString(), color = Skerry.colors.faint, size = 10.sp)
        }
    }
}

/**
 * Header padding on a phone.
 */
fun mobileFolderHeaderPadding(horizontal: Dp = 0.dp): PaddingValues =
    PaddingValues(horizontal = horizontal, vertical = 8.dp)

/**
 * A list rendered as folder sections with optional drag-and-drop reordering and group management.
 */
@Composable
fun <T> FolderSections(
    items: List<T>,
    scope: String,
    collapse: FolderCollapse,
    group: (T) -> String?,
    itemKey: (T) -> String,
    headerPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    onEditGroup: ((String) -> Unit)? = null,
    onMoveItem: ((itemId: String, targetGroup: String?, targetIndexInGroup: Int) -> Unit)? = null,
    item: @Composable (T) -> Unit,
) {
    val groupList = items.map { group(it) }
    val folders = remember(items, groupList) { foldersOf(items, group) }
    val hasAnyFolders = hasFolders(items, group)

    if (!hasAnyFolders && onMoveItem == null) {
        items.forEach { row -> key(itemKey(row)) { item(row) } }
        return
    }

    val dragState = remember { ListDragState() }

    if (!hasAnyFolders) {
        val singleFolder = Folder(UNGROUPED_FOLDER, items)
        val others = items.filter { itemKey(it) != dragState.draggingId }
        val isDropTarget = dragState.isDragging && dragState.activeDrop?.group == null
        val dropIndex = if (isDropTarget) dragState.activeDrop?.index?.coerceIn(0, others.size) else null
        val lineBeforeId = dropIndex?.takeIf { it < others.size }?.let { itemKey(others[it]) }

        Column(
            Modifier
                .fillMaxWidth()
                .folderRangeAnchor(dragState, UNGROUPED_FOLDER)
        ) {
            items.forEach { row ->
                val key = itemKey(row)
                key(key) {
                    if (key == lineBeforeId) ListDropLine()
                    val isRowDragging = dragState.draggingId == key
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .itemBoundsAnchor(dragState, key)
                            .alpha(if (isRowDragging) 0.4f else 1f)
                            .draggableItemRow(
                                state = dragState,
                                id = key,
                                folders = { listOf(singleFolder) },
                                keyOf = itemKey,
                                onDrop = { drop -> onMoveItem?.invoke(key, drop.group, drop.index) },
                            )
                    ) {
                        item(row)
                    }
                }
            }
            if (dropIndex != null && dropIndex == others.size) ListDropLine()
        }
        return
    }

    folders.forEach { folder ->
        key(folder.name) {
            val collapseKey = folderCollapseKey(scope, folder.name)
            val collapsed = collapse.isGroupCollapsed(collapseKey)
            val targetGroup = if (folder.name == UNGROUPED_FOLDER) null else folder.name
            val isDropTarget = dragState.isDragging && dragState.activeDrop?.group == targetGroup
            val onEdit = if (folder.name != UNGROUPED_FOLDER && onEditGroup != null) {
                { onEditGroup(folder.name) }
            } else null

            val others = folder.items.filter { itemKey(it) != dragState.draggingId }
            val dropIndex = if (isDropTarget) dragState.activeDrop?.index?.coerceIn(0, others.size) else null
            val lineBeforeId = dropIndex?.takeIf { it < others.size }?.let { itemKey(others[it]) }

            Column(
                Modifier
                    .fillMaxWidth()
                    .folderRangeAnchor(dragState, folder.name)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, if (isDropTarget) Skerry.colors.cyan else Color.Transparent, RoundedCornerShape(6.dp))
            ) {
                FolderSectionHeader(
                    name = folder.name,
                    count = folder.items.size,
                    collapsed = collapsed,
                    onToggle = { collapse.toggleGroupCollapsed(collapseKey) },
                    padding = headerPadding,
                    onEdit = onEdit,
                )
                if (!collapsed) {
                    folder.items.forEach { row ->
                        val key = itemKey(row)
                        key(key) {
                            if (key == lineBeforeId) ListDropLine()
                            val isRowDragging = dragState.draggingId == key
                            val rowModifier = if (onMoveItem != null) {
                                Modifier
                                    .fillMaxWidth()
                                    .itemBoundsAnchor(dragState, key)
                                    .alpha(if (isRowDragging) 0.4f else 1f)
                                    .draggableItemRow(
                                        state = dragState,
                                        id = key,
                                        folders = { folders },
                                        keyOf = itemKey,
                                        onDrop = { drop -> onMoveItem(key, drop.group, drop.index) },
                                    )
                            } else Modifier.fillMaxWidth()

                            Box(rowModifier) {
                                item(row)
                            }
                        }
                    }
                    if (dropIndex != null && dropIndex == others.size) ListDropLine()
                }
            }
        }
    }
}
