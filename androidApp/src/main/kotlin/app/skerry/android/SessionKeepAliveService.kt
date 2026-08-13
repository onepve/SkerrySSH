package app.skerry.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Foreground service that keeps the app process — and with it the open SSH keep-alive loops —
 * alive while sessions are open. Backgrounding the app therefore does not freeze the connection:
 * the keepalive pings keep flowing, NAT/firewall idle timers don't fire, and returning to the app
 * shows the same live terminal instead of a dropped session.
 *
 * One notification per live session (Termius-style): the summary notification ([SUMMARY_ID]) is
 * the foreground one that must stay up while the service is foreground; each session gets its own
 * notification titled with the host, and tapping it routes back to that exact terminal (the
 * session id travels in the intent). The moment the last session closes, [ACTION_REMOVE] tears the
 * service down and every notification disappears — the persistent notification is present exactly
 * when it matters, never parked forever.
 *
 * [ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE] is deliberate: unlike dataSync, the
 * connectedDevice type has no 6-hour timeout on Android 14+, and its semantics fit — we keep a
 * network connection to an external device/server alive.
 */
class SessionKeepAliveService : Service() {

    companion object {
        const val ACTION_ADD = "app.skerry.android.action.KEEPALIVE_ADD"
        const val ACTION_REMOVE = "app.skerry.android.action.KEEPALIVE_REMOVE"
        const val EXTRA_SESSION_ID = "app.skerry.android.extra.SESSION_ID"
        const val EXTRA_HOST_LABEL = "app.skerry.android.extra.HOST_LABEL"

        private const val CHANNEL_ID = "session_keepalive"
        // Foreground summary notification (must stay up while the service is foreground).
        private const val SUMMARY_ID = 0x5E77
        // Per-session notifications start here and increment.
        private const val SESSION_ID_BASE = 0x5E78
    }

    // sessionId -> notification id. Kept only while the service lives; the process-side
    // AndroidSessionKeepAlive map survives service death and re-promotes on the next change.
    private val sessions = LinkedHashMap<String, Int>()
    private var nextSessionNotificationId = SESSION_ID_BASE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        when (intent.action) {
            ACTION_REMOVE -> removeSession(intent)
            else -> addSession(intent) // ACTION_ADD; also the plain-start fallback path
        }
        return START_NOT_STICKY
    }

    private fun addSession(intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val hostLabel = intent.getStringExtra(EXTRA_HOST_LABEL) ?: return
        createChannel()
        if (sessions.isEmpty()) {
            // First session (or the service was dead and is re-promoting): go foreground. The
            // summary notification is the one Android requires to stay; sessions follow below.
            val summary = buildSummaryNotification(1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+ takes an explicit type; API 34+ requires it to match a manifest type.
                // connectedDevice is only defined from 34 — earlier versions accept 0.
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                }
                startForeground(SUMMARY_ID, summary, type)
            } else {
                startForeground(SUMMARY_ID, summary)
            }
        }
        val notifId = sessions.getOrPut(sessionId) { nextSessionNotificationId++ }
        notificationManager().notify(notifId, buildSessionNotification(hostLabel, sessionId, notifId))
        updateSummary()
    }

    private fun removeSession(intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val notifId = sessions.remove(sessionId) ?: return // idempotent
        notificationManager().cancel(notifId)
        if (sessions.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateSummary()
        }
    }

    private fun updateSummary() {
        if (sessions.isEmpty()) return
        notificationManager().notify(SUMMARY_ID, buildSummaryNotification(sessions.size))
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.session_keepalive_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
        }
    }

    /** Summary (foreground) notification: N sessions alive; tap opens the app. */
    private fun buildSummaryNotification(count: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.session_keepalive_title))
            .setContentText(
                resources.getQuantityString(R.plurals.session_keepalive_count, count, count)
            )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .build()
    }

    /** Per-session notification: host label as the title; tap routes to that terminal. */
    private fun buildSessionNotification(hostLabel: String, sessionId: String, notifId: Int): Notification {
        // requestCode = notifId (unique per session in this service) so FLAG_UPDATE_CURRENT can
        // never mix two sessions' extras on a hashCode collision.
        val tap = PendingIntent.getActivity(
            this,
            notifId,
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(hostLabel)
            .setContentText(getString(R.string.session_keepalive_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(tap)
            .build()
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)
}
