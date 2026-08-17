package app.skerry.shared.vault

import app.skerry.shared.sync.SyncSettingsStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/**
 * A portable backup of everything the account syncs: one record per synced vault record, with the
 * plaintext payload attached. The file itself is either plain JSON (for migrating terminals or
 * eyeballing the data) or sealed with a key derived from the master password (for storage on disk,
 * in email, anywhere).
 *
 * Records are carried with their Lamport version so an import can merge under the same LWW rule as
 * sync ([app.skerry.shared.vault.Vault.putAtLeast]); tombstones travel too, so a deletion survives
 * the move. `TRASH` follows its origin type, exactly like sync. Terminal history is deliberately
 * left out — it is device-local by design ([app.skerry.shared.sync.SyncSettings.shouldSync]).
 *
 * The encrypted form is `[16-byte Argon2id salt][XChaCha20-Poly1305(JSON)]` sealed under the
 * master-key-derived 256-bit key with the fixed AAD `skerry-vault-backup:v1`; a wrong password is
 * an AEAD failure and reports [BackupLoadResult.WrongPassword].
 */
@Serializable
data class BackupRecord(
    val id: String,
    val type: RecordType,
    val version: Long,
    val updatedAt: String,
    val deviceId: String,
    val deleted: Boolean,
    /** Plaintext payload; `null` for tombstones (an empty payload is the same thing). */
    val payload: String? = null,
)

@Serializable
data class VaultBackup(
    val format: String,
    val version: Int = FORMAT_VERSION,
    val createdAt: String = "",
    val records: List<BackupRecord> = emptyList(),
) {
    val encrypted: Boolean get() = format == FORMAT_ENCRYPTED

    companion object {
        const val FORMAT_PLAIN = "skerry-vault-backup"
        const val FORMAT_ENCRYPTED = "skerry-vault-backup.enc"
        const val FORMAT_VERSION = 1
    }
}

/** Outcome of loading a backup file. */
sealed interface BackupLoadResult {
    data class Ok(val backup: VaultBackup) : BackupLoadResult
    data object WrongPassword : BackupLoadResult
    data object Corrupted : BackupLoadResult
}

/** How an imported backup is written into the vault. */
enum class BackupImportMode {
    /** LWW merge: a record whose version wins replaces the local one; the local copy survives ties/higher versions. */
    MERGE,

    /** Full replace: synced records are cleared first, then the backup is written verbatim. */
    REPLACE,
}

/** Applies a loaded [backup] to [vault] (unlocked). Returns the number of records applied. */
fun applyBackup(vault: Vault, backup: VaultBackup, mode: BackupImportMode): Int {
    if (mode == BackupImportMode.REPLACE) {
        // Replace means "the file is the truth": every synced record the backup does NOT carry must
        // go, not just the types it happens to mention (a backup without hosts should delete hosts).
        val settings = SyncSettingsStore(vault).load()
        vault.clearRecords(RecordType.entries.filter { settings.shouldSync(it) }.toSet())
    }
    val local = vault.records().associateBy { it.id }
    var applied = 0
    backup.records.forEach { record ->
        if (mode == BackupImportMode.MERGE && !winsOver(local[record.id], record)) return@forEach
        applyRecord(vault, record, mode)
        applied++
    }
    return applied
}

private fun applyRecord(vault: Vault, record: BackupRecord, mode: BackupImportMode) {
    when {
        record.deleted -> vault.remove(record.id)
        record.payload != null -> {
            val payload = record.payload.encodeUtf8().toByteArray()
            if (mode == BackupImportMode.REPLACE) {
                vault.put(record.id, record.type, payload)
            } else {
                vault.putAtLeast(record.id, record.type, payload, record.version)
            }
        }
    }
}

/** LWW tie-break between the local record and an incoming one, mirroring [Vault.mergeRemote]. */
private fun winsOver(local: VaultRecord?, incoming: BackupRecord): Boolean = when {
    local == null -> true
    incoming.version > local.version -> true
    incoming.version == local.version -> incoming.deviceId > local.deviceId
    else -> false
}

object VaultBackupCodec {

