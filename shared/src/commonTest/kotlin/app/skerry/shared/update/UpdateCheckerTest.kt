package app.skerry.shared.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UpdateCheckerTest {

    private fun release(tag: String) = ReleaseInfo(
        tagName = tag,
        htmlUrl = "https://github.com/SeCherkasov/SkerrySSH/releases/tag/$tag",
    )

    @Test
    fun `reports an update when the latest release is newer`() = runTest {
        val checker = UpdateChecker(currentVersion = "0.1.1") { release("v0.2.0") }

        val update = checker.check()

        assertEquals("0.2.0", update?.versionLabel)
        assertEquals("https://github.com/SeCherkasov/SkerrySSH/releases/tag/v0.2.0", update?.releaseUrl)
    }

    @Test
    fun `stays quiet when the latest release is current or older`() = runTest {
        assertNull(UpdateChecker(currentVersion = "0.2.0") { release("v0.2.0") }.check())
        assertNull(UpdateChecker(currentVersion = "0.2.0") { release("v0.1.9") }.check())
    }

    @Test
    fun `stays quiet when fetch yields nothing`() = runTest {
        assertNull(UpdateChecker(currentVersion = "0.1.1") { null }.check())
    }

    @Test
    fun `treats fetch failures as no update`() = runTest {
        assertNull(UpdateChecker(currentVersion = "0.1.1") { throw RuntimeException("offline") }.check())
    }

    @Test
    fun `rethrows cancellation`() = runTest {
        val checker = UpdateChecker(currentVersion = "0.1.1") { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { checker.check() }
    }

    @Test
    fun `stays quiet when either version is unparseable`() = runTest {
        assertNull(UpdateChecker(currentVersion = "dev") { release("v0.2.0") }.check())
        assertNull(UpdateChecker(currentVersion = "0.1.1") { release("nightly") }.check())
    }
}
