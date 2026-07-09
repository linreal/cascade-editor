package io.github.linreal.cascade.ios.controller

import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.SelectBlock
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.registry.BlockDescriptor
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.ui.LinkPopupSlot
import io.github.linreal.cascade.ios.model.CascadeEditorDocumentBuilder
import io.github.linreal.cascade.ios.model.CascadeSpanKind
import io.github.linreal.cascade.ios.toolbar.CascadeToolbarState
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CascadeEditorControllerTest {
    @Test
    fun defaultConstructorsUseDefaultConfiguration() {
        val configuration = CascadeEditorConfiguration()
        val emptyController = CascadeEditorController()
        val jsonController = CascadeEditorController(initialJson = CascadeEditorDocumentBuilder().paragraph("Seed").buildJson())

        assertEquals(CascadeToolbarMode.builtIn, configuration.toolbarMode)
        assertEquals(CascadeCrashPolicy.containAndReport, configuration.crashPolicy)
        assertEquals(configuration, emptyController.configuration)
        assertEquals("Seed", jsonController.exportPlainText())
    }

    @Test
    fun toolbarAndHistoryCommandsAreUnavailableBeforeMount() {
        val controller = CascadeEditorController(
            initialJson = CascadeEditorDocumentBuilder().paragraph("Seed").buildJson(),
        )

        controller.undo()
        controller.redo()
        controller.toggleBold()
        controller.toggleHighlight(argb = 0xFF00FF00L)
        controller.indentForward()
        controller.applyLink(url = "example.com", title = "Example")
        controller.removeLink()

        assertEquals(CascadeToolbarState.Empty, controller.toolbarState)
        assertFalse(controller.canUndo)
        assertFalse(controller.canRedo)
        assertEquals("Seed", controller.exportPlainText())
    }

    @Test
    fun toggleHighlightDelegatesCallerArgbWhenMounted() {
        val controller = CascadeEditorController()
        var highlightedArgb: Long? = null
        controller.mounted = true
        controller.toolbarActions = CascadeToolbarActions(
            toggleBold = {},
            toggleItalic = {},
            toggleUnderline = {},
            toggleStrikeThrough = {},
            toggleInlineCode = {},
            toggleHighlight = { argb -> highlightedArgb = argb },
            indentForward = {},
            indentBackward = {},
            applyLink = { _, _ -> },
            removeLink = {},
        )

        controller.toggleHighlight(argb = 0xFF00FF00L)

        assertEquals(0xFF00FF00L, highlightedArgb)
    }

    @Test
    fun loadAndExportJsonRoundTrip() {
        val json = CascadeEditorDocumentBuilder()
            .paragraph("Hello")
            .heading(2, "World")
            .buildJson()
        val controller = CascadeEditorController(initialJson = null)

        val result = controller.loadJson(json)

        assertTrue(result.success)
        assertEquals(jsonDocumentText(json), jsonDocumentText(controller.exportJson()))
    }

    @Test
    fun loadJsonDefersCallbacksToHostObserverWhenMounted() {
        val json = CascadeEditorDocumentBuilder()
            .paragraph("Mounted")
            .buildJson()
        val controller = CascadeEditorController(initialJson = null)
        var documentChanges = 0
        var stateChanges = 0
        controller.onDocumentChanged = { documentChanges++ }
        controller.onStateChanged = { stateChanges++ }
        controller.mounted = true

        val result = controller.loadJson(json)

        assertTrue(result.success)
        assertEquals("Mounted", controller.exportPlainText())
        assertEquals(0, documentChanges)
        assertEquals(0, stateChanges)
    }

    @Test
    fun loadJsonNotifiesDirectlyWhenUnmounted() {
        val json = CascadeEditorDocumentBuilder()
            .paragraph("Unmounted")
            .buildJson()
        val controller = CascadeEditorController(initialJson = null)
        var documentChanges = 0
        var stateChanges = 0
        controller.onDocumentChanged = { documentChanges++ }
        controller.onStateChanged = { stateChanges++ }

        val result = controller.loadJson(json)

        assertTrue(result.success)
        assertEquals("Unmounted", controller.exportPlainText())
        assertEquals(1, documentChanges)
        assertEquals(1, stateChanges)
    }

    @Test
    fun deleteSelectedOrFocusedDefersCallbacksToHostObserverWhenMounted() {
        val controller = CascadeEditorController(
            initialJson = CascadeEditorDocumentBuilder()
                .paragraph("Delete")
                .paragraph("Keep")
                .buildJson(),
        )
        controller.stateHolder.dispatch(FocusBlock(controller.stateHolder.state.blocks.first().id))
        var documentChanges = 0
        var stateChanges = 0
        controller.onDocumentChanged = { documentChanges++ }
        controller.onStateChanged = { stateChanges++ }
        controller.mounted = true

        controller.deleteSelectedOrFocused()

        assertEquals("Keep", controller.exportPlainText())
        assertEquals(0, documentChanges)
        assertEquals(0, stateChanges)
    }

    @Test
    fun deleteSelectedOrFocusedNotifiesDirectlyWhenUnmounted() {
        val controller = CascadeEditorController(
            initialJson = CascadeEditorDocumentBuilder()
                .paragraph("Delete")
                .paragraph("Keep")
                .buildJson(),
        )
        controller.stateHolder.dispatch(FocusBlock(controller.stateHolder.state.blocks.first().id))
        var documentChanges = 0
        var stateChanges = 0
        controller.onDocumentChanged = { documentChanges++ }
        controller.onStateChanged = { stateChanges++ }

        controller.deleteSelectedOrFocused()

        assertEquals("Keep", controller.exportPlainText())
        assertEquals(1, documentChanges)
        assertEquals(1, stateChanges)
    }

    @Test
    fun clearFocusDefersStateCallbackToHostObserverWhenMounted() {
        val controller = CascadeEditorController(
            initialJson = CascadeEditorDocumentBuilder().paragraph("Focused").buildJson(),
        )
        controller.stateHolder.dispatch(FocusBlock(controller.stateHolder.state.blocks.first().id))
        var stateChanges = 0
        controller.onStateChanged = { stateChanges++ }
        controller.mounted = true

        controller.clearFocus()

        assertEquals(0, stateChanges)
    }

    @Test
    fun clearFocusNotifiesDirectlyWhenUnmounted() {
        val controller = CascadeEditorController(
            initialJson = CascadeEditorDocumentBuilder().paragraph("Focused").buildJson(),
        )
        controller.stateHolder.dispatch(FocusBlock(controller.stateHolder.state.blocks.first().id))
        var stateChanges = 0
        controller.onStateChanged = { stateChanges++ }

        controller.clearFocus()

        assertEquals(1, stateChanges)
    }

    @Test
    fun malformedJsonReturnsWarningInsteadOfThrowing() {
        val controller = CascadeEditorController(initialJson = null)

        val result = controller.loadJson("{")

        assertFalse(result.success)
        assertTrue(result.warningMessages.isNotEmpty())
    }

    @Test
    fun failedLoadJsonPreservesTheCurrentDocument() {
        val controller = CascadeEditorController(
            initialJson = CascadeEditorDocumentBuilder().paragraph("Keep me").buildJson(),
        )
        var documentChanges = 0
        controller.onDocumentChanged = { documentChanges++ }

        val result = controller.loadJson("{ not json")

        assertFalse(result.success)
        // A payload that failed to parse must not destroy the loaded document,
        // and a load that changed nothing must not signal a document change.
        assertEquals("Keep me", controller.exportPlainText())
        assertEquals(0, documentChanges)
    }

    @Test
    fun exportPlainTextOmitsNonTextBlocks() {
        val json = CascadeEditorDocumentBuilder()
            .paragraph("One")
            .divider()
            .paragraph("Two")
            .customBlock("metric", """{"label":"Revenue"}""")
            .paragraph("Three")
            .buildJson()
        val controller = CascadeEditorController(initialJson = json)

        // Dividers and custom blocks carry no user-visible text and must not
        // contribute blank lines to the plain-text export.
        assertEquals("One\nTwo\nThree", controller.exportPlainText())
        // Rich text still exposes every block as a typed run.
        assertEquals(5, controller.exportRichText().blocks.size)
    }

    @Test
    fun offMainExportHopsToMainAndReturnsTheRealDocument() {
        val controller = CascadeEditorController(
            initialJson = CascadeEditorDocumentBuilder().paragraph("Background").buildJson(),
        )
        var exported: String? = null
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            exported = controller.exportPlainText()
        }

        // The off-main export dispatch_syncs onto the main queue; pumping the
        // main run loop services that hop (blocking the main thread on the
        // background caller — like runOffMain does — would deadlock it).
        drainMainQueue { exported != null }

        assertEquals("Background", exported)
    }

    @Test
    fun plainTextAndRichTextExportUseDocumentOrder() {
        val json = CascadeEditorDocumentBuilder()
            .paragraph("One")
            .paragraph("Two")
            .buildJson()
        val controller = CascadeEditorController(initialJson = json)

        assertEquals("One\nTwo", controller.exportPlainText())
        assertEquals(listOf("One", "Two"), controller.exportRichText().blocks.map { it.text })
    }

    @Test
    fun richTextExportMapsSupportedSpansAndOmitsCustomSpans() {
        val json = DocumentSchema.encodeToString(
            listOf(
                Block.paragraph(
                    text = "Styled link custom",
                    spans = listOf(
                        TextSpan(0, 6, SpanStyle.Bold),
                        TextSpan(7, 11, SpanStyle.Link("https://example.com")),
                        TextSpan(12, 18, SpanStyle.Custom("native-only")),
                    ),
                ),
            ),
        )
        val controller = CascadeEditorController(initialJson = json)

        val block = controller.exportRichText().blocks.first()

        assertEquals("Styled link custom", block.text)
        assertEquals(listOf(CascadeSpanKind.bold, CascadeSpanKind.link), block.spans.map { it.kind })
        assertEquals("https://example.com", block.spans[1].url)
    }

    @Test
    fun deleteSelectedOrFocusedNoOpsWhenReadOnly() {
        val json = CascadeEditorDocumentBuilder()
            .paragraph("Keep me")
            .buildJson()
        val controller = CascadeEditorController(
            initialJson = json,
            configuration = CascadeEditorConfiguration().copy(readOnly = true),
        )
        val focusedBlockId = controller.stateHolder.state.blocks.first().id
        controller.stateHolder.dispatch(FocusBlock(focusedBlockId))

        controller.deleteSelectedOrFocused()

        assertEquals("Keep me", controller.exportPlainText())
    }

    @Test
    fun loadHtmlDecoderExceptionWarningIsFailure() {
        val warnings = listOf(
            HtmlDecodeWarning.DecoderException(
                tag = "custom",
                message = "boom",
                charOffset = 0,
            ),
        )

        assertFalse(warnings.isSuccessfulHtmlLoad())
    }

    @Test
    fun richTextExportClampsOutOfRangeSnapshotSpans() {
        val json = DocumentSchema.encodeToString(
            listOf(
                Block.paragraph(
                    text = "Clip",
                    spans = listOf(
                        TextSpan(1, 99, SpanStyle.Bold),
                        TextSpan(8, 12, SpanStyle.Italic),
                    ),
                ),
            ),
        )
        val controller = CascadeEditorController(initialJson = json)

        val spans = controller.exportRichText().blocks.first().spans

        assertEquals(1, spans.size)
        assertEquals(CascadeSpanKind.bold, spans.single().kind)
        assertEquals(1, spans.single().start)
        assertEquals(4, spans.single().end)
    }

    @Test
    fun documentChangedCallbackExceptionsAreContained() {
        val controller = CascadeEditorController()
        var internalError: String? = null
        controller.onDocumentChanged = { error("callback failed") }
        controller.onInternalError = { message -> internalError = message }

        val result = controller.loadJson(CascadeEditorDocumentBuilder().paragraph("Safe").buildJson())

        assertTrue(result.success)
        assertEquals("Safe", controller.exportPlainText())
        assertTrue(assertNotNull(internalError).contains("onDocumentChanged"))
    }

    @Test
    fun offMainConfigurationGetterReportsInternalErrorAndReturnsDefaultConfiguration() {
        val controller = CascadeEditorController(
            initialJson = null,
            configuration = CascadeEditorConfiguration().copy(readOnly = true),
        )
        var internalError: String? = null
        var deliveredOnMainThread: Boolean? = null
        controller.onInternalError = { message ->
            internalError = message
            deliveredOnMainThread = NSThread.isMainThread
        }

        val configuration = runOffMain {
            controller.configuration
        }

        assertEquals(CascadeEditorConfiguration(), configuration)
        // The misuse report is marshaled onto the main thread, so it is not yet
        // delivered when the off-main call returns; it arrives once the main queue drains.
        assertNull(internalError, "off-main error must not be delivered synchronously off-main")
        drainMainQueue { internalError != null }
        assertEquals("CascadeEditorController must be used on the main thread", internalError)
        assertEquals(true, deliveredOnMainThread, "callbacks must be delivered on the main thread")
    }

    @Test
    fun builtInToolbarUsesDefaultLinkPopup() {
        assertEquals(LinkPopupSlot.Default, CascadeToolbarMode.builtIn.toEditorLinkPopupSlot())
        assertEquals(LinkPopupSlot.None, CascadeToolbarMode.none.toEditorLinkPopupSlot())
    }

    @Test
    fun hasSelectionStaysConsistentWithSelectedBlockCount() {
        val controller = CascadeEditorController(
            initialJson = CascadeEditorDocumentBuilder()
                .paragraph("One")
                .paragraph("Two")
                .buildJson(),
        )
        val firstBlockId = controller.stateHolder.state.blocks.first().id

        controller.stateHolder.dispatch(SelectBlock(firstBlockId))
        assertTrue(controller.hasSelection)
        assertEquals(1, controller.selectedBlockCount)

        controller.clearSelection()
        assertFalse(controller.hasSelection)
        assertEquals(0, controller.selectedBlockCount)
    }

    @Test
    fun offMainHasSelectionReportsInternalErrorAndReturnsFalse() {
        val controller = CascadeEditorController(
            initialJson = CascadeEditorDocumentBuilder().paragraph("One").buildJson(),
        )
        controller.stateHolder.dispatch(SelectBlock(controller.stateHolder.state.blocks.first().id))
        var internalError: String? = null
        var deliveredOnMainThread: Boolean? = null
        controller.onInternalError = { message ->
            internalError = message
            deliveredOnMainThread = NSThread.isMainThread
        }

        val hasSelection = runOffMain { controller.hasSelection }

        assertFalse(hasSelection)
        assertNull(internalError, "off-main error must not be delivered synchronously off-main")
        drainMainQueue { internalError != null }
        assertEquals("CascadeEditorController must be used on the main thread", internalError)
        assertEquals(true, deliveredOnMainThread, "callbacks must be delivered on the main thread")
    }

    @Test
    fun documentChangeSignalReflectsRuntimeTextEditsWithoutSerialization() {
        val controller = CascadeEditorController(
            initialJson = CascadeEditorDocumentBuilder().paragraph("Hello").buildJson(),
        )
        val blockId = controller.stateHolder.state.blocks.first().id
        controller.textStates.getOrCreate(blockId, "Hello")

        val before = controller.currentDocumentBlocks()
        controller.textStates.setText(blockId, "Hello world")
        val after = controller.currentDocumentBlocks()

        // The change signal is the resolved block list — no JSON encoding — and it
        // reflects runtime text edits that never touch EditorState identity.
        assertNotEquals(before, after)
        assertEquals(0, controller.selectedBlockCount)
    }

    @Test
    fun controllerOwnsStableRegistriesSeededWithBuiltIns() {
        val controller = CascadeEditorController()
        val registryInstance = controller.registry
        val slashInstance = controller.slashRegistry

        // Seeded from createEditorRegistry(); slash registry starts empty (built-ins
        // are merged in by the hosted editor).
        assertTrue(controller.registry.isRegistered("paragraph"))
        assertTrue(controller.slashRegistry.getRootItems().isEmpty())

        // A registration made before first mount survives on the same stable instance.
        controller.registry.registerDescriptor(
            BlockDescriptor(
                typeId = "stub-block",
                displayName = "Stub",
                description = "",
                factory = { id -> Block(id, BlockType.Paragraph, BlockContent.Text("")) },
            ),
        )

        assertSame(registryInstance, controller.registry)
        assertSame(slashInstance, controller.slashRegistry)
        assertTrue(controller.registry.isRegistered("stub-block"))
    }

    @Test
    fun setDarkModeSwitchesTheResolvedEditorTheme() {
        val controller = CascadeEditorController()

        // configurationSnapshot + resolveEditorTheme is exactly what the hosted
        // view recomposes the mounted theme from, so asserting through them pins
        // live dark-mode switching headlessly.
        assertEquals(CascadeEditorTheme.light(), controller.configurationSnapshot.value.resolveEditorTheme())

        controller.setDarkMode(true)
        assertEquals(CascadeEditorTheme.dark(), controller.configurationSnapshot.value.resolveEditorTheme())

        controller.setDarkMode(false)
        assertEquals(CascadeEditorTheme.light(), controller.configurationSnapshot.value.resolveEditorTheme())
    }

    private fun jsonDocumentText(json: String): String =
        CascadeEditorController(initialJson = json).exportPlainText()

    /**
     * Pumps the main run loop (the test runs on the main thread) until [until] holds or
     * the timeout elapses, so blocks dispatched to the main queue — e.g. an off-main
     * error report marshaled back onto main — get a chance to execute.
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
