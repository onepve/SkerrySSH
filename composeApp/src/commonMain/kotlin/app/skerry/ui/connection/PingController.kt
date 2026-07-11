package app.skerry.ui.connection

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Periodically measures RTT to the server via [measure] (one round-trip per cycle) and publishes
 * it in [rttMs] (ms) for the status bar. Runs on the session's [scope] (like
 * [app.skerry.ui.metrics.HostMetricsController]): survives tab switches and stops together with
 * the session ([stop] from [ConnectionController.disconnect]).
 *
 * A failed measurement (dropped connection, timeout — [measure] returned `null` or threw) does
 * NOT reset the indicator: [rttMs] holds the last successful value until the next successful
 * cycle. The first measurement runs immediately.
 */
@Stable
class PingController(
    private val measure: suspend () -> Long?,
    private val scope: CoroutineScope,
    private val pollIntervalMillis: Long = 5000,
) {
    var rttMs: Long? by mutableStateOf(null)
        private set

    private var job: Job? = null

    // The loop runs on a multi-threaded [scope] while stop() comes from elsewhere, and Job.cancel()
    // does not wait: a measurement could land after stop() and leak into a restarted cycle. Storing
    // a value is therefore gated by a generation stamp under a lock (same as UpdateNoticeController).
    private val lock = Any()
    private var generation = 0

    /** Start periodic measurement (idempotent: a repeat call doesn't spawn a second cycle). */
    fun start() {
        synchronized(lock) {
            if (job != null) return
            val gen = generation
            job = scope.launch {
                while (isActive) {
                    val measured = runCatching { measure() }
                        .onFailure { if (it is CancellationException) throw it } // don't swallow cancellation
                        .getOrNull()
                    // Discard a value from a cycle that was stopped while measure() was in flight.
                    if (measured != null) synchronized(lock) { if (gen == generation) rttMs = measured }
                    delay(pollIntervalMillis)
                }
            }
        }
    }

    /** Stop measuring. */
    fun stop() {
        synchronized(lock) {
            generation++
            job?.cancel()
            job = null
        }
    }
}
