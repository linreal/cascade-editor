package io.github.linreal.cascade.ios.slash

import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.InsertBlockAfter
import io.github.linreal.cascade.editor.action.InsertBlockBefore
import io.github.linreal.cascade.editor.action.ReplaceBlock
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.slash.SlashCommandAction
import io.github.linreal.cascade.editor.slash.SlashCommandContext
import io.github.linreal.cascade.editor.slash.SlashCommandEditor
import io.github.linreal.cascade.editor.slash.SlashCommandResult
import io.github.linreal.cascade.editor.slash.builtInBlockSlashCommandId
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.ios.block.CascadeCustomBlockRegistration
import io.github.linreal.cascade.ios.block.NativeCustomBlockType
import io.github.linreal.cascade.ios.controller.CascadeEditorController
import io.github.linreal.cascade.ios.model.CascadeEditorDocumentBuilder
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIViewController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeSlashCommandTest {

    @Test
    fun textCommandReplacesAnchorQueryTextAndReturnsDone() {
        val (controller, anchorId) = controllerWithAnchor("/timestamp")
        controller.registerSlashCommand(
            CascadeSlashCommand(
                id = "timestamp",
                title = "Timestamp",
                description = "Insert the current timestamp",
            ) { context ->
                context.replaceQueryText("2026-07-09")
                CascadeSlashCommandResult.done
            },
        )

        val result = execute(controller, "timestamp", anchorId, SlashQueryRange(0, TIMESTAMP_LEN))

        assertEquals(SlashCommandResult.Done, result)
        assertEquals("2026-07-09", controller.textStates.getVisibleText(anchorId))
    }

    @Test
    fun blockCommandInsertsCustomBlockAfterAnchorWithPayload() {
        val (controller, anchorId) = controllerWithAnchor("hello")
        controller.registerBlock(tableRegistration())
        controller.registerSlashCommand(
            CascadeSlashCommand(
                id = "insert-table",
                title = "Table",
                description = "Insert a table",
            ) { context ->
                context.insertBlockAfterAnchor("table", """{"rows":3}""")
                CascadeSlashCommandResult.done
            },
        )

        val result = execute(controller, "insert-table", anchorId, SlashQueryRange(0, 0))

        assertEquals(SlashCommandResult.Done, result)
        val blocks = controller.stateHolder.state.blocks
        val inserted = blocks[blocks.indexOfFirst { it.id == anchorId } + 1]
        assertEquals("table", assertIs<NativeCustomBlockType>(inserted.type).typeId)
        assertEquals(3L, assertIs<BlockContent.Custom>(inserted.content).data["rows"])
    }

    @Test
    fun blockCommandWithInvalidPayloadIsRejectedAndNotMutated() {
        val (controller, anchorId) = controllerWithAnchor("hi")
        controller.registerBlock(tableRegistration())
        var internalError: String? = null
        controller.onInternalError = { message -> internalError = message }
        var insertReturned: Boolean? = null
        controller.registerSlashCommand(
            CascadeSlashCommand(id = "bad-insert", title = "Bad", description = "d") { context ->
                insertReturned = context.insertBlockAfterAnchor("table", "not json at all")
                CascadeSlashCommandResult.done
            },
        )
        val blocksBefore = controller.stateHolder.state.blocks.size

        execute(controller, "bad-insert", anchorId, SlashQueryRange(0, 0))

        assertEquals(false, insertReturned)
        assertEquals(blocksBefore, controller.stateHolder.state.blocks.size, "document must not be mutated")
        assertTrue(assertNotNull(internalError).contains("payload", ignoreCase = true))
    }

    @Test
    fun handlerFailureReportsMessageAndReturnsFailure() {
        val (controller, anchorId) = controllerWithAnchor("hi")
        var internalError: String? = null
        controller.onInternalError = { message -> internalError = message }
        controller.registerSlashCommand(
            CascadeSlashCommand(
                id = "boom",
                title = "Boom",
                description = "Always fails",
            ) { CascadeSlashCommandResult.failure("boom") },
        )

        val result = execute(controller, "boom", anchorId, SlashQueryRange(0, 0))

        assertEquals("boom", assertIs<SlashCommandResult.Failure>(result).message)
        assertTrue(assertNotNull(internalError).contains("boom"))
    }

    @Test
    fun builtInAndNativeCommandsCoexist() {
        val controller = CascadeEditorController()
        var internalError: String? = null
        controller.onInternalError = { message -> internalError = message }

        val builtInIdsBefore = controller.builtInSlashCommandIds()
        controller.registerSlashCommand(
            CascadeSlashCommand(id = "timestamp", title = "Timestamp", description = "d") {
                CascadeSlashCommandResult.done
            },
        )

        assertNull(internalError, "a non-colliding registration must not warn")
        assertTrue(builtInIdsBefore.contains(builtInBlockSlashCommandId("paragraph")))
        assertEquals(
            builtInIdsBefore,
            controller.builtInSlashCommandIds(),
            "registering a native command must not remove built-in commands",
        )
        assertTrue(controller.slashRegistry.getRootItems().any { it.id.value == "timestamp" })
    }

    @Test
    fun registeringExistingBuiltInIdOverridesAndWarns() {
        val controller = CascadeEditorController()
        var internalError: String? = null
        controller.onInternalError = { message -> internalError = message }
        val builtInId = builtInBlockSlashCommandId("paragraph").value

        controller.registerSlashCommand(
            CascadeSlashCommand(id = builtInId, title = "Custom Paragraph", description = "d") {
                CascadeSlashCommandResult.done
            },
        )

        assertTrue(assertNotNull(internalError).contains(builtInId))
        val overriding = controller.slashRegistry.getRootItems().single { it.id.value == builtInId }
        assertEquals("Custom Paragraph", overriding.title)
    }

    @Test
    fun focusAnchorMovesFocusToAnchorBlock() {
        val (controller, anchorId) = controllerWithAnchor("focus me")
        controller.registerSlashCommand(
            CascadeSlashCommand(id = "focus", title = "Focus", description = "d") { context ->
                context.focusAnchor()
                CascadeSlashCommandResult.done
            },
        )

        execute(controller, "focus", anchorId, SlashQueryRange(0, 0))

        assertEquals(anchorId, controller.stateHolder.state.focusedBlockId)
    }

    private fun controllerWithAnchor(text: String): Pair<CascadeEditorController, BlockId> {
        val controller = CascadeEditorController()
        controller.loadJson(CascadeEditorDocumentBuilder().paragraph(text).buildJson())
        val anchorId = controller.stateHolder.state.blocks.first().id
        // A slash command only ever runs on a mounted, focused block, which always
        // has a runtime text state; seed it so the headless test mirrors that.
        controller.textStates.getOrCreate(anchorId, text)
        return controller to anchorId
    }

    private fun execute(
        controller: CascadeEditorController,
        commandId: String,
        anchorId: BlockId,
        queryRange: SlashQueryRange,
    ): SlashCommandResult {
        val action = controller.slashRegistry.getRootItems()
            .single { it.id.value == commandId } as SlashCommandAction
        val editor = TestSlashCommandEditor(controller, anchorId, queryRange)
        val context = SlashCommandContext(
            anchorBlockId = anchorId,
            query = commandId,
            queryRange = queryRange,
            editor = editor,
        )
        return runBlocking { action.onExecute(context) }
    }

    private fun tableRegistration(): CascadeCustomBlockRegistration =
        CascadeCustomBlockRegistration(
            typeId = "table",
            displayName = "Table",
            description = "A table block",
        ) { UIViewController() }

    private companion object {
        const val TIMESTAMP_LEN: Int = 10 // "/timestamp".length
    }
}

