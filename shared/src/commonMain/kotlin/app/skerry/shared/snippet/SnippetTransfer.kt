package app.skerry.shared.snippet

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable, plain-text form of the whole snippet library — the file exchanged for batch edits:
 * export it, edit the JSON by hand, import it back (MERGE to sync changes, REPLACE to make the
 * file the truth). Deliberately the same shape as [Snippet] itself so a diff between two exports
 * reads like the snippet list it is.
 */
@Serializable
data class SnippetTransferFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val snippets: List<Snippet> = emptyList(),
) {
    companion object {
        const val FORMAT = "skerry-snippets"
        const val VERSION = 1
    }
}

sealed interface SnippetTransferResult {
    data class Ok(val file: SnippetTransferFile) : SnippetTransferResult

    /** Not a snippet transfer file — anything from a wrong app's JSON to a typo'd filename. */
    data object Corrupted : SnippetTransferResult
}

private val transferJson = Json { ignoreUnknownKeys = true }

/** Human-readable JSON for a snippet transfer file — the whole point is editing it by hand between export and import. */
private val exportJson = Json { prettyPrint = true; prettyPrintIndent = "    " }

/** Parses [text] as a snippet transfer file; [SnippetTransferResult.Corrupted] on any mismatch. */
fun parseSnippetTransfer(text: String): SnippetTransferResult = try {
    val file = transferJson.decodeFromString<SnippetTransferFile>(text)
    if (file.format == SnippetTransferFile.FORMAT) SnippetTransferResult.Ok(file) else SnippetTransferResult.Corrupted
} catch (_: Exception) {
    SnippetTransferResult.Corrupted
}

/** Serializes [file] as pretty-printed JSON for the export file. */
fun encodeSnippetTransfer(file: SnippetTransferFile): String = exportJson.encodeToString(file)
