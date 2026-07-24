package app.skerry.shared.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL

/**
 * Fetches the latest published release from the R2-hosted `version.json` (uploaded by CI
 * every tag push). The manifest is a tiny JSON at a fixed URL — no auth, cache-busting
 * via `no-cache`, and orders of magnitude simpler than the GitHub Releases API.
 */
object R2ReleaseClient {

    /** Stable public URL of the version manifest. */
    const val MANIFEST_URL = "https://dl.onepve.com/Skerry/latest/version.json"

    @Serializable
    private data class R2Manifest(
        @SerialName("version") val version: String,
        @SerialName("page_url") val pageUrl: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * One-shot fetch: open a connection, parse, return [ReleaseInfo] or null on any failure.
     * Runs on [Dispatchers.IO]: java.net.URL is a blocking call, and Android callers pass a
     * main-thread scope (rememberCoroutineScope), which would throw NetworkOnMainThreadException
     * and get silently swallowed into null — the check never succeeding on Android.
     */
    suspend fun fetchOnce(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(MANIFEST_URL).openConnection()
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            val body = conn.getInputStream().reader().readText()
            val m = json.decodeFromString<R2Manifest>(body)
            // The existing checker strips leading 'v'/'V', so tagName = "v1.2.3" is the idiomatic
            // input. If the manifest's version doesn't start with 'v', prepend it.
            val tag = if (m.version.startsWith("v", ignoreCase = true)) m.version else "v${m.version}"
            ReleaseInfo(tagName = tag, htmlUrl = m.pageUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
