package app.skerry.ui.runbook

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * View state of the runbook library (search, category chip, collapsed sections) — the runbook
 * mirror of [app.skerry.ui.snippet.SnippetLibraryState], living on the design state so leaving the
 * section and coming back doesn't reset the view. Collapsed categories are persisted across
 * restarts (see the desktop prefs / Android filesDir wiring in the entry points) and are inherited
 * by the ephemeral run panels, whose toggles write back to the same store.
 */
@Stable
class RunbookLibraryState(
    initialCollapsedTags: Set<String> = emptySet(),
    val onCollapsedTagsChange: (Set<String>) -> Unit = {},
) {
    var query: String by mutableStateOf("")
    var activeChip: String? by mutableStateOf(null)
    var collapsedTags: Set<String> by mutableStateOf(initialCollapsedTags); private set

    /** The entries after the active chip and search query are applied (see [filterRunbooks]). */
    fun visible(all: List<RunbookEntry>): List<RunbookEntry> =
        filterRunbooks(all, effectiveChip(all) ?: ALL_RUNBOOKS_CHIP, query)

    /** Filter chips: `All`, then every category in rendering order. */
    fun chips(all: List<RunbookEntry>): List<String> = runbookCategoryChips(all)

    /** The active chip, or null when it no longer exists (its tag was deleted). */
    fun effectiveChip(all: List<RunbookEntry>): String? = activeChip?.takeIf { it in chips(all) }

    fun isTagCollapsed(tag: String): Boolean = tag in collapsedTags

    /** Collapses/expands a section and persists the set through [onCollapsedTagsChange]. */
    fun toggleTagCollapsed(tag: String) {
        collapsedTags = if (tag in collapsedTags) collapsedTags - tag else collapsedTags + tag
        onCollapsedTagsChange(collapsedTags)
    }
}
