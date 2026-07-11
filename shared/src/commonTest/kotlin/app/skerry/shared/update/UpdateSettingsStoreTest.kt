package app.skerry.shared.update

import app.skerry.shared.vault.FakeVault
import app.skerry.shared.vault.RecordType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateSettingsStoreTest {

    @Test
    fun `empty vault defaults to checking for updates`() {
        assertTrue(UpdateSettingsStore(FakeVault()).load().checkForUpdates)
    }

    @Test
    fun `round trips a disabled toggle`() {
        val store = UpdateSettingsStore(FakeVault())

        store.save(UpdateSettings(checkForUpdates = false))

        assertFalse(store.load().checkForUpdates)
    }

    @Test
    fun `corrupt payload falls back to default-on`() {
        val vault = FakeVault()
        vault.put(UpdateSettingsStore.SETTINGS_ID, RecordType.SETTINGS, "not json".encodeToByteArray())

        assertTrue(UpdateSettingsStore(vault).load().checkForUpdates)
    }
}
