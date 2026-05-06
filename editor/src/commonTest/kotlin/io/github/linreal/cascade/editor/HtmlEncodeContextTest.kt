package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlEmit
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import io.github.linreal.cascade.editor.htmlserialization.HtmlTagPair
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlEncodeContextTest {

    @Test
    fun `encodeInline escapes text and emits registered span tags`() {
        val block = Block.paragraph(
            text = "a < b & c",
            spans = listOf(TextSpan(2, 5, SpanStyle.Bold)),
        )
        val profile = HtmlProfile.Default
            .withBlockEncoder<BlockType.Paragraph> { ctx, encodedBlock, _ ->
                HtmlEmit.Raw("<p>${ctx.encodeInline(encodedBlock)}</p>")
            }
            .withSpanEncoder<SpanStyle.Bold> {
                HtmlTagPair(open = "<strong>", close = "</strong>")
            }

        val html = HtmlSchema.encode(listOf(block), profile)

        assertEquals("<p>a <strong>&lt; b</strong> &amp; c</p>", html)
    }

    @Test
    fun `encodeInline renders non-code newlines as br`() {
        val profile = HtmlProfile.Default.withBlockEncoder<BlockType.Paragraph> { ctx, block, _ ->
            HtmlEmit.Raw("<p>${ctx.encodeInline(block)}</p>")
        }

        val html = HtmlSchema.encode(listOf(Block.paragraph("a\nb")), profile)

        assertEquals("<p>a<br>b</p>", html)
    }

    @Test
    fun `encodeInline preserves literal newlines for code blocks`() {
        val block = Block(
            id = BlockId.generate(),
            type = BlockType.Code,
            content = BlockContent.Text("a\nb"),
        )
        val profile = HtmlProfile.Default.withBlockEncoder<BlockType.Code> { ctx, encodedBlock, _ ->
            HtmlEmit.Raw("<pre>${ctx.encodeInline(encodedBlock)}</pre>")
        }

        val html = HtmlSchema.encode(listOf(block), profile)

        assertEquals("<pre>a\nb</pre>", html)
    }

    @Test
    fun `context helpers expose text-only attr escaping and free fragment encoding`() {
        val block = Block.paragraph(
            text = "plain < text",
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        val profile = HtmlProfile.Default.withBlockEncoder<BlockType.Paragraph> { ctx, encodedBlock, _ ->
            val title = ctx.escapeAttr("""a "quote" & b""")
            val textOnly = ctx.encodeTextOnly(encodedBlock)
            val fragment = ctx.encodeInlineFragment(
                text = "x\ny",
                spans = emptyList(),
                preserveNewlines = false,
            )
            HtmlEmit.Raw("""<p data-title="$title">$textOnly|$fragment</p>""")
        }

        val html = HtmlSchema.encode(listOf(block), profile)

        assertEquals("""<p data-title="a &quot;quote&quot; &amp; b">plain &lt; text|x<br>y</p>""", html)
    }
}
