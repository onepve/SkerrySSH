package app.skerry.android

import android.content.Context
import android.content.Intent
import android.os.Build
import app.skerry.ui.keepalive.SessionKeepAliveBridge
import java.util.concurrent.ConcurrentHashMap

/**
 * Android implementation of [SessionKeepAliveBridge]: runs the foreground service while at least
 * one session is open, one notification per live session (Termius-style), and stops the service
 * when the last one closes. The session map tolerates unbalanced calls (a failed connect tears
 * down without a matching start; multi-pane sessions interleave events) — [onSessionEnded] for an
 * unknown id is a no-op, so stray events can never wedge the service.
 */
class AndroidSessionKeepAlive(private val context: Context) : SessionKeepAliveBridge {

    // sessionId -> host label. The source of truth for "is the service needed at all"; the service
    // keeps its own copy for notification ids.
    private val sessions = ConcurrentHashMap<String, String>()

    override fun onSessionStarted(sessionId: String, hostLabel: String) {
        val wasEmpty = sessions.isEmpty()
        sessions[sessionId] = hostLabel
        val intent = Intent(context, SessionKeepAliveService::class.java)
            .setAction(SessionKeepAliveService.ACTION_ADD)
            .putExtra(SessionKeepAliveService.EXTRA_SESSION_ID, sessionId)
            .putExtra(SessionKeepAliveService.EXTRA_HOST_LABEL, hostLabel)
        if (wasEmpty) {
            // First session: bring the service up. startForegroundService is required on API 26+;
            // called while the app is foreground (a session was just opened), exactly when Android
            // permits it. Reconnects of an already-notified session take the plain path below.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            // Service already up (or a reconnect of a known session) — plain start is enough to
            // deliver the intent; a dead service re-promotes itself inside onStartCommand.
            runCatching { context.startService(intent) }
        }
    }

    override fun onSessionEnded(sessionId: String) {
        if (sessions.remove(sessionId) == null) return  // idempotent: unknown id is a no-op
        val intent = Intent(context, SessionKeepAliveService::class.java)
            .setAction(SessionKeepAliveService.ACTION_REMOVE)
            .putExtra(SessionKeepAliveService.EXTRA_SESSION_ID, sessionId)
        runCatching { context.startService(intent) }
    }
}