    private const val SALT_BYTES = 16
    private val AAD = "skerry-vault-backup:v1".encodeUtf8().toByteArray()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; prettyPrintIndent = "    " }

    /**
     * Exports the synced records of [vault] (unlocked) as a backup file. Every record whose type
     * [app.skerry.shared.sync.SyncSettings.shouldSync] says syncs — including tombstones — is read
     * in plaintext and written as readable UTF-8; a record that fails to decrypt (AEAD failure on a
     * stale/foreign blob) is skipped rather than aborting the whole export. With [encrypt] the
     * whole JSON (including the readable payloads) is sealed and carried as base64 under the format
     * header (the app's file pickers are text-only); the plain form is the JSON itself. [password]
     * is wiped on the way out.
     */
    fun export(
        vault: Vault,
        crypto: VaultCrypto,
        password: CharArray,
        encrypt: Boolean,
        nowIso: () -> String,
    ): String {
        require(vault.isUnlocked) { "vault must be unlocked to export a backup" }
        val settings = SyncSettingsStore(vault).load()
        val records = vault.records()
            .filter { settings.shouldSync(it.type, it.id) }
            .mapNotNull { record ->
                val payload = if (record.deleted) null else vault.openPayload(record.id) ?: return@mapNotNull null
                BackupRecord(
                    id = record.id,
                    type = record.type,
                    version = record.version,
                    updatedAt = record.updatedAt,
                    deviceId = record.deviceId,
                    deleted = record.deleted,
                    // Readable UTF-8: a plain export must be eyeballable end to end.
                    payload = payload?.decodeToString(),
                )
            }
        val backup = VaultBackup(
            format = if (encrypt) VaultBackup.FORMAT_ENCRYPTED else VaultBackup.FORMAT_PLAIN,
            createdAt = nowIso(),
            records = records,
        )
        val jsonText = json.encodeToString(backup)
        return if (encrypt) {
            // Text envelope: header line + base64 of [salt][sealed JSON].
            val sealed = seal(crypto, password, jsonText.encodeUtf8().toByteArray())
            ENCRYPTED_HEADER + "\n" + sealed.toByteString().base64()
        } else {
            jsonText
        }
    }

    /**
     * Parses [text], decrypting it first when the file carries the encrypted header. [password] is
     * only consulted for an encrypted file — a plain one imports without it. A wrong password
     * surfaces as [BackupLoadResult.WrongPassword]; anything unparseable is
     * [BackupLoadResult.Corrupted]. [password] is wiped on the way out.
     */
    fun load(text: String, crypto: VaultCrypto, password: CharArray?): BackupLoadResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return BackupLoadResult.Corrupted
        if (trimmed.startsWith(ENCRYPTED_HEADER)) {
            val body = trimmed.removePrefix(ENCRYPTED_HEADER).trim()
            val sealed = body.decodeBase64()?.toByteArray() ?: return BackupLoadResult.Corrupted
            if (sealed.size < SALT_BYTES + 16) return BackupLoadResult.Corrupted
            val salt = sealed.copyOfRange(0, SALT_BYTES)
            val ciphertext = sealed.copyOfRange(SALT_BYTES, sealed.size)
            val opened = open(crypto, password, salt, ciphertext)
            if (opened == null) {
                // Either no password was supplied (the UI probes with null to detect encryption) or
                // the password was wrong — both mean the file needs the right password to open.
                return BackupLoadResult.WrongPassword
            }
            return parsePlain(opened.decodeToString())
        }
        return parsePlain(trimmed)
    }

    private const val ENCRYPTED_HEADER = "skerry-vault-backup.enc:v1"

    private fun parsePlain(text: String): BackupLoadResult = try {
        val backup = json.decodeFromString<VaultBackup>(text)
        // The format tag says where the file came from; once the JSON is in hand both forms are
        // equally importable (an encrypted file simply arrives here decrypted).
        if (backup.format == VaultBackup.FORMAT_PLAIN || backup.format == VaultBackup.FORMAT_ENCRYPTED) {
            BackupLoadResult.Ok(backup)
        } else {
            BackupLoadResult.Corrupted
        }
    } catch (_: Exception) {
        BackupLoadResult.Corrupted
    }

    private fun seal(crypto: VaultCrypto, password: CharArray, plaintext: ByteArray): ByteArray {
        val salt = crypto.newSalt()
        val masterKey = crypto.deriveMasterKey(password, salt)
        try {
            val sealed = crypto.seal(DataKey(masterKey.bytes), plaintext, AAD)
            return salt + sealed
        } finally {
            masterKey.zeroize()
            password.fill(' ')
        }
    }

    private fun open(crypto: VaultCrypto, password: CharArray?, salt: ByteArray, sealed: ByteArray): ByteArray? {
        if (password == null) return null
        val masterKey = crypto.deriveMasterKey(password, salt)
        try {
            return crypto.open(DataKey(masterKey.bytes), sealed, AAD)
        } finally {
            masterKey.zeroize()
            password.fill(' ')
        }
    }
}