/**
 * A [SlashCommandEditor] over the controller's real state holder, mirroring the
 * production host's dispatch behavior so slash execution is exercised against
 * real editor state without a mounted Compose tree.
 */
private class TestSlashCommandEditor(
    private val controller: CascadeEditorController,
    private val anchorBlockId: BlockId,
    private val queryRange: SlashQueryRange,
) : SlashCommandEditor {

    private val stateHolder get() = controller.stateHolder
    private val textStates get() = controller.textStates

    override fun getAnchorBlock(): Block? = stateHolder.state.getBlock(anchorBlockId)

    override fun getAnchorVisibleText(): String? = textStates.getVisibleText(anchorBlockId)

    override fun replaceQueryText(replacement: String) {
        val visible = textStates.getVisibleText(anchorBlockId)
            ?: (getAnchorBlock()?.content as? BlockContent.Text)?.text
            ?: return
        val start = queryRange.start.coerceIn(0, visible.length)
        val end = queryRange.endExclusive.coerceIn(start, visible.length)
        val newText = visible.substring(0, start) + replacement + visible.substring(end)
        textStates.setText(anchorBlockId, newText, start + replacement.length)
        stateHolder.dispatch(UpdateBlockContent(anchorBlockId, BlockContent.Text(newText, emptyList())))
    }

    override fun updateAnchorText(text: String, cursorPosition: Int?) {
        textStates.setText(anchorBlockId, text, cursorPosition)
        stateHolder.dispatch(UpdateBlockContent(anchorBlockId, BlockContent.Text(text, emptyList())))
    }

    override fun replaceAnchorBlock(
        block: Block,
        preserveAnchorId: Boolean,
        requestFocus: Boolean,
        cursorPosition: Int?,
    ) {
        val effective = if (preserveAnchorId) Block(anchorBlockId, block.type, block.content) else block
        stateHolder.dispatch(ReplaceBlock(anchorBlockId, effective))
    }

    override fun insertBlockAfterAnchor(block: Block, requestFocus: Boolean, cursorPosition: Int?) {
        if (getAnchorBlock() == null) return
        stateHolder.dispatch(InsertBlockAfter(block, anchorBlockId))
    }

    override fun insertBlockBeforeAnchor(block: Block) {
        if (getAnchorBlock() == null) return
        stateHolder.dispatch(InsertBlockBefore(block, anchorBlockId))
    }

    override fun focusBlock(blockId: BlockId, cursorPosition: Int?) {
        if (stateHolder.state.getBlock(blockId) == null) return
        stateHolder.dispatch(FocusBlock(blockId))
    }

    override fun closeMenu() = Unit
}
