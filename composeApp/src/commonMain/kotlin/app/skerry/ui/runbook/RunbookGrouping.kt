package app.skerry.ui.runbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import app.skerry.ui.design.folderNames
import app.skerry.ui.design.tagChipLabel
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.lib_snippets_chip_all
import app.skerry.ui.snippet.UNCATEGORIZED_KEY
import app.skerry.ui.snippet.uncategorizedSnippetsLabel
import org.jetbrains.compose.resources.stringResource

/**
 * Names the library's folders in the one collapsed set the app persists
 * ([app.skerry.ui.design.folderCollapseKey]) — a `Production` folder here and a `Production` folder
 * of snippets or hosts fold independently.
 */
const val RUNBOOK_FOLDER_SCOPE = "runbook"

/** Technical key of the "all runbooks" chip at the start of the filter row. */
const val ALL_RUNBOOKS_CHIP = "All"

/** A runbook section: category name plus its runbooks. */
@Immutable
data class RunbookCategory(val name: String, val runbooks: List<RunbookEntry>)

/** Folders the library already uses — what the editor's "Group" select offers. */
fun runbookFolders(runbooks: List<RunbookEntry>): List<String> =
    folderNames(runbooks.map { it.runbook.group })

/** Chip label for display: localized for the two technical keys, `#tag` for a real category. */
@Composable
fun runbookChipLabel(chip: String): String = when (chip) {
    ALL_RUNBOOKS_CHIP -> stringResource(Res.string.lib_snippets_chip_all)
    UNCATEGORIZED_KEY -> uncategorizedSnippetsLabel()
    else -> tagChipLabel(chip)
}

/**
 * Group runbooks into sections by tag. Untagged runbooks land in [UNCATEGORIZED_KEY].
 */
fun groupRunbooksByCategory(runbooks: List<RunbookEntry>): List<RunbookCategory> {
    val buckets = sortedMapOf<String, MutableList<RunbookEntry>>()
    val uncategorized = mutableListOf<RunbookEntry>()
    for (entry in runbooks) {
        val tags = entry.runbook.tags
        if (tags.isEmpty()) uncategorized += entry
        else for (tag in tags) buckets.getOrPut(tag) { mutableListOf() }.add(entry)
    }
    return buildList {
        buckets.forEach { (name, list) -> add(RunbookCategory(name, list)) }
        if (uncategorized.isNotEmpty()) add(RunbookCategory(UNCATEGORIZED_KEY, uncategorized))
    }
}

/**
 * Whether anything is tagged at all in the runbooks.
 */
fun hasRunbookCategories(runbooks: List<RunbookEntry>): Boolean = runbooks.any { it.runbook.tags.isNotEmpty() }

/**
 * Filter chips for runbook categories.
 */
fun runbookCategoryChips(runbooks: List<RunbookEntry>): List<String> =
    listOf(ALL_RUNBOOKS_CHIP) + groupRunbooksByCategory(runbooks).map { it.name }

/**
 * Filter runbooks by active chip and search query.
 */
fun filterRunbooks(
    runbooks: List<RunbookEntry>,
    activeChip: String = ALL_RUNBOOKS_CHIP,
    query: String = "",
): List<RunbookEntry> {
    val q = query.trim().lowercase()
    return runbooks.filter { entry ->
        val matchesQuery = q.isEmpty() || entry.matches(q)
        val matchesChip = when (activeChip) {
            ALL_RUNBOOKS_CHIP -> true
            UNCATEGORIZED_KEY -> entry.runbook.tags.isEmpty()
            else -> entry.runbook.tags.any { it.equals(activeChip, ignoreCase = true) }
        }
        matchesQuery && matchesChip
    }
}
