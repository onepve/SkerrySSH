package app.skerry.ui

import java.nio.file.Files
import java.nio.file.Path

/**
 * Fork: startup housekeeping tasks extracted from main.kt to reduce merge conflicts.
 */

/** Delete stale jars from overwrite-installed portable ZIPs. No-op outside a packaged build. */
fun cleanupStaleJars() {
    try {
        val appPath = System.getProperty("jpackage.app-path") ?: return
        val appDir = Path.of(appPath).parent?.resolve("app") ?: return
        if (!Files.isDirectory(appDir)) return
        val cfg = appDir.resolve("Skerry.cfg")
        if (!Files.isRegularFile(cfg)) return
        val listed = Files.readString(cfg)
            .lineSequence()
            .filter { it.startsWith("app.classpath=") }
            .flatMap { it.removePrefix("app.classpath=").splitToSequence(";") }
            .map { it.trim().substringAfterLast('/').substringAfterLast('\\') }
            .filter { it.endsWith(".jar") }
            .toSet()
        if (listed.isEmpty()) return
        Files.list(appDir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar") }
                .filter { it.fileName.toString() !in listed }
                .forEach { runCatching { Files.deleteIfExists(it) } }
        }
    } catch (_: Exception) { }
}
