package app.skerry.server.db

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.security.SecureRandom

/**
 * Invitation codes for gated registration. Codes are 8-char alphanumeric in XXX-XXXXX format
 * (upper+digits, no ambiguous chars: 0/O/1/I/L), generated server-side.
 */
class InviteCodeRepository(private val db: Database) {

    companion object {
        /** Pronounceable code format: XXX-XXXXX, 8 alphanumeric chars. */
        private const val CODE_LENGTH = 8
        /** Chars without visually ambiguous glyphs (0/O, 1/I/L). */
        private val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray()

        fun generateCodes(count: Int, random: SecureRandom = SecureRandom()): List<String> =
            List(count) {
                val chars = CharArray(CODE_LENGTH) { ALPHABET[random.nextInt(ALPHABET.size)] }
                buildString(9) {
                    append(chars, 0, 3)
                    append('-')
                    append(chars, 3, 5)
                }
            }
    }

    data class InviteCodeRow(
        val code: String,
        val createdBy: String,
        val createdAt: Long,
        val expiresAt: Long?,
        val maxUses: Int,
        val useCount: Int,
        val usedBy: String?,
        val usedAt: Long?,
        val isPublic: Boolean,
    ) {
        val isExpired: Boolean get() = expiresAt != null && System.currentTimeMillis() > expiresAt
        val isExhausted: Boolean get() = useCount >= maxUses
        val isUsable: Boolean get() = !isExpired && !isExhausted
    }

    /**
     * Insert [count] codes, each valid for [ttlDays] (null = never expires).
     * Returns the generated codes — only this call reveals the plaintext.
     */
    suspend fun create(
        count: Int,
        ttlDays: Int? = 30,
        createdBy: String = "admin",
        isPublic: Boolean = false,
    ): List<String> = dbTransaction(db) {
        val now = System.currentTimeMillis()
        val expiresAt = ttlDays?.let { now + it * 86_400_000L }
        val codes = generateCodes(count)
        for (code in codes) {
            InviteCodes.insert {
                it[InviteCodes.code] = code
                it[InviteCodes.createdBy] = createdBy
                it[InviteCodes.createdAt] = now
                it[InviteCodes.expiresAt] = expiresAt
                it[InviteCodes.maxUses] = 1
                it[InviteCodes.useCount] = 0
                it[InviteCodes.isPublic] = isPublic
            }
        }
        codes
    }

    /**
     * Result of attempting to consume an invitation code.
     */
    enum class ConsumeResult { OK, INVALID }

    /**
     * Attempt to consume an invitation code for [accountId]. Returns [ConsumeResult.OK] on success,
     * otherwise the specific failure reason. Idempotent for the same account.
     */
    suspend fun consume(code: String, accountId: String): ConsumeResult = dbTransaction(db) {
        val row = InviteCodes.selectAll().where { InviteCodes.code eq code }.singleOrNull()
            ?: return@dbTransaction ConsumeResult.INVALID
        val now = System.currentTimeMillis()
        val expiresAt = row[InviteCodes.expiresAt]
        val maxUses = row[InviteCodes.maxUses]
        val useCount = row[InviteCodes.useCount]
        val currentUsedBy = row[InviteCodes.usedBy]

        if (currentUsedBy == accountId) return@dbTransaction ConsumeResult.OK // idempotent
        if (currentUsedBy != null || useCount >= maxUses) return@dbTransaction ConsumeResult.INVALID
        if (expiresAt != null && now > expiresAt) return@dbTransaction ConsumeResult.INVALID

        val updated = InviteCodes.update({
            (InviteCodes.code eq code) and
                (InviteCodes.usedBy.isNull()) and
                (InviteCodes.useCount less maxUses) and
                (InviteCodes.expiresAt.isNull() or (InviteCodes.expiresAt greater now))
        }) {
            it[InviteCodes.useCount] = useCount + 1
            it[InviteCodes.usedBy] = accountId
            it[InviteCodes.usedAt] = now
        }
        if (updated > 0) ConsumeResult.OK else ConsumeResult.INVALID
    }

