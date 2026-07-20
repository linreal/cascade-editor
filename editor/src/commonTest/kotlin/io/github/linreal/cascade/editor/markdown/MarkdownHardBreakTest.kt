package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NewlineSemantics.HardBreak mode. Every source newline in a text
 * leaf is a literal line break, blank-line runs become explicit empty
 * paragraphs, and `setextHeading` / `indentedCode` are dropped.
 *
 * The canonical encode terminates with one final newline (like CommonMark), so
 * the string assertions below carry that trailing `\n`; the load-bearing
 * contract is `decode(encode(x)) == x`, asserted throughout.
 */
class MarkdownHardBreakTest {

    // withNewlineSemantics rebinds the default support-set claim to the new
    // profile, so this profile's own support set already allows empty text
    // blocks and verifies encodes under HardBreak.
    private val profile = MarkdownProfile.Default.withNewlineSemantics(NewlineSemantics.HardBreak)

    private fun texts(markdown: String): List<Pair<BlockType, String>> =
        MarkdownSchema.decode(markdown, profile)!!.map {
            it.type to ((it.content as? BlockContent.Text)?.text ?: "")
        }

    @Test
    fun `single newline is one paragraph with literal newline`() {
        val blocks = MarkdownSchema.decode("a\nb", profile)!!
        assertEquals(1, blocks.size)
        assertEquals("a\nb", (blocks.single().content as BlockContent.Text).text)
        assertEquals("a\nb\n", MarkdownSchema.encode(blocks, profile))
    }

    @Test
    fun `two blank lines become two empty paragraphs`() {
        val blocks = MarkdownSchema.decode("a\n\n\nb", profile)!!
        assertEquals(
            listOf("a", "", "", "b"),
            blocks.map { (it.content as BlockContent.Text).text },
        )
        // N=2 empties → N+1 = 3 intervening newlines; one final newline appended.
        assertEquals("a\n\n\nb\n", MarkdownSchema.encode(blocks, profile))
    }

    @Test
    fun `setext underline is not recognized in hard-break mode`() {
        val decoded = texts("text\n---")
        assertEquals(BlockType.Paragraph, decoded[0].first)
        assertEquals("text", decoded[0].second)
        assertEquals(BlockType.Divider, decoded[1].first)
    }

    @Test
    fun `four-space indent is paragraph content not code`() {
        val decoded = texts("    code")
        assertEquals(1, decoded.size)
        assertEquals(BlockType.Paragraph, decoded.single().first)
    }

    @Test
    fun `trailing blank line survives round-trip`() {
        val blocks = MarkdownSchema.decode("a\n\n", profile)!!
        assertEquals(listOf("a", ""), blocks.map { (it.content as BlockContent.Text).text })
        val reencoded = MarkdownSchema.encode(blocks, profile)!!
        val redecoded = MarkdownSchema.decode(reencoded, profile)!!
        assertEquals(
            blocks.map { (it.content as BlockContent.Text).text },
            redecoded.map { (it.content as BlockContent.Text).text },
        )
    }

    @Test
    fun `support set claims empty text blocks in hard-break mode`() {
        assertTrue(profile.supportSet.supportsDocument(MarkdownSchema.decode("a\n\nb", profile)!!))
    }

    @Test
    fun `property round-trip of generated hard-break documents`() {
        val generator = MarkdownSupportSetBlockGenerator(
            profile = profile,
            supportSet = profile.supportSet,
            seed = 0x11A2DBEEF,
            hardBreak = true,
        )
        repeat(300) {
            val blocks = generator.nextDocument()
            val encoded = MarkdownSchema.encode(blocks, profile) ?: error("encode aborted: $blocks")
            val decoded = MarkdownSchema.decode(encoded, profile) ?: error("decode aborted:\n$encoded")
            try {
                assertMarkdownSemanticallyEquals(blocks, decoded)
            } catch (failure: AssertionError) {
                throw AssertionError("hard-break counterexample:\n$encoded", failure)
            }
        }
    }
}
