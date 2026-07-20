package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Preservation recognizers and the `UnsupportedSyntax.Preserve` path. */
class MarkdownPreservationTest {

    private val degradeProfile =
        MarkdownProfile.Default.withUnsupportedSyntax(UnsupportedSyntax.WarnAndDegrade)

    private fun decode(
        input: String,
        profile: MarkdownProfile = MarkdownProfile.Default,
    ): MarkdownDecodeResult = MarkdownDecodeEngine.decode(input, profile)

    private fun MarkdownDecodeResult.requireBlocks(): List<Block> =
        blocks ?: fail("expected a successful decode, got aborted with warnings=$warnings")

    private fun Block.preservedPayload(expectedKind: String): String {
        val type = assertIs<UnknownBlockType>(this.type)
        assertEquals("md.preserved", type.typeId)
        assertEquals("""{"typeId":"md.preserved"}""", type.rawTypeJson)
        val content = assertIs<BlockContent.Custom>(this.content)
        assertEquals("md.preserved", content.typeId)
        assertEquals(expectedKind, content.data["kind"])
        return content.data["rawMarkdown"] as? String
            ?: fail("expected a rawMarkdown slice, got ${content.data}")
    }

    @Test
    fun mathBlockNeverInterruptsAParagraph() {
        // CommonMark keeps "$$" after paragraph text as paragraph content; the
        // recognizer must not claim it (conservative guard + linear lookahead).
        val result = decode("text\n$$\nx\n$$")
        val blocks = result.requireBlocks()
        assertEquals(1, blocks.size)
        val content = assertIs<BlockContent.Text>(blocks.single().content)
        // Soft breaks lower to spaces under the SoftBreak.Space default.
        assertEquals("text $$ x $$", content.text)
        assertTrue(result.warnings.filterIsInstance<MarkdownDecodeWarning.PreservedSyntax>().isEmpty())
    }

    @Test
    fun dollarPrefixedProseIsNotAMathOpener() {
        // A multi-line opener must be exactly "$$" — "$$100 spent" is prose.
        val result = decode("$$100 spent\nmore text $$")
        val blocks = result.requireBlocks()
        assertEquals(1, blocks.size)
        assertIs<BlockContent.Text>(blocks.single().content)
        assertTrue(result.warnings.filterIsInstance<MarkdownDecodeWarning.PreservedSyntax>().isEmpty())
    }

    // Pipe tables

    @Test
    fun pipeTablePreservesCharacterExactSliceUnderPreserve() {
        val input = "| a | b |\n| --- | --- |\n| 1 | 2 |"
        val result = decode(input)
        val block = result.requireBlocks().single()
        assertEquals(input, block.preservedPayload("pipeTable"))
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.PreservedSyntax>()
            .single()
        assertEquals("pipeTable", warning.kind)
        assertEquals(MarkdownFidelityImpact.OpaquePreservation, warning.impact)
    }

    @Test
    fun pipeRowWithoutDelimiterRowStaysAPlainParagraph() {
        val result = decode("| a | b |")
        val block = result.requireBlocks().single()
        assertEquals(BlockType.Paragraph, block.type)
        assertEquals("| a | b |", (block.content as BlockContent.Text).text)
        assertTrue(result.warnings.none { it is MarkdownDecodeWarning.PreservedSyntax })
    }

    @Test
    fun delimiterRowColumnMismatchIsNotATable() {
        val result = decode("| a | b |\n| --- |")
        assertTrue(result.requireBlocks().all { it.type == BlockType.Paragraph })
    }

    @Test
    fun pipeTableDegradesToParagraphTextUnderWarnAndDegrade() {
        val input = "| a | b |\n| --- | --- |\n| 1 | 2 |"
        val result = decode(input, degradeProfile)
        val blocks = result.requireBlocks()
        assertTrue(blocks.isNotEmpty())
        assertTrue(blocks.all { it.type == BlockType.Paragraph })
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.UnsupportedSyntax>()
            .single { it.construct == "pipeTable" }
        assertEquals(MarkdownFidelityImpact.DataLoss, warning.impact)
    }

    // Front matter

    @Test
    fun frontMatterAtDocumentStartPreservesUnderPreserve() {
        val input = "---\ntitle: x\n---"
        val block = decode(input).requireBlocks().single()
        assertEquals(input, block.preservedPayload("frontMatter"))
    }

