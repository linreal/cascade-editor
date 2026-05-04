package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.htmlserialization.BlockGroupEncoder
import io.github.linreal.cascade.editor.htmlserialization.HtmlEmit
import io.github.linreal.cascade.editor.htmlserialization.HtmlEncodeContext
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlBlockGroupEncoderTest {

    @Test
    fun `group encoder is invoked once for a contiguous matching run`() {
        val groupEncoder = RecordingGroupEncoder()
        val profile = HtmlProfile.Default
            .withoutDefaultGroupEncoders()
            .withBlockGroupEncoder("lists", groupEncoder)
            .withBlockEncoder<BlockType.Quote> { ctx, block, _ ->
                HtmlEmit.Raw("<q>${ctx.encodeInline(block)}</q>")
            }

        val html = HtmlSchema.encode(
            listOf(
                Block.bulletList("one"),
                Block.bulletList("two"),
                Block(
                    id = io.github.linreal.cascade.editor.core.BlockId.generate(),
                    type = BlockType.Quote,
                    content = BlockContent.Text("quote"),
                ),
            ),
            profile,
        )

        assertEquals("<group>one,two</group><q>quote</q>", html)
        assertEquals(1, groupEncoder.encodedRuns.size)
        assertEquals(listOf("one", "two"), groupEncoder.encodedRuns.single())
    }

    @Test
    fun `matching groups are split by unrelated blocks`() {
        val groupEncoder = RecordingGroupEncoder()
        val profile = HtmlProfile.Default
            .withoutDefaultGroupEncoders()
            .withBlockGroupEncoder("lists", groupEncoder)
            .withBlockEncoder<BlockType.Paragraph> { ctx, block, _ ->
                HtmlEmit.Raw("<p>${ctx.encodeInline(block)}</p>")
            }

        val html = HtmlSchema.encode(
            listOf(
                Block.bulletList("one"),
                Block.paragraph("gap"),
                Block.bulletList("two"),
            ),
            profile,
        )

        assertEquals("<group>one</group><p>gap</p><group>two</group>", html)
        assertEquals(listOf(listOf("one"), listOf("two")), groupEncoder.encodedRuns)
    }

    @Test
    fun `null group key falls through to per-block encoder`() {
        val groupEncoder = RecordingGroupEncoder()
        val profile = HtmlProfile.Default
            .withoutDefaultGroupEncoders()
            .withBlockGroupEncoder("lists", groupEncoder)
            .withBlockEncoder<BlockType.Paragraph> { ctx, block, _ ->
                HtmlEmit.Raw("<p>${ctx.encodeInline(block)}</p>")
            }

        val html = HtmlSchema.encode(listOf(Block.paragraph("plain")), profile)

        assertEquals("<p>plain</p>", html)
        assertEquals(emptyList(), groupEncoder.encodedRuns)
    }

    private class RecordingGroupEncoder : BlockGroupEncoder {
        val encodedRuns: MutableList<List<String>> = mutableListOf()

        override fun groupKey(block: Block): Any? =
            if (block.type == BlockType.BulletList) "list-run" else null

        override fun encodeGroup(ctx: HtmlEncodeContext, blocks: List<Block>): HtmlEmit {
            val texts = blocks.map { block -> (block.content as BlockContent.Text).text }
            encodedRuns += texts
            return HtmlEmit.Raw("<group>${texts.joinToString(",")}</group>")
        }
    }

    private fun HtmlProfile.withoutDefaultGroupEncoders(): HtmlProfile {
        // Custom list-group tests remove the default listOutline encoder because it
        // is registered first and would claim list blocks before the test encoder.
        return withoutBlockGroupEncoder("listOutline")
    }
}
