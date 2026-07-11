package app.skerry.shared.update

import kotlinx.coroutines.CancellationException

/** A release newer than the running build: what to show and where the button leads. */
data class AvailableUpdate(
    /** Human version without the tag's `v` prefix, e.g. "0.2.0". */
    val versionLabel: String,
    /** GitHub release page with all platform artifacts. */
    val releaseUrl: String,
)

/**
 * Decides whether an update notice should be shown: fetches the latest release via the injected
 * [fetchLatest] and compares it to [currentVersion]. Any fetch/parse problem (offline, rate limit,
 * malformed tag) yields null — the check is a background nicety and must never surface an error.
 */
class UpdateChecker(
    private val currentVersion: String,
    private val fetchLatest: suspend () -> ReleaseInfo?,
) {

    suspend fun check(): AvailableUpdate? {
        val current = UpdateVersion.parse(currentVersion) ?: return null
        val release = try {
            fetchLatest() ?: return null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return null
        }
        val latest = UpdateVersion.parse(release.tagName) ?: return null
        if (latest <= current) return null
        return AvailableUpdate(
            versionLabel = release.tagName.removePrefix("v").removePrefix("V"),
            releaseUrl = release.htmlUrl,
        )
    }
}
