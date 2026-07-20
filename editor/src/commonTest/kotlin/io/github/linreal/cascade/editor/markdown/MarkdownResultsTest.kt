package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCascadeMarkdownApi::class)
class MarkdownResultsTest {

    private val anyRange = MarkdownSourceRange(0, 1)

    @Test
    fun `success with zero blocks has non-null empty payload`() {
        val result = MarkdownDecodeResult.success(blocks = emptyList())

        assertEquals(MarkdownCodecStatus.Success, result.status)
        assertTrue(result.isSuccess)
        val blocks = assertNotNull(result.blocks)
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `success with content carries payload and warnings`() {
        val result = MarkdownDecodeResult.success(
            blocks = listOf(Block.paragraph("hello")),
            warnings = listOf(
                MarkdownDecodeWarning.PreservedSyntax(kind = "pipeTable", range = anyRange),
            ),
        )

        assertEquals(1, assertNotNull(result.blocks).size)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `aborted decode has null payload and requires a fatal warning`() {
        val result = MarkdownDecodeResult.aborted(
            warnings = listOf(
                MarkdownDecodeWarning.InputLimitExceeded(limit = 10, actual = 20),
            ),
        )

        assertEquals(MarkdownCodecStatus.Aborted, result.status)
        assertTrue(result.isAborted)
        assertNull(result.blocks)
    }

    @Test
    fun `aborted decode without a fatal warning is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            MarkdownDecodeResult.aborted(
                warnings = listOf(
                    MarkdownDecodeWarning.PreservedSyntax(kind = "mathBlock", range = anyRange),
                ),
            )
        }
    }

    @Test
    fun `aborted encode has null payload and requires a fatal warning`() {
        val result = MarkdownEncodeResult.aborted(
            warnings = listOf(MarkdownEncodeWarning.OutputLimitExceeded(limit = 100)),
        )

        assertEquals(MarkdownCodecStatus.Aborted, result.status)
        assertNull(result.markdown)

        assertFailsWith<IllegalArgumentException> {
            MarkdownEncodeResult.aborted(
                warnings = listOf(
                    MarkdownEncodeWarning.DroppedAttribute(
                        attr = "indentationLevel",
                        reason = "not representable",
                        blockId = null,
                    ),
                ),
            )
        }
    }

    @Test
    fun `success with a fatal warning is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            MarkdownDecodeResult.success(
                blocks = emptyList(),
                warnings = listOf(MarkdownDecodeWarning.InputLimitExceeded(limit = 1, actual = 2)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MarkdownEncodeResult.success(
                markdown = "x",
                warnings = listOf(MarkdownEncodeWarning.OutputLimitExceeded(limit = 1)),
            )
        }
    }

    @Test
    fun `empty string is a valid successful encode payload`() {
        val result = MarkdownEncodeResult.success(markdown = "")

        assertEquals(MarkdownCodecStatus.Success, result.status)
        assertEquals("", result.markdown)
    }

    @Test
    fun `decode warning members expose their documented impact`() {
        assertEquals(
            MarkdownFidelityImpact.DataLoss,
            MarkdownDecodeWarning.UnsupportedSyntax("table", "degraded", anyRange).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.OpaquePreservation,
            MarkdownDecodeWarning.PreservedSyntax("frontMatter", anyRange).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.Informational,
            MarkdownDecodeWarning.HtmlBridged(anyRange).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.DataLoss,
            MarkdownDecodeWarning.HtmlStripped("span", anyRange).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.DataLoss,
            MarkdownDecodeWarning.DroppedAttribute("list", "start", "non-1 start", anyRange).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.Fatal,
            MarkdownDecodeWarning.InputLimitExceeded(limit = 1, actual = 2).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.Fatal,
            MarkdownDecodeWarning.LimitExceeded(MarkdownCodecLimitKind.Blocks, 1, anyRange).impact,
        )
    }

    @Test
    fun `encode warning members expose their documented impact`() {
        assertEquals(
            MarkdownFidelityImpact.DataLoss,
            MarkdownEncodeWarning.AmbiguousEmphasis(blockId = null, textRange = null).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.DataLoss,
            MarkdownEncodeWarning.DroppedSpanOverlap(null, null, "inside code span").impact,
        )
        assertEquals(
            MarkdownFidelityImpact.DataLoss,
            MarkdownEncodeWarning.DroppedAttribute("indentationLevel", "no encoding", null).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.DataLoss,
            MarkdownEncodeWarning.UnsupportedBlock("custom", "no encoder", null).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.DataLoss,
            MarkdownEncodeWarning.UnsupportedSpan("spoiler", "no encoder", null, null).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.DataLoss,
            MarkdownEncodeWarning.EncoderException("table", "boom", null).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.Fatal,
            MarkdownEncodeWarning.OutputLimitExceeded(limit = 1).impact,
        )
        assertEquals(
            MarkdownFidelityImpact.Fatal,
            MarkdownEncodeWarning.LimitExceeded(MarkdownCodecLimitKind.Warnings, 1).impact,
        )
    }

    @Test
    fun `decode warnings carry source ranges and encode warnings carry block ids`() {
        val decodeWarning = MarkdownDecodeWarning.PreservedSyntax("pipeTable", MarkdownSourceRange(4, 20))
        assertEquals(MarkdownSourceRange(4, 20), decodeWarning.range)

        val block = Block.paragraph("x")
        val encodeWarning = MarkdownEncodeWarning.DroppedSpanOverlap(
            blockId = block.id,
            textRange = MarkdownTextRange(2, 5),
            reason = "span wholly inside code content",
        )
        assertEquals(block.id, encodeWarning.blockId)
        assertEquals(MarkdownTextRange(2, 5), encodeWarning.textRange)
    }

    @Test
    fun `default limits match the decided values`() {
        val limits = MarkdownCodecLimits.Default

        assertEquals(4_000_000, limits.maxInputChars)
        assertEquals(32, limits.maxBlockNesting)
        assertEquals(50_000, limits.maxBlocks)
        assertEquals(1_000, limits.maxSpansPerBlock)
        assertEquals(50_000, limits.maxTotalSpans)
        assertEquals(5_000, limits.maxReferenceDefinitions)
        assertEquals(10_000, limits.maxDelimiterRuns)
        assertEquals(1_000, limits.maxWarnings)
        assertEquals(16_000_000, limits.maxOutputChars)
    }

    @Test
    fun `non-positive limits are rejected`() {
        assertFailsWith<IllegalArgumentException> { MarkdownCodecLimits(maxInputChars = 0) }
        assertFailsWith<IllegalArgumentException> { MarkdownCodecLimits(maxWarnings = -1) }
    }
}
