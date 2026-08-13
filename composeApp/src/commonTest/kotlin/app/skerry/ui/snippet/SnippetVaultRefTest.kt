package app.skerry.ui.snippet

import app.skerry.shared.vault.CredentialStore
import app.skerry.shared.vault.DataKey
import app.skerry.shared.vault.MergeResult
import app.skerry.shared.vault.RecordType
import app.skerry.shared.vault.SyncMeta
import app.skerry.shared.vault.UnlockResult
import app.skerry.shared.vault.Vault
import app.skerry.shared.vault.VaultRecord
import app.skerry.ui.identity.CredentialDraft
import app.skerry.ui.identity.CredentialKind
import app.skerry.ui.identity.CredentialManagerController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SnippetVaultRefTest {

    private fun controller(vararg drafts: CredentialDraft): CredentialManagerController {
        var n = 0
        return CredentialManagerController(CredentialStore(FakeVault())) { "gen${n++}" }.apply {
            drafts.forEach { save(it) }
        }
    }

    @Test
    fun password_entry_resolves_to_its_secret() {
        val ref = resolveVaultRef("db", controller(CredentialDraft(label = "db", kind = CredentialKind.PASSWORD, password = "s3cret")))
        assertEquals(VaultRef.Ok("s3cret"), ref)
    }

    @Test
    fun ssh_key_entry_defers_to_public_half_inspection() {
        val ref = resolveVaultRef("Hermes", controller(
            CredentialDraft(label = "Hermes", kind = CredentialKind.PRIVATE_KEY, privateKeyPem = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc"),
        ))
        assertEquals(VaultRef.Resolving, ref)
    }

    @Test
    fun certificate_entry_stays_not_injectable() {
        // Upstream v1 behaviour, unchanged: only passwords and SSH-key public halves inject.
        val ref = resolveVaultRef("cert", controller(
            CredentialDraft(label = "cert", kind = CredentialKind.CERTIFICATE, privateKeyPem = "pem", certificate = "ssh-ed25519-cert-v01@openssh.com AAAA"),
        ))
        assertEquals(VaultRef.NotAPassword, ref)
    }

    @Test
    fun same_name_entries_surface_as_choices_with_kind_and_notes() {
        val ref = resolveVaultRef("dup", controller(
            CredentialDraft(label = "dup", kind = CredentialKind.PASSWORD, password = "pw", notes = "第一台"),
            CredentialDraft(label = "dup", kind = CredentialKind.PRIVATE_KEY, privateKeyPem = "pem", notes = "第二台"),
        ))
        val ambiguous = assertIs<VaultRef.Ambiguous>(ref)
        assertEquals(2, ambiguous.candidates.size)
        assertEquals(VaultEntryKind.PASSWORD, ambiguous.candidates[0].kind)
        assertEquals("第一台", ambiguous.candidates[0].notes)
        assertEquals(VaultEntryKind.SSH_KEY, ambiguous.candidates[1].kind)
        assertEquals("第二台", ambiguous.candidates[1].notes)
    }

    @Test
    fun missing_entry_reports_missing() {
        assertEquals(VaultRef.Missing, resolveVaultRef("nope", controller()))
    }
}

private class FakeVault : Vault {
    private val payloads = mutableMapOf<String, ByteArray>()
    private val records = mutableMapOf<String, VaultRecord>()

    override fun exists(): Boolean = true
    override val isUnlocked: Boolean = true
    override fun create(password: CharArray) = Unit
    override fun unlock(password: CharArray): UnlockResult = UnlockResult.Success
    override fun lock() = Unit
    override fun reset() { payloads.clear(); records.clear() }

    override fun records(): List<VaultRecord> = records.values.toList()
    override fun syncMeta(): SyncMeta? = null
    override fun mergeRemote(remote: List<VaultRecord>): MergeResult = MergeResult.EMPTY
    override fun openPayload(id: String): ByteArray? =
        records[id]?.takeIf { !it.deleted }?.let { payloads[id] }

    override fun put(id: String, type: RecordType, payload: ByteArray) {
        val version = (records[id]?.version ?: 0L) + 1
        records[id] = VaultRecord(id, type, version, "2026-06-12T00:00:00Z", "dev", deleted = false, blob = ByteArray(0))
        payloads[id] = payload
    }

    override fun remove(id: String) {
        records[id] = (records[id] ?: return).copy(version = records[id]!!.version + 1, deleted = true)
    }

    override fun changePassword(oldPassword: CharArray, newPassword: CharArray): Boolean = true
    override fun verifyPassword(password: CharArray): Boolean = true

    override fun unlockWithDataKey(dataKey: DataKey): UnlockResult = UnlockResult.Corrupted
    override fun exportDataKey(): DataKey? = null
    override fun adoptDataKey(newDataKey: DataKey, password: CharArray): Boolean = false
}
