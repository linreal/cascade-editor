package io.github.linreal.cascade.ios.block

import io.github.linreal.cascade.editor.action.DeleteBlock
import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.InsertBlockAfter
import io.github.linreal.cascade.editor.action.InsertBlockBefore
import io.github.linreal.cascade.editor.action.ReplaceBlock
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.registry.BlockRenderScope
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import io.github.linreal.cascade.ios.controller.CascadeEditorController
import io.github.linreal.cascade.ios.model.CascadeEditorDocumentBuilder
import platform.UIKit.UIViewController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NativeCustomBlockTest {

    @Test
    fun registeredCustomBlockDecodesToNativeTypeAndRoundTripsPayload() {
        val controller = CascadeEditorController()
        controller.registerBlock(tableRegistration())

        val json = CascadeEditorDocumentBuilder()
            .customBlock("table", """{"rows":2,"header":true}""")
            .buildJson()

        val loadResult = controller.loadJson(json)
        assertTrue(loadResult.success)

        // A non-text block gets a trailing editable paragraph appended by the editor.
        assertEquals(2, controller.stateHolder.state.blocks.size)
        val block = controller.stateHolder.state.blocks.first()
        val type = assertIs<NativeCustomBlockType>(block.type)
        assertEquals("table", type.typeId)

        val data = assertIs<BlockContent.Custom>(block.content).data
        assertEquals(2L, data["rows"])
        assertEquals(true, data["header"])

        // Re-export and reload: type + payload survive the round-trip.
        val reloaded = CascadeEditorController()
        reloaded.registerBlock(tableRegistration())
        reloaded.loadJson(controller.exportJson())
        val reloadedType = assertIs<NativeCustomBlockType>(reloaded.stateHolder.state.blocks.first().type)
        assertEquals("table", reloadedType.typeId)
        assertEquals(
            2L,
            assertIs<BlockContent.Custom>(reloaded.stateHolder.state.blocks.first().content).data["rows"],
        )
    }

    @Test
    fun unregisteredCustomTypeStaysUnknownBlockType() {
        val controller = CascadeEditorController()
        val json = CascadeEditorDocumentBuilder()
            .customBlock("table", """{"rows":1}""")
            .buildJson()

        controller.loadJson(json)

        assertIs<UnknownBlockType>(controller.stateHolder.state.blocks.first().type)
    }

    @Test
    fun updatePayloadJsonMergesFieldAndUndoRevertsIt() {
        val controller = CascadeEditorController()
        controller.registerBlock(tableRegistration())
        controller.loadJson(
            CascadeEditorDocumentBuilder()
                .customBlock("table", """{"rows":2,"header":true}""")
                .buildJson(),
        )
        controller.mounted = true
        val blockId = controller.stateHolder.state.blocks.first().id
        val context = context(controller, blockId)

        val result = context.updatePayloadJson("""{"rows":5}""")

        assertEquals(CascadeCustomBlockMutationResult.success, result)
        val afterData = assertIs<BlockContent.Custom>(
            controller.stateHolder.state.getBlock(blockId)!!.content,
        ).data
        assertEquals(5L, afterData["rows"])
        assertEquals(true, afterData["header"], "merge preserves untouched fields")

        controller.undo()

        val revertedData = assertIs<BlockContent.Custom>(
            controller.stateHolder.state.getBlock(blockId)!!.content,
        ).data
        assertEquals(2L, revertedData["rows"])
    }

    @Test
    fun replacePayloadJsonReplacesEntirePayload() {
        val controller = CascadeEditorController()
        controller.registerBlock(tableRegistration())
        controller.loadJson(
            CascadeEditorDocumentBuilder()
                .customBlock("table", """{"rows":2,"header":true}""")
                .buildJson(),
        )
        val blockId = controller.stateHolder.state.blocks.first().id
        val context = context(controller, blockId)

        val result = context.replacePayloadJson("""{"rows":9}""")

        assertEquals(CascadeCustomBlockMutationResult.success, result)
        val data = assertIs<BlockContent.Custom>(
            controller.stateHolder.state.getBlock(blockId)!!.content,
        ).data
        assertEquals(9L, data["rows"])
        assertFalse(data.containsKey("header"), "replace drops fields not present in the new payload")
    }

    @Test
    fun mutationNoOpsAndReportsReadOnly() {
        val controller = CascadeEditorController()
        controller.registerBlock(tableRegistration())
        controller.loadJson(
            CascadeEditorDocumentBuilder().customBlock("table", """{"rows":2}""").buildJson(),
        )
        val blockId = controller.stateHolder.state.blocks.first().id
        val context = context(controller, blockId, readOnly = true, canUpdate = false)

        val result = context.updatePayloadJson("""{"rows":7}""")

        assertEquals(CascadeCustomBlockMutationResult.readOnly, result)
        assertEquals(
            2L,
            assertIs<BlockContent.Custom>(controller.stateHolder.state.getBlock(blockId)!!.content).data["rows"],
        )
    }

    @Test
    fun invalidPayloadIsRejectedBeforeMutatingDocument() {
        val controller = CascadeEditorController()
        controller.registerBlock(tableRegistration())
        controller.loadJson(
            CascadeEditorDocumentBuilder().customBlock("table", """{"rows":2}""").buildJson(),
        )
        var internalError: String? = null
        controller.onInternalError = { message -> internalError = message }
        val blockId = controller.stateHolder.state.blocks.first().id
        val context = context(controller, blockId)

        val result = context.updatePayloadJson("not json at all")

        assertEquals(CascadeCustomBlockMutationResult.invalidPayload, result)
        assertEquals(
            2L,
            assertIs<BlockContent.Custom>(controller.stateHolder.state.getBlock(blockId)!!.content).data["rows"],
        )
        assertTrue(assertNotNull(internalError).contains("payload", ignoreCase = true))
    }

    @Test
    fun setPreferredHeightClampsOutOfRangeValues() {
        assertEquals(MIN_BLOCK_HEIGHT, clampBlockHeight(-5.0))
        assertEquals(MAX_BLOCK_HEIGHT, clampBlockHeight(1e12))
        assertEquals(DEFAULT_BLOCK_HEIGHT, clampBlockHeight(Double.NaN))

        val controller = CascadeEditorController()
        controller.registerBlock(tableRegistration())
        controller.loadJson(
            CascadeEditorDocumentBuilder().customBlock("table", """{"rows":1}""").buildJson(),
        )
        val blockId = controller.stateHolder.state.blocks.first().id
        val applied = mutableListOf<Double>()
        val context = context(controller, blockId, onPreferredHeight = { applied += it })

        context.setPreferredHeight(-42.0)
        context.setPreferredHeight(1e9)

        assertEquals(listOf(MIN_BLOCK_HEIGHT, MAX_BLOCK_HEIGHT), applied)
    }

    @Test
    fun duplicateRegistrationLastWinsAndWarns() {
        val controller = CascadeEditorController()
        var internalError: String? = null
        controller.onInternalError = { message -> internalError = message }

        controller.registerBlock(tableRegistration(displayName = "First"))
        controller.registerBlock(tableRegistration(displayName = "Second"))

        assertEquals("Second", controller.registry.getDescriptor("table")?.displayName)
        assertSame(
            controller.registry.getRenderer("table"),
            controller.registry.getRenderer("table"),
        )
        assertTrue(assertNotNull(internalError).contains("table"))
    }

    @Test
    fun registeringReservedBuiltInTypeIdIsRejectedAndDoesNotOverwriteBuiltIn() {
        val controller = CascadeEditorController()
        var internalError: String? = null
        controller.onInternalError = { message -> internalError = message }
        val originalParagraph = controller.registry.getDescriptor("paragraph")

        controller.registerBlock(
            CascadeCustomBlockRegistration(
                typeId = "paragraph",
                displayName = "Hijacked",
                description = "should be rejected",
            ) { UIViewController() },
        )

        // Built-in descriptor untouched, no native registration recorded, warning surfaced.
        assertSame(originalParagraph, controller.registry.getDescriptor("paragraph"))
        assertFalse(controller.nativeRegistrations.containsKey("paragraph"))
        assertTrue(assertNotNull(internalError).contains("reserved", ignoreCase = true))
    }

    @Test
    fun registeringReservedDividerAndHeadingFamilyIsRejected() {
        val controller = CascadeEditorController()

        controller.registerBlock(
            CascadeCustomBlockRegistration(typeId = "divider", displayName = "D", description = "") {
                UIViewController()
            },
        )
        // heading_9 is not a seeded descriptor, but the document decoder still resolves
        // the whole heading_ family as built-in, so it must be reserved too.
        controller.registerBlock(
            CascadeCustomBlockRegistration(typeId = "heading_9", displayName = "H9", description = "") {
                UIViewController()
            },
        )

        assertFalse(controller.nativeRegistrations.containsKey("divider"))
        assertFalse(controller.nativeRegistrations.containsKey("heading_9"))
    }

    @Test
    fun onChangeExceptionsAreContainedAndReported() {
        val controller = CascadeEditorController()
        controller.registerBlock(tableRegistration())
        controller.loadJson(
            CascadeEditorDocumentBuilder().customBlock("table", "{}").buildJson(),
        )
        var internalError: String? = null
        controller.onInternalError = { message -> internalError = message }
        val blockId = controller.stateHolder.state.blocks.first().id
        val context = context(controller, blockId)
        context.onChange = { throw IllegalStateException("native onChange blew up") }

        // A throwing native onChange must be contained (no exception escapes here).
        context.fireOnChange()

        assertTrue(assertNotNull(internalError).contains("onChange"))
    }

    @Test
    fun contextIsDarkFollowsControllerDarkMode() {
        val controller = CascadeEditorController(
            CascadeEditorDocumentBuilder().customBlock("table", "{}").buildJson(),
        )
        val blockId = controller.stateHolder.state.blocks.first().id
        val context = context(controller, blockId)

        assertFalse(context.isDark)

        controller.setDarkMode(true)
        assertTrue(context.isDark)

        controller.setDarkMode(false)
        assertFalse(context.isDark)
    }

    private fun context(
        controller: CascadeEditorController,
        blockId: BlockId,
        readOnly: Boolean = false,
        canUpdate: Boolean = true,
        onPreferredHeight: (Double) -> Unit = {},
    ): CascadeCustomBlockContext = CascadeCustomBlockContext(
        blockId = blockId.value,
        typeId = "table",
        scope = TestBlockRenderScope(
            stateHolder = controller.stateHolder,
            textStates = controller.textStates,
            spanStates = controller.spanStates,
            readOnly = readOnly,
            canUpdate = canUpdate,
        ),
        reportError = { message -> controller.onInternalError?.invoke(message) },
        // Mirrors the provider NativeCustomBlockRenderer wires in production.
        isDarkProvider = { controller.configurationSnapshot.value.isDark },
        applyPreferredHeight = onPreferredHeight,
        buildBlock = { typeId, payloadJson ->
            buildInsertableBlock(typeId, payloadJson, controller.registry) { it == "table" }
        },
    )

    private fun tableRegistration(displayName: String = "Table"): CascadeCustomBlockRegistration =
        CascadeCustomBlockRegistration(
            typeId = "table",
            displayName = displayName,
            description = "A table block",
        ) { UIViewController() }
}

