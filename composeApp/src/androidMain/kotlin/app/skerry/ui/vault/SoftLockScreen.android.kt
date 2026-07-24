package app.skerry.ui.vault

import java.security.MessageDigest

actual fun sha256Internal(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(bytes)
    return hash.joinToString("") { "%02x".format(it) }
}
