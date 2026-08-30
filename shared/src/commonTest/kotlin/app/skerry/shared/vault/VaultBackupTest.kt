package app.skerry.shared.vault

import app.skerry.shared.snippet.Snippet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultBackupTest {

    private val crypto = IonspinVaultCrypto()

    @BeforeTest
    fun initLibsodium() = runTest { initializeVaultCrypto() }

    private val snippetPayload = Json.encodeToString(
        Snippet(id = "s1", label = "check", command = "df -h", tags = listOf("ops"))
    ).encodeUtf8().toByteArray()

    private val hostPayload = "host-payload".encodeUtf8().toByteArray()

    private fun password(s: String = "correct horse") = s.toCharArray()

    private fun vaultWithSnippetAndHost(): FakeVault = FakeVault().apply {
        put("s1", RecordType.SNIPPET, snippetPayload)
        put("h1", RecordType.HOST, hostPayload)
    }

    private fun export(vault: FakeVault, encrypt: Boolean): String =
        VaultBackupCodec.export(vault, crypto, password(), encrypt) { "2026-08-05T00:00:00Z" }

    @Test
    fun plain_export_load_roundtrip_keeps_payloads() {
        val vault = vaultWithSnippetAndHost()
        val file = export(vault, encrypt = false)

        val result = VaultBackupCodec.load(file, crypto, password = null)

        val backup = assertIs<BackupLoadResult.Ok>(result).backup
        assertEquals(VaultBackup.FORMAT_PLAIN, backup.format)
        assertEquals(2, backup.records.size)
        val snippet = backup.records.first { it.id == "s1" }
        assertEquals(RecordType.SNIPPET, snippet.type)
        // Payloads are exported as readable UTF-8, not base64.
        assertEquals(snippetPayload.decodeToString(), snippet.payload)
    }

    @Test
    fun encrypted_export_loads_with_password() {
        val vault = vaultWithSnippetAndHost()
        val file = export(vault, encrypt = true)

        // The sealed form is a text envelope, not a JSON document.
        assertTrue(file.startsWith("skerry-vault-backup.enc:v1"))
        val ok = assertIs<BackupLoadResult.Ok>(VaultBackupCodec.load(file, crypto, password()))
        assertEquals(VaultBackup.FORMAT_ENCRYPTED, ok.backup.format)
        assertEquals(2, ok.backup.records.size)
    }

    @Test
    fun encrypted_export_wrong_password_is_rejected() {
        val file = export(vaultWithSnippetAndHost(), encrypt = true)
        assertIs<BackupLoadResult.WrongPassword>(VaultBackupCodec.load(file, crypto, password("nope")))
        assertIs<BackupLoadResult.WrongPassword>(VaultBackupCodec.load(file, crypto, password = null))
    }

    @Test
    fun garbage_file_is_corrupted() {
        assertIs<BackupLoadResult.Corrupted>(VaultBackupCodec.load("not a backup", crypto, password()))
    }

    @Test
    fun merge_import_keeps_newer_local() {
        val vault = FakeVault().apply { put("s1", RecordType.SNIPPET, "local-new".encodeUtf8().toByteArray()) }
        val localVersion = vault.records().single().version
        val backup = VaultBackup(
            format = VaultBackup.FORMAT_PLAIN,
            records = listOf(
                BackupRecord("s1", RecordType.SNIPPET, version = localVersion - 1, updatedAt = "old", deviceId = "a", deleted = false, payload = "backup-old"),
            ),
        )

        applyBackup(vault, backup, BackupImportMode.MERGE)

        assertContentEquals("local-new".encodeUtf8().toByteArray(), vault.openPayload("s1")!!)
        assertEquals(localVersion, vault.records().single().version)
    }

    @Test
    fun merge_import_applies_newer_incoming() {
        val vault = FakeVault().apply { put("s1", RecordType.SNIPPET, "local-old".encodeUtf8().toByteArray()) }
        val localVersion = vault.records().single().version
        val backup = VaultBackup(
            format = VaultBackup.FORMAT_PLAIN,
            records = listOf(
                BackupRecord("s1", RecordType.SNIPPET, version = localVersion + 10, updatedAt = "new", deviceId = "a", deleted = false, payload = "backup-new"),
            ),
        )

        applyBackup(vault, backup, BackupImportMode.MERGE)

        assertContentEquals("backup-new".encodeUtf8().toByteArray(), vault.openPayload("s1")!!)
    }

    @Test
    fun replace_import_clears_untouched_types() {
        val vault = vaultWithSnippetAndHost() // s1 + h1
        val backup = VaultBackup(
            format = VaultBackup.FORMAT_PLAIN,
            records = listOf(
                BackupRecord("s1", RecordType.SNIPPET, version = 1, updatedAt = "x", deviceId = "a", deleted = false, payload = "only-snippet"),
            ),
        )

        applyBackup(vault, backup, BackupImportMode.REPLACE)

        assertEquals(1, vault.records().size)
        assertContentEquals("only-snippet".encodeUtf8().toByteArray(), vault.openPayload("s1")!!)
        assertNull(vault.openPayload("h1"))
    }

    @Test
    fun tombstone_in_backup_deletes_on_import() {
        val vault = FakeVault().apply { put("s1", RecordType.SNIPPET, snippetPayload) }
        val backup = VaultBackup(
            format = VaultBackup.FORMAT_PLAIN,
            records = listOf(
                BackupRecord("s1", RecordType.SNIPPET, version = 99, updatedAt = "x", deviceId = "a", deleted = true),
            ),
        )

        applyBackup(vault, backup, BackupImportMode.MERGE)

        assertTrue(vault.records().single().deleted)
        assertNull(vault.openPayload("s1"))
    }
}
