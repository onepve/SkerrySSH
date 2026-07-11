package app.skerry.ui.update

import app.skerry.shared.update.AvailableUpdate
import app.skerry.shared.update.UpdateSettings
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateNoticeControllerTest {

    private val update = AvailableUpdate("0.2.0", "https://github.com/SeCherkasov/SkerrySSH/releases/tag/v0.2.0")

    @Test
    fun `refresh with checks enabled runs a check and exposes the update`() = runTest {
        var checks = 0
        val c = UpdateNoticeController(UpdateSettings(), persist = {}, check = { checks++; update }, scope = backgroundScope)

        c.refresh()
        runCurrent()

        assertEquals(1, checks)
        assertEquals(update, c.notice)
    }

    @Test
    fun `refresh with checks disabled never checks`() = runTest {
        var checks = 0
        val c = UpdateNoticeController(
            UpdateSettings(checkForUpdates = false),
            persist = {},
            check = { checks++; update },
            scope = backgroundScope,
        )

        c.refresh()
        runCurrent()

        assertEquals(0, checks)
        assertNull(c.notice)
    }

    @Test
    fun `re-checks on the interval while running`() = runTest {
        var checks = 0
        val c = UpdateNoticeController(
            UpdateSettings(),
            persist = {},
            check = { checks++; update },
            scope = backgroundScope,
            recheckIntervalMs = 1_000,
        )

        c.refresh()
        runCurrent()
        testScheduler.advanceTimeBy(2_500)
        runCurrent()

        assertEquals(3, checks)
    }

    @Test
    fun `repeated refresh does not restart the loop or re-check`() = runTest {
        var checks = 0
        val c = UpdateNoticeController(UpdateSettings(), persist = {}, check = { checks++; update }, scope = backgroundScope)

        c.refresh()
        runCurrent()
        c.refresh()
        runCurrent()

        assertEquals(1, checks)
    }

    @Test
    fun `a failed later check keeps the earlier notice`() = runTest {
        var checks = 0
        val c = UpdateNoticeController(
            UpdateSettings(),
            persist = {},
            check = { checks++; if (checks == 1) update else null },
            scope = backgroundScope,
            recheckIntervalMs = 1_000,
        )

        c.refresh()
        runCurrent()
        testScheduler.advanceTimeBy(1_500)
        runCurrent()

        assertEquals(2, checks)
        assertEquals(update, c.notice)
    }

    @Test
    fun `disabling the toggle persists stops checking and clears the notice`() = runTest {
        var persisted: UpdateSettings? = null
        var checks = 0
        val c = UpdateNoticeController(
            UpdateSettings(),
            persist = { persisted = it },
            check = { checks++; update },
            scope = backgroundScope,
            recheckIntervalMs = 1_000,
        )
        c.refresh()
        runCurrent()

        c.setCheckForUpdates(false)
        testScheduler.advanceTimeBy(5_000)
        runCurrent()

        assertEquals(UpdateSettings(checkForUpdates = false), persisted)
        assertFalse(c.settings.checkForUpdates)
        assertEquals(1, checks)
        assertNull(c.notice)
    }

    @Test
    fun `enabling the toggle starts checking`() = runTest {
        var checks = 0
        val c = UpdateNoticeController(
            UpdateSettings(checkForUpdates = false),
            persist = {},
            check = { checks++; update },
            scope = backgroundScope,
        )
        c.refresh()
        runCurrent()
        assertEquals(0, checks)

        c.setCheckForUpdates(true)
        runCurrent()

        assertTrue(c.settings.checkForUpdates)
        assertEquals(1, checks)
        assertEquals(update, c.notice)
    }

    @Test
    fun `a check result that lands after the toggle turned off is discarded`() = runTest {
        // Single-threaded stand-in for the cross-thread race: on a multi-threaded scope the toggle
        // can flip off after check() returned but before the loop stores the result; a stale store
        // would resurrect the notice forever (the loop is already dead and never overwrites it).
        var controller: UpdateNoticeController? = null
        val c = UpdateNoticeController(
            UpdateSettings(),
            persist = {},
            check = { controller!!.setCheckForUpdates(false); update },
            scope = backgroundScope,
        )
        controller = c

        c.refresh()
        runCurrent()

        assertNull(c.available)
        assertNull(c.notice)
    }

    @Test
    fun `stop halts the loop but leaves settings and notice intact`() = runTest {
        var checks = 0
        val c = UpdateNoticeController(
            UpdateSettings(),
            persist = {},
            check = { checks++; update },
            scope = backgroundScope,
            recheckIntervalMs = 1_000,
        )
        c.refresh()
        runCurrent()

        c.stop()
        testScheduler.advanceTimeBy(5_000)
        runCurrent()

        assertEquals(1, checks)
        assertTrue(c.settings.checkForUpdates)
        assertEquals(update, c.notice)
    }

    @Test
    fun `dismiss hides the notice but a newer version resurfaces it`() = runTest {
        var latest = update
        val c = UpdateNoticeController(
            UpdateSettings(),
            persist = {},
            check = { latest },
            scope = backgroundScope,
            recheckIntervalMs = 1_000,
        )
        c.refresh()
        runCurrent()

        c.dismiss()
        assertNull(c.notice)

        testScheduler.advanceTimeBy(1_500)
        runCurrent()
        assertNull(c.notice, "the dismissed version must stay hidden")

        latest = AvailableUpdate("0.3.0", "https://github.com/SeCherkasov/SkerrySSH/releases/tag/v0.3.0")
        testScheduler.advanceTimeBy(1_000)
        runCurrent()
        assertEquals(latest, c.notice)
    }
}
