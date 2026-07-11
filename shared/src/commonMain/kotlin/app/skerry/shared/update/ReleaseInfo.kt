package app.skerry.shared.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The latest published release, as reported by the GitHub `releases/latest` endpoint. */
data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
)

@Serializable
private data class LatestReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

private val releaseJson = Json { ignoreUnknownKeys = true }

/**
 * Parses a GitHub `releases/latest` response body into [ReleaseInfo], or null when the payload is
 * malformed or misses either field — the response is an untrusted network input, so a bad body
 * means "no update information", never an exception.
 */
fun parseLatestRelease(body: String): ReleaseInfo? {
    val dto = try {
        releaseJson.decodeFromString(LatestReleaseDto.serializer(), body)
    } catch (_: Exception) {
        return null
    }
    val tag = dto.tagName ?: return null
    val url = dto.htmlUrl ?: return null
    return ReleaseInfo(tagName = tag, htmlUrl = url)
}
