package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeResult
import io.github.linreal.cascade.editor.htmlserialization.HtmlEmit
import io.github.linreal.cascade.editor.htmlserialization.HtmlEncodeResult
import io.github.linreal.cascade.editor.htmlserialization.HtmlEncodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HtmlNoThrowGuaranteeTest {

    @Test
    fun `decodeWithReport returns a result for empty input`() {
        assertDecodesWithReport("")
    }

    @Test
    fun `decodeWithReport returns a result for a lone opening bracket`() {
        assertDecodesWithReport("<")
    }

    @Test
    fun `decodeWithReport returns a result for a truncated tag name`() {
        assertDecodesWithReport("<p")
    }

    @Test
    fun `decodeWithReport returns a result for a truncated quoted attribute`() {
        assertDecodesWithReport("<p class=\"")
    }

    @Test
    fun `decodeWithReport returns a result for moderately nested malformed input`() {
        assertDecodesWithReport(
            buildString {
                repeat(64) { append("<x>") }
                repeat(32) { append("</y>") }
            }
        )
    }

    @Test
    fun `decodeWithReport returns a result for deeply nested malformed input`() {
        assertDecodesWithReport("<x>".repeat(1000))
    }

    @Test
    fun `decodeWithReport returns a result for a tag with many attributes`() {
        val html = buildString {
            append("<x")
            repeat(50_000) { index ->
                append(" data-")
                append(index)
                append("=\"")
                append(index)
                append('"')
            }
            append(">text</x>")
        }

        assertDecodesWithReport(html)
    }

    @Test
    fun `decodeWithReport returns a result for control characters`() {
        assertDecodesWithReport("\u0000\u0001\u0002\u007F\u00FF")
    }

    @Test
    fun `decodeWithReport returns a result for large plain text`() {
        assertDecodesWithReport("x".repeat(1024 * 1024))
    }

    @Test
    fun `decodeWithReport returns a result for truncated custom html dialect`() {
        assertDecodesWithReport(
            "Hello world!\n<ul><li>List\n</li><li class=\"ql-indent-2\">Nested</li></ul>"
                .removeRange(8, 12)
        )
    }

    @Test
    fun `decodeWithReport returns a result when a consumer decoder throws Throwable`() {
        val profile = HtmlProfile.Default.withTagDecoder("p") { _, _, _ ->
            throw Throwable("consumer boom")
        }

        val result = assertDecodesWithReport("<p>boom</p>", profile)

        val warning = assertIs<HtmlDecodeWarning.DecoderException>(result.warnings.single())
        assertEquals("p", warning.tag)
        assertEquals("consumer boom", warning.message)
    }

    @Test
    fun `encodeWithReport returns a result for empty block list`() {
        assertEncodesWithReport(emptyList())
    }

    @Test
    fun `encodeWithReport returns a result for large text`() {
        assertEncodesWithReport(listOf(Block.paragraph("x".repeat(1024 * 1024))))
    }

    @Test
    fun `encodeWithReport returns a result for custom content without a matching encoder`() {
        val block = Block(
            id = BlockId.generate(),
            type = BlockType.Paragraph,
            content = BlockContent.Custom("unknown", data = mapOf("value" to "x")),
        )

        assertEncodesWithReport(listOf(block))
    }

    @Test
    fun `encodeWithReport returns a result when a consumer block encoder throws Throwable`() {
        val profile = HtmlProfile.Default.withBlockEncoder<BlockType.Paragraph> { _, _, _ ->
            throw Throwable("block boom")
        }

        val result = assertEncodesWithReport(listOf(Block.paragraph("boom")), profile)

        val warning = assertIs<HtmlEncodeWarning.EncoderException>(result.warnings.single())
        assertEquals("paragraph", warning.typeId)
        assertEquals("block boom", warning.message)
    }

    @Test
    fun `encodeWithReport returns a result when a consumer span encoder throws Throwable`() {
        val block = Block.paragraph(
            text = "boom",
            spans = listOf(TextSpan(0, 4, SpanStyle.Bold)),
        )
        val profile = HtmlProfile.Default
            .withBlockEncoder<BlockType.Paragraph> { ctx, encodedBlock, _ ->
                HtmlEmit.Raw(ctx.encodeInline(encodedBlock))
            }
            .withSpanEncoder<SpanStyle.Bold> {
                throw Throwable("span boom")
            }

        val result = assertEncodesWithReport(listOf(block), profile)

        val warning = assertIs<HtmlEncodeWarning.EncoderException>(result.warnings.single())
        assertEquals("bold", warning.typeId)
        assertEquals("span boom", warning.message)
    }

    private fun assertDecodesWithReport(
        html: String,
        profile: HtmlProfile = HtmlProfile.Default,
    ): HtmlDecodeResult = assertIs<HtmlDecodeResult>(
        HtmlSchema.decodeWithReport(html, profile),
        message = "Input should decode without throwing: ${html.take(120)}",
    )

    private fun assertEncodesWithReport(
        blocks: List<Block>,
        profile: HtmlProfile = HtmlProfile.Default,
    ): HtmlEncodeResult = assertIs<HtmlEncodeResult>(
        HtmlSchema.encodeWithReport(blocks, profile),
        message = "Blocks should encode without throwing",
    )
}
