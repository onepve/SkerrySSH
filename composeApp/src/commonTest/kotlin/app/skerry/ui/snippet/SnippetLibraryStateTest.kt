package app.skerry.ui.snippet

import app.skerry.shared.snippet.Snippet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnippetLibraryStateTest {

    private fun entry(label: String, tags: List<String> = emptyList()) =
        SnippetEntry(Snippet(id = label, label = label, command = "cmd $label", tags = tags))

    private val all = listOf(
        entry("Disk", listOf("disk")),
        entry("Ports", listOf("net")),
        entry("Loose"),
    )

    @Test
    fun starts_unfiltered() {
        val s = SnippetLibraryState()

        assertEquals(ALL_SNIPPETS_CHIP, s.activeChip)
        assertEquals(3, s.visible(all).size)
    }

    @Test
    fun chip_and_query_narrow_the_list() {
        val s = SnippetLibraryState()
        s.activeChip = "disk"
        assertEquals(listOf("Disk"), s.visible(all).map { it.snippet.label })

        s.activeChip = ALL_SNIPPETS_CHIP
        s.query = "ports"
        assertEquals(listOf("Ports"), s.visible(all).map { it.snippet.label })
    }

    @Test
    fun a_chip_whose_category_disappeared_behaves_like_all() {
        val s = SnippetLibraryState()
        s.activeChip = "docker" // last #docker snippet has just been deleted

        assertEquals(3, s.visible(all).size)
    }

    @Test
    fun a_snippet_carrying_several_tags_is_listed_once_per_chip() {
        val multi = listOf(
            entry("Deploy", listOf("prod", "db")),
            entry("Dump", listOf("db")),
        )
        val s = SnippetLibraryState()
        s.activeChip = "db"

        // The list is flat: a snippet with two tags appears once, not once per tag.
        assertEquals(listOf("Deploy", "Dump"), s.visible(multi).map { it.snippet.label })
    }

    @Test
    fun a_rename_moves_the_active_chip_instead_of_falling_back_to_all() {
        val s = SnippetLibraryState()
        s.activeChip = "db"

        s.onTagRenamed("db", "database")

        assertEquals("database", s.activeChip)
    }

    @Test
    fun a_rename_leaves_an_unrelated_chip_untouched() {
        val s = SnippetLibraryState()
        s.activeChip = "net"

        s.onTagRenamed("db", "database") // "db" was not the active chip

        assertEquals("net", s.activeChip)
    }

    @Test
    fun collapsed_categories_toggle_off_and_on() {
        val s = SnippetLibraryState()

        assertFalse(s.isTagCollapsed("disk"))

        s.toggleTagCollapsed("disk")
        assertTrue(s.isTagCollapsed("disk"))

        s.toggleTagCollapsed("disk")
        assertFalse(s.isTagCollapsed("disk"))
    }

    @Test
    fun collapsing_one_category_leaves_the_others_open() {
        val s = SnippetLibraryState()
        s.toggleTagCollapsed("disk")

        assertTrue(s.isTagCollapsed("disk"))
        assertFalse(s.isTagCollapsed("net"))
    }

    @Test
    fun a_rename_moves_the_collapsed_state() {
        val s = SnippetLibraryState()
        s.toggleTagCollapsed("db")

        s.onTagRenamed("db", "database")

        assertTrue(s.isTagCollapsed("database"))
        assertFalse(s.isTagCollapsed("db"))
    }

    @Test
    fun collapsed_state_starts_from_persistence() {
        val s = SnippetLibraryState(initialCollapsedTags = setOf("disk"))

        assertTrue(s.isTagCollapsed("disk"))
        assertFalse(s.isTagCollapsed("net"))
    }

    @Test
    fun toggling_reports_the_new_set_to_persistence() {
        val persisted = mutableListOf<Set<String>>()
        val s = SnippetLibraryState(onCollapsedTagsChange = { persisted += it })

        s.toggleTagCollapsed("disk")
        assertEquals(setOf("disk"), persisted.last())

        s.toggleTagCollapsed("disk")
        assertEquals(emptySet<String>(), persisted.last())
        assertEquals(2, persisted.size)
    }

    @Test
    fun a_rename_reports_the_migrated_set_to_persistence() {
        val persisted = mutableListOf<Set<String>>()
        val s = SnippetLibraryState(initialCollapsedTags = setOf("db"), onCollapsedTagsChange = { persisted += it })

        s.onTagRenamed("db", "database")

        assertEquals(setOf("database"), persisted.last())
    }
}
