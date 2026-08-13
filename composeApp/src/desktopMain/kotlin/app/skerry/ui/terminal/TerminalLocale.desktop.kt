package app.skerry.ui.terminal

/** System UI locale via the JVM `Locale` (desktop: OS-level UI language). */
actual fun currentLocaleTag(): String? =
    runCatching { java.util.Locale.getDefault().toLanguageTag() }.getOrNull()
