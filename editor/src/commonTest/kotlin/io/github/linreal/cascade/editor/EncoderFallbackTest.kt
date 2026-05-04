package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlEmit
import io.github.linreal.cascade.editor.htmlserialization.HtmlEncodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import io.github.linreal.cascade.editor.htmlserialization.HtmlTagPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EncoderFallbackTest {

    @Test
    fun `default block fallback preserves text and drops unsupported formatting via span fallback`() {
        val block = Block.todo(
            text = "a < b",
            checked = false,
        ).withContent(
            BlockContent.Text(
                text = "a < b",
                spans = listOf(TextSpan(0, 1, SpanStyle.Custom("unsupported"))),
            )
        )

        val result = HtmlSchema.encodeWithReport(listOf(block), HtmlProfile.Default)

        assertEquals("<p>a &lt; b</p>", result.html)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `encoder skip uses block fallback`() {
        val profile = HtmlProfile.Default.withBlockEncoder<BlockType.Paragraph> { _, _, _ ->
            HtmlEmit.Skip
        }

        val result = HtmlSchema.encodeWithReport(listOf(Block.paragraph("fallback")), profile)

        assertEquals("<p>fallback</p>", result.html)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `default block fallback preserves code newlines when code encoder skips`() {
        val profile = HtmlProfile.Default.withBlockEncoder<BlockType.Code> { _, _, _ ->
            HtmlEmit.Skip
        }
        val block = Block(
            id = BlockId.generate(),
            type = BlockType.Code,
            content = BlockContent.Text("line 1\nline 2"),
        )

        val result = HtmlSchema.encodeWithReport(listOf(block), profile)

        assertEquals("<p>line 1\nline 2</p>", result.html)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `throwing block encoder records warning and uses fallback`() {
        val profile = HtmlProfile.Default.withBlockEncoder<BlockType.Paragraph> { _, _, _ ->
            throw Throwable("primary boom")
        }

        val result = HtmlSchema.encodeWithReport(listOf(Block.paragraph("safe")), profile)

        assertEquals("<p>safe</p>", result.html)
        val warning = assertIs<HtmlEncodeWarning.EncoderException>(result.warnings.single())
        assertEquals("paragraph", warning.typeId)
        assertEquals("primary boom", warning.message)
    }

    @Test
    fun `throwing primary and fallback return empty output with warnings`() {
        val profile = HtmlProfile.Default
            .withBlockEncoder<BlockType.Paragraph> { _, _, _ ->
                throw IllegalStateException("primary boom")
            }
            .withEncoderBlockFallback { _, _, _ ->
                throw IllegalStateException("fallback boom")
            }

        val result = HtmlSchema.encodeWithReport(listOf(Block.paragraph("lost")), profile)

        assertEquals("", result.html)
        assertEquals(
            listOf(
                HtmlEncodeWarning.EncoderException(typeId = "paragraph", message = "primary boom"),
                HtmlEncodeWarning.EncoderException(typeId = "paragraph", message = "fallback boom"),
            ),
            result.warnings,
        )
    }

    @Test
    fun `throwing span encoder records warning and uses span fallback`() {
        val block = Block.paragraph(
            text = "bold",
            spans = listOf(TextSpan(0, 4, SpanStyle.Bold)),
        )
        val profile = HtmlProfile.Default
            .withBlockEncoder<BlockType.Paragraph> { ctx, encodedBlock, _ ->
                HtmlEmit.Raw("<p>${ctx.encodeInline(encodedBlock)}</p>")
            }
            .withSpanEncoder<SpanStyle.Bold> {
                throw IllegalStateException("span boom")
            }

        val result = HtmlSchema.encodeWithReport(listOf(block), profile)

        assertEquals("<p>bold</p>", result.html)
        val warning = assertIs<HtmlEncodeWarning.EncoderException>(result.warnings.single())
        assertEquals("bold", warning.typeId)
        assertEquals("span boom", warning.message)
    }

    @Test
    fun `custom block content encoder is resolved by content type id`() {
        val block = Block(
            id = BlockId.generate(),
            type = BlockType.Paragraph,
            content = BlockContent.Custom(typeId = "custom.payload", data = mapOf("value" to "x")),
        )
        val profile = HtmlProfile.Default.withCustomBlockEncoder("custom.payload") { _, _, content ->
            HtmlEmit.Raw("<custom>${(content as BlockContent.Custom).data["value"]}</custom>")
        }

        val html = HtmlSchema.encode(listOf(block), profile)

        assertEquals("<custom>x</custom>", html)
    }

    @Test
    fun `custom span encoder is resolved by type id`() {
        val block = Block.paragraph(
            text = "custom",
            spans = listOf(TextSpan(0, 6, SpanStyle.Custom("mention", payload = "1"))),
        )
        val profile = HtmlProfile.Default
            .withBlockEncoder<BlockType.Paragraph> { ctx, encodedBlock, _ ->
                HtmlEmit.Raw("<p>${ctx.encodeInline(encodedBlock)}</p>")
            }
            .withCustomSpanEncoder("mention") {
                HtmlTagPair(open = """<span data-id="${it.payload}">""", close = "</span>")
            }

        val html = HtmlSchema.encode(listOf(block), profile)

        assertEquals("""<p><span data-id="1">custom</span></p>""", html)
    }
}
