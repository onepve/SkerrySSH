package app.skerry.shared.transfer

/**
 * How an imported transfer file is applied to the local library.
 */
enum class TransferMode {
    /** Upsert by id: entries in the file update or create, local-only entries are kept. */
    MERGE,

    /** The file is the truth: local entries are cleared first, then the file is written verbatim. */
    REPLACE,
}

/**
 * What an import would do, computed before anything is touched so the confirmation dialog can show
 * it: how many entries the file adds, updates, and how many local ones disappear.
 */
data class TransferPlan(
    /** Entries present in the file but not locally — will be created. */
    val additions: Int,
    /** Entries present in both — will be overwritten by the file's version. */
    val updates: Int,
    /** Local entries not present in the file. MERGE keeps them; REPLACE removes them. */
    val localOnly: Int,
    /** Total entries that will exist after the import (merge) or exactly the file's count (replace). */
    val resultingTotal: Int,
    val mode: TransferMode,
) {
    /**
     * Nothing would change: the file carries no entries that write anything. A REPLACE with an empty
     * file is NOT a no-op — it wipes the library — hence the mode check.
     */
    val isNoOp: Boolean
        get() = additions == 0 && updates == 0 && (mode == TransferMode.MERGE || localOnly == 0)
}

/** Pure planning for both snippet and runbook imports — no storage, no side effects. */
fun <T> planTransfer(
    incoming: List<T>,
    local: List<T>,
    mode: TransferMode,
    idOf: (T) -> String,
): TransferPlan {
    val incomingIds = incoming.map(idOf).toSet()
    val localIds = local.map(idOf).toSet()
    val additions = incoming.count { idOf(it) !in localIds }
    val updates = incoming.count { idOf(it) in localIds }
    val localOnly = (localIds - incomingIds).size
    val resultingTotal = if (mode == TransferMode.REPLACE) incoming.size else localIds.size + additions
    return TransferPlan(additions, updates, localOnly, resultingTotal, mode)
}
