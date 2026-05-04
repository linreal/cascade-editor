package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import io.github.linreal.cascade.editor.htmlserialization.TagDecodeResult
import io.github.linreal.cascade.editor.htmlserialization.UnknownTagPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HtmlDecodeWarningTest {

    @Test
    fun `decoder exception is reported and sibling content remains decoded`() {
        val profile = HtmlProfile.Default
            .withTagDecoder("p") { ctx, _, children ->
                val inline = ctx.decodeInline(children)
                TagDecodeResult.AsBlock(Block.paragraph(inline.text, inline.spans))
            }
            .withTagDecoder("bad") { _, _, _ ->
                throw IllegalStateException("boom")
            }

        val result = HtmlSchema.decodeWithReport("<p>a<bad></bad>b</p>", profile)

        assertEquals("ab", assertTextContent(result.blocks.single()).text)
        val warning = assertIs<HtmlDecodeWarning.DecoderException>(result.warnings.single())
        assertEquals("bad", warning.tag)
        assertEquals("boom", warning.message)
        assertEquals(4, warning.charOffset)
    }

    @Test
    fun `parser warnings keep source offsets when decoded with report`() {
        val result = HtmlSchema.decodeWithReport("<b><i></b></i>", HtmlProfile.Default)

        assertTrue(
            result.warnings.any {
                it == HtmlDecodeWarning.MismatchedNesting(
                    expected = "i",
                    found = "b",
                    charOffset = 6,
                )
            }
        )
    }

    @Test
    fun `stray closing and unclosed tag parser warnings are reported through decode`() {
        val stray = HtmlSchema.decodeWithReport("</span>", HtmlProfile.Default)
        val unclosed = HtmlSchema.decodeWithReport(
            "<p",
            HtmlProfile.Default.withUnknownTagPolicy(UnknownTagPolicy.Strip),
        )

        assertEquals(HtmlDecodeWarning.StrayClosingTag(tag = "span", charOffset = 0), stray.warnings.single())
        assertEquals(HtmlDecodeWarning.UnclosedTag(tag = "p", charOffset = 0), unclosed.warnings.single())
    }

    private fun assertTextContent(block: Block): BlockContent.Text = assertIs<BlockContent.Text>(block.content)
}
