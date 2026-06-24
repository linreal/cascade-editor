package io.github.linreal.cascade.editor.ui

import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.SelectBlock
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultBlockRenderScopeTest {

    @Test
    fun `updateBlock creates one structural history entry`() {
        val block = paragraph("block", "old")
        val harness = ScopeHarness(listOf(block))

        harness.scope.updateBlock(block.id) { current: Block ->
            current.withContent(BlockContent.Text("new"))
        }

        assertEquals("new", harness.textOf(block.id))
        assertTrue(harness.stateHolder.canUndo)

        harness.stateHolder.undo()

        assertEquals("old", harness.textOf(block.id))
        assertFalse(harness.stateHolder.canUndo)
        assertTrue(harness.stateHolder.canRedo)

        harness.stateHolder.redo()

        assertEquals("new", harness.textOf(block.id))
    }

    @Test
    fun `updateBlock preserves block identity even if transform changes it`() {
        val block = paragraph("original", "text")
        val harness = ScopeHarness(listOf(block))

        harness.scope.updateBlock(block.id) { current: Block ->
            current.copy(id = BlockId("other"))
        }

        assertEquals(BlockId("original"), harness.stateHolder.state.blocks.single().id)
        assertNull(harness.stateHolder.state.getBlock(BlockId("other")))
        assertFalse(harness.stateHolder.canUndo)
    }

    @Test
    fun `replaceBlock may replace identity and remains undoable`() {
        val original = paragraph("a", "old")
        val replacement = paragraph("b", "new")
        val focusHarness = ScopeHarness(listOf(original))
        focusHarness.stateHolder.dispatch(FocusBlock(original.id))

        focusHarness.scope.replaceBlock(original.id, replacement)

        assertEquals(listOf(replacement.id), focusHarness.blockIds())
        assertEquals(replacement.id, focusHarness.stateHolder.state.focusedBlockId)
        assertTrue(focusHarness.stateHolder.canUndo)

        focusHarness.stateHolder.undo()

        assertEquals(listOf(original.id), focusHarness.blockIds())
        assertEquals(original.id, focusHarness.stateHolder.state.focusedBlockId)

        val selectionHarness = ScopeHarness(listOf(original))
        selectionHarness.stateHolder.dispatch(SelectBlock(original.id))

        selectionHarness.scope.replaceBlock(original.id, replacement)

        assertEquals(listOf(replacement.id), selectionHarness.blockIds())
        assertEquals(setOf(replacement.id), selectionHarness.stateHolder.state.selectedBlockIds)
        assertTrue(selectionHarness.stateHolder.canUndo)

        selectionHarness.stateHolder.undo()

        assertEquals(listOf(original.id), selectionHarness.blockIds())
        assertEquals(setOf(original.id), selectionHarness.stateHolder.state.selectedBlockIds)
    }

    @Test
    fun `insert before after and delete are structural history mutations`() {
        val a = paragraph("a", "a")
        val b = paragraph("b", "b")
        val c = paragraph("c", "c")
        val beforeHarness = ScopeHarness(listOf(a, c))

        beforeHarness.scope.insertBlockBefore(c.id, b)

        assertEquals(listOf(a.id, b.id, c.id), beforeHarness.blockIds())
        assertTrue(beforeHarness.stateHolder.canUndo)

        beforeHarness.stateHolder.undo()

        assertEquals(listOf(a.id, c.id), beforeHarness.blockIds())
        assertTrue(beforeHarness.stateHolder.canRedo)

        beforeHarness.stateHolder.redo()

        assertEquals(listOf(a.id, b.id, c.id), beforeHarness.blockIds())

        val afterHarness = ScopeHarness(listOf(a, c))

        afterHarness.scope.insertBlockAfter(a.id, b)

        assertEquals(listOf(a.id, b.id, c.id), afterHarness.blockIds())
        assertTrue(afterHarness.stateHolder.canUndo)

        afterHarness.stateHolder.undo()

        assertEquals(listOf(a.id, c.id), afterHarness.blockIds())
        assertTrue(afterHarness.stateHolder.canRedo)

        afterHarness.stateHolder.redo()

        assertEquals(listOf(a.id, b.id, c.id), afterHarness.blockIds())

        val deleteHarness = ScopeHarness(listOf(a, b, c))

        deleteHarness.scope.deleteBlock(b.id)

        assertEquals(listOf(a.id, c.id), deleteHarness.blockIds())
        assertTrue(deleteHarness.stateHolder.canUndo)

        deleteHarness.stateHolder.undo()

        assertEquals(listOf(a.id, b.id, c.id), deleteHarness.blockIds())
        assertTrue(deleteHarness.stateHolder.canRedo)

        deleteHarness.stateHolder.redo()

        assertEquals(listOf(a.id, c.id), deleteHarness.blockIds())
    }

    @Test
    fun `missing targets do not mutate or create history`() {
        val a = paragraph("a", "a")
        val b = paragraph("b", "b")
        val harness = ScopeHarness(listOf(a, b))
        val initialState = harness.stateHolder.state
        val missing = BlockId("missing")

        harness.scope.updateBlock(missing) { current: Block ->
            current.withContent(BlockContent.Text("updated"))
        }
        harness.scope.replaceBlock(missing, paragraph("replacement", "replacement"))
        harness.scope.insertBlockBefore(missing, paragraph("before", "before"))
        harness.scope.insertBlockAfter(missing, paragraph("after", "after"))
        harness.scope.deleteBlock(missing)
        harness.scope.focusBlock(missing)

        assertEquals(initialState, harness.stateHolder.state)
        assertFalse(harness.stateHolder.canUndo)
    }

    @Test
    fun `equal update and equal replace do not create history`() {
        val block = paragraph("block", "same")
        val harness = ScopeHarness(listOf(block))

        harness.scope.updateBlock(block.id) { current: Block -> current }
        harness.scope.replaceBlock(block.id, block.copy())

        assertEquals(listOf(block), harness.stateHolder.state.blocks)
        assertFalse(harness.stateHolder.canUndo)
    }

    @Test
    fun `focusBlock does not create history`() {
        val block = paragraph("block", "text")
        val harness = ScopeHarness(listOf(block))

        harness.scope.focusBlock(block.id)

        assertEquals(block.id, harness.stateHolder.state.focusedBlockId)
        assertFalse(harness.stateHolder.canUndo)

        harness.scope.focusBlock(null)

        assertNull(harness.stateHolder.state.focusedBlockId)
        assertFalse(harness.stateHolder.canUndo)
    }

    @Test
    fun `readOnly scope disables mutation but reports capability flags`() {
        val block = paragraph("block", "old")
        val harness = ScopeHarness(
            initialBlocks = listOf(block),
            initialConfig = CascadeEditorConfig(readOnly = true),
            initialPolicy = EditorInteractionPolicy.ReadOnly,
        )
        val initialBlocks = harness.stateHolder.state.blocks

        assertTrue(harness.scope.readOnly)
        assertFalse(harness.scope.canUpdateBlock)
        assertFalse(harness.scope.canEditBlockStructure)
        assertFalse(harness.scope.canSelectBlocks)
        assertFalse(harness.scope.canDragBlocks)

        harness.scope.updateBlock(block.id) { current: Block ->
            current.withContent(BlockContent.Text("updated"))
        }
        harness.scope.replaceBlock(block.id, paragraph("replacement", "replacement"))
        harness.scope.insertBlockBefore(block.id, paragraph("before", "before"))
        harness.scope.insertBlockAfter(block.id, paragraph("after", "after"))
        harness.scope.deleteBlock(block.id)

        assertEquals(initialBlocks, harness.stateHolder.state.blocks)
        assertFalse(harness.stateHolder.canUndo)
    }

    @Test
    fun `stale scope reads latest policy when mutating`() {
        assertStaleReadOnlyMutationIgnored("updateBlock") { harness, target ->
            harness.scope.updateBlock(target.id) { current: Block ->
                current.withContent(BlockContent.Text("updated"))
            }
        }
        assertStaleReadOnlyMutationIgnored("replaceBlock") { harness, target ->
            harness.scope.replaceBlock(target.id, paragraph("replacement", "replacement"))
        }
        assertStaleReadOnlyMutationIgnored("insertBlockBefore") { harness, target ->
            harness.scope.insertBlockBefore(target.id, paragraph("before", "before"))
        }
        assertStaleReadOnlyMutationIgnored("insertBlockAfter") { harness, target ->
            harness.scope.insertBlockAfter(target.id, paragraph("after", "after"))
        }
        assertStaleReadOnlyMutationIgnored("deleteBlock") { harness, target ->
            harness.scope.deleteBlock(target.id)
        }
    }

    private fun assertStaleReadOnlyMutationIgnored(
        name: String,
        mutation: (ScopeHarness, Block) -> Unit,
    ) {
        val target = paragraph("target-$name", "target")
        val sibling = paragraph("sibling-$name", "sibling")
        val harness = ScopeHarness(listOf(target, sibling))
        val initialState = harness.stateHolder.state
        val capturedMutation = {
            mutation(harness, target)
        }

        harness.config = CascadeEditorConfig(readOnly = true)
        harness.policy = EditorInteractionPolicy.ReadOnly
        capturedMutation()

        assertEquals(initialState, harness.stateHolder.state, "$name changed document state")
        assertFalse(harness.stateHolder.canUndo, "$name created an undo entry")
    }

    private class ScopeHarness(
        initialBlocks: List<Block>,
        initialConfig: CascadeEditorConfig = CascadeEditorConfig.Default,
        initialPolicy: EditorInteractionPolicy = EditorInteractionPolicy.Editable,
    ) {
        val stateHolder = EditorStateHolder(EditorState.withBlocks(initialBlocks))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        var config = initialConfig
        var policy = initialPolicy
        val scope = DefaultBlockRenderScope(
            stateHolder = stateHolder,
            configProvider = { config },
            policyProvider = { policy },
        )

        init {
            stateHolder.bindHistoryRuntime(textStates, spanStates)
        }

        fun blockIds(): List<BlockId> = stateHolder.state.blocks.map { it.id }

        fun textOf(blockId: BlockId): String {
            return (stateHolder.state.getBlock(blockId)?.content as BlockContent.Text).text
        }
    }
}

private fun paragraph(
    id: String,
    text: String,
): Block = Block.paragraph(text).copy(id = BlockId(id))
