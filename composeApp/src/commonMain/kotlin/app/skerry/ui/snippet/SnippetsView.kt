package app.skerry.ui.snippet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import app.skerry.ui.app.DesktopDesignState
import app.skerry.ui.app.LocalSessions
import app.skerry.ui.app.LocalSnippets
import app.skerry.ui.app.LocalRunbooks
import app.skerry.ui.convert.ConvertDialog
import app.skerry.ui.design.GhostButton
import app.skerry.ui.generated.resources.convert_confirm
import app.skerry.ui.generated.resources.convert_name_conflict_runbook
import app.skerry.ui.generated.resources.convert_name_label
import app.skerry.ui.generated.resources.convert_to_runbook
import app.skerry.ui.generated.resources.transfer_dialog_corrupted
import app.skerry.ui.generated.resources.transfer_dialog_error_title
import app.skerry.ui.generated.resources.transfer_dialog_import_done
import app.skerry.ui.generated.resources.transfer_dialog_import_snippets
import app.skerry.ui.generated.resources.transfer_dialog_pick_snippets
import app.skerry.ui.generated.resources.transfer_dialog_result
import app.skerry.ui.generated.resources.transfer_export
import app.skerry.ui.generated.resources.transfer_import
import app.skerry.shared.runbook.RunbookConverter
import app.skerry.shared.snippet.SnippetTransferFile
import app.skerry.shared.snippet.SnippetTransferResult
import app.skerry.shared.snippet.parseSnippetTransfer
import app.skerry.shared.transfer.TransferMode
import app.skerry.shared.transfer.planTransfer
import app.skerry.ui.runbook.RunbookDraft
import app.skerry.ui.transfer.TransferImportDialog
import app.skerry.ui.transfer.TransferInfoDialog
import app.skerry.ui.transfer.exportSnippetLibrary
import app.skerry.ui.transfer.importSnippetLibrary
import app.skerry.ui.vault.exportTextFile
import app.skerry.ui.vault.importTextFile
import app.skerry.ui.connection.ConnectionUiState
import app.skerry.ui.design.ChipButton
import app.skerry.ui.design.EmptyState
import app.skerry.ui.design.HLine
import app.skerry.ui.design.HelpDialog
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.PrimaryButton
import app.skerry.ui.design.SectionHeader
import app.skerry.ui.design.SidebarSearchField
import app.skerry.ui.design.VLine
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.lib_snippets_command_count
import app.skerry.ui.generated.resources.lib_snippets_empty
import app.skerry.ui.generated.resources.lib_snippets_facts_variables
import app.skerry.ui.generated.resources.lib_snippets_new
import app.skerry.ui.generated.resources.lib_snippets_no_matches
import app.skerry.ui.generated.resources.lib_snippets_screen_title
import app.skerry.ui.generated.resources.lib_snippets_search
import app.skerry.ui.generated.resources.lib_snippets_select_or_create
import app.skerry.ui.generated.resources.lib_snippets_starter_pack
import app.skerry.ui.session.SessionsController
import app.skerry.ui.theme.Skerry
import app.skerry.ui.vault.copyTextToClipboard
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.platform.testTag
import app.skerry.ui.app.UiTags

/** Width of the search field in the section header. */
private val SEARCH_WIDTH = 240.dp

/** Import files are plain text; anything past this size is not a snippet transfer file. */
private const val TRANSFER_FILE_MAX_BYTES = 2 * 1024 * 1024

/** Pending whole-library import: the parsed file, waiting for the mode choice in the dialog. */
private sealed interface SnippetImportUiState {
    data class Pending(val fileName: String, val file: SnippetTransferFile) : SnippetImportUiState
}

/** Completed import: how many entries the file added and updated, shown in the notice. */
private data class ImportDone(val additions: Int, val updates: Int)

/**
 * Global Snippets section: a flat library of saved commands with a run panel on the right. A snippet
 * is a self-contained plain config, no secrets. Running happens here (into a connected session), from
 * the terminal palette, a hotkey, or "Run snippet…" in a host's context menu; editing is a separate
 * mode of the same panel. Shows a live list when a manager is provided ([LocalSnippets]); otherwise
 * (offscreen render/preview) an empty state.
 */
