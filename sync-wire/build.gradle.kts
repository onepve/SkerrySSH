plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
}

group = "app.skerry"
// Same single source of truth as :server — skerry.versionName in gradle.properties.
val versionName = providers.gradleProperty("skerry.versionName").get()
version = versionName

kotlin {
    jvmToolchain(21)
}

dependencies {
    // api: the contract's @Serializable types are visible to consumers together with their serializers.
    api(libs.kotlinx.serialization.json)
}

// Kover coverage — applied via pluginManager; the classpath comes from the root buildscript.
pluginManager.apply("org.jetbrains.kotlinx.kover")