/**
 * A faithful [BlockRenderScope] over the controller's real state holder. Routes
 * every mutation through the same [EditorStateHolder.dispatchStructuralAction]
 * history primitive the production [DefaultBlockRenderScope] uses, so undo/redo
 * exercised here is the editor's real history behavior.
 */
private class TestBlockRenderScope(
    private val stateHolder: EditorStateHolder,
    private val textStates: BlockTextStates,
    private val spanStates: BlockSpanStates,
    override val readOnly: Boolean,
    private val canUpdate: Boolean,
) : BlockRenderScope {
    override val state: EditorState get() = stateHolder.state
    override val config: CascadeEditorConfig = CascadeEditorConfig.Default
    override val canUpdateBlock: Boolean get() = canUpdate
    override val canEditBlockStructure: Boolean get() = canUpdate
    override val canSelectBlocks: Boolean = true
    override val canDragBlocks: Boolean = true

    override fun getBlock(blockId: BlockId): Block? = stateHolder.state.getBlock(blockId)

    override fun updateBlock(blockId: BlockId, transform: (Block) -> Block) {
        if (!canUpdate) return
        val current = stateHolder.state.getBlock(blockId) ?: return
        val replacement = transform(current).copy(id = current.id)
        if (replacement == current) return
        stateHolder.dispatchStructuralAction(ReplaceBlock(blockId, replacement), textStates, spanStates)
    }

    override fun replaceBlock(blockId: BlockId, block: Block) {
        if (!canUpdate) return
        val current = stateHolder.state.getBlock(blockId) ?: return
        if (block == current) return
        stateHolder.dispatchStructuralAction(ReplaceBlock(blockId, block), textStates, spanStates)
    }

    override fun insertBlockBefore(blockId: BlockId, block: Block) {
        if (!canUpdate) return
        if (stateHolder.state.getBlock(blockId) == null) return
        stateHolder.dispatchStructuralAction(InsertBlockBefore(block, blockId), textStates, spanStates)
    }

    override fun insertBlockAfter(blockId: BlockId, block: Block) {
        if (!canUpdate) return
        if (stateHolder.state.getBlock(blockId) == null) return
        stateHolder.dispatchStructuralAction(InsertBlockAfter(block, blockId), textStates, spanStates)
    }

    override fun deleteBlock(blockId: BlockId) {
        if (!canUpdate) return
        if (stateHolder.state.getBlock(blockId) == null) return
        stateHolder.dispatchStructuralAction(DeleteBlock(blockId), textStates, spanStates)
    }

    override fun focusBlock(blockId: BlockId?) {
        if (blockId != null && stateHolder.state.getBlock(blockId) == null) return
        stateHolder.dispatch(FocusBlock(blockId))
    }
}
