package app.skerry.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.app.LocalRunbookHistory
import app.skerry.ui.app.LocalRunbookRunner
import app.skerry.ui.app.LocalRunbooks
import app.skerry.ui.app.LocalSessions
import app.skerry.ui.app.MobileDesignState
import app.skerry.ui.app.MobileRoute
import app.skerry.ui.connection.ConnectionUiState
import app.skerry.ui.design.FilterChipRow
import app.skerry.ui.design.HelpDialog
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.Sym
import app.skerry.ui.design.Txt
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.runbook_delete
import app.skerry.ui.generated.resources.runbook_empty_mobile
import app.skerry.ui.generated.resources.runbook_new
import app.skerry.ui.generated.resources.runbook_run
import app.skerry.ui.generated.resources.runbook_run_busy
import app.skerry.ui.generated.resources.runbook_run_needs_session
import app.skerry.ui.generated.resources.runbook_save
import app.skerry.ui.generated.resources.runbook_section
import app.skerry.ui.generated.resources.runbook_step_count
import app.skerry.ui.generated.resources.runbook_untitled
import app.skerry.ui.generated.resources.convert_confirm
import app.skerry.ui.generated.resources.convert_name_conflict_snippet
import app.skerry.ui.generated.resources.convert_name_label
import app.skerry.ui.generated.resources.convert_skipped_transfer
import app.skerry.ui.generated.resources.convert_to_snippet
import app.skerry.ui.runbook.ALL_RUNBOOKS_CHIP
import app.skerry.ui.convert.ConvertDialog
import app.skerry.shared.runbook.RunbookConverter
import app.skerry.ui.snippet.SnippetDraft
import app.skerry.ui.runbook.RunbookEditorFields
import app.skerry.ui.runbook.RunbookEntry
import app.skerry.ui.runbook.RunbookFormState
import app.skerry.ui.runbook.RunbookManager
import app.skerry.ui.app.LocalSnippets
import app.skerry.ui.runbook.groupRunbooksByCategory
import app.skerry.ui.runbook.hasRunbookCategories
import app.skerry.ui.runbook.runbookChipLabel
import app.skerry.ui.runbook.runbookHelpExamples
import app.skerry.ui.runbook.runbookHelpSections
import app.skerry.ui.runbook.runbookHelpTitle
import app.skerry.ui.runbook.runbookTarget
import app.skerry.ui.runbook.runbookExampleTemplatesByKey
import app.skerry.ui.runbook.shouldGroupRunbooks
import app.skerry.ui.snippet.SnippetCategoryHeader
import app.skerry.ui.theme.Skerry
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.platform.testTag
import app.skerry.ui.app.UiTags

/**
 * Runbooks screen (More → Runbooks): the saved procedures plus an add FAB. Tapping a card opens the
 * edit sheet; Run starts the procedure in the active session and jumps to the terminal, where the
 * start confirmation and the run panel take over. Parity with the desktop section — same form
 * state, same runner.
 */
