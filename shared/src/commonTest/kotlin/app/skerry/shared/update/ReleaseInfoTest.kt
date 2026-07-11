package app.skerry.shared.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseInfoTest {

    @Test
    fun `parses tag and url from a GitHub latest-release payload`() {
        val body = """
            {
              "url": "https://api.github.com/repos/SeCherkasov/SkerrySSH/releases/1",
              "tag_name": "v0.2.0",
              "name": "Skerry 0.2.0",
              "html_url": "https://github.com/SeCherkasov/SkerrySSH/releases/tag/v0.2.0",
              "draft": false,
              "assets": [{"name": "skerry_0.2.0.AppImage"}]
            }
        """.trimIndent()

        val release = parseLatestRelease(body)

        assertEquals("v0.2.0", release?.tagName)
        assertEquals("https://github.com/SeCherkasov/SkerrySSH/releases/tag/v0.2.0", release?.htmlUrl)
    }

    @Test
    fun `returns null for malformed or incomplete payloads`() {
        assertNull(parseLatestRelease("not json"))
        assertNull(parseLatestRelease("{}"))
        assertNull(parseLatestRelease("""{"html_url": "https://example.com"}"""))
        assertNull(parseLatestRelease("""{"tag_name": "v0.2.0"}"""))
    }
}
