// Top-level build file. Versions live in gradle/libs.versions.toml.
//
// AGP 9.2.x is pinned deliberately: its minimum Gradle is 9.4.1, the
// standalone Gradle already installed on the build machine, and it runs
// Kotlin "built in" (no org.jetbrains.kotlin.android plugin — AGP 9
// rejects it under the new DSL). Only the Compose compiler plugin is
// applied, at the same version as AGP's bundled Kotlin (2.2.10).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
