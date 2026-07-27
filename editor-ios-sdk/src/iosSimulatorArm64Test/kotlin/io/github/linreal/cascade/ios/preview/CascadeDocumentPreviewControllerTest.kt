@file:OptIn(io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi::class)

package io.github.linreal.cascade.ios.preview

import androidx.compose.ui.text.style.TextOverflow
import io.github.linreal.cascade.editor.CrashPolicy
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.ios.controller.CascadeCrashPolicy
import io.github.linreal.cascade.ios.model.CascadeEditorDocumentBuilder
import io.github.linreal.cascade.ios.theme.CascadeEditorColors
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CascadeDocumentPreviewControllerTest {
    @Test
    fun defaultConfigurationMapsToBoundedCoreGridCardPolicy() {
        val configuration = CascadeDocumentPreviewConfiguration()

        assertEquals(4, configuration.maxBlocks)
        assertEquals(3, configuration.maxLinesPerTextBlock)
        assertEquals(1.0, configuration.textScale)
        assertFalse(configuration.textSelectionEnabled)
        assertTrue(configuration.linksEnabled)
        assertFalse(configuration.isDark)
        assertEquals(CascadeCrashPolicy.containAndReport, configuration.crashPolicy)

        val core = configuration.toCoreConfig()

        assertEquals(4, core.maxBlocks)
        assertEquals(3, core.maxLinesPerTextBlock)
        assertEquals(1f, core.textScale)
        assertEquals(TextOverflow.Ellipsis, core.textOverflow)
        assertFalse(core.textSelectionEnabled)
        assertTrue(core.linksEnabled)
        assertEquals(CrashPolicy.ContainAndReport, core.crashPolicy)
    }

    @Test
    fun legacySwiftFacingInitializerKeepsTheDefaultTextScale() {
        val configuration = CascadeDocumentPreviewConfiguration(
            maxBlocks = 2,
            maxLinesPerTextBlock = 1,
            textSelectionEnabled = true,
            linksEnabled = false,
            isDark = true,
            crashPolicy = CascadeCrashPolicy.rethrow,
        )

        assertEquals(1.0, configuration.textScale)
        assertEquals(1f, configuration.toCoreConfig().textScale)
    }

    @Test
    fun explicitConfigurationMapsEverySwiftFacingPreviewPolicy() {
        val configuration = CascadeDocumentPreviewConfiguration(
            maxBlocks = 2,
            maxLinesPerTextBlock = 1,
            textScale = 0.8,
            textSelectionEnabled = true,
            linksEnabled = false,
            isDark = true,
            crashPolicy = CascadeCrashPolicy.rethrow,
        )

        val core = configuration.toCoreConfig()

        assertEquals(2, core.maxBlocks)
        assertEquals(1, core.maxLinesPerTextBlock)
        assertEquals(0.8f, core.textScale)
        assertEquals(TextOverflow.Ellipsis, core.textOverflow)
        assertTrue(core.textSelectionEnabled)
        assertFalse(core.linksEnabled)
        assertEquals(CrashPolicy.Rethrow, core.crashPolicy)
    }

    @Test
    fun nonPositiveSwiftLimitsNormalizeToSafePositiveBounds() {
        val configuration = CascadeDocumentPreviewConfiguration(
            maxBlocks = 0,
            maxLinesPerTextBlock = -12,
            textScale = 0.75,
            textSelectionEnabled = false,
            linksEnabled = true,
            isDark = false,
            crashPolicy = CascadeCrashPolicy.containAndReport,
        )

        val core = configuration.toCoreConfig()

        assertEquals(1, core.maxBlocks)
        assertEquals(1, core.maxLinesPerTextBlock)
        assertEquals(0.75f, core.textScale)
    }

    @Test
    fun invalidOrCoreUnrepresentableSwiftTextScalesNormalizeToOne() {
        listOf(
            0.0,
            -1.0,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.MIN_VALUE,
            Double.MAX_VALUE,
        ).forEach { invalidScale ->
            val core = CascadeDocumentPreviewConfiguration()
                .copy(textScale = invalidScale)
                .toCoreConfig()

            assertEquals(1f, core.textScale, "textScale=$invalidScale")
        }
    }

    @Test
    fun validJsonLoadReplacesTheImmutablePreviewSnapshot() {
        val controller = CascadeDocumentPreviewController()
        val json = CascadeEditorDocumentBuilder()
            .heading(level = 2, text = "Field notes")
            .paragraph("Previewed without editor runtime")
            .buildJson()

        val result = controller.loadJson(json)

        assertTrue(result.success)
        assertEquals(2, controller.blocksSnapshot.value.size)
        assertEquals(
            "Field notes",
            assertIs<BlockContent.Text>(controller.blocksSnapshot.value[0].content).text,
        )
        assertEquals(
            "Previewed without editor runtime",
            assertIs<BlockContent.Text>(controller.blocksSnapshot.value[1].content).text,
        )
    }

    @Test
    fun malformedJsonPreservesThePreviouslyLoadedPreviewSnapshot() {
        val initialJson = CascadeEditorDocumentBuilder()
            .paragraph("Keep this preview")
            .buildJson()
        val controller = CascadeDocumentPreviewController()
        assertTrue(controller.loadJson(initialJson).success)
        val originalBlocks = controller.blocksSnapshot.value

        val result = controller.loadJson("{ not valid json")

        assertFalse(result.success)
        assertTrue(result.warningMessages.isNotEmpty())
        assertSame(originalBlocks, controller.blocksSnapshot.value)
        assertEquals(
            "Keep this preview",
            assertIs<BlockContent.Text>(controller.blocksSnapshot.value.single().content).text,
        )
    }

    @Test
    fun runtimeConfigurationUpdatesPublishOneCompleteSnapshot() {
        val controller = CascadeDocumentPreviewController()
        val updated = CascadeDocumentPreviewConfiguration(
            maxBlocks = 6,
            maxLinesPerTextBlock = 2,
            textScale = 0.7,
            textSelectionEnabled = true,
            linksEnabled = false,
            isDark = true,
            crashPolicy = CascadeCrashPolicy.rethrow,
        )

        controller.updateConfiguration(updated)

        assertEquals(updated, controller.configuration)
        assertEquals(updated, controller.configurationSnapshot.value)
    }

    @Test
    fun setDarkModePreservesEveryOtherPreviewSetting() {
        val initial = CascadeDocumentPreviewConfiguration(
            maxBlocks = 7,
            maxLinesPerTextBlock = 5,
            textScale = 0.65,
            textSelectionEnabled = true,
            linksEnabled = false,
            isDark = false,
            crashPolicy = CascadeCrashPolicy.rethrow,
        )
        val controller = CascadeDocumentPreviewController(configuration = initial)

        controller.setDarkMode(true)

        assertEquals(initial.copy(isDark = true), controller.configuration)
        assertEquals(initial.copy(isDark = true), controller.configurationSnapshot.value)
    }

    @Test
    fun controllerSnapshotsRuntimeColorsAndCanRestorePresetSelection() {
        val controller = CascadeDocumentPreviewController()
        val colors = CascadeEditorColors()
        colors.primary = 0xFF123456L
        val expected = colors.snapshot()

        controller.setColors(colors)

        assertEquals(expected, controller.customColorsSnapshot.value)
        assertEquals(
            expected,
            controller.configurationSnapshot.value.resolveEditorTheme(expected).colors,
        )

        colors.primary = 0xFFABCDEFL
        assertEquals(expected, controller.customColorsSnapshot.value)

        controller.setDarkMode(true)
        assertEquals(
            expected,
            controller.configurationSnapshot.value.resolveEditorTheme(expected).colors,
        )

        controller.clearCustomColors()
        assertNull(controller.customColorsSnapshot.value)
        assertEquals(
            CascadeEditorTheme.dark(),
            controller.configurationSnapshot.value.resolveEditorTheme(),
        )
    }

    @Test
    fun linkCallbackCapabilityTracksRegistrationAndDelegatesTheTarget() {
        val controller = CascadeDocumentPreviewController()
        var openedTarget: String? = null

        assertFalse(controller.hasLinkOpenerSnapshot.value)

        controller.onOpenLink = { target -> openedTarget = target }
        controller.openLink("https://example.com/field-note")

        assertTrue(controller.hasLinkOpenerSnapshot.value)
        assertEquals("https://example.com/field-note", openedTarget)

        controller.onOpenLink = null

        assertFalse(controller.hasLinkOpenerSnapshot.value)
    }

    @Test
    fun linkCallbackFailureIsContainedAndReported() {
        val controller = CascadeDocumentPreviewController()
        var internalError: String? = null
        controller.onOpenLink = { error("host link callback failed") }
        controller.onInternalError = { message -> internalError = message }

        controller.openLink("https://example.com")

        val error = assertIs<String>(internalError)
        assertTrue(error.contains("onOpenLink"))
        assertTrue(error.contains("host link callback failed"))
    }

    @Test
    fun internalErrorCallbackFailureCannotEscapeTheNativeBoundary() {
        val controller = CascadeDocumentPreviewController()
        var callbackInvoked = false
        controller.onInternalError = {
            callbackInvoked = true
            error("telemetry failed")
        }

        controller.reportInternalError("contained preview failure")

        assertTrue(callbackInvoked)
    }

    @Test
    fun callbackPropertiesRejectOffMainAccessAndPreserveRegisteredCallbacks() {
        val controller = CascadeDocumentPreviewController()
        val errors = mutableListOf<String>()
        val deliveryThreads = mutableListOf<Boolean>()
        val linkCallback: (String) -> Unit = {}
        val errorCallback: (String) -> Unit = { message ->
            errors += message
            deliveryThreads += NSThread.isMainThread
        }
        controller.onOpenLink = linkCallback
        controller.onInternalError = errorCallback

        val offMainCallbacks = runOffMain {
            controller.onOpenLink to controller.onInternalError
        }
        runOffMain {
            controller.onOpenLink = {}
            controller.onInternalError = {}
        }

        assertNull(offMainCallbacks.first)
        assertNull(offMainCallbacks.second)
        assertSame(linkCallback, controller.onOpenLink)
        assertSame(errorCallback, controller.onInternalError)
        assertTrue(controller.hasLinkOpenerSnapshot.value)

        drainMainQueue { errors.size == 4 }

        assertEquals(
            List(4) { "CascadeDocumentPreviewController must be used on the main thread" },
            errors,
        )
        assertTrue(deliveryThreads.all { deliveredOnMain -> deliveredOnMain })
    }

    @Test
    fun unknownCustomDocumentDataIsPreservedForTheSafePreviewFallback() {
        val controller = CascadeDocumentPreviewController()
        val json =
            """
            {
              "version": 2,
              "blocks": [
                {
                  "id": "metric-1",
                  "type": {
                    "typeId": "metric",
                    "custom": true,
                    "schemaVersion": 3
                  },
                  "content": {
                    "kind": "metric",
                    "data": {
                      "label": "Adoption",
                      "value": 42
                    }
                  }
                }
              ]
            }
            """.trimIndent()

        val result = controller.loadJson(json)

        assertTrue(result.success)
        assertTrue(result.warningMessages.any { warning -> warning.contains("metric") })
        val block = controller.blocksSnapshot.value.single()
        val type = assertIs<UnknownBlockType>(block.type)
        assertEquals("metric", type.typeId)
        assertTrue(type.rawTypeJson.contains("\"schemaVersion\":3"))
        val content = assertIs<BlockContent.Custom>(block.content)
        assertEquals("metric", content.typeId)
        assertEquals("Adoption", content.data["label"])
        assertEquals(42L, content.data["value"])
    }

    /**
     * Pumps the main run loop until [until] holds or the timeout elapses, so
     * off-main misuse reports dispatched to the main queue can be delivered.
     */
    private fun drainMainQueue(timeoutSeconds: Double = 2.0, until: () -> Boolean) {
        val runLoop = NSRunLoop.mainRunLoop
        var elapsed = 0.0
        val step = 0.02
        while (!until() && elapsed < timeoutSeconds) {
            runLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(step))
            elapsed += step
        }
    }

    private fun <T> runOffMain(block: () -> T): T {
        val semaphore = dispatch_semaphore_create(0)
        var result: Result<T>? = null
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            result = runCatching(block)
            dispatch_semaphore_signal(semaphore)
        }
        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)
        return result!!.getOrThrow()
    }
}
