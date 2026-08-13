package app.skerry.ui.runbook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.shared.snippet.stripUnsafeFormatChars
import app.skerry.ui.app.DesktopDesignState
import app.skerry.ui.app.LocalRunbookHistory
import app.skerry.ui.app.LocalRunbooks
import app.skerry.ui.app.LocalSnippets
import app.skerry.ui.convert.ConvertDialog
import app.skerry.ui.generated.resources.convert_confirm
import app.skerry.ui.generated.resources.convert_name_conflict_snippet
import app.skerry.ui.generated.resources.convert_name_label
import app.skerry.ui.generated.resources.convert_skipped_transfer
import app.skerry.ui.generated.resources.convert_to_snippet
import app.skerry.shared.runbook.RunbookConverter
import app.skerry.ui.snippet.SnippetDraft
import app.skerry.ui.design.Chip
import app.skerry.ui.design.EmptyState
import app.skerry.ui.design.FilterChipRow
import app.skerry.ui.design.GhostButton
import app.skerry.ui.design.HelpDialog
import app.skerry.ui.design.HLine
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.PrimaryButton
import app.skerry.ui.design.SectionHeader
import app.skerry.ui.design.SidebarSearchField
import app.skerry.ui.design.Sym
import app.skerry.ui.design.Txt
import app.skerry.shared.runbook.RunbookTransferFile
import app.skerry.shared.runbook.RunbookTransferResult
import app.skerry.shared.runbook.parseRunbookTransfer
import app.skerry.shared.transfer.TransferMode
import app.skerry.shared.transfer.planTransfer
import app.skerry.ui.transfer.TransferImportDialog
import app.skerry.ui.transfer.TransferInfoDialog
import app.skerry.ui.transfer.exportRunbookLibrary
import app.skerry.ui.transfer.importRunbookLibrary
import app.skerry.ui.vault.exportTextFile
import app.skerry.ui.vault.importTextFile
import app.skerry.ui.design.VLine
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.runbook_count
import app.skerry.ui.generated.resources.runbook_empty
import app.skerry.ui.generated.resources.runbook_new
import app.skerry.ui.generated.resources.runbook_no_matches
import app.skerry.ui.generated.resources.runbook_section
import app.skerry.ui.generated.resources.runbook_search
import app.skerry.ui.generated.resources.runbook_select_or_create
import app.skerry.ui.generated.resources.runbook_step_count
import app.skerry.ui.generated.resources.runbook_steps_total
import app.skerry.ui.generated.resources.runbook_untitled
import app.skerry.ui.generated.resources.transfer_dialog_corrupted
import app.skerry.ui.generated.resources.transfer_dialog_error_title
import app.skerry.ui.generated.resources.transfer_dialog_import_done
import app.skerry.ui.generated.resources.transfer_dialog_import_runbooks
import app.skerry.ui.generated.resources.transfer_dialog_pick_runbooks
import app.skerry.ui.generated.resources.transfer_dialog_result
import app.skerry.ui.generated.resources.transfer_export
import app.skerry.ui.generated.resources.transfer_import
import app.skerry.ui.snippet.SnippetCategoryHeader
import app.skerry.ui.snippet.snippetTagLabel
import app.skerry.ui.theme.Skerry
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.platform.testTag
import app.skerry.ui.app.UiTags

/** Width of the search field in the section header — the same one the snippets section uses. */
private val SEARCH_WIDTH = 240.dp

/** Import files are plain text; anything past this size is not a runbook transfer file. */
private const val TRANSFER_FILE_MAX_BYTES = 2 * 1024 * 1024

/** Pending whole-library import: the parsed file, waiting for the mode choice in the dialog. */
private sealed interface RunbookImportUiState {
    data class Pending(val fileName: String, val file: RunbookTransferFile) : RunbookImportUiState
}

/** Completed import: how many entries the file added and updated, shown in the notice. */
private data class ImportDone(val additions: Int, val updates: Int)

