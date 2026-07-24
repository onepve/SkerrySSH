package app.skerry.server.db

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.concurrent.TimeUnit

/**
 * Metadata audit log for the admin console. Append-only, with dual retention:
 *   1. Time-based: events older than [retentionDays] are pruned on each write.
 *   2. Row-cap: a hard [maxRows] backstop so the log never grows unbounded.
 *
 * Contains no record content — only event, device, and a human-readable summary ([detail]).
 */
class ActivityRepository(
    private val db: Database,
    private val retentionDays: Int = 30,
    private val maxRows: Int = 10_000,
) {
    private val retentionMillis = retentionDays.toLong() * TimeUnit.DAYS.toMillis(1)

    suspend fun record(
        accountId: String,
        event: String,
        detail: String,
        deviceId: String? = null,
        teamId: String? = null,
        now: Long = System.currentTimeMillis(),
    ): Unit = dbTransaction(db) {
        ActivityLog.insert {
            it[ActivityLog.accountId] = accountId
            it[ActivityLog.deviceId] = deviceId
            it[ActivityLog.event] = event
            it[ActivityLog.detail] = detail
            it[ActivityLog.teamId] = teamId
            it[createdAt] = now
        }
        prune(now)
    }

    /** Most recent events first, with optional [offset] for page-based pagination. */
    suspend fun recent(limit: Int = 20, offset: Long = 0): List<ActivityRow> = dbTransaction(db) {
        ActivityLog.selectAll()
            .orderBy(ActivityLog.seq to SortOrder.DESC)
            .limit(limit).offset(offset)
            .map { it.toRow() }
    }

    /** Most recent events for one team (team-scoped history for owner/admin members). */
    suspend fun recentForTeam(teamId: String, limit: Int = 100): List<ActivityRow> =
        dbTransaction(db) {
            ActivityLog.selectAll()
                .where { ActivityLog.teamId eq teamId }
                .orderBy(ActivityLog.seq to SortOrder.DESC)
                .limit(limit)
                .map { it.toRow() }
        }

    /** Total retained events, for an accurate "N of M" in the console. */
    suspend fun count(): Long = dbTransaction(db) {
        ActivityLog.selectAll().count()
    }

    /**
     * Prune old events: first by [retentionDays] (time-based), then by [maxRows] (row-cap
     * backstop). Both use the column seq (monotonic), not createdAt, for correctness under
     * clock skew: a newer seq always has a later createdAt, so deleting by seq boundary
     * derived from createdAt is safe.
     */
    private fun prune(now: Long) {
        // 1. Time-based cleanup: delete events older than retentionDays.
        if (retentionDays > 0) {
            val cutoff = now - retentionMillis
            val oldestToKeep = ActivityLog.selectAll()
                .where { ActivityLog.createdAt greaterEq cutoff }
                .orderBy(ActivityLog.seq to SortOrder.ASC)
                .limit(1)
                .firstOrNull()?.get(ActivityLog.seq) ?: return
            ActivityLog.deleteWhere { seq lessEq oldestToKeep - 1 }
        }
        // 2. Row-cap backstop: never keep more than maxRows.
        val total = ActivityLog.selectAll().count()
        if (total <= maxRows) return
        val keepFrom = ActivityLog.selectAll()
            .orderBy(ActivityLog.seq to SortOrder.DESC)
            .limit(1).offset((maxRows - 1).toLong())
            .firstOrNull()?.get(ActivityLog.seq) ?: return
        ActivityLog.deleteWhere { seq lessEq keepFrom - 1 }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toRow() = ActivityRow(
        seq = this[ActivityLog.seq],
        accountId = this[ActivityLog.accountId],
        deviceId = this[ActivityLog.deviceId],
        event = this[ActivityLog.event],
        detail = this[ActivityLog.detail],
        createdAt = this[ActivityLog.createdAt],
    )
}
