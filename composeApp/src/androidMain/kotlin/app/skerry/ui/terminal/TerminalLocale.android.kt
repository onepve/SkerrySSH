package app.skerry.ui.terminal

/** System UI locale via the Android `Locale` — matches the app language selector's source. */
actual fun currentLocaleTag(): String? =
    runCatching { java.util.Locale.getDefault().toLanguageTag() }.getOrNull()
