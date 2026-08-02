import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateCascadeEditorVersionTask : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputFile = outputDirectory
            .file("io/github/linreal/cascade/ios/CascadeEditorVersion.generated.kt")
            .get()
            .asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            |package io.github.linreal.cascade.ios
            |
            |internal const val CASCADE_EDITOR_VERSION: String = "${versionName.get()}"
            |
            """.trimMargin()
        )
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.binaryCompatibilityValidator)
    alias(libs.plugins.applePrivacyManifests)
}

// The Swift-facing contract snapshot: api/editor-ios-sdk.klib.api captures every
// public Kotlin declaration of the iOS targets, which is the source the Obj-C
// framework header is generated from — bridge drift fails `apiCheck` (wired into
// `check`). Refresh intentionally with `./gradlew :editor-ios-sdk:apiDump` and
// commit the diff. Note the klib dump does not capture @ObjCName spellings or
// Obj-C-specific lowering; renames visible only in the generated header should be
// eyeballed in CascadeEditor.h when touching @ObjCName annotations.
apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

val cascadeEditorXcframework = XCFramework("CascadeEditor")
val cascadeEditorVersion = providers.gradleProperty("VERSION_NAME").get()
val generatedVersionDirectory = layout.buildDirectory.dir("generated/cascadeVersion/iosMain/kotlin")
val generateCascadeEditorVersion = tasks.register<GenerateCascadeEditorVersionTask>(
    "generateCascadeEditorVersion"
) {
    versionName.set(cascadeEditorVersion)
    outputDirectory.set(generatedVersionDirectory)
}

kotlin {
    explicitApi()
    privacyManifest {
        embed(
            privacyManifest = layout.projectDirectory.file("PrivacyInfo.xcprivacy").asFile,
        )
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "CascadeEditor"
            isStatic = false
            // Favor install/download size for the distributed Release binary.
            // Kotlin/Native maps this experimental option to LLVM -Oz for
            // release linking; benchmark native editor interactions when
            // upgrading Kotlin/Compose because -Oz can trade speed for size.
            binaryOption("smallBinary", "true")
            binaryOption("bundleId", "io.github.linreal.cascade.editor")
            binaryOption("bundleShortVersionString", cascadeEditorVersion)
            binaryOption("bundleVersion", cascadeEditorVersion)
            freeCompilerArgs += "-Xoverride-konan-properties=minVersion.ios=16.0"
            cascadeEditorXcframework.add(this)
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("io.github.linreal.cascade.editor.htmlserialization.ExperimentalCascadeHtmlApi")
        }

        iosMain {
            kotlin.srcDir(files(generatedVersionDirectory).builtBy(generateCascadeEditorVersion))
            dependencies {
                implementation(projects.editor)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val iosSimulatorArm64Test by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

// Golden parity tests read fixture files from the host filesystem. `simctl spawn`
// forwards only SIMCTL_CHILD_-prefixed variables to the spawned test binary, where
// they arrive with the prefix stripped (CASCADE_FIXTURES_DIR / CASCADE_SAMPLE_FILES_DIR).
// Both directories are declared as task inputs so fixture edits re-run the tests.
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    val fixturesDir = projectDir.resolve("src/commonTest/resources")
    val sampleFilesDir = rootDir.resolve("sample/src/commonMain/composeResources/files")
    val iosNativeSampleFilesDir = rootDir.resolve("iosNativeSample/iosNativeSample/Resources")
    inputs.dir(fixturesDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(sampleFilesDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(iosNativeSampleFilesDir).withPathSensitivity(PathSensitivity.RELATIVE)
    environment("SIMCTL_CHILD_CASCADE_FIXTURES_DIR", fixturesDir.absolutePath)
    environment("SIMCTL_CHILD_CASCADE_SAMPLE_FILES_DIR", sampleFilesDir.absolutePath)
    environment(
        "SIMCTL_CHILD_CASCADE_IOS_NATIVE_SAMPLE_FILES_DIR",
        iosNativeSampleFilesDir.absolutePath,
    )
}
