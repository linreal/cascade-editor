package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownSerializationExtTest {

    private fun holderWith(vararg blocks: Block): EditorStateHolder =
        EditorStateHolder(EditorState.withBlocks(blocks.toList()))

    @Test
    fun `toMarkdown exports live runtime text`() {
        val id = BlockId("b1")
        val holder = holderWith(Block(id, BlockType.Paragraph, BlockContent.Text("snapshot")))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        textStates.getOrCreate(id, "snapshot")
        textStates.setText(id, "runtime")

        assertEquals("runtime\n", holder.toMarkdown(textStates, spanStates))
    }

    @Test
    fun `loadFromMarkdown replaces state and clears runtime holders`() {
        val oldId = BlockId("old")
        val holder = holderWith(Block(oldId, BlockType.Paragraph, BlockContent.Text("old")))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        textStates.getOrCreate(oldId, "old")
        spanStates.getOrCreate(oldId, listOf(TextSpan(0, 3, SpanStyle.Bold)), textLength = 3)

        val result = holder.loadFromMarkdown("# Imported\n\nBody", textStates, spanStates)

        assertTrue(result.isSuccess)
        assertNull(textStates.get(oldId))
        assertNull(spanStates.get(oldId))
        assertEquals(2, holder.state.blocks.size)
        assertEquals(BlockType.Heading(1), holder.state.blocks.first().type)
    }

    @Test
    fun `loadFromMarkdown over-limit leaves document untouched`() {
        val holder = holderWith(Block.paragraph("keep me"))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val before = holder.state.blocks

        val result = holder.loadFromMarkdown(
            "way too long input",
            textStates,
            spanStates,
            limits = MarkdownCodecLimits(maxInputChars = 3),
        )

        assertTrue(result.isAborted)
        assertEquals(before, holder.state.blocks)
    }

    @Test
    fun `applyMarkdownDecodeResult rejects aborted result`() {
        val holder = holderWith(Block.paragraph("keep"))
        val aborted = MarkdownSchema.decodeWithReport(
            "too long",
            MarkdownProfile.Default,
            MarkdownCodecLimits(maxInputChars = 2),
        )
        assertFailsWith<IllegalArgumentException> {
            holder.applyMarkdownDecodeResult(aborted, BlockTextStates(), BlockSpanStates())
        }
        assertEquals(1, holder.state.blocks.size)
    }

    @Test
    fun `commonmark export drops trailing empty paragraph after a divider`() {
        val holder = holderWith(Block.divider(), Block.paragraph(""))
        val md = holder.toMarkdown(BlockTextStates(), BlockSpanStates())
        assertEquals("---\n", md)
    }

    @Test
    fun `loadFromMarkdown clears history on success and leaves it on abort`() {
        val holder = holderWith(Block.paragraph("keep"))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        // Abort leaves history state (canUndo) untouched.
        val before = holder.canUndo
        val aborted = holder.loadFromMarkdown(
            "too long",
            textStates,
            spanStates,
            limits = MarkdownCodecLimits(maxInputChars = 2),
        )
        assertTrue(aborted.isAborted)
        assertEquals(before, holder.canUndo)

        // Success is a hard replacement: history is cleared.
        holder.loadFromMarkdown("# Fresh\n", textStates, spanStates)
        assertFalse(holder.canUndo)
    }

    @Test
    fun `toMarkdown strips runtime spans from a non-spans code block`() {
        val id = BlockId("code")
        val holder = holderWith(Block(id, BlockType.Code, BlockContent.Text("code")))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        textStates.getOrCreate(id, "code")
        spanStates.getOrCreate(id, listOf(TextSpan(0, 4, SpanStyle.Bold)), textLength = 4)

        val md = holder.toMarkdown(textStates, spanStates)!!
        assertFalse(md.contains("**"), "code must not carry span markers: $md")
        assertTrue(md.contains("code"))
    }

    @Test
    fun `commonmark export drops a sole empty trailing paragraph`() {
        val holder = holderWith(Block.paragraph(""))
        assertEquals("", holder.toMarkdown(BlockTextStates(), BlockSpanStates()))
    }

    @Test
    fun `hardbreak export keeps trailing empty paragraph`() {
        val hardBreakBase = MarkdownProfile.Default.withNewlineSemantics(NewlineSemantics.HardBreak)
        val holder = holderWith(Block.paragraph("a"), Block.paragraph(""))
        val md = holder.toMarkdown(BlockTextStates(), BlockSpanStates(), profile = hardBreakBase)
        assertEquals("a\n\n", md)
    }
}
