package app.skerry.server.db

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** One invite code, as the admin console and the front page list it. */
data class InviteRow(
    val code: String,
    val remainingUses: Int,
    val public: Boolean,
    val createdAt: Long,
    val usedBy: String?,
    val usedAt: Long?,
)

/**
 * Invite codes and their pre-registrations — the fork's gated registration
 * (`SKERRY_REGISTRATION=invite`).
 *
 * A code is spent at pre-registration by a conditional UPDATE (the same TOCTOU-safe shape as
 * [PairingRepository.consume]: only a code with a remaining use is debited, and only the
 * transaction whose UPDATE changed a row wins), so a code can never be overspent under a race.
 * The spent use is refunded if the pre-registration expires before the account registers.
 *
 * The account id is stored verbatim — deliberately NOT normalized to an email, matching the
 * server's own account id (a free-form `varchar(320)` with no format validation).
 */
class InviteRepository(private val db: Database) {

    /** Admin: create a code redeemable [uses] times, [public] if it should list on the front page. */
    suspend fun create(code: String, uses: Int, public: Boolean, now: Long = System.currentTimeMillis()) =
        dbTransaction(db) {
            InviteCodes.insert {
                it[InviteCodes.code] = code
                it[remainingUses] = uses
                it[InviteCodes.public] = public
                it[createdAt] = now
            }
        }

    /** Admin: every code, newest first. */
    suspend fun list(): List<InviteRow> = dbTransaction(db) {
        InviteCodes.selectAll()
            .orderBy(InviteCodes.createdAt to SortOrder.DESC)
            .map {
                InviteRow(
                    code = it[InviteCodes.code],
                    remainingUses = it[InviteCodes.remainingUses],
                    public = it[InviteCodes.public],
                    createdAt = it[InviteCodes.createdAt],
                    usedBy = it[InviteCodes.usedBy],
                    usedAt = it[InviteCodes.usedAt],
                )
            }
    }

    /** Front page: the public codes that still have a use left. */
    suspend fun listPublic(): List<InviteRow> = dbTransaction(db) {
        InviteCodes.selectAll()
            .where { (InviteCodes.public eq true) and (InviteCodes.remainingUses greater 0) }
            .map {
                InviteRow(
                    code = it[InviteCodes.code],
                    remainingUses = it[InviteCodes.remainingUses],
                    public = it[InviteCodes.public],
                    createdAt = it[InviteCodes.createdAt],
                    usedBy = it[InviteCodes.usedBy],
                    usedAt = it[InviteCodes.usedAt],
                )
            }
    }

    /** Admin: delete a code. Returns true if it existed. */
    suspend fun delete(code: String): Boolean = dbTransaction(db) {
        InviteCodes.deleteWhere { InviteCodes.code eq code } > 0
    }

    /** Whether an account id already exists (registered). */
    suspend fun accountExists(accountId: String): Boolean = dbTransaction(db) {
        Accounts.selectAll().where { Accounts.id eq accountId }.any()
    }

    /**
     * Redeem [code] for [accountId]: spend one use (conditional UPDATE) and record the
     * pre-registration expiring at [expiresAt]. Returns false when the code is unknown or
     * exhausted, true on success. A duplicate [accountId] re-redeems (the pre-registration is
     * upserted), so a re-entry before the old one expires simply refreshes it.
     */
    suspend fun preregister(
        accountId: String,
        code: String,
        expiresAt: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean = dbTransaction(db) {
        val spent = InviteCodes.update({
            (InviteCodes.code eq code) and (InviteCodes.remainingUses greater 0)
        }) {
            it[remainingUses] = InviteCodes.remainingUses - 1
            it[usedBy] = accountId
            it[usedAt] = now
        }
        if (spent != 1) return@dbTransaction false

        // Upsert the pre-registration: delete any existing row for this id, then insert fresh.
        Preregistrations.deleteWhere { Preregistrations.accountId eq accountId }
        Preregistrations.insert {
            it[Preregistrations.accountId] = accountId
            it[Preregistrations.inviteCode] = code
            it[Preregistrations.expiresAt] = expiresAt
            it[createdAt] = now
        }
        true
    }

    /** Whether [accountId] holds a live (unexpired) pre-registration. */
    suspend fun isPreregistered(accountId: String, now: Long = System.currentTimeMillis()): Boolean =
        dbTransaction(db) {
            Preregistrations.selectAll()
                .where {
                    (Preregistrations.accountId eq accountId) and (Preregistrations.expiresAt greater now)
                }
                .any()
        }

    /** Consume the pre-registration on a successful registration. Returns true if it existed. */
    suspend fun removePreregistration(accountId: String): Boolean = dbTransaction(db) {
        Preregistrations.deleteWhere { Preregistrations.accountId eq accountId } > 0
    }

    /**
     * Refund the use of every expired pre-registration and delete them. Refund is read-then-write
     * (not a conditional UPDATE): it only runs from the periodic cleanup loop, never concurrently
     * with a redeem, so the non-atomic read of [InviteCodes.remainingUses] is safe. Returns the
     * number of expired pre-registrations removed.
     */
    suspend fun cleanupExpired(now: Long = System.currentTimeMillis()): Int = dbTransaction(db) {
        val expired = Preregistrations.selectAll()
            .where { Preregistrations.expiresAt lessEq now }
            .toList()
        expired.forEach { row ->
            val code = row[Preregistrations.inviteCode]
            InviteCodes.selectAll().where { InviteCodes.code eq code }.singleOrNull()?.let { c ->
                InviteCodes.update({ InviteCodes.code eq code }) {
                    it[remainingUses] = c[InviteCodes.remainingUses] + 1
                }
            }
        }
        Preregistrations.deleteWhere { Preregistrations.expiresAt lessEq now }
    }
}