/**
 * Runbooks section: a flat library of saved procedures with a panel on the right — the same shape
 * as [app.skerry.ui.snippet.SnippetsView], because the two are reached for in the same way. The
 * panel shows what the selected runbook will do and where it will run; editing is a separate mode
 * of that panel rather than a form that is always open.
 *
 * Its own section rather than a mode of Snippets: a runbook is a step list with per-step flags and
 * a run policy, and it is picked up at a different moment than a one-liner.
 */
@Composable
fun RunbooksView(state: DesktopDesignState) {
    val mono = LocalFonts.current.mono
    val manager = LocalRunbooks.current
    if (manager == null) {
        // Mock/preview path (no vault behind the section): say so instead of a blank pane.
        Column(Modifier.fillMaxSize().background(Skerry.colors.bg)) {
            RunbooksHeader(runbooks = 0, steps = 0, query = "", onQuery = {}, onNew = {})
            EmptyState(icon = "checklist", title = stringResource(Res.string.runbook_empty))
        }
        return
    }
    LiveRunbooksView(manager, state, mono)
}

/** What the panel is showing: the selected runbook's run card, a new runbook's form, or an edit. */
private sealed interface RunbookPanelMode {
    data object Run : RunbookPanelMode
    data object New : RunbookPanelMode

    /**
     * Editing [id]. The id is carried rather than re-read from the list selection: the search field
     * stays live while the form is open, and a filter that hides the edited runbook would otherwise
     * swap the form onto a different one, discarding unsaved changes.
     */
    data class Edit(val id: String) : RunbookPanelMode
}

