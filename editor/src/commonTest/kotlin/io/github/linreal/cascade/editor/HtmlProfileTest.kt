package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.htmlserialization.BlockEncoder
import io.github.linreal.cascade.editor.htmlserialization.BlockGroupEncoder
import io.github.linreal.cascade.editor.htmlserialization.BlockSeparator
import io.github.linreal.cascade.editor.htmlserialization.EntityDecode
import io.github.linreal.cascade.editor.htmlserialization.HtmlEmit
import io.github.linreal.cascade.editor.htmlserialization.HtmlEncodeContext
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfileSupportSet
import io.github.linreal.cascade.editor.htmlserialization.HtmlTagPair
import io.github.linreal.cascade.editor.htmlserialization.InlineRoot
import io.github.linreal.cascade.editor.htmlserialization.SpanEncoder
import io.github.linreal.cascade.editor.htmlserialization.TagDecodeResult
import io.github.linreal.cascade.editor.htmlserialization.TagDecoder
import io.github.linreal.cascade.editor.htmlserialization.UnknownTagPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HtmlProfileTest {

    // tagDecoderFor — registration replacement and removal

    @Test
    fun `withTagDecoder registers a decoder by lowercased tag name`() {
        val decoder = TagDecoder { _, _, _ -> TagDecodeResult.Drop }
        val profile = HtmlProfile.Default.withTagDecoder("p", decoder)
        assertSame(decoder, profile.tagDecoderFor("p"))
    }

    @Test
    fun `tagDecoderFor lookup is case-insensitive`() {
        val decoder = TagDecoder { _, _, _ -> TagDecodeResult.Drop }
        val profile = HtmlProfile.Default.withTagDecoder("P", decoder)
        assertSame(decoder, profile.tagDecoderFor("p"))
        assertSame(decoder, profile.tagDecoderFor("P"))
    }

    @Test
    fun `withTagDecoder replaces a previously registered decoder for the same tag`() {
        val first = TagDecoder { _, _, _ -> TagDecodeResult.Drop }
        val replacement = TagDecoder { _, _, _ -> TagDecodeResult.AsText("ok", emptyList()) }
        val profile = HtmlProfile.Default
            .withTagDecoder("li", first)
            .withTagDecoder("li", replacement)
        assertSame(replacement, profile.tagDecoderFor("li"))
    }

    @Test
    fun `withoutTagDecoder removes a registered decoder`() {
        val decoder = TagDecoder { _, _, _ -> TagDecodeResult.Drop }
        val withDecoder = HtmlProfile.Default.withTagDecoder("p", decoder)
        val withoutDecoder = withDecoder.withoutTagDecoder("p")
        assertNull(withoutDecoder.tagDecoderFor("p"))
    }

    @Test
    fun `tagDecoderFor returns null for an unregistered tag`() {
        assertNull(HtmlProfile.Default.tagDecoderFor("never-registered"))
    }

    // Profile immutability — every with* / without* returns a new instance and never mutates the receiver.

    @Test
    fun `withTagDecoder returns a new profile and leaves the original unchanged`() {
        val original = HtmlProfile.Default
        val originalDecoder = original.tagDecoderFor("p")
        val decoder = TagDecoder { _, _, _ -> TagDecodeResult.Drop }
        val mutated = original.withTagDecoder("p", decoder)
        assertNotSame(original, mutated)
        assertSame(originalDecoder, original.tagDecoderFor("p"))
        assertSame(decoder, mutated.tagDecoderFor("p"))
    }

    @Test
    fun `withParserPolicy BlockSeparator returns new profile with new policy and original unchanged`() {
        val original = HtmlProfile.Default
        val mutated = original.withParserPolicy(BlockSeparator.Newline)
        assertNotSame(original, mutated)
        assertEquals(BlockSeparator.BlockTags, original.blockSeparator)
        assertEquals(BlockSeparator.Newline, mutated.blockSeparator)
    }

    @Test
    fun `withParserPolicy InlineRoot returns new profile with new policy and original unchanged`() {
        val original = HtmlProfile.Default
        val mutated = original.withParserPolicy(InlineRoot.WrapInParagraph)
        assertNotSame(original, mutated)
        assertEquals(InlineRoot.Drop, original.inlineRoot)
        assertEquals(InlineRoot.WrapInParagraph, mutated.inlineRoot)
    }

    @Test
    fun `withParserPolicy EntityDecode returns new profile with new policy and original unchanged`() {
        val original = HtmlProfile.Default
        val mutated = original.withParserPolicy(EntityDecode.None)
        assertNotSame(original, mutated)
        assertEquals(EntityDecode.Standard, original.entityDecode)
        assertEquals(EntityDecode.None, mutated.entityDecode)
    }

    // withUnknownTagPolicy — exhaustive on the four documented variants.

    @Test
    fun `withUnknownTagPolicy Strip exposes new policy and leaves original unchanged`() {
        val original = HtmlProfile.Default
        val mutated = original.withUnknownTagPolicy(UnknownTagPolicy.Strip)
        assertNotSame(original, mutated)
        assertEquals(UnknownTagPolicy.WarnAndStrip, original.unknownTagPolicy)
        assertEquals(UnknownTagPolicy.Strip, mutated.unknownTagPolicy)
    }

    @Test
    fun `withUnknownTagPolicy WarnAndStrip exposes new policy and leaves original unchanged`() {
        val original = HtmlProfile.Default.withUnknownTagPolicy(UnknownTagPolicy.Strip)
        val mutated = original.withUnknownTagPolicy(UnknownTagPolicy.WarnAndStrip)
        assertNotSame(original, mutated)
        assertEquals(UnknownTagPolicy.Strip, original.unknownTagPolicy)
        assertEquals(UnknownTagPolicy.WarnAndStrip, mutated.unknownTagPolicy)
    }

    @Test
    fun `withUnknownTagPolicy Preserve exposes new policy and leaves original unchanged`() {
        val original = HtmlProfile.Default
        val mutated = original.withUnknownTagPolicy(UnknownTagPolicy.Preserve)
        assertNotSame(original, mutated)
        assertEquals(UnknownTagPolicy.WarnAndStrip, original.unknownTagPolicy)
        assertEquals(UnknownTagPolicy.Preserve, mutated.unknownTagPolicy)
    }

    @Test
    fun `withUnknownTagPolicy Custom exposes the same handler instance`() {
        val original = HtmlProfile.Default
        val custom = UnknownTagPolicy.Custom { _, _ -> TagDecodeResult.Drop }
        val mutated = original.withUnknownTagPolicy(custom)
        assertNotSame(original, mutated)
        assertEquals(UnknownTagPolicy.WarnAndStrip, original.unknownTagPolicy)
        assertSame(custom, mutated.unknownTagPolicy)
    }

    // Encoder lookups — by built-in type class and by custom typeId.

    @Test
    fun `withBlockEncoder registers an encoder under the built-in BlockType class`() {
        val encoder: BlockEncoder<BlockType.Paragraph> =
            BlockEncoder { _, _, _ -> HtmlEmit.Raw("<p></p>") }
        val profile = HtmlProfile.Default.withBlockEncoder(encoder)

        val paragraph = Block(
            id = BlockId.generate(),
            type = BlockType.Paragraph,
            content = BlockContent.Text(""),
        )
        assertSame(encoder, profile.blockEncoderFor(paragraph))
    }

    @Test
    fun `withBlockEncoder for Heading is shared across all heading levels`() {
        val encoder: BlockEncoder<BlockType.Heading> =
            BlockEncoder { _, _, _ -> HtmlEmit.Raw("<h1></h1>") }
        val profile = HtmlProfile.Default.withBlockEncoder(encoder)

        val h1 = Block(BlockId.generate(), BlockType.Heading(1), BlockContent.Text(""))
        val h6 = Block(BlockId.generate(), BlockType.Heading(6), BlockContent.Text(""))
        assertSame(encoder, profile.blockEncoderFor(h1))
        assertSame(encoder, profile.blockEncoderFor(h6))
    }

    @Test
    fun `block encoders for unrelated keys are not affected by a registration`() {
        val paragraphEncoder: BlockEncoder<BlockType.Paragraph> =
            BlockEncoder { _, _, _ -> HtmlEmit.Raw("<p></p>") }
        val profile = HtmlProfile.Default.withBlockEncoder(paragraphEncoder)

        val heading = Block(BlockId.generate(), BlockType.Heading(1), BlockContent.Text(""))
        assertNotSame(paragraphEncoder, profile.blockEncoderFor(heading))
    }

    @Test
    fun `withCustomBlockEncoder registers an encoder by typeId on BlockContent Custom`() {
        val encoder = BlockEncoder<BlockType> { _, _, _ -> HtmlEmit.Raw("custom") }
        val profile = HtmlProfile.Default.withCustomBlockEncoder("html.preserved", encoder)

        val customBlock = Block(
            id = BlockId.generate(),
            type = BlockType.Paragraph,
            content = BlockContent.Custom(typeId = "html.preserved", data = emptyMap()),
        )
        assertSame(encoder, profile.blockEncoderFor(customBlock))
    }

    @Test
    fun `withCustomBlockEncoder does not affect encoders for other typeIds`() {
        val encoder = BlockEncoder<BlockType> { _, _, _ -> HtmlEmit.Raw("a") }
        val profile = HtmlProfile.Default.withCustomBlockEncoder("a", encoder)

        val unrelated = Block(
            id = BlockId.generate(),
            type = BlockType.Paragraph,
            content = BlockContent.Custom(typeId = "b", data = emptyMap()),
        )
        assertNotSame(encoder, profile.blockEncoderFor(unrelated))
    }

    @Test
    fun `withSpanEncoder registers an encoder under the built-in SpanStyle class`() {
        val encoder: SpanEncoder<SpanStyle.Bold> =
            SpanEncoder { HtmlTagPair("<strong>", "</strong>") }
        val profile = HtmlProfile.Default.withSpanEncoder(encoder)
        assertSame(encoder, profile.spanEncoderFor(SpanStyle.Bold))
    }

    @Test
    fun `withSpanEncoder for Highlight is shared across all colors`() {
        val encoder: SpanEncoder<SpanStyle.Highlight> =
            SpanEncoder { HtmlTagPair("<mark>", "</mark>") }
        val profile = HtmlProfile.Default.withSpanEncoder(encoder)

        assertSame(encoder, profile.spanEncoderFor(SpanStyle.Highlight(0xFF00FF00)))
        assertSame(encoder, profile.spanEncoderFor(SpanStyle.Highlight(0xFFFFFF00)))
    }

    @Test
    fun `span encoders for unrelated keys are not affected by a registration`() {
        val encoder: SpanEncoder<SpanStyle.Bold> =
            SpanEncoder { HtmlTagPair("<strong>", "</strong>") }
        val profile = HtmlProfile.Default.withSpanEncoder(encoder)
        assertNotSame(encoder, profile.spanEncoderFor(SpanStyle.Italic))
    }

    @Test
    fun `withCustomSpanEncoder registers an encoder for SpanStyle Custom by typeId`() {
        val encoder = SpanEncoder<SpanStyle.Custom> { HtmlTagPair("<x>", "</x>") }
        val profile = HtmlProfile.Default.withCustomSpanEncoder("wrike.mention", encoder)
        assertSame(encoder, profile.spanEncoderFor(SpanStyle.Custom("wrike.mention")))
    }

    @Test
    fun `withCustomSpanEncoder does not affect Custom spans with other typeIds`() {
        val encoder = SpanEncoder<SpanStyle.Custom> { HtmlTagPair("<x>", "</x>") }
        val profile = HtmlProfile.Default.withCustomSpanEncoder("a", encoder)
        assertNull(profile.spanEncoderFor(SpanStyle.Custom("b")))
    }

    // Block group encoders — registration / removal / replacement.

    @Test
    fun `withBlockGroupEncoder registers an encoder under a name`() {
        val encoder = object : BlockGroupEncoder {
            override fun groupKey(block: Block): Any? = if (block.type is BlockType.BulletList) "bullets" else null
            override fun encodeGroup(ctx: HtmlEncodeContext, blocks: List<Block>): HtmlEmit =
                HtmlEmit.Raw("<ul></ul>")
        }
        val profile = HtmlProfile.Default
            .withoutBlockGroupEncoder("listOutline")
            .withBlockGroupEncoder("bullets", encoder)
        assertEquals(1, profile.blockGroupEncoders.size)
        assertEquals("bullets", profile.blockGroupEncoders[0].name)
        assertSame(encoder, profile.blockGroupEncoders[0].encoder)
    }

    @Test
    fun `withBlockGroupEncoder replaces a previously registered encoder under the same name`() {
        val first = object : BlockGroupEncoder {
            override fun groupKey(block: Block): Any? = null
            override fun encodeGroup(ctx: HtmlEncodeContext, blocks: List<Block>) = HtmlEmit.Raw("first")
        }
        val replacement = object : BlockGroupEncoder {
            override fun groupKey(block: Block): Any? = null
            override fun encodeGroup(ctx: HtmlEncodeContext, blocks: List<Block>) = HtmlEmit.Raw("replacement")
        }
        val profile = HtmlProfile.Default
            .withBlockGroupEncoder("listOutline", first)
            .withBlockGroupEncoder("listOutline", replacement)
        assertEquals(1, profile.blockGroupEncoders.size)
        assertSame(replacement, profile.blockGroupEncoders[0].encoder)
    }

    @Test
    fun `withoutBlockGroupEncoder removes a registered encoder`() {
        val encoder = object : BlockGroupEncoder {
            override fun groupKey(block: Block): Any? = null
            override fun encodeGroup(ctx: HtmlEncodeContext, blocks: List<Block>) = HtmlEmit.Raw("")
        }
        val profile = HtmlProfile.Default
            .withBlockGroupEncoder("listOutline", encoder)
            .withoutBlockGroupEncoder("listOutline")
        assertTrue(profile.blockGroupEncoders.isEmpty())
    }

    @Test
    fun `Default profile starts with the documented default policies`() {
        val default = HtmlProfile.Default
        assertEquals(BlockSeparator.BlockTags, default.blockSeparator)
        assertEquals(InlineRoot.Drop, default.inlineRoot)
        assertEquals(EntityDecode.Standard, default.entityDecode)
        assertEquals(UnknownTagPolicy.WarnAndStrip, default.unknownTagPolicy)
    }

    @Test
    fun `withSupportSet returns a new profile with custom support predicates and original unchanged`() {
        val original = HtmlProfile.Default
        val customSupportSet = HtmlProfileSupportSet(
            supportsBlockPredicate = { block -> block.type == BlockType.Paragraph },
            supportsSpanPredicate = { style -> style == SpanStyle.Bold },
        )

        val mutated = original.withSupportSet(customSupportSet)

        assertNotSame(original, mutated)
        assertSame(customSupportSet, mutated.supportSet)
        assertSame(HtmlProfileSupportSet.Default, original.supportSet)
        assertTrue(mutated.supportSet.supportsBlock(Block.paragraph("x")))
        assertTrue(mutated.supportSet.supportsSpan(SpanStyle.Bold))
    }
}
