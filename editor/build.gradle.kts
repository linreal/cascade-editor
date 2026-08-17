import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktechPublish)
    alias(libs.plugins.binaryCompatibilityValidator)
}

// Public-API snapshots under api/ are verified by `apiCheck` (wired into `check`):
// api/desktop/editor.api covers the JVM surface (BCV does not hook the AGP 9 KMP
// android target, but its API is identical to desktop's), .klib.api covers
// klib-backed targets (iOS/wasm) at Kotlin-declaration level. After an intentional
// API change, refresh the baseline with `./gradlew :editor:apiDump` and commit the diff.
apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

kotlin {
    explicitApi()

    android {
        namespace = "io.github.linreal.cascade.editor"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        // Compose Multiplatform resources are packaged through the Android
        // resource pipeline; without this they are silently dropped (CMP-9547)
        androidResources { enable = true }

        // androidHostTest runs commonTest against the android/ framework stubs
        withHostTest {}

        optimization {
            consumerKeepRules.file("consumer-rules.pro")
            consumerKeepRules.publish = true
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "CascadeEditorCore"
            isStatic = true
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("io.github.linreal.cascade.editor.htmlserialization.ExperimentalCascadeHtmlApi")
            languageSettings.optIn("io.github.linreal.cascade.editor.markdown.ExperimentalCascadeMarkdownApi")
        }

        commonMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jetbrains.markdown)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.compose.ui.test)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)

    signAllPublications()

    pom {
        name.set("CascadeEditor")
        description.set("Block-based editor (Craft/Notion-like) for Compose Multiplatform")
        url.set("https://github.com/linreal/cascade-editor")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("linreal")
                name.set("Sergey Drymchenko")
                url.set("https://github.com/linreal")
            }
        }

        scm {
            url.set("https://github.com/linreal/cascade-editor")
            connection.set("scm:git:git://github.com/linreal/cascade-editor.git")
            developerConnection.set("scm:git:ssh://git@github.com/linreal/cascade-editor.git")
        }
    }
}
