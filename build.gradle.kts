import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.gradle.versions)
}

private fun String.isNonStableVersion(): Boolean {
    val hasStableKeyword = listOf("RELEASE", "FINAL", "GA").any { uppercase().contains(it) }
    val stableVersion = "^[0-9,.v-]+(-r)?$".toRegex()
    return !hasStableKeyword && !stableVersion.matches(this)
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    revision = "release"
    gradleReleaseChannel = "current"

    // The report comes from live repository lookups: replaying it from the configuration cache
    // would freeze whatever the network answered (or failed to answer) when the entry was stored.
    notCompatibleWithConfigurationCache(
        "dependencyUpdates queries repositories for the latest versions on every run.",
    )

    // Only stable releases are proposed: alphas, betas and release candidates are ignored.
    rejectVersionIf {
        candidate.version.isNonStableVersion()
    }
}
