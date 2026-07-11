package app.skerry.shared.update

import app.skerry.shared.vault.RecordType
import app.skerry.shared.vault.Vault
import app.skerry.shared.vault.VaultSingletonStore
import kotlinx.serialization.Serializable

/**
 * Update-check preference. [checkForUpdates] gates the only network request the app makes to a
 * host the user didn't configure (the GitHub Releases API), so it is a user-visible toggle,
 * default-on. Stored as an encrypted [RecordType.SETTINGS] record, so it syncs across devices
 * like [app.skerry.shared.sync.SyncSettings].
 */
@Serializable
data class UpdateSettings(
    val checkForUpdates: Boolean = true,
)

/**
 * Reads/writes [UpdateSettings] as a singleton vault record. On a locked vault [load] returns the
 * default (check on); a corrupt payload falls back to the same default — the check is harmless
 * and the toggle stays reachable in Settings.
 */
class UpdateSettingsStore(vault: Vault) {

    private val store = VaultSingletonStore(vault, SETTINGS_ID, RecordType.SETTINGS, UpdateSettings.serializer()) {
        UpdateSettings()
    }

    fun load(): UpdateSettings = store.load()

    fun save(settings: UpdateSettings) {
        store.save(settings)
    }

    companion object {
        /** Stable id of the update settings singleton record in the vault. */
        const val SETTINGS_ID = "update.settings"
    }
}
