// Repack the standard .deb for Chinese domestic Linux distributions
// (Galaxy Kylin V10, UnionTech UOS, NARI Linx).
// One unified .deb for all three — the binary and desktop integration are identical;
// only the .desktop Name used to differ, which we now standardise to "Skerry".
//
// UOS-legacy variant: `packageChineseDebUos` repacks with skiko 0.9.17
// (last version needing only GLIBC_2.17), so UnionTech UOS V20 (glibc 2.28)
// can run it. Invoke with `-Pskiko.version=0.9.17` (see build.gradle.kts).
fun registerChineseDebRepack(
    taskName: String,
    publicName: String,
    taskDescription: String,
    versionSuffix: String = "",
) {
    tasks.register<Exec>(taskName) {
        group = "compose desktop"
        description = taskDescription
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
            val dstDeb = debDir.get().file("skerry-xinchuang${versionSuffix}_${appVersion}_${arch}.deb").asFile

            commandLine("bash", repackScript, srcDeb.absolutePath, dstDeb.absolutePath, "chinese")
            stash = dstDeb to arch
        }

        doLast {
            val (dstDeb, arch) = stash ?: error("stash not set")
            val publicDeb = debDir.get().file("$publicName-${arch}.deb").asFile
            dstDeb.copyTo(publicDeb, overwrite = true)
            println("✅ $publicName .deb: ${publicDeb.absolutePath}")
        }
    }
}

// Chinese distro deb (kylin/uos/nari unified) — build with -Pskiko.version=0.9.17 for the
// UOS-legacy (glibc 2.28-compatible) package. Version suffix unused by the unified task.
registerChineseDebRepack(
    taskName = "packageChineseDeb",
    publicName = "Skerry-xinchuang",
    taskDescription = "Repack the .deb for Chinese distros (kylin/uos/nari unified)",
)

// Backward-compatible alias: build the unified Chinese distro deb.
tasks.register("packageChineseDebs") {
    group = "compose desktop"
    description = "Alias for packageChineseDeb (unified kylin/uos/nari deb)"
    dependsOn("packageChineseDeb")
}
