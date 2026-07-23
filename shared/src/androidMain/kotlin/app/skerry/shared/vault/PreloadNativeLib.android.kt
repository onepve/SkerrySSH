package app.skerry.shared.vault

/** Android loads libsodium from the APK's jniLibs — no jar resource loading involved, no-op. */
internal actual fun preloadNativeLibIfNeeded() {}