@Composable
fun SnippetsView(state: DesktopDesignState) {
    val mono = LocalFonts.current.mono
    val manager = LocalSnippets.current
    if (manager == null) {
        Column(Modifier.fillMaxSize().background(Skerry.colors.bg)) {
            SnippetsHeader(SnippetLibraryFacts(0, 0), query = "", onQuery = {}, onNew = {}, onHelp = {}, onExport = {}, onImport = {})
            EmptyState(icon = "code_blocks", title = stringResource(Res.string.lib_snippets_empty))
        }
        return
    }
    LiveSnippetsView(manager, state.snippetLibrary, mono)
}

/** What the panel is showing: the selected snippet's run card, a new snippet's form, or an edit form. */
private sealed interface PanelMode {
    data object Run : PanelMode
    data object New : PanelMode

    /**
     * Editing [id]. The id is carried rather than re-read from the list selection: the search field
     * and the tag chips stay live while the form is open, and a filter that hides the edited snippet
     * would otherwise swap the form onto a different one, discarding unsaved changes.
     */
    data class Edit(val id: String) : PanelMode
}

@Composable
private fun LiveSnippetsView(manager: SnippetManager, library: SnippetLibraryState, mono: FontFamily) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf<PanelMode>(PanelMode.Run) }
    var showHelp by remember { mutableStateOf(false) }
    var showConvert by remember { mutableStateOf(false) }

    // Whole-library import: the pending confirmation (parsed file + plan), and the one-shot notice
    // shown after an import (result) or when the picked file didn't parse.
    var importState by remember { mutableStateOf<SnippetImportUiState?>(null) }
    var importMode by remember { mutableStateOf(TransferMode.MERGE) }
    var importDone by remember { mutableStateOf<ImportDone?>(null) }
    var importError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickTitle = stringResource(Res.string.transfer_dialog_pick_snippets)

    val all = manager.snippets
    val visible = library.visible(all)
    val selected = resolveSelectedSnippet(visible, selectedId)
    // Keyed on the snippet values, not on `all`: saving an edit mutates an entry in place, so the
    // list keeps its identity while the variable count changes.
    val snippets = all.map { it.snippet }
    val facts = remember(snippets) { snippetLibraryFacts(snippets) }

    val sessions = LocalSessions.current
    val targets = snippetRunTargets(sessions)

    Column(Modifier.fillMaxSize().background(Skerry.colors.bg)) {
        SnippetsHeader(
            facts = facts,
            query = library.query,
            onQuery = { library.query = it },
            onNew = { mode = PanelMode.New },
            onHelp = { showHelp = true },
            onExport = {
                scope.launch {
                    exportTextFile("snippets.json", exportSnippetLibrary(manager))
                }
            },
            onImport = {
                scope.launch {
                    val picked = importTextFile(pickTitle, TRANSFER_FILE_MAX_BYTES)
                        ?: return@launch
                    when (val parsed = parseSnippetTransfer(picked.text)) {
                        is SnippetTransferResult.Ok -> {
                            importMode = TransferMode.MERGE
                            importState = SnippetImportUiState.Pending(
                                fileName = picked.name,
                                file = parsed.file,
                            )
                        }
                        is SnippetTransferResult.Corrupted -> importError = true
                    }
                }
            },
        )
        if (all.any { it.snippet.tags.isNotEmpty() }) {
            SnippetTagFilterRow(
                chips = library.chips(all),
                library = library,
                all = all,
                onRenameTag = { oldTag, newTag -> manager.renameTag(oldTag, newTag)?.let { library.onTagRenamed(oldTag, it) } },
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp),
            )
        }
        HLine()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (visible.isEmpty()) {
                    SnippetsEmptyList(libraryEmpty = all.isEmpty(), onInstallStarterPack = { manager.installStarterPack() })
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SnippetLibraryColumns(
                            visible = visible,
                            library = library,
                            selectedId = selected?.id,
                            mono = mono,
                            onSelect = { id -> selectedId = id; mode = PanelMode.Run },
                        )
                    }
                }
            }
            val editMode = mode as? PanelMode.Edit
            val editing = editMode?.let { manager.find(it.id) }
            when {
                mode is PanelMode.New || editing != null -> {
                    VLine(Skerry.colors.line)
                    Box(Modifier.width(SNIPPET_PANEL_WIDTH).fillMaxHeight().background(Skerry.colors.surface2)) {
                        // Keyed by the edited snippet's identity so the form resets instead of
                        // carrying over the previous values.
                        key(editing?.id, mode) {
                            SnippetEditor(
                                entry = editing,
                                manager = manager,
                                mono = mono,
                                onSaved = { id -> selectedId = id; mode = PanelMode.Run },
                                onCancel = { mode = PanelMode.Run },
                            )
                        }
                    }
                }
                selected != null -> {
                    VLine(Skerry.colors.line)
                    key(selected.id) {
                        SnippetRunPanel(
                            entry = selected,
                            targets = targets,
                            activeTargetId = sessions?.active?.focusedPaneId,
                            mono = mono,
                            onRun = { target, params -> runSnippetInSession(manager, sessions, selected.id, target.id, params) },
                            onCopy = { copyTextToClipboard(it) },
                            onEdit = { mode = PanelMode.Edit(selected.id) },
                            onConvert = { showConvert = true },
                            onDelete = { manager.delete(selected.id); selectedId = null },
                        )
                    }
                }
            }
        }
    }
    if (showHelp) {
        HelpDialog(
            title = snippetHelpTitle(),
            sections = snippetHelpSections(),
            examples = snippetHelpExamples(),
            onDismiss = { showHelp = false },
        )
    }
    val runbookManager = LocalRunbooks.current
    if (showConvert && selected != null && runbookManager != null) {
        ConvertDialog(
            title = stringResource(Res.string.convert_to_runbook),
            initialName = selected.snippet.label,
            nameLabel = stringResource(Res.string.convert_name_label),
            confirmLabel = stringResource(Res.string.convert_confirm),
            nameConflict = { name -> runbookManager.runbooks.any { it.runbook.label == name } },
            conflictMessage = stringResource(Res.string.convert_name_conflict_runbook),
            onConfirm = { name ->
                val converted = RunbookConverter.snippetToRunbook(selected.snippet.copy(label = name))
                runbookManager.save(
                    RunbookDraft(
                        label = converted.label,
                        steps = converted.steps,
                        tags = converted.tags,
                    )
                )
                showConvert = false
            },
            onDismiss = { showConvert = false },
        )
    }
    when (val state = importState) {
        is SnippetImportUiState.Pending -> {
            val plan = planTransfer(state.file.snippets, snippets, importMode) { it.id }
            TransferImportDialog(
                title = stringResource(Res.string.transfer_dialog_import_snippets),
                fileName = state.fileName,
                plan = plan,
                mode = importMode,
                onModeChange = { importMode = it },
                onConfirm = {
                    importSnippetLibrary(manager, state.file, importMode)
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
private fun SnippetsHeader(
    facts: SnippetLibraryFacts,
    query: String,
    onQuery: (String) -> Unit,
    onNew: () -> Unit,
    onHelp: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val commands = pluralStringResource(Res.plurals.lib_snippets_command_count, facts.total, facts.total)
    SectionHeader(
        title = stringResource(Res.string.lib_snippets_screen_title),
        // Two numbers, because they answer different questions: how much is saved, and how much of
        // it stops to ask for something before running.
        subtitle = if (facts.withVariables > 0) {
            "$commands · ${stringResource(Res.string.lib_snippets_facts_variables, facts.withVariables)}"
        } else {
            commands
        },
        help = onHelp,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SidebarSearchField(query, onQuery, stringResource(Res.string.lib_snippets_search), Modifier.width(SEARCH_WIDTH))
                PrimaryButton(stringResource(Res.string.lib_snippets_new), onClick = onNew, icon = "add", modifier = Modifier.testTag(UiTags.NEW_SNIPPET))
                GhostButton(stringResource(Res.string.transfer_export), onClick = onExport, icon = "download")
                GhostButton(stringResource(Res.string.transfer_import), onClick = onImport, icon = "upload")
            }
        },
    )
}

@Composable
private fun SnippetsEmptyList(libraryEmpty: Boolean, onInstallStarterPack: () -> Unit) {
    EmptyState(
        icon = "code_blocks",
        title = if (libraryEmpty) stringResource(Res.string.lib_snippets_empty) else stringResource(Res.string.lib_snippets_no_matches),
        subtitle = if (libraryEmpty) stringResource(Res.string.lib_snippets_select_or_create) else null,
        action = if (libraryEmpty) {
            {
                ChipButton(
                    label = stringResource(Res.string.lib_snippets_starter_pack),
                    color = Skerry.colors.cyan,
                    onClick = onInstallStarterPack,
                )
            }
        } else {
            null
        },
    )
}

/**
 * Snippet list of the library: flat rows, or collapsible category sections when the "All" view has
 * tags to group by ([shouldGroupSnippets]). A collapsed section shows only its header. Selection
 * works across sections — the selected row is highlighted wherever it appears.
 */
@Composable
private fun SnippetLibraryColumns(
    visible: List<SnippetEntry>,
    library: SnippetLibraryState,
    selectedId: String?,
    mono: FontFamily,
    onSelect: (String) -> Unit,
) {
    if (!shouldGroupSnippets(visible, library.activeChip)) {
        FlatSnippetRows(visible, selectedId, mono, onSelect)
        return
    }
    groupSnippetsByCategory(visible).forEach { category ->
        val collapsed = library.isTagCollapsed(category.name)
        SnippetCategoryHeader(
            category = category.name,
            count = category.snippets.size,
            collapsed = collapsed,
            onToggle = { library.toggleTagCollapsed(category.name) },
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp),
        )
        if (!collapsed) FlatSnippetRows(category.snippets, selectedId, mono, onSelect)
    }
}

/** One row per entry with a divider between them, keyed by id so selection keeps its lambda. */
@Composable
private fun FlatSnippetRows(
    entries: List<SnippetEntry>,
    selectedId: String?,
    mono: FontFamily,
    onSelect: (String) -> Unit,
) {
    entries.forEach { entry ->
        key(entry.id) {
            val onClick = remember(entry.id) { { onSelect(entry.id) } }
            SnippetListRow(entry = entry, selected = entry.id == selectedId, mono = mono, onClick = onClick)
            HLine()
        }
    }
}

/**
 * Sessions a snippet can run into: connected terminal panes across every tab. A remote desktop, a
 * recording player and a pane that is still connecting have no shell to take the line, so they never
 * become targets.
 */
@Composable
private fun snippetRunTargets(sessions: SessionsController?): List<SnippetRunTarget> {
    if (sessions == null) return emptyList()
    return sessions.tabs.flatMap { tab -> tab.panes }
        .filter { pane -> !pane.isVnc && !pane.isPlayer && pane.controller.uiState is ConnectionUiState.Connected }
        .map { pane -> SnippetRunTarget(id = pane.id, label = pane.displayTitle) }
}

/**
 * Run [snippetId] in the pane [targetId]. The terminal is looked up at click time, so a session
 * closed between the frame that enabled Run and the click cannot be sent into; `false` says so, and
 * the panel reports it rather than leaving a dropped run looking like a command with no output. A
 * snippet with variables parks in [SnippetManager.pendingRun] and reaches the terminal only after
 * the confirmation dialog.
 */
private fun runSnippetInSession(
    manager: SnippetManager,
    sessions: SessionsController?,
    snippetId: String,
    targetId: String,
    params: Map<String, String>,
): Boolean {
    val pane = sessions?.tabs?.flatMap { it.panes }?.firstOrNull { it.id == targetId } ?: return false
    val terminal = (pane.controller.uiState as? ConnectionUiState.Connected)?.terminal ?: return false
    manager.run(snippetId, recording = terminal.recording, params = params) { text -> terminal.sendUserInputGuarded(text) }
    return true
}
