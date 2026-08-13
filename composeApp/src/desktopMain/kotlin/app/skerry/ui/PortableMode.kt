package app.skerry.ui

import app.skerry.shared.io.PrivateConfig
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fork: portable mode detection, config/data directory resolution.
 * Extracted from main.kt to avoid merge conflicts with upstream startup logic. Upstream main.kt's
 * [configDir]/[dataDir] delegate here, so the portable-mode / Windows-exe-dir logic stays the
 * single source of truth for where config and data live.
 */

private val portableDir: Path? by lazy {
    val marker = resolveAppDir()?.resolve("portable")
    if (marker != null && Files.isDirectory(marker)) marker else null
}

private fun resolveAppDir(): Path? {
    System.getProperty("jpackage.app-path")?.let {
        val launcherDir = Path.of(it).parent
        return if (launcherDir?.fileName?.toString() == "bin") launcherDir.parent else launcherDir
    }
    try {
        val jarUrl = object {}.javaClass.protectionDomain?.codeSource?.location
        if (jarUrl != null && jarUrl.protocol == "file") {
            val jarPath = Path.of(jarUrl.toURI())
            return if (Files.isDirectory(jarPath)) jarPath else jarPath.parent
        }
    } catch (_: Exception) { }
    return null
}

internal fun portableConfigDir(): Path {
    portableDir?.let { return it.also { Files.createDirectories(it) } }
    val os = System.getProperty("os.name", "").lowercase()
    if (os.contains("win")) {
        val exeDir = appDirectory()
        if (Files.isWritable(exeDir)) {
            return exeDir.resolve("config").also { PrivateConfig.ensureDir(it) }
        }
        val localAppData = System.getenv("LOCALAPPDATA")
        if (localAppData != null) {
            return Path.of(localAppData, "Skerry", "config").also { Files.createDirectories(it) }
        }
    }
    val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
    val base = xdg?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".config")
    return base.resolve("skerry").also { PrivateConfig.ensureDir(it) }
}

/** jpackage install root: the app's own directory (Windows) or the parent of `bin/` (Linux). */
private fun appDirectory(): Path = resolveAppDir() ?: Path.of(System.getProperty("user.dir"))

internal fun portableDataDir(): Path {
    portableDir?.let { return it }
    val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
    val base = xdg?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".local", "share")
    return base.resolve("skerry").also { Files.createDirectories(it) }
}
