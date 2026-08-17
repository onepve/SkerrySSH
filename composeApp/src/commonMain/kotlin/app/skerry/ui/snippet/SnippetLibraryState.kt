package app.skerry.ui.snippet

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * View state of the snippet library — search text, the active tag chip, and collapsed categories.
 * The "All" view renders as collapsible category sections; a chip narrows the list to one
 * category. Shared by the desktop and mobile screens (only the layout differs), and held on the
 * app-level design state so switching to the terminal and back doesn't reset it.
 */
@Stable
class SnippetLibraryState(
    /**
     * Collapsed categories read from persistence at startup (all expanded by default). Defaults
     * preserve prior behavior for previews/tests.
     */
    initialCollapsedTags: Set<String> = emptySet(),
    /**
     * Writes the collapsed set back to persistence, so the state survives a restart — same
     * contract as the host folders' [DesktopDesignState.collapsedGroups]. Defaults to no-op.
     * Public so the run panels (snippet palette / mobile run sheet) can inherit the library's
     * collapsed set and write their toggles back through the same persisted store.
     */
    val onCollapsedTagsChange: (Set<String>) -> Unit = {},
) {

    var query: String by mutableStateOf("")

    /** Active tag chip: [ALL_SNIPPETS_CHIP], [UNCATEGORIZED_KEY], or a tag. */
    var activeChip: String by mutableStateOf(ALL_SNIPPETS_CHIP)

    /**
     * Collapsed category names in the "All" view (their snippet lists are hidden). Keys are the
     * same technical category names [groupSnippetsByCategory] returns, so renaming a tag migrates
     * the state ([onTagRenamed]) instead of leaving a stale key behind.
     */
    var collapsedTags: Set<String> by mutableStateOf(initialCollapsedTags); private set

    /** Whether the [tag] category section is collapsed. */
    fun isTagCollapsed(tag: String): Boolean = tag in collapsedTags

    /** Toggle whether the [tag] category section shows its snippets. */
    fun toggleTagCollapsed(tag: String) {
        collapsedTags = if (tag in collapsedTags) collapsedTags - tag else collapsedTags + tag
        onCollapsedTagsChange(collapsedTags)
    }

    /**
     * Keep the active chip on a renamed tag ([SnippetManager.renameTag]) instead of falling back to
     * "all". [newKey] is the canonical target; a merge onto an existing tag just re-points the old key.
     * The collapsed state follows the chip, like host folders do on rename.
     */
    fun onTagRenamed(oldKey: String, newKey: String) {
        if (activeChip == oldKey) activeChip = newKey
        if (oldKey in collapsedTags) {
            collapsedTags = collapsedTags - oldKey + newKey
            onCollapsedTagsChange(collapsedTags)
        }
    }

    /**
     * Snippets to show: [query] AND the active chip. A chip whose tag no longer exists (its last
     * snippet was deleted or re-tagged) falls back to "all" instead of emptying the list.
     */
    fun visible(all: List<SnippetEntry>): List<SnippetEntry> =
        filterSnippets(all, activeChip = effectiveChip(all), query = query)

    /** Chips to render: `All` plus the tags present in [all] (unaffected by the search text). */
    fun chips(all: List<SnippetEntry>): List<String> = snippetCategoryChips(all)

    /** The chip actually in effect — [activeChip] unless its tag is gone. */
    fun effectiveChip(all: List<SnippetEntry>): String =
        if (activeChip in snippetCategoryChips(all)) activeChip else ALL_SNIPPETS_CHIP
}
