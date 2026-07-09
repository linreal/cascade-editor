plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.vanniktechPublish) apply false
    // Applied in :editor and :editor-ios-sdk. Baseline dumps live in each module's
    // api/ directory; regenerate with `./gradlew :editor:apiDump :editor-ios-sdk:apiDump`
    // after an intentional public-API change (`apiCheck` runs as part of `check`).
    alias(libs.plugins.binaryCompatibilityValidator) apply false
}