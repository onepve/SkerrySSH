package app.skerry.ui.snippet

import app.skerry.shared.snippet.Snippet
import app.skerry.shared.text.normalizeGroup

/**
 * Move snippet [snippetId] into group [targetGroup] at [targetIndexInGroup] among its snippets.
 * Covers both drag scenarios: reordering within a folder ([targetGroup] == current group) and
 * moving to another (rewriting [Snippet.group]).
 */
fun moveSnippetToGroup(
    snippets: List<Snippet>,
    snippetId: String,
    targetGroup: String?,
    targetIndexInGroup: Int,
): List<Snippet> {
    val moving = snippets.firstOrNull { it.id == snippetId } ?: return snippets
    val canonicalTarget = normalizeGroup(targetGroup)
    val buckets = LinkedHashMap<String?, MutableList<Snippet>>()
    for (s in snippets) {
        if (s.id == snippetId) continue
        buckets.getOrPut(normalizeGroup(s.group)) { mutableListOf() }.add(s)
    }
    val target = buckets.getOrPut(canonicalTarget) { mutableListOf() }
    target.add(targetIndexInGroup.coerceIn(0, target.size), moving.copy(group = canonicalTarget))
    return buckets.values.flatten()
}

/**
 * Rename group [oldName] to [newName] across all snippets.
 * Blank/null [newName] ungroups the snippets (`Snippet.group = null`), which is also how a group is deleted.
 */
fun renameSnippetGroup(
    snippets: List<Snippet>,
    oldName: String?,
    newName: String?,
): List<Snippet> {
    val canonicalOld = normalizeGroup(oldName) ?: return snippets
    val canonicalNew = normalizeGroup(newName)
    if (canonicalOld == canonicalNew) return snippets
    val buckets = LinkedHashMap<String?, MutableList<Snippet>>()
    for (s in snippets) {
        val group = normalizeGroup(s.group)
        val updatedGroup = if (group == canonicalOld) canonicalNew else group
        val updated = if (group == canonicalOld) s.copy(group = canonicalNew) else s
        buckets.getOrPut(updatedGroup) { mutableListOf() }.add(updated)
    }
    return buckets.values.flatten()
}
