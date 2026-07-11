package app.skerry.ui.update

import app.skerry.shared.update.GithubReleaseClient
import app.skerry.shared.update.UpdateChecker
import app.skerry.shared.update.UpdateSettingsStore
import app.skerry.shared.vault.Vault
import app.skerry.ui.app.AppVersion
import kotlinx.coroutines.CoroutineScope

/**
 * Assembles the update-notice controller over the vault-backed toggle and the GitHub Releases
 * check (one shared recipe for desktop `main` and [app.skerry.ui.mobile.MobileDesignApp]). The
 * vault starts locked, so [UpdateSettingsStore.load] yields the default here; the loop only starts
 * from [UpdateNoticeController.refresh] after unlock — no network before the user can reach the toggle.
 */
fun updateNoticeController(vault: Vault, scope: CoroutineScope): UpdateNoticeController {
    val store = UpdateSettingsStore(vault)
    val checker = UpdateChecker(AppVersion.VERSION) { GithubReleaseClient.fetchOnce() }
    return UpdateNoticeController(
        initialSettings = store.load(),
        persist = store::save,
        check = checker::check,
        scope = scope,
        reload = store::load,
    )
}
