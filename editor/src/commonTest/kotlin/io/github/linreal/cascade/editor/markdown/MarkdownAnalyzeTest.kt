package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownAnalyzeTest {

    @Test
    fun `fully supported gfm is native with no data loss`() {
        val report = MarkdownSchema.analyze("# Title\n\nBody **bold** text\n", MarkdownProfile.Default)
        assertEquals(MarkdownEditModeRecommendation.Native, report.recommendedMode)
        assertTrue(report.nativeEditingSafe)
        assertTrue(report.dataLossWarnings.isEmpty())
        assertEquals(0, report.preservedBlockCount)
    }

    @Test
    fun `pipe table under preserve is raw fallback`() {
        val report = MarkdownSchema.analyze("| a | b |\n| - | - |\n", MarkdownProfile.Default)
        assertEquals(1, report.preservedBlockCount)
        assertFalse(report.nativeEditingSafe)
        assertEquals(MarkdownEditModeRecommendation.RawFallback, report.recommendedMode)
    }

    @Test
    fun `decode abort yields null encode result and raw fallback`() {
        val limits = MarkdownCodecLimits(maxInputChars = 3)
        val report = MarkdownSchema.analyze("way too long", MarkdownProfile.Default, limits)
        assertTrue(report.decodeResult.isAborted)
        assertNull(report.encodeResult)
        assertNull(report.wouldRewriteSource)
        assertEquals(MarkdownEditModeRecommendation.RawFallback, report.recommendedMode)
    }

    @Test
    fun `missing final newline is native but would rewrite source`() {
        val report = MarkdownSchema.analyze("# a", MarkdownProfile.Default)
        assertEquals(MarkdownEditModeRecommendation.Native, report.recommendedMode)
        assertEquals(true, report.wouldRewriteSource)
    }

    @Test
    fun `byte-identical canonical input does not rewrite source`() {
        val report = MarkdownSchema.analyze("# a\n", MarkdownProfile.Default)
        assertEquals(MarkdownEditModeRecommendation.Native, report.recommendedMode)
        assertEquals(false, report.wouldRewriteSource)
    }

    @Test
    fun `bom and crlf drive wouldRewriteSource but stay native`() {
        val bom = MarkdownSchema.analyze("\uFEFF# a\n", MarkdownProfile.Default)
        assertEquals(MarkdownEditModeRecommendation.Native, bom.recommendedMode)
        assertEquals(true, bom.wouldRewriteSource)

        val crlf = MarkdownSchema.analyze("# a\r\n", MarkdownProfile.Default)
        assertEquals(MarkdownEditModeRecommendation.Native, crlf.recommendedMode)
        assertEquals(true, crlf.wouldRewriteSource)
    }

    @Test
    fun `strip policy still records data loss for analyze even when suppressed`() {
        val profile = MarkdownProfile.Default.withHtmlInMarkdown(HtmlInMarkdown.Strip)
        val report = MarkdownSchema.analyze("a <span>x</span> b", profile)
        assertEquals(MarkdownEditModeRecommendation.RawFallback, report.recommendedMode)
        assertTrue(report.dataLossWarnings.isNotEmpty())
    }

    @Test
    fun `custom support set branch is honored by analyze`() {
        val claimAll = MarkdownProfileSupportSet(
            supportsBlockPredicate = { true },
            supportsSpanPredicate = { true },
            supportsDocumentPredicate = { true },
        )
        val profile = MarkdownProfile.Default.withSupportSet(claimAll)
        val report = MarkdownSchema.analyze("# a\n", profile)
        assertEquals(MarkdownEditModeRecommendation.Native, report.recommendedMode)
    }

    @Test
    fun `custom support set cannot widen a failed canonical round trip`() {
        val claimAll = MarkdownProfileSupportSet(
            supportsBlockPredicate = { true },
            supportsSpanPredicate = { true },
            supportsDocumentPredicate = { true },
        )
        val profile = MarkdownProfile.Default
            .withMarkdownBlockEncoder<BlockType.Paragraph> { _, _, _ ->
                MarkdownEmit.Raw("different")
            }
            .withSupportSet(claimAll)

        val report = MarkdownSchema.analyze("original\n", profile)

        assertEquals(MarkdownEditModeRecommendation.RawFallback, report.recommendedMode)
    }

    @Test
    fun `canonicalization alone does not force raw fallback`() {
        // Non-canonical bullet spelling `*` canonicalizes to `-`; still Native.
        val report = MarkdownSchema.analyze("* item\n", MarkdownProfile.Default)
        assertEquals(MarkdownEditModeRecommendation.Native, report.recommendedMode)
        assertEquals(true, report.wouldRewriteSource)
    }
}
