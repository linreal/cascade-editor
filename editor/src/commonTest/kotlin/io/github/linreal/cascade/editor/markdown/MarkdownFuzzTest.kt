package io.github.linreal.cascade.editor.markdown

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fuzz suite: random hostile input across every reference profile must
 * terminate and stay within the codec's warning/output limits.
 */
class MarkdownFuzzTest {

    private val limits = MarkdownCodecLimits.Default

    private val profiles: List<MarkdownProfile> = listOf(
        MarkdownProfile.Default,
        MarkdownReferenceProfiles.StrictGfmFieldProfile,
        MarkdownReferenceProfiles.StrictHardBreakFieldProfile,
    )

    private fun assertBounded(input: String, profile: MarkdownProfile) {
        val decode = MarkdownSchema.decodeWithReport(input, profile, limits)
        assertTrue(decode.warnings.size <= limits.maxWarnings + 1, "warnings bounded")
        if (decode.isSuccess) {
            val encode = MarkdownSchema.encodeWithReport(decode.blocks!!, profile, limits)
            if (encode.isSuccess) {
                assertTrue(encode.markdown!!.length <= limits.maxOutputChars, "output bounded")
            }
        }
    }

    @Test
    fun `random hostile input terminates bounded across every profile`() {
        val alphabet = "#*_~`[]()<>|$\\!-. \n\t\rabcXYZ=+".toCharArray()
        val random = Random(0xF0FA)
        repeat(500) {
            val length = random.nextInt(0, 600)
            val input = buildString {
                repeat(length) { append(alphabet[random.nextInt(alphabet.size)]) }
            }
            for (profile in profiles) assertBounded(input, profile)
        }
    }

    @Test
    fun `multi-kilobyte and deeply nested inputs terminate bounded`() {
        val inputs = listOf(
            "text ".repeat(4000),
            "> ".repeat(2000) + "deep quote",
            "          ".repeat(500) + "deep indent",
            "- ".repeat(3000) + "x",
            "*".repeat(20_000),
            "`".repeat(20_000),
            "[".repeat(20_000),
            "<u>".repeat(4000),
            ("a\n\n").repeat(3000),
        )
        for (input in inputs) {
            for (profile in profiles) assertBounded(input, profile)
        }
    }

}
