package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import io.github.linreal.cascade.editor.htmlserialization.TagDecodeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TagDecodeResultDispatchTest {

    @Test
    fun `custom decoder can return one block`() {
        val profile = HtmlProfile.Default.withTagDecoder("x") { _, _, _ ->
            TagDecodeResult.AsBlock(Block.paragraph("decoded"))
        }

        val result = HtmlSchema.decodeWithReport("<x></x>", profile)

        assertEquals("decoded", assertTextContent(result.blocks.single()).text)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `custom decoder can return multiple blocks`() {
        val profile = HtmlProfile.Default.withTagDecoder("x") { _, _, _ ->
            TagDecodeResult.AsBlocks(listOf(Block.paragraph("a"), Block.paragraph("b")))
        }

        val result = HtmlSchema.decodeWithReport("<x></x>", profile)

        assertEquals(listOf("a", "b"), result.blocks.map { assertTextContent(it).text })
    }

    @Test
    fun `AsBlock with text content in inline context is flattened with warning`() {
        val profile = HtmlProfile.Default
            .withTagDecoder("p") { ctx, _, children ->
                val inline = ctx.decodeInline(children)
                TagDecodeResult.AsBlock(Block.paragraph(inline.text, inline.spans))
            }
            .withTagDecoder("x") { _, _, _ ->
                TagDecodeResult.AsBlock(
                    Block.paragraph(
                        text = "inner",
                        spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
                    )
                )
            }

        val result = HtmlSchema.decodeWithReport("<p>a<x></x>b</p>", profile)

        val content = assertTextContent(result.blocks.single())
        assertEquals("ainnerb", content.text)
        assertEquals(listOf(TextSpan(1, 6, SpanStyle.Bold)), content.spans)
        assertEquals(HtmlDecodeWarning.BlockInInlineContext(tag = "x", charOffset = 4), result.warnings.single())
    }

    @Test
    fun `AsBlock with non-text content in inline context is dropped with warning`() {
        val profile = HtmlProfile.Default
            .withTagDecoder("p") { ctx, _, children ->
                val inline = ctx.decodeInline(children)
                TagDecodeResult.AsBlock(Block.paragraph(inline.text, inline.spans))
            }
            .withTagDecoder("x") { _, _, _ ->
                TagDecodeResult.AsBlock(Block.divider())
            }

        val result = HtmlSchema.decodeWithReport("<p>a<x></x>b</p>", profile)

        assertEquals("ab", assertTextContent(result.blocks.single()).text)
        assertEquals(HtmlDecodeWarning.BlockInInlineContext(tag = "x", charOffset = 4), result.warnings.single())
    }

    @Test
    fun `inline AsText appends returned text and spans`() {
        val profile = HtmlProfile.Default
            .withTagDecoder("p") { ctx, _, children ->
                val inline = ctx.decodeInline(children)
                TagDecodeResult.AsBlock(Block.paragraph(inline.text, inline.spans))
            }
            .withTagDecoder("x") { _, _, _ ->
                TagDecodeResult.AsText("styled", listOf(TextSpan(0, 6, SpanStyle.Italic)))
            }

        val result = HtmlSchema.decodeWithReport("<p>a<x></x></p>", profile)

        val content = assertTextContent(result.blocks.single())
        assertEquals("astyled", content.text)
        assertEquals(listOf(TextSpan(1, 7, SpanStyle.Italic)), content.spans)
    }

    private fun assertTextContent(block: Block): BlockContent.Text = assertIs<BlockContent.Text>(block.content)
}
