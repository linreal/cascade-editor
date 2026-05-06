package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import io.github.linreal.cascade.editor.htmlserialization.PreservedHtmlBlockType
import io.github.linreal.cascade.editor.htmlserialization.TagDecodeResult
import io.github.linreal.cascade.editor.htmlserialization.UnknownTagPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UnknownTagPolicyTest {

    @Test
    fun `Strip drops unknown tag keeps child text and emits no unknown-tag warning`() {
        val profile = HtmlProfile.Default.withUnknownTagPolicy(UnknownTagPolicy.Strip)

        val result = HtmlSchema.decodeWithReport("<x>Hello</x>", profile)

        assertEquals("Hello", assertTextContent(result.blocks.single()).text)
        assertTrue(result.warnings.none { it is HtmlDecodeWarning.UnknownTag })
    }

    @Test
    fun `WarnAndStrip drops unknown tag keeps child text and emits offset warning`() {
        val profile = HtmlProfile.Default.withUnknownTagPolicy(UnknownTagPolicy.WarnAndStrip)

        val result = HtmlSchema.decodeWithReport("  <x>Hello</x>", profile)

        assertEquals("Hello", assertTextContent(result.blocks.single()).text)
        assertEquals(HtmlDecodeWarning.UnknownTag(tag = "x", charOffset = 2), result.warnings.single())
    }

    @Test
    fun `Preserve stores block-level unknown tag as raw html custom block`() {
        val html = """<X Data-ID="1">&amp;</X>"""
        val profile = HtmlProfile.Default.withUnknownTagPolicy(UnknownTagPolicy.Preserve)

        val result = HtmlSchema.decodeWithReport(html, profile)

        val block = result.blocks.single()
        assertSame(PreservedHtmlBlockType, block.type)
        val content = assertIs<BlockContent.Custom>(block.content)
        assertEquals("html.preserved", content.typeId)
        assertEquals("x", content.data["tagName"])
        assertEquals(html, content.data["rawHtml"])
    }

    @Test
    fun `Preserve behaves like warn-and-strip for inline unknown tags`() {
        val profile = HtmlProfile.Default
            .withUnknownTagPolicy(UnknownTagPolicy.Preserve)
            .withTagDecoder("p") { ctx, _, children ->
                val inline = ctx.decodeInline(children)
                TagDecodeResult.AsBlock(Block.paragraph(inline.text, inline.spans))
            }

        val result = HtmlSchema.decodeWithReport("<p>a<x>b</x>c</p>", profile)

        assertEquals("abc", assertTextContent(result.blocks.single()).text)
        assertTrue(result.blocks.single().type is BlockType.Paragraph)
        assertEquals(HtmlDecodeWarning.UnknownTag(tag = "x", charOffset = 4), result.warnings.single())
    }

    @Test
    fun `Custom unknown tag policy controls the decode result`() {
        val profile = HtmlProfile.Default.withUnknownTagPolicy(
            UnknownTagPolicy.Custom { element, _ ->
                TagDecodeResult.AsBlock(Block.heading(1, "custom:${element.tag}"))
            }
        )

        val result = HtmlSchema.decodeWithReport("<x></x>", profile)

        assertTrue(result.blocks.single().type is BlockType.Heading)
        assertEquals("custom:x", assertTextContent(result.blocks.single()).text)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `Custom unknown tag policy attributes handler exceptions to the unknown tag`() {
        val profile = HtmlProfile.Default.withUnknownTagPolicy(
            UnknownTagPolicy.Custom { _, _ ->
                throw IllegalStateException("custom boom")
            }
        )

        val result = HtmlSchema.decodeWithReport("<x><a></a></x>", profile)

        assertTrue(result.blocks.isEmpty())
        val warning = assertIs<HtmlDecodeWarning.DecoderException>(result.warnings.single())
        assertEquals("x", warning.tag)
        assertEquals("custom boom", warning.message)
        assertEquals(0, warning.charOffset)
    }

    @Test
    fun `Custom unknown tag policy keeps delegated child decoder exceptions attributed to the child`() {
        val profile = HtmlProfile.Default
            .withTagDecoder("a") { _, _, _ ->
                throw IllegalStateException("child boom")
            }
            .withUnknownTagPolicy(
                UnknownTagPolicy.Custom { element, ctx ->
                    TagDecodeResult.AsBlocks(ctx.decodeBlocks(element.children))
                }
            )

        val result = HtmlSchema.decodeWithReport("<x><a></a></x>", profile)

        assertTrue(result.blocks.isEmpty())
        val warning = assertIs<HtmlDecodeWarning.DecoderException>(result.warnings.single())
        assertEquals("a", warning.tag)
        assertEquals("child boom", warning.message)
        assertEquals(3, warning.charOffset)
    }

    private fun assertTextContent(block: Block): BlockContent.Text = assertIs<BlockContent.Text>(block.content)
}
