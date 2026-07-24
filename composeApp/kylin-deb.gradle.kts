// Repack the standard .deb for Chinese domestic Linux distributions
// (Galaxy Kylin V10, UnionTech UOS, NARI Linx).
// One unified .deb for all three — the binary and desktop integration are identical;
// only the .desktop Name used to differ, which we now standardise to "Skerry".
tasks.register<Exec>("packageChineseDeb") {
    group = "compose desktop"
    description = "Repack the .deb for Chinese distros (kylin/uos/nari unified)"
    dependsOn("packageDeb")

    val appVersion = providers.gradleProperty("skerry.versionName").orNull ?: "0.1.0"
    val debDir = layout.buildDirectory.dir("compose/binaries/main/deb")
    val repackScript = project.file("package-kylin-deb.sh").absolutePath

    var stash: Pair<java.io.File, String>? = null

    doFirst {
        val dir = debDir.get().asFile
        if (!dir.isDirectory) throw GradleException("deb output directory not found: ${dir.absolutePath}")
        val srcDeb = dir.listFiles()?.firstOrNull {
            it.name.matches(Regex("skerry_${Regex.escape(appVersion)}_(amd64|arm64)\\.deb"))
        } ?: throw GradleException("No source .deb found in ${dir.absolutePath}. Run packageDeb first.")
        val arch = srcDeb.nameWithoutExtension.substringAfterLast("_")
        val dstDeb = debDir.get().file("skerry-xinchuang_${appVersion}_${arch}.deb").asFile

        commandLine("bash", repackScript, srcDeb.absolutePath, dstDeb.absolutePath, "chinese")
        stash = dstDeb to arch
    }

    doLast {
        val (dstDeb, arch) = stash ?: error("stash not set")
        val publicDeb = debDir.get().file("Skerry-xinchuang-${arch}.deb").asFile
        dstDeb.copyTo(publicDeb, overwrite = true)
        println("✅ xinchuang public .deb: ${publicDeb.absolutePath}")
    }
}

// Backward-compatible alias: build the unified Chinese distro deb.
tasks.register("packageChineseDebs") {
    group = "compose desktop"
    description = "Alias for packageChineseDeb (unified kylin/uos/nari deb)"
    dependsOn("packageChineseDeb")
}
