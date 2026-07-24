package app.skerry.shared.vault

/** Desktop non-ASCII install paths are rejected at startup (see main.kt); ASCII paths use the stock loader. No-op. */
internal actual fun preloadNativeLibIfNeeded() {}