@Composable
fun MobileRunbooksScreen(state: MobileDesignState) {
    val manager = LocalRunbooks.current
    if (manager == null) {
        // Mock/preview path (no vault behind the library): still a real push screen, so the back
        // arrow exists and the user isn't trapped on a blank one.
        Column(Modifier.fillMaxSize().background(Skerry.colors.bg)) {
            MobilePushHeader(stringResource(Res.string.runbook_section), onBack = state::pop)
            Txt(
                stringResource(Res.string.runbook_empty_mobile), color = Skerry.colors.faint, size = 13.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 30.dp),
            )
        }
        return
    }
    val mono = LocalFonts.current.mono
    val runner = LocalRunbookRunner.current
    val sessions = LocalSessions.current
    val session = sessions?.activeTerminal?.focusedPane
    val terminal = (session?.controller?.uiState as? ConnectionUiState.Connected)?.terminal

    var editing by remember { mutableStateOf<RunbookEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var convertTarget by remember { mutableStateOf<RunbookEntry?>(null) }
    val sheetOpen = adding || editing != null

    // An open sheet hides the tab bar, which would otherwise float over the fields above the keyboard.
    LaunchedEffect(sheetOpen) { state.modalOverlay(sheetOpen) }
    DisposableEffect(Unit) { onDispose { state.modalOverlay(false) } }

    val runbooks = manager.runbooks

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Skerry.colors.bg).verticalScroll(rememberScrollState())) {
            MobilePushHeader(
                stringResource(Res.string.runbook_section),
                onBack = state::pop,
                onHelp = { showHelp = true },
            )
            if (runbooks.isEmpty()) {
                Txt(
                    stringResource(Res.string.runbook_empty_mobile), color = Skerry.colors.faint, size = 13.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 30.dp),
                )
            } else {
                val library = state.runbookLibrary
                val shown = library.visible(runbooks)
                if (hasRunbookCategories(runbooks)) {
                    FilterChipRow(
                        chips = library.chips(runbooks),
                        activeChip = library.effectiveChip(runbooks) ?: ALL_RUNBOOKS_CHIP,
                        onSelect = { library.activeChip = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        label = { runbookChipLabel(it) },
                    )
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (shouldGroupRunbooks(shown, library.effectiveChip(runbooks) ?: ALL_RUNBOOKS_CHIP)) {
                        groupRunbooksByCategory(shown).forEach { category ->
                            key(category.name) {
                                SnippetCategoryHeader(
                                    category = category.name,
                                    count = category.runbooks.size,
                                    collapsed = library.isTagCollapsed(category.name),
                                    onToggle = { library.toggleTagCollapsed(category.name) },
                                )
                                if (!library.isTagCollapsed(category.name)) {
                                    category.runbooks.forEach { entry ->
                                        key(entry.id) { RunbookCard(entry, mono) { editing = entry; adding = false } }
                                    }
                                }
                            }
                        }
                    } else {
                        shown.forEach { entry ->
                            key(entry.id) { RunbookCard(entry, mono) { editing = entry; adding = false } }
                        }
                    }
                }
            }
            // Clears the tab bar and the FAB above it, so the last card can scroll out from under "+".
            Spacer(Modifier.height(176.dp))
        }

        if (!sheetOpen) {
            MobileFabButton(
                onClick = { adding = true; editing = null },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 104.dp).testTag(UiTags.NEW_RUNBOOK),
            )
        }

        if (sheetOpen) {
            val target = editing
            MobileRunbookEditSheet(
                entry = target,
                manager = manager,
                mono = mono,
                runHint = when {
                    target == null -> null
                    terminal == null -> stringResource(Res.string.runbook_run_needs_session)
                    runner == null || runner.active || runner.pending != null ->
                        stringResource(Res.string.runbook_run_busy)
                    else -> null
                },
                onDismiss = { adding = false; editing = null },
                onSaved = { adding = false; editing = null },
                onDeleted = { adding = false; editing = null },
                onConvert = target?.let { e -> { convertTarget = e; adding = false; editing = null } },
                onRun = run@{
                    val entry = target ?: return@run
                    if (runner == null || session == null || terminal == null) return@run
                    val started = runner.requestStart(
                        entry.runbook,
                        runbookTarget(session.id, terminal, session.controller),
                        recording = terminal.recording,
                    )
                    adding = false; editing = null
                    // The confirmation dialog and the progress panel both live over the terminal.
                    if (started) state.push(MobileRoute.Terminal)
                },
            )
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

        // Convert to snippet: same shared dialog as desktop, opened from the edit sheet.
        val snippetManager = LocalSnippets.current
        convertTarget?.let { target ->
            if (snippetManager != null) {
                val (converted, skipped) = remember(target.id) { RunbookConverter.runbookToSnippet(target.runbook) }
                ConvertDialog(
                    title = stringResource(Res.string.convert_to_snippet),
                    initialName = target.runbook.label,
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
                        convertTarget = null
                    },
                    onDismiss = { convertTarget = null },
                )
            }
        }
    }
}

@Composable
private fun RunbookCard(entry: RunbookEntry, mono: FontFamily, onClick: () -> Unit) {
    val runbook = entry.runbook
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Skerry.colors.card)
            .border(1.dp, Skerry.colors.cyan08, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sym("checklist", size = 17.sp, color = Skerry.colors.cyanBright)
            Txt(
                runbook.label.ifBlank { stringResource(Res.string.runbook_untitled) },
                color = Skerry.colors.textBright, size = 14.sp, weight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Txt(
            stringResource(Res.string.runbook_step_count, runbook.steps.size),
            color = Skerry.colors.faint, size = 11.sp, font = mono,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (runbook.description.isNotBlank()) {
            Txt(
                runbook.description, color = Skerry.colors.dim, size = 12.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Runbook create/edit sheet. [entry] == null means create; a non-null [runHint] disables Run. */
@Composable
private fun MobileRunbookEditSheet(
    entry: RunbookEntry?,
    manager: RunbookManager,
    mono: FontFamily,
    runHint: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onRun: () -> Unit,
    onConvert: (() -> Unit)? = null,
) {
    // Shared form state (desktop <-> mobile): same fields, same validation, same draft assembly.
    val form = remember(entry) { RunbookFormState.fromEntry(entry) }
    val history = LocalRunbookHistory.current

    MobileBottomSheet(onDismiss = onDismiss, maxHeightFraction = 0.92f) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Txt(
                if (entry == null) stringResource(Res.string.runbook_new) else stringResource(Res.string.runbook_section),
                color = Skerry.colors.text, size = 18.sp, weight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            RunbookEditorFields(form, mono, horizontalPadding = 18.dp)
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entry != null) {
                    MobileSheetButton(
                        stringResource(Res.string.runbook_run), onClick = { if (runHint == null) onRun() },
                        icon = "play_arrow", filled = false, modifier = Modifier.fillMaxWidth(),
                    )
                    if (runHint != null) Txt(runHint, color = Skerry.colors.faint, size = 11.sp)
                }
                if (onConvert != null) {
                    MobileSheetButton(
                        stringResource(Res.string.convert_to_snippet), onClick = onConvert,
                        icon = "swap_horiz", filled = false, modifier = Modifier.fillMaxWidth(),
                    )
                }
                MobileSheetButton(
                    stringResource(Res.string.runbook_save),
                    onClick = { if (form.canSave) { manager.save(form.toDraft()); onSaved() } },
                    modifier = Modifier.fillMaxWidth().testTag(UiTags.FORM_SAVE),
                )
                if (entry != null) {
                    MobileSheetButton(
                        stringResource(Res.string.runbook_delete),
                        onClick = {
                            manager.delete(entry.id)
                            // The runbook is gone; its run log has nothing left to belong to.
                            history?.forget(entry.id)
                            onDeleted()
                        },
                        filled = false, danger = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
