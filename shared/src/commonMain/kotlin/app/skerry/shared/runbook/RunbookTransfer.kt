package app.skerry.shared.runbook

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable, plain-text form of the whole runbook library (see [app.skerry.shared.snippet.SnippetTransferFile]
 * for the shared design rationale). Steps carry their `kind` discriminator exactly as the vault
 * stores them, so a transferred runbook decodes to the same steps it ran with.
 */
@Serializable
data class RunbookTransferFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val runbooks: List<Runbook> = emptyList(),
) {
    companion object {
        const val FORMAT = "skerry-runbooks"
        const val VERSION = 1
    }
}

sealed interface RunbookTransferResult {
    data class Ok(val file: RunbookTransferFile) : RunbookTransferResult

    /** Not a runbook transfer file — anything from a wrong app's JSON to a typo'd filename. */
    data object Corrupted : RunbookTransferResult
}

private val transferJson = Json { ignoreUnknownKeys = true }

/** Human-readable JSON for a runbook transfer file — the whole point is editing it by hand between export and import. */
private val exportJson = Json { prettyPrint = true; prettyPrintIndent = "    " }

/** Parses [text] as a runbook transfer file; [RunbookTransferResult.Corrupted] on any mismatch. */
fun parseRunbookTransfer(text: String): RunbookTransferResult = try {
    val file = transferJson.decodeFromString<RunbookTransferFile>(text)
    if (file.format == RunbookTransferFile.FORMAT) RunbookTransferResult.Ok(file) else RunbookTransferResult.Corrupted
} catch (_: Exception) {
    RunbookTransferResult.Corrupted
}

/** Serializes [file] as pretty-printed JSON for the export file. */
fun encodeRunbookTransfer(file: RunbookTransferFile): String = exportJson.encodeToString(file)
