package app.skerry.shared.sync

import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.websocket.WebSockets
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorSyncClientConfigTest {

    @Test
    fun `default client pings the sync WS so a half-dead socket fails instead of hanging`() {
        val http = KtorSyncClient.defaultHttpClient()
        try {
            // Without pings a connection that died with no FIN/RST (Wi-Fi switch, suspend, NAT
            // timeout) never errors: changes() hangs on a dead socket while the status stays
            // Online and live-pull is silently gone. The pinger surfaces the death, and the
            // coordinator's watch loop reconnects.
            assertEquals(30_000L, http.pluginOrNull(WebSockets)?.pingIntervalMillis)
        } finally {
            http.close()
        }
    }
}
