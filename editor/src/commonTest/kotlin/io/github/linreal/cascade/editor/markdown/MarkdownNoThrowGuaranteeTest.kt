package io.github.linreal.cascade.editor.markdown

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownNoThrowGuaranteeTest {
    private fun assertCompletes(
        input: String,
        profile: MarkdownProfile = MarkdownProfile.Default,
        limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
    ): MarkdownDecodeResult {
        val result = MarkdownSchema.decodeWithReport(input, profile, limits)
        assertTrue(result.isSuccess || result.isAborted)
        if (result.isAborted) {
            assertNull(result.blocks)
            assertTrue(result.warnings.any { it.impact == MarkdownFidelityImpact.Fatal })
        }
        return result
    }

    @Test
    fun everyDecodeLimitAbortsWithoutPartialPayload() {
        val cases = listOf(
            "x".repeat(64) to MarkdownCodecLimits.Default.copy(maxInputChars = 16),
            ">".repeat(500) + " deep" to MarkdownCodecLimits.Default.copy(maxBlockNesting = 8),
            (1..50).joinToString("\n\n") { "p$it" } to MarkdownCodecLimits.Default.copy(maxBlocks = 5),
            (1..20).joinToString("\n") { "[ref$it]: /url$it" } to
                MarkdownCodecLimits.Default.copy(maxReferenceDefinitions = 4),
            "*a*".repeat(200) to MarkdownCodecLimits.Default.copy(maxDelimiterRuns = 10),
            "*a*".repeat(200) to MarkdownCodecLimits.Default.copy(maxSpansPerBlock = 4),
            "*a*".repeat(200) to MarkdownCodecLimits.Default.copy(maxTotalSpans = 4),
        )
        for ((input, limits) in cases) {
            val result = assertCompletes(input, limits = limits)
            assertTrue(result.isAborted, "expected abort for limits=$limits")
        }
    }

    @Test
    fun warningLimitAbortsWithoutThrowing() {
        val input = (1..20).joinToString("\n\n") { "paragraph \$m$it\$" }
        val profile = MarkdownProfile.Default.withUnsupportedSyntax(UnsupportedSyntax.WarnAndDegrade)
        val result = assertCompletes(input, profile, MarkdownCodecLimits.Default.copy(maxWarnings = 3))
        assertTrue(result.isAborted)
    }

    @Test
    fun hostileInputsNeverEscapeThePublicBoundary() {
        val hostile = listOf(
            "", "\uFEFF", "\uFEFF# bom heading", "\n\n\n", "\r\r\r", "a\r\nb\rc\nd",
            "```", "```\nunclosed", "*".repeat(20_000), "`".repeat(20_000),
            "[".repeat(20_000), ">".repeat(5000), "- ".repeat(3000) + "x",
            "<u>".repeat(4000), ("a\n\n").repeat(3000),
        )
        hostile.forEach { assertCompletes(it) }
    }
}
