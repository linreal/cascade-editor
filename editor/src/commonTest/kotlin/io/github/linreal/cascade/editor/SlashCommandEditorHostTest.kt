package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.slash.SlashCommandEditorHost
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.SlashQueryRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlashCommandEditorHostTest {

    // -- replaceQueryText --

    @Test
    fun `replaceQueryText removes exactly the captured range`() {
        val env = createHost(
            text = "Hello /cmd World",
            queryRange = SlashQueryRange(6, 10), // "/cmd"
        )

        env.host.replaceQueryText("")

        assertEquals("Hello  World", env.textStates.getVisibleText(env.anchorId))
    }

    @Test
    fun `replaceQueryText with replacement text`() {
        val env = createHost(
            text = "Type /heading here",
            queryRange = SlashQueryRange(5, 13), // "/heading"
        )

        env.host.replaceQueryText("Result")

        assertEquals("Type Result here", env.textStates.getVisibleText(env.anchorId))
    }

    @Test
    fun `replaceQueryText preserves spans outside query range`() {
        val anchorId = BlockId.generate()
        val spans = listOf(
            TextSpan(0, 4, SpanStyle.Bold),       // "Hell" before query
            TextSpan(10, 15, SpanStyle.Italic),    // "World" after query — "Hello /cmd World"
        )
        val env = createHost(
            anchorId = anchorId,
            text = "Hello /cmd World",
            queryRange = SlashQueryRange(6, 10), // "/cmd"
            spans = spans,
        )

        env.host.replaceQueryText("")

        val resultSpans = env.spanStates.getSpans(anchorId)
        // Bold on "Hell" (0..4) should remain unchanged
        assertTrue(resultSpans.any { it.style == SpanStyle.Bold && it.start == 0 && it.end == 4 })
        // Italic should shift left by 4 chars (deleted "/cmd" = 4 chars, inserted 0)
        assertTrue(resultSpans.any { it.style == SpanStyle.Italic && it.start == 6 && it.end == 11 })
    }

    @Test
    fun `replaceQueryText syncs snapshot`() {
        val env = createHost(
            text = "abc /q def",
            queryRange = SlashQueryRange(4, 6), // "/q"
        )

        env.host.replaceQueryText("")

        val block = env.host.getAnchorBlock()
        assertNotNull(block)
        val content = block.content as BlockContent.Text
        assertEquals("abc  def", content.text)
    }

    @Test
    fun `replaceQueryText no-op when anchor text is missing`() {
        val anchorId = BlockId.generate()
        val block = Block(anchorId, BlockType.Paragraph, BlockContent.Text("x"))
        val stateHolder = EditorStateHolder(EditorState.withBlocks(listOf(block)))
        // Do NOT create text state — simulates missing runtime text
        val host = SlashCommandEditorHost(
            anchorBlockId = anchorId,
            queryRange = SlashQueryRange(0, 1),
            stateHolder = stateHolder,
            textStates = BlockTextStates(),
            spanStates = BlockSpanStates(),
        )

        // Should not throw
        host.replaceQueryText("y")
    }

    // -- updateAnchorText --

    @Test
    fun `updateAnchorText replaces full text`() {
        val env = createHost(
            text = "old text",
            queryRange = SlashQueryRange(0, 1),
        )

        env.host.updateAnchorText("new text", cursorPosition = 3)

        assertEquals("new text", env.textStates.getVisibleText(env.anchorId))
    }

    @Test
    fun `updateAnchorText syncs snapshot`() {
        val env = createHost(
            text = "old text",
            queryRange = SlashQueryRange(0, 1),
        )

        env.host.updateAnchorText("replaced")

        val content = env.stateHolder.state.getBlock(env.anchorId)?.content as? BlockContent.Text
        assertNotNull(content)
        assertEquals("replaced", content.text)
    }

    @Test
    fun `updateAnchorText no-op when anchor missing`() {
        val anchorId = BlockId.generate()
        val stateHolder = EditorStateHolder(EditorState.Empty)
        val host = SlashCommandEditorHost(
            anchorBlockId = anchorId,
            queryRange = SlashQueryRange(0, 1),
            stateHolder = stateHolder,
            textStates = BlockTextStates(),
            spanStates = BlockSpanStates(),
        )

        host.updateAnchorText("anything")
        // No crash, no state change
    }

    // -- replaceAnchorBlock --

    @Test
    fun `replaceAnchorBlock with preserveAnchorId keeps block id`() {
        val env = createHost(
            text = "original",
            queryRange = SlashQueryRange(0, 1),
        )
        val newBlock = Block(BlockId.generate(), BlockType.Heading(1), BlockContent.Text("heading"))

        env.host.replaceAnchorBlock(newBlock, preserveAnchorId = true, requestFocus = false)

        val block = env.host.getAnchorBlock()
        assertNotNull(block)
        assertEquals(env.anchorId, block.id)
        assertEquals(BlockType.Heading(1), block.type)
    }

    @Test
    fun `replaceAnchorBlock without preserveAnchorId uses new id`() {
        val env = createHost(
            text = "original",
            queryRange = SlashQueryRange(0, 1),
        )
        val newId = BlockId.generate()
        val newBlock = Block(newId, BlockType.Heading(1), BlockContent.Text("heading"))

        env.host.replaceAnchorBlock(newBlock, preserveAnchorId = false, requestFocus = false)

        // Old anchor should be gone (replaced with new id)
        assertNull(env.host.getAnchorBlock())
        // New block exists
        assertNotNull(env.stateHolder.state.getBlock(newId))
    }

    @Test
    fun `replaceAnchorBlock with requestFocus sets focus`() {
        val env = createHost(
            text = "original",
            queryRange = SlashQueryRange(0, 1),
        )
        val newBlock = Block(BlockId.generate(), BlockType.Heading(1), BlockContent.Text("heading"))

        env.host.replaceAnchorBlock(newBlock, preserveAnchorId = true, requestFocus = true)

        assertEquals(env.anchorId, env.stateHolder.state.focusedBlockId)
    }

    @Test
    fun `replaceAnchorBlock no-op when anchor missing`() {
        val anchorId = BlockId.generate()
        val stateHolder = EditorStateHolder(EditorState.Empty)
        val host = SlashCommandEditorHost(
            anchorBlockId = anchorId,
            queryRange = SlashQueryRange(0, 1),
            stateHolder = stateHolder,
            textStates = BlockTextStates(),
            spanStates = BlockSpanStates(),
        )

        host.replaceAnchorBlock(Block(BlockId.generate(), BlockType.Paragraph, BlockContent.Text("x")))
        assertTrue(stateHolder.state.blocks.isEmpty())
    }

    // -- insertBlockAfterAnchor --

    @Test
    fun `insertBlockAfterAnchor adds block after anchor`() {
        val env = createHost(
            text = "anchor",
            queryRange = SlashQueryRange(0, 1),
        )
        val insertedBlock = Block.divider()

        env.host.insertBlockAfterAnchor(insertedBlock, requestFocus = false)

        val blocks = env.stateHolder.state.blocks
        assertEquals(2, blocks.size)
        assertEquals(env.anchorId, blocks[0].id)
        assertEquals(insertedBlock.id, blocks[1].id)
    }

    @Test
    fun `insertBlockAfterAnchor with requestFocus sets focus to inserted block`() {
        val env = createHost(
            text = "anchor",
            queryRange = SlashQueryRange(0, 1),
        )
        val insertedBlock = Block(BlockId.generate(), BlockType.Paragraph, BlockContent.Text("new"))

        env.host.insertBlockAfterAnchor(insertedBlock, requestFocus = true)

        assertEquals(insertedBlock.id, env.stateHolder.state.focusedBlockId)
    }

    @Test
    fun `insertBlockAfterAnchor no-op when anchor missing`() {
        val anchorId = BlockId.generate()
        val stateHolder = EditorStateHolder(EditorState.Empty)
        val host = SlashCommandEditorHost(
            anchorBlockId = anchorId,
            queryRange = SlashQueryRange(0, 1),
            stateHolder = stateHolder,
            textStates = BlockTextStates(),
            spanStates = BlockSpanStates(),
        )

        host.insertBlockAfterAnchor(Block.divider())
        assertTrue(stateHolder.state.blocks.isEmpty())
    }

    // -- focusBlock --

    @Test
    fun `focusBlock updates focused block in state`() {
        val anchorId = BlockId.generate()
        val secondId = BlockId.generate()
        val blocks = listOf(
            Block(anchorId, BlockType.Paragraph, BlockContent.Text("a")),
            Block(secondId, BlockType.Paragraph, BlockContent.Text("b")),
        )
        val stateHolder = EditorStateHolder(EditorState.withBlocks(blocks))
        val textStates = BlockTextStates()
        textStates.getOrCreate(anchorId, "a")
        textStates.getOrCreate(secondId, "b")
        val host = SlashCommandEditorHost(
            anchorBlockId = anchorId,
            queryRange = SlashQueryRange(0, 1),
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = BlockSpanStates(),
        )

        host.focusBlock(secondId, cursorPosition = 0)

        assertEquals(secondId, stateHolder.state.focusedBlockId)
    }

    @Test
    fun `focusBlock no-op for nonexistent block`() {
        val env = createHost(text = "a", queryRange = SlashQueryRange(0, 1))
        val bogusId = BlockId.generate()

        env.host.focusBlock(bogusId) // no crash
        // Focus should not have changed
        assertNull(env.stateHolder.state.focusedBlockId)
    }

    // -- closeMenu --

    @Test
    fun `closeMenu clears slash session`() {
        val env = createHost(text = "a", queryRange = SlashQueryRange(0, 1))

        env.host.closeMenu()

        assertNull(env.stateHolder.state.slashCommandState)
    }

    // -- getAnchorBlock / getAnchorVisibleText --

    @Test
    fun `getAnchorBlock returns block when present`() {
        val env = createHost(
            text = "content",
            queryRange = SlashQueryRange(0, 1),
        )

        val block = env.host.getAnchorBlock()
        assertNotNull(block)
        assertEquals(env.anchorId, block.id)
    }

    @Test
    fun `getAnchorBlock returns null when anchor deleted`() {
        val anchorId = BlockId.generate()
        val host = SlashCommandEditorHost(
            anchorBlockId = anchorId,
            queryRange = SlashQueryRange(0, 1),
            stateHolder = EditorStateHolder(EditorState.Empty),
            textStates = BlockTextStates(),
            spanStates = BlockSpanStates(),
        )

        assertNull(host.getAnchorBlock())
    }

    @Test
    fun `getAnchorVisibleText returns runtime text`() {
        val env = createHost(
            text = "visible",
            queryRange = SlashQueryRange(0, 1),
        )

        assertEquals("visible", env.host.getAnchorVisibleText())
    }

    @Test
    fun `getAnchorVisibleText returns null when no text state`() {
        val anchorId = BlockId.generate()
        val host = SlashCommandEditorHost(
            anchorBlockId = anchorId,
            queryRange = SlashQueryRange(0, 1),
            stateHolder = EditorStateHolder(EditorState.Empty),
            textStates = BlockTextStates(),
            spanStates = BlockSpanStates(),
        )

        assertNull(host.getAnchorVisibleText())
    }

    // -- Helpers --

    private data class HostEnv(
        val host: SlashCommandEditorHost,
        val stateHolder: EditorStateHolder,
        val textStates: BlockTextStates,
        val spanStates: BlockSpanStates,
        val anchorId: BlockId,
    )

    private fun createHost(
        text: String,
        queryRange: SlashQueryRange,
        anchorId: BlockId = BlockId.generate(),
        spans: List<TextSpan> = emptyList(),
    ): HostEnv {
        val block = Block(anchorId, BlockType.Paragraph, BlockContent.Text(text, spans))
        val stateHolder = EditorStateHolder(EditorState.withBlocks(listOf(block)))
        val textStates = BlockTextStates()
        textStates.getOrCreate(anchorId, text)
        val spanStates = BlockSpanStates()
        spanStates.getOrCreate(anchorId, spans, text.length)

        val host = SlashCommandEditorHost(
            anchorBlockId = anchorId,
            queryRange = queryRange,
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
        )
        return HostEnv(host, stateHolder, textStates, spanStates, anchorId)
    }
}
