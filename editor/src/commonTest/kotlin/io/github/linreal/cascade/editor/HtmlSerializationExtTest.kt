package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.BlockEncoder
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlEmit
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.TagDecodeResult
import io.github.linreal.cascade.editor.htmlserialization.loadFromHtml
import io.github.linreal.cascade.editor.htmlserialization.toHtml
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.DragState
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.SlashCommandState
import io.github.linreal.cascade.editor.state.SlashQueryRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlSerializationExtTest {

    @Test
    fun `toHtml runtime text override exports live text`() {
        val blockId = BlockId("b1")
        val holder = holderWith(
            Block(blockId, BlockType.Paragraph, BlockContent.Text("snapshot")),
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(blockId, "snapshot")
        textStates.setText(blockId, "runtime")

        val html = holder.toHtml(textStates, spanStates, HtmlProfile.Default)

        assertEquals("<p>runtime</p>", html)
    }

    @Test
    fun `toHtml runtime span override exports live spans`() {
        val blockId = BlockId("b1")
        val snapshotSpans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val runtimeSpans = listOf(TextSpan(0, 3, SpanStyle.Italic))
        val holder = holderWith(
            Block(blockId, BlockType.Paragraph, BlockContent.Text("abc", snapshotSpans)),
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        spanStates.getOrCreate(blockId, runtimeSpans, textLength = 3)

        val html = holder.toHtml(textStates, spanStates, HtmlProfile.Default)

        assertEquals("<p><em>abc</em></p>", html)
    }

    @Test
    fun `toHtml strips stale runtime spans from non-spans block types`() {
        val blockId = BlockId("code")
        val holder = holderWith(
            Block(blockId, BlockType.Code, BlockContent.Text("code")),
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val profile = HtmlProfile.Default.withBlockEncoder<BlockType.Code>(
            BlockEncoder { ctx, block, _ -> HtmlEmit.Raw("<code>${ctx.encodeInline(block)}</code>") },
        )

        textStates.getOrCreate(blockId, "code")
        spanStates.getOrCreate(
            blockId,
            listOf(TextSpan(0, 4, SpanStyle.Bold)),
            textLength = 4,
        )

        val html = holder.toHtml(textStates, spanStates, profile)

        assertEquals("<code>code</code>", html)
    }

    @Test
    fun `toHtml off-screen blocks export snapshot content`() {
        val holder = holderWith(
            Block(BlockId("b1"), BlockType.Paragraph, BlockContent.Text("snapshot")),
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        val html = holder.toHtml(textStates, spanStates, HtmlProfile.Default)

        assertEquals("<p>snapshot</p>", html)
    }

    @Test
    fun `loadFromHtml clears runtime state replaces editor state and returns warnings`() {
        val oldId = BlockId("old")
        val holder = EditorStateHolder(
            EditorState(
                blocks = listOf(Block(oldId, BlockType.Paragraph, BlockContent.Text("old"))),
                focusedBlockId = oldId,
                selectedBlockIds = emptySet(),
                dragState = DragState(draggingBlockIds = setOf(oldId), targetIndex = 0),
                slashCommandState = SlashCommandState(
                    anchorBlockId = oldId,
                    query = "",
                    queryRange = SlashQueryRange(start = 0, endExclusive = 1),
                ),
            )
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(oldId, "old")
        spanStates.getOrCreate(oldId, listOf(TextSpan(0, 3, SpanStyle.Bold)), textLength = 3)

        val result = holder.loadFromHtml(
            "<p>First</p><aside>Second</aside>",
            textStates,
            spanStates,
            HtmlProfile.Default,
        )

        assertNull(textStates.get(oldId))
        assertNull(spanStates.get(oldId))
        assertEquals(2, result.blocks.size)
        assertTrue(result.warnings.any { it is HtmlDecodeWarning.UnknownTag })
        assertEquals(2, holder.state.blocks.size)
        assertEquals("First", assertTextContent(holder.state.blocks[0]).text)
        assertEquals("Second", assertTextContent(holder.state.blocks[1]).text)
        assertNull(holder.state.focusedBlockId)
        assertTrue(holder.state.selectedBlockIds.isEmpty())
        assertNull(holder.state.dragState)
        assertNull(holder.state.slashCommandState)
    }

    @Test
    fun `toHtml and loadFromHtml pass through custom profile mappings`() {
        val holder = holderWith(
            Block(BlockId("b1"), BlockType.Paragraph, BlockContent.Text("Exported")),
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val profile = HtmlProfile.Default
            .withBlockEncoder<BlockType.Paragraph>(
                BlockEncoder { ctx, block, _ ->
                    HtmlEmit.Raw("<custom>${ctx.encodeInline(block)}</custom>")
                },
            )
            .withTagDecoder("custom") { ctx, _, children ->
                val inline = ctx.collectInlineText(children = children, trimEdges = true)
                TagDecodeResult.AsBlock(Block.heading(level = 3, text = inline.text))
            }

        val html = holder.toHtml(textStates, spanStates, profile)
        val result = holder.loadFromHtml("<custom>Imported</custom>", textStates, spanStates, profile)

        assertEquals("<custom>Exported</custom>", html)
        assertEquals(1, result.blocks.size)
        assertIs<BlockType.Heading>(holder.state.blocks.single().type)
        assertEquals("Imported", assertTextContent(holder.state.blocks.single()).text)
    }

    private fun holderWith(vararg blocks: Block): EditorStateHolder =
        EditorStateHolder(EditorState.withBlocks(blocks.toList()))

    private fun assertTextContent(block: Block): BlockContent.Text =
        assertIs<BlockContent.Text>(block.content)
}
