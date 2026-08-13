package app.skerry.ui.runbook

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.lib_snippets_chip_all
import app.skerry.ui.generated.resources.lib_snippets_uncategorized
import org.jetbrains.compose.resources.stringResource

/** A runbook library section: category name plus its runbooks (in source list order). */
@Immutable
data class RunbookCategory(val name: String, val runbooks: List<RunbookEntry>)

/**
 * Technical key for the synthetic bucket holding runbooks without tags — same contract as
 * [UNCATEGORIZED_KEY]: used as the grouping key and the chip value, never localized.
 */
const val UNCATEGORIZED_RUNBOOK_KEY = "Uncategorized"

/** Technical key of the "all runbooks" chip at the start of the library filter row. */
const val ALL_RUNBOOKS_CHIP = "All"

/**
 * Chip label for display: localized for the two technical keys, `#tag` for a real category.
 * Reuses the snippet library's labels — "All"/"Uncategorized" read the same for both sections.
 */
@Composable
fun runbookChipLabel(chip: String): String = when (chip) {
    ALL_RUNBOOKS_CHIP -> stringResource(Res.string.lib_snippets_chip_all)
    UNCATEGORIZED_RUNBOOK_KEY -> stringResource(Res.string.lib_snippets_uncategorized)
    else -> "#$chip"
}

/**
 * Group runbooks into library sections by tag — same model as snippets: a runbook carries several
 * tags and appears in every matching section, sections are ordered alphabetically (tags are
 * canonical, lowercase), untagged runbooks land in the [UNCATEGORIZED_RUNBOOK_KEY] bucket kept
 * last, source order is preserved inside a section. Pure function, shared by desktop and mobile.
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
        if (uncategorized.isNotEmpty()) add(RunbookCategory(UNCATEGORIZED_RUNBOOK_KEY, uncategorized))
    }
}

/** Whether anything is tagged at all (flat list otherwise — chips/headers would be pure chrome). */
fun hasRunbookCategories(runbooks: List<RunbookEntry>): Boolean = runbooks.any { it.runbook.tags.isNotEmpty() }

/**
 * Whether the library should render as collapsible category sections: the "All" view and at least
 * one tag among the visible runbooks (same rule as the snippet library).
 */
fun shouldGroupRunbooks(visible: List<RunbookEntry>, activeChip: String): Boolean =
    activeChip == ALL_RUNBOOKS_CHIP && hasRunbookCategories(visible)

/** Filter chips: `All`, then the categories in the same order [groupRunbooksByCategory] renders them. */
fun runbookCategoryChips(runbooks: List<RunbookEntry>): List<String> =
    listOf(ALL_RUNBOOKS_CHIP) + groupRunbooksByCategory(runbooks).map { it.name }

/**
 * Narrow [runbooks] by the active chip ([activeChip] = category, `All` = no filter) and [query]
 * (AND). Search is case-insensitive across label/description/tags/steps (see [RunbookEntry.matches]).
 */
fun filterRunbooks(
    runbooks: List<RunbookEntry>,
    activeChip: String = ALL_RUNBOOKS_CHIP,
    query: String = "",
): List<RunbookEntry> = runbooks.filter { entry ->
    val chipOk = when (activeChip) {
        ALL_RUNBOOKS_CHIP -> true
        UNCATEGORIZED_RUNBOOK_KEY -> entry.runbook.tags.isEmpty()
        else -> activeChip in entry.runbook.tags
    }
    chipOk && (query.isBlank() || entry.matches(query))
}
