package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Corpus suite for the reference field profiles. Corpus
 * documents are synthetic but plausible (no copied private content); each
 * assertion pins no-throw, an expected analyze recommendation, and — for the
 * hard-break family — the #339 blank-line and #348 numbered-continuation bug
 * classes.
 */
class MarkdownReferenceCorpusTest {

    @Test
    fun `strict gfm plain note is native`() {
        val doc = "# Standup\n\nShipped **auth** and reviewed the [PR](../pr/42.md).\n\n- done\n- next\n"
        val report = MarkdownSchema.analyze(doc, MarkdownReferenceProfiles.StrictGfmFieldProfile)
        assertEquals(MarkdownEditModeRecommendation.Native, report.recommendedMode)
    }

    @Test
    fun `strict gfm table is raw fallback not native`() {
        val doc = "Costs:\n\n| item | usd |\n| - | - |\n| a | 1 |\n"
        val report = MarkdownSchema.analyze(doc, MarkdownReferenceProfiles.StrictGfmFieldProfile)
        assertEquals(MarkdownEditModeRecommendation.RawFallback, report.recommendedMode)
        assertTrue(report.preservedBlockCount >= 1)
    }

    @Test
    fun `strict profile with raw html analyzes as raw fallback not silent strip`() {
        val doc = "Note with <span style=\"x\">inline html</span> inside.\n"
        val report = MarkdownSchema.analyze(doc, MarkdownReferenceProfiles.StrictGfmFieldProfile)
        assertEquals(MarkdownEditModeRecommendation.RawFallback, report.recommendedMode)
        assertTrue(report.dataLossWarnings.isNotEmpty())
    }

    @Test
    fun `hardbreak memo preserves blank line runs issue 339`() {
        val profile = MarkdownReferenceProfiles.StrictHardBreakFieldProfile
        val doc = "first thought\n\n\nthird thought after two blanks\n"
        val blocks = MarkdownSchema.decode(doc, profile)!!
        val texts = blocks.map { (it.content as BlockContent.Text).text }
        assertEquals(listOf("first thought", "", "", "third thought after two blanks"), texts)
        // Simulated edit round-trip: the blank-line run survives re-encode.
        val reencoded = MarkdownSchema.encode(blocks, profile)!!
        val redecoded = MarkdownSchema.decode(reencoded, profile)!!
        assertEquals(texts, redecoded.map { (it.content as BlockContent.Text).text })
    }

    @Test
    fun `hardbreak numbered continuation is renumbered issue 348`() {
        val profile = MarkdownReferenceProfiles.StrictHardBreakFieldProfile
        // Chat-style input where every item is typed as "1." — the bug class is
        // that the continuation must be renumbered, not left as 1/1/1.
        val doc = "1. first\n1. second\n1. third\n"
        val blocks = MarkdownSchema.decode(doc, profile)!!
        val numbered = blocks.filter { it.type is BlockType.NumberedList }
        assertEquals(3, numbered.size)
        assertEquals(listOf(1, 2, 3), numbered.map { (it.type as BlockType.NumberedList).number })
        val reencoded = MarkdownSchema.encode(blocks, profile)!!
        assertTrue(reencoded.contains("1. first"), reencoded)
        assertTrue(reencoded.contains("2. second"), reencoded)
        assertTrue(reencoded.contains("3. third"), reencoded)
    }

}
