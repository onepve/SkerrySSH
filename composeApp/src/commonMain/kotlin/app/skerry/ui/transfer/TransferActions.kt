package app.skerry.ui.transfer

import app.skerry.shared.runbook.RunbookTransferFile
import app.skerry.shared.runbook.encodeRunbookTransfer
import app.skerry.shared.snippet.SnippetTransferFile
import app.skerry.shared.snippet.encodeSnippetTransfer
import app.skerry.shared.transfer.TransferMode
import app.skerry.ui.runbook.RunbookDraft
import app.skerry.ui.runbook.RunbookManager
import app.skerry.ui.snippet.SnippetDraft
import app.skerry.ui.snippet.SnippetManager

/**
 * Whole-library import/export for the snippet and runbook libraries. Serialization lives in shared
 * ([app.skerry.shared.snippet.encodeSnippetTransfer] / [app.skerry.shared.runbook.encodeRunbookTransfer]),
 * planning in [app.skerry.shared.transfer.planTransfer]; this file only bridges them onto the
 * desktop managers.
 */

/** Serializes the whole snippet library for export. */
fun exportSnippetLibrary(manager: SnippetManager): String =
    encodeSnippetTransfer(SnippetTransferFile(snippets = manager.snippets.map { it.snippet }))

/** Serializes the whole runbook library for export. */
fun exportRunbookLibrary(manager: RunbookManager): String =
    encodeRunbookTransfer(RunbookTransferFile(runbooks = manager.runbooks.map { it.runbook }))

/**
 * Applies a parsed transfer file to the snippet library. MERGE upserts by id (file wins); REPLACE
 * clears the library first so the file is the truth. Runs synchronously on the caller's context —
 * a library is a handful of records, far cheaper than a vault backup.
 */
fun importSnippetLibrary(manager: SnippetManager, file: SnippetTransferFile, mode: TransferMode) {
    if (mode == TransferMode.REPLACE) {
        manager.snippets.forEach { manager.delete(it.id) }
    }
    file.snippets.forEach { s ->
        manager.save(
            SnippetDraft(id = s.id, label = s.label, command = s.command, tags = s.tags, shortcut = s.shortcut),
        )
    }
}

/** Applies a parsed transfer file to the runbook library (same semantics as [importSnippetLibrary]). */
fun importRunbookLibrary(manager: RunbookManager, file: RunbookTransferFile, mode: TransferMode) {
    if (mode == TransferMode.REPLACE) {
        manager.runbooks.forEach { manager.delete(it.id) }
    }
    file.runbooks.forEach { r ->
        manager.save(
            RunbookDraft(
                id = r.id,
                label = r.label,
                description = r.description,
                steps = r.steps,
                tags = r.tags,
                policy = r.policy,
                interactive = r.interactive,
            ),
        )
    }
}