    @Test
    fun frontMatterShapedLinesMidDocumentAreNotRecognized() {
        val result = decode("intro\n\n---\ntitle: x\n---")
        val blocks = result.requireBlocks()
        assertTrue(blocks.none { it.type is UnknownBlockType })
        assertTrue(result.warnings.none { it is MarkdownDecodeWarning.PreservedSyntax })
    }

    @Test
    fun documentStartingWithThematicBreakIsNotFrontMatter() {
        val result = decode("---\n\ntext")
        val blocks = result.requireBlocks()
        assertEquals(BlockType.Divider, blocks.first().type)
        assertTrue(result.warnings.none { it is MarkdownDecodeWarning.PreservedSyntax })
    }

    // Math blocks

    @Test
    fun multiLineMathBlockPreserves() {
        val input = "$$\nx^2\n$$"
        val block = decode(input).requireBlocks().single()
        assertEquals(input, block.preservedPayload("mathBlock"))
    }

    @Test
    fun singleLineMathBlockPreserves() {
        val input = "\$\$x^2\$\$"
        val block = decode(input).requireBlocks().single()
        assertEquals(input, block.preservedPayload("mathBlock"))
    }

    @Test
    fun unclosedMathIsNotRecognized() {
        val result = decode("$$\nx")
        assertTrue(result.requireBlocks().all { it.type == BlockType.Paragraph })
    }

    // Footnote definitions and block images

    @Test
    fun footnoteDefinitionPreserves() {
        val input = "[^1]: a note"
        val block = decode(input).requireBlocks().single()
        assertEquals(input, block.preservedPayload("footnoteDefinition"))
    }

    @Test
    fun blockImagePreserves() {
        val input = "![alt](img.png)"
        val block = decode(input).requireBlocks().single()
        assertEquals(input, block.preservedPayload("blockImage"))
    }

    @Test
    fun imageWithTrailingTextIsNotABlockImage() {
        // The block recognizer stands down (trailing text), so the line decodes
        // as a paragraph — whose inline image then escalates the whole leaf
        // under Preserve. The proof the *block* recognizer refused
        // is the "inlineImage" kind.
        val result = decode("![alt](img.png) tail")
        val block = result.requireBlocks().single()
        assertEquals("![alt](img.png) tail", block.preservedPayload("inlineImage"))

        // Under WarnAndDegrade the same line stays an editable paragraph.
        val degraded = decode("![alt](img.png) tail", degradeProfile)
        val paragraph = degraded.requireBlocks().single()
        assertEquals(BlockType.Paragraph, paragraph.type)
        assertEquals("alt tail", (paragraph.content as BlockContent.Text).text)
    }

    // Fenced code with info string

    @Test
    fun fenceWithInfoStringPreservesWholeFenceUnderPreserve() {
        val input = "```kotlin\ncode\n```"
        val result = decode(input)
        val block = result.requireBlocks().single()
        assertEquals(input, block.preservedPayload("fencedCode"))
        assertEquals(
            "fencedCode",
            result.warnings.filterIsInstance<MarkdownDecodeWarning.PreservedSyntax>().single().kind,
        )
    }

    @Test
    fun fenceWithInfoStringDegradesToCodeUnderWarnAndDegrade() {
        val result = decode("```kotlin\ncode\n```", degradeProfile)
        val block = result.requireBlocks().single()
        assertEquals(BlockType.Code, block.type)
        assertEquals("code", (block.content as BlockContent.Text).text)
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.DroppedAttribute>()
            .single { it.construct == "fencedCode" }
        assertEquals("infoString", warning.attr)
    }

    @Test
    fun fenceWithoutInfoStringLowersNativelyUnderPreserve() {
        val block = decode("```\ncode\n```").requireBlocks().single()
        assertEquals(BlockType.Code, block.type)
    }

    // Persistence round trip

    @Test
    fun preservedBlockRePersistsThroughDocumentSchema() {
        val input = "| a | b |\n| --- | --- |"
        val decoded = decode(input).requireBlocks()
        val json = DocumentSchema.encodeToString(decoded)
        val reloaded = DocumentSchema.decodeFromString(json)
        val block = reloaded.single()
        assertEquals(input, block.preservedPayload("pipeTable"))
    }
}