@Composable
private fun LiveRunbooksView(manager: RunbookManager, state: DesktopDesignState, mono: FontFamily) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf<RunbookPanelMode>(RunbookPanelMode.Run) }
    var showHelp by remember { mutableStateOf(false) }
    var showConvert by remember { mutableStateOf(false) }
    var importState by remember { mutableStateOf<RunbookImportUiState?>(null) }
    var importMode by remember { mutableStateOf(TransferMode.MERGE) }
    var importDone by remember { mutableStateOf<ImportDone?>(null) }
    var importError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickTitle = stringResource(Res.string.transfer_dialog_pick_runbooks)
    val library = state.runbookLibrary
    val history = LocalRunbookHistory.current

    val all = manager.runbooks
    // Not memoized: RunbookManager.save() updates an entry in place, so neither `all` nor `query`
    // changes identity on a rename and a cached result would go stale (snippets filter the same way).
    val visible = library.visible(all)
    val selected = visible.firstOrNull { it.id == selectedId } ?: visible.firstOrNull()
    val runbooks = all.map { it.runbook }

    Column(Modifier.fillMaxSize().background(Skerry.colors.bg)) {
        RunbooksHeader(
            runbooks = all.size,
            steps = all.sumOf { it.runbook.steps.size },
            query = library.query,
            onQuery = { library.query = it },
            onNew = { mode = RunbookPanelMode.New },
            onHelp = { showHelp = true },
            onExport = {
                scope.launch { exportTextFile("runbooks.json", exportRunbookLibrary(manager)) }
            },
            onImport = {
                scope.launch {
                    val picked = importTextFile(pickTitle, TRANSFER_FILE_MAX_BYTES)
                        ?: return@launch
                    when (val parsed = parseRunbookTransfer(picked.text)) {
                        is RunbookTransferResult.Ok -> {
                            importMode = TransferMode.MERGE
                            importState = RunbookImportUiState.Pending(fileName = picked.name, file = parsed.file)
                        }
                        is RunbookTransferResult.Corrupted -> importError = true
                    }
                }
            },
        )
        if (hasRunbookCategories(all)) {
            FilterChipRow(
                chips = library.chips(all),
                activeChip = library.effectiveChip(all) ?: ALL_RUNBOOKS_CHIP,
                onSelect = { library.activeChip = it },
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp),
                label = { runbookChipLabel(it) },
            )
        }
        HLine()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (visible.isEmpty()) {
                    EmptyState(
                        icon = "checklist",
                        title = if (all.isEmpty()) stringResource(Res.string.runbook_empty)
                        else stringResource(Res.string.runbook_no_matches),
                        subtitle = if (all.isEmpty()) stringResource(Res.string.runbook_select_or_create) else null,
                    )
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        if (shouldGroupRunbooks(visible, library.effectiveChip(all) ?: ALL_RUNBOOKS_CHIP)) {
                            groupRunbooksByCategory(visible).forEach { category ->
                                key(category.name) {
                                    SnippetCategoryHeader(
                                        category = category.name,
                                        count = category.runbooks.size,
                                        collapsed = library.isTagCollapsed(category.name),
                                        onToggle = { library.toggleTagCollapsed(category.name) },
                                    )
                                    if (!library.isTagCollapsed(category.name)) {
                                        category.runbooks.forEach { entry ->
                                            key(entry.id) {
                                                val onClick = remember(entry.id) {
                                                    { selectedId = entry.id; mode = RunbookPanelMode.Run }
                                                }
                                                RunbookListRow(entry, entry.id == selected?.id, mono, onClick)
                                                HLine()
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            visible.forEach { entry ->
                                key(entry.id) {
                                    val onClick = remember(entry.id) {
                                        { selectedId = entry.id; mode = RunbookPanelMode.Run }
                                    }
                                    RunbookListRow(entry, entry.id == selected?.id, mono, onClick)
                                    HLine()
                                }
                            }
                        }
                    }
                }
            }
            val editMode = mode as? RunbookPanelMode.Edit
            val editing = editMode?.let { manager.find(it.id) }
            when {
                mode is RunbookPanelMode.New || editing != null -> {
                    VLine(Skerry.colors.line)
                    Box(Modifier.width(RUNBOOK_PANEL_WIDTH).fillMaxHeight().background(Skerry.colors.surface2)) {
                        // Keyed by the edited runbook's identity so the form resets instead of
                        // carrying over the previous values.
                        key(editing?.id, mode) {
                            RunbookEditorPanel(
                                entry = editing,
                                manager = manager,
                                mono = mono,
                                onSaved = { id -> selectedId = id; mode = RunbookPanelMode.Run },
                                onCancel = { mode = RunbookPanelMode.Run },
                            )
                        }
                    }
                }
                selected != null -> {
                    VLine(Skerry.colors.line)
                    key(selected.id) {
                        RunbookRunCard(
                            entry = selected,
                            state = state,
                            mono = mono,
                            onEdit = { mode = RunbookPanelMode.Edit(selected.id) },
                            onConvert = { showConvert = true },
                            onDelete = {
                            manager.delete(selected.id)
                            // The runbook is gone; its run log has nothing left to belong to.
                            history?.forget(selected.id)
                            selectedId = null
                        },
                        )
                    }
                }
            }
        }
    }
    if (showHelp) {
        val exampleRunbooks = runbookExampleTemplatesByKey()
        HelpDialog(
            title = runbookHelpTitle(),
            sections = runbookHelpSections(),
            examples = runbookHelpExamples(),
            onDismiss = { showHelp = false },
            onExampleAction = { example ->
                exampleRunbooks[example.key]?.let { manager.save(it) }
                showHelp = false
            },
        )
    }
    val snippetManager = LocalSnippets.current
    if (showConvert && selected != null && snippetManager != null) {
        val (converted, skipped) = remember(selected.id) { RunbookConverter.runbookToSnippet(selected.runbook) }
        ConvertDialog(
            title = stringResource(Res.string.convert_to_snippet),
            initialName = selected.runbook.label,
            nameLabel = stringResource(Res.string.convert_name_label),
            confirmLabel = stringResource(Res.string.convert_confirm),
            nameConflict = { name -> snippetManager.snippets.any { it.snippet.label == name } },
            conflictMessage = stringResource(Res.string.convert_name_conflict_snippet),
            info = if (skipped > 0) stringResource(Res.string.convert_skipped_transfer, skipped) else null,
            onConfirm = { name ->
                snippetManager.save(
                    SnippetDraft(
                        label = name,
                        command = converted.command,
                        tags = converted.tags,
                    )
                )
                showConvert = false
            },
            onDismiss = { showConvert = false },
        )
    }
    when (val state = importState) {
        is RunbookImportUiState.Pending -> {
            val plan = planTransfer(state.file.runbooks, runbooks, importMode) { it.id }
            TransferImportDialog(
                title = stringResource(Res.string.transfer_dialog_import_runbooks),
                fileName = state.fileName,
                plan = plan,
                mode = importMode,
                onModeChange = { importMode = it },
                onConfirm = {
                    importRunbookLibrary(manager, state.file, importMode)
                    importDone = ImportDone(plan.additions, plan.updates)
                    importState = null
                },
                onDismiss = { importState = null },
            )
        }
        null -> Unit
    }
    importDone?.let { done ->
        TransferInfoDialog(
            title = stringResource(Res.string.transfer_dialog_import_done),
            message = stringResource(Res.string.transfer_dialog_result, done.additions, done.updates),
            onDismiss = { importDone = null },
        )
    }
    if (importError) {
        TransferInfoDialog(
            title = stringResource(Res.string.transfer_dialog_error_title),
            message = stringResource(Res.string.transfer_dialog_corrupted),
            onDismiss = { importError = false },
        )
    }
}

