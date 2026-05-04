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

class HtmlDecodeContextHelpersTest {

    @Test
    fun `decodeInline preserves nested text and spans in child order`() {
        val profile = HtmlProfile.Default
            .withTagDecoder("p") { ctx, _, children ->
                val inline = ctx.decodeInline(children)
                TagDecodeResult.AsBlock(Block.paragraph(inline.text, inline.spans))
            }
            .withTagDecoder("strong") { ctx, _, children ->
                val inline = ctx.decodeInline(children)
                TagDecodeResult.AsText(
                    text = inline.text,
                    spans = inline.spans + TextSpan(0, inline.text.length, SpanStyle.Bold),
                )
            }
            .withTagDecoder("em") { ctx, _, children ->
                val inline = ctx.decodeInline(children)
                TagDecodeResult.AsText(
                    text = inline.text,
                    spans = inline.spans + TextSpan(0, inline.text.length, SpanStyle.Italic),
                )
            }

        val result = HtmlSchema.decodeWithReport("<p>a<strong>b<em>c</em></strong>d</p>", profile)

        val text = assertTextContent(result.blocks.single())
        assertEquals("abcd", text.text)
        assertEquals(
            listOf(
                TextSpan(2, 3, SpanStyle.Italic),
                TextSpan(1, 3, SpanStyle.Bold),
            ),
            text.spans,
        )
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `context exposes source parent and warning helpers`() {
        val profile = HtmlProfile.Default
            .withTagDecoder("section") { ctx, _, children ->
                TagDecodeResult.AsBlocks(ctx.decodeBlocks(children))
            }
            .withTagDecoder("x") { ctx, _, children ->
                assertTrue(ctx.isBlockContext)
                assertEquals("section", ctx.parentTag)
                assertEquals(9, ctx.charOffset)
                assertEquals("<section><x data-id=\"1\">text</x></section>", ctx.rawSource)
                assertEquals("text", ctx.rawSliceOf(children.first()))
                ctx.warn(HtmlDecodeWarning.UnknownAttribute("x", "data-id", ctx.charOffset))
                TagDecodeResult.AsBlock(Block.paragraph("ok"))
            }

        val result = HtmlSchema.decodeWithReport("""<section><x data-id="1">text</x></section>""", profile)

        assertEquals("ok", assertTextContent(result.blocks.single()).text)
        assertEquals(HtmlDecodeWarning.UnknownAttribute("x", "data-id", 9), result.warnings.single())
    }

    @Test
    fun `context exposes registered tag decoder lookup to custom decoders`() {
        val profile = HtmlProfile.Default
            .withTagDecoder("wrapper") { ctx, _, children ->
                assertTrue(ctx.tagDecoderFor("p") != null)
                TagDecodeResult.AsBlocks(ctx.decodeBlocks(children))
            }

        val result = HtmlSchema.decodeWithReport("<wrapper><p>ok</p></wrapper>", profile)

        assertEquals("ok", assertTextContent(result.blocks.single()).text)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `collectInlineText applies trim and collapse flags`() {
        val profile = HtmlProfile.Default
            .withTagDecoder("trim") { ctx, _, children ->
                val inline = ctx.collectInlineText(children, trimEdges = true)
                TagDecodeResult.AsBlock(Block.paragraph(inline.text, inline.spans))
            }
            .withTagDecoder("trail") { ctx, _, children ->
                val inline = ctx.collectInlineText(children, trimSingleTrailingNewline = true)
                TagDecodeResult.AsBlock(Block.paragraph(inline.text, inline.spans))
            }
            .withTagDecoder("collapse") { ctx, _, children ->
                val inline = ctx.collectInlineText(children, collapseInternalSpaces = true)
                TagDecodeResult.AsBlock(Block.paragraph(inline.text, inline.spans))
            }

        val result = HtmlSchema.decodeWithReport(
            "<trim>  a  </trim><trail>a\n</trail><collapse>a   b\tc</collapse>",
            profile,
        )

        assertEquals(
            listOf("a", "a", "a b c"),
            result.blocks.map { assertTextContent(it).text },
        )
    }

    @Test
    fun `collectInlineText normalizes an isolated tab when collapseInternalSpaces is enabled`() {
        val profile = HtmlProfile.Default
            .withTagDecoder("collapse") { ctx, _, children ->
                val inline = ctx.collectInlineText(children, collapseInternalSpaces = true)
                TagDecodeResult.AsBlock(Block.paragraph(inline.text, inline.spans))
            }

        val result = HtmlSchema.decodeWithReport("<collapse>a\tb</collapse>", profile)

        assertEquals("a b", assertTextContent(result.blocks.single()).text)
    }

    @Test
    fun `context reports inline context and null parent at root`() {
        var sawInlineContext = false
        var sawNullParent = false
        val profile = HtmlProfile.Default
            .withTagDecoder("p") { ctx, _, children ->
                sawNullParent = ctx.parentTag == null
                val inline = ctx.decodeInline(children)
                TagDecodeResult.AsBlock(Block.paragraph(inline.text, inline.spans))
            }
            .withTagDecoder("x") { ctx, _, _ ->
                sawInlineContext = !ctx.isBlockContext
                TagDecodeResult.AsText("x", emptyList())
            }

        HtmlSchema.decodeWithReport("<p><x></x></p>", profile)

        assertTrue(sawNullParent)
        assertTrue(sawInlineContext)
    }

    private fun assertTextContent(block: Block): BlockContent.Text = assertIs<BlockContent.Text>(block.content)
}