    /** List codes by filter. Supports `public=true/false/any` for the landing page. */
    suspend fun list(filter: String = "unused", public: String? = null, limit: Int = 20, offset: Long = 0): List<InviteCodeRow> =
        dbTransaction(db) {
            val now = System.currentTimeMillis()
            var q = InviteCodes.selectAll()
            q = q.where { whereFilter(now, filter, public) }
            q.orderBy(InviteCodes.createdAt to SortOrder.DESC)
                .limit(limit).offset(offset)
                .map { it.toRow() }
        }

    /** Count codes matching the same filter as [list], for pagination totals. */
    suspend fun count(filter: String = "unused", public: String? = null): Long = dbTransaction(db) {
        val now = System.currentTimeMillis()
        InviteCodes.selectAll().where { whereFilter(now, filter, public) }.count()
    }

    /** Build WHERE predicate for the given filter + public combination. */
    private fun whereFilter(now: Long, filter: String, public: String?): org.jetbrains.exposed.v1.core.Op<Boolean> {
        val f: org.jetbrains.exposed.v1.core.Op<Boolean>? = when (filter.lowercase()) {
            "unused" -> (InviteCodes.expiresAt.isNull() or (InviteCodes.expiresAt greater now)) and
                (InviteCodes.useCount less InviteCodes.maxUses)
            "used" -> (InviteCodes.usedBy.isNotNull()) and
                ((InviteCodes.expiresAt.isNull() or (InviteCodes.expiresAt greater now)) or
                    (InviteCodes.useCount greaterEq InviteCodes.maxUses))
            "expired" -> InviteCodes.expiresAt.isNotNull() and (InviteCodes.expiresAt lessEq now)
            else -> null
        }
        val p = when (public?.lowercase()) {
            "true" -> InviteCodes.isPublic eq true
            "false" -> InviteCodes.isPublic eq false
            else -> null
        }
        return if (f != null && p != null) f and p else (f ?: p) ?: org.jetbrains.exposed.v1.core.Op.TRUE
    }

    /** Public codes for the landing page: usable + public, no admin token needed. */
    suspend fun listPublic(): List<InviteCodeRow> = dbTransaction(db) {
        val now = System.currentTimeMillis()
        InviteCodes.selectAll()
            .where {
                (InviteCodes.isPublic eq true) and
                    (InviteCodes.expiresAt.isNull() or (InviteCodes.expiresAt greater now)) and
                    (InviteCodes.useCount less InviteCodes.maxUses)
            }
            .orderBy(InviteCodes.createdAt to SortOrder.DESC)
            .limit(200)
            .map { it.toRow() }
    }

    /** Delete specific codes by their plaintext values. Returns deleted count.
     *  All deletions run in a single transaction (dbTransaction wraps the block). */
    suspend fun deleteMany(codes: List<String>): Int = dbTransaction(db) {
        if (codes.isEmpty()) return@dbTransaction 0
        codes.sumOf { c ->
            InviteCodes.deleteWhere { InviteCodes.code eq c }
        }
    }

    /** Delete all consumed codes. */
    suspend fun purgeUsed(): Int = dbTransaction(db) {
        InviteCodes.deleteWhere { InviteCodes.usedBy.isNotNull() }
    }

    /** Delete all expired codes. */
    suspend fun purgeExpired(): Int = dbTransaction(db) {
        val now = System.currentTimeMillis()
        InviteCodes.deleteWhere {
            InviteCodes.expiresAt.isNotNull() and (InviteCodes.expiresAt lessEq now)
        }
    }

    suspend fun deleteOne(code: String): Boolean = dbTransaction(db) {
        InviteCodes.deleteWhere { InviteCodes.code eq code } > 0
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toRow() = InviteCodeRow(
        code = this[InviteCodes.code],
        createdBy = this[InviteCodes.createdBy],
        createdAt = this[InviteCodes.createdAt],
        expiresAt = this[InviteCodes.expiresAt],
        maxUses = this[InviteCodes.maxUses],
        useCount = this[InviteCodes.useCount],
        usedBy = this[InviteCodes.usedBy],
        usedAt = this[InviteCodes.usedAt],
        isPublic = this[InviteCodes.isPublic],
    )
}
