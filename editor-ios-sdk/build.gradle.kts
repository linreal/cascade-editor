import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.binaryCompatibilityValidator)
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

kotlin {
    explicitApi()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "CascadeEditor"
            isStatic = true
            cascadeEditorXcframework.add(this)
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("io.github.linreal.cascade.editor.htmlserialization.ExperimentalCascadeHtmlApi")
        }

        iosMain.dependencies {
            implementation(projects.editor)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.serialization.json)
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
    inputs.dir(fixturesDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(sampleFilesDir).withPathSensitivity(PathSensitivity.RELATIVE)
    environment("SIMCTL_CHILD_CASCADE_FIXTURES_DIR", fixturesDir.absolutePath)
    environment("SIMCTL_CHILD_CASCADE_SAMPLE_FILES_DIR", sampleFilesDir.absolutePath)
}