@Composable
private fun RunbooksHeader(
    runbooks: Int,
    steps: Int,
    query: String,
    onQuery: (String) -> Unit,
    onNew: () -> Unit,
    onHelp: () -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    SectionHeader(
        title = stringResource(Res.string.runbook_section),
        // Two numbers, because they answer different questions: how many procedures are saved, and
        // how much work they add up to.
        subtitle = pluralStringResource(Res.plurals.runbook_count, runbooks, runbooks) + " · " +
            pluralStringResource(Res.plurals.runbook_steps_total, steps, steps),
        help = onHelp,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SidebarSearchField(query, onQuery, stringResource(Res.string.runbook_search), Modifier.width(SEARCH_WIDTH))
                PrimaryButton(stringResource(Res.string.runbook_new), onClick = onNew, icon = "add", modifier = Modifier.testTag(UiTags.NEW_RUNBOOK))
                GhostButton(stringResource(Res.string.transfer_export), onClick = onExport, icon = "download")
                GhostButton(stringResource(Res.string.transfer_import), onClick = onImport, icon = "upload")
            }
        },
    )
}

/** One runbook row: name and its first steps on the left, tags on the right. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunbookListRow(entry: RunbookEntry, selected: Boolean, mono: FontFamily, onClick: () -> Unit) {
    val runbook = entry.runbook
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Skerry.colors.cyan10 else Skerry.colors.bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Sym("checklist", size = 16.sp, color = if (selected) Skerry.colors.cyanBright else Skerry.colors.dim)
        Column(Modifier.weight(1f)) {
            Txt(
                // Stripped like everywhere a runbook names itself: it can arrive over sync, and a
                // bidi override must not be able to make one procedure read as another.
                stripUnsafeFormatChars(runbook.label).ifBlank { stringResource(Res.string.runbook_untitled) },
                color = if (selected) Skerry.colors.cyanBright else Skerry.colors.textBright,
                size = 13.sp, weight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Txt(
                stringResource(Res.string.runbook_step_count, runbook.steps.size) +
                    runbook.steps.firstOrNull()?.let { " · " + stripUnsafeFormatChars(it.summaryLine()) }.orEmpty(),
                color = Skerry.colors.faint, size = 11.sp, font = mono,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (runbook.tags.isNotEmpty()) {
            FlowRow(
                Modifier.widthIn(max = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                runbook.tags.forEach { tag -> key(tag) { Chip(snippetTagLabel(tag)) } }
            }
        }
    }
}

internal fun RunbookEntry.matches(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return runbook.label.lowercase().contains(q) ||
        runbook.description.lowercase().contains(q) ||
        runbook.tags.any { it.lowercase().contains(q) } ||
        runbook.steps.any { it.title.lowercase().contains(q) || it.summaryLine().lowercase().contains(q) }
}
