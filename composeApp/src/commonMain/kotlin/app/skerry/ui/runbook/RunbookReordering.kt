package app.skerry.ui.runbook

import app.skerry.shared.runbook.Runbook
import app.skerry.shared.text.normalizeGroup

/**
 * Move runbook [runbookId] into group [targetGroup] at [targetIndexInGroup] among its runbooks.
 * Covers both drag scenarios: reordering within a folder ([targetGroup] == current group) and
 * moving to another (rewriting [Runbook.group]).
 */
fun moveRunbookToGroup(
    runbooks: List<Runbook>,
    runbookId: String,
    targetGroup: String?,
    targetIndexInGroup: Int,
): List<Runbook> {
    val moving = runbooks.firstOrNull { it.id == runbookId } ?: return runbooks
    val canonicalTarget = normalizeGroup(targetGroup)
    val buckets = LinkedHashMap<String?, MutableList<Runbook>>()
    for (r in runbooks) {
        if (r.id == runbookId) continue
        buckets.getOrPut(normalizeGroup(r.group)) { mutableListOf() }.add(r)
    }
    val target = buckets.getOrPut(canonicalTarget) { mutableListOf() }
    target.add(targetIndexInGroup.coerceIn(0, target.size), moving.copy(group = canonicalTarget))
    return buckets.values.flatten()
}

/**
 * Rename group [oldName] to [newName] across all runbooks.
 * Blank/null [newName] ungroups the runbooks (`Runbook.group = null`), which is also how a group is deleted.
 */
fun renameRunbookGroup(
    runbooks: List<Runbook>,
    oldName: String?,
    newName: String?,
): List<Runbook> {
    val canonicalOld = normalizeGroup(oldName) ?: return runbooks
    val canonicalNew = normalizeGroup(newName)
    if (canonicalOld == canonicalNew) return runbooks
    val buckets = LinkedHashMap<String?, MutableList<Runbook>>()
    for (r in runbooks) {
        val group = normalizeGroup(r.group)
        val updatedGroup = if (group == canonicalOld) canonicalNew else group
        val updated = if (group == canonicalOld) r.copy(group = canonicalNew) else r
        buckets.getOrPut(updatedGroup) { mutableListOf() }.add(updated)
    }
    return buckets.values.flatten()
}
