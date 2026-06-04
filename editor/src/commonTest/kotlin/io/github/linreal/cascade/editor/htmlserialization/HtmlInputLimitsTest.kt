package io.github.linreal.cascade.editor.htmlserialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlInputLimitsTest {

    @Test
    fun oversized_input_is_rejected_with_warning_and_no_blocks() {
        val limits = HtmlDecodeLimits(maxInputChars = 10)
        val html = "<p>" + "x".repeat(50) + "</p>" // length > 10
        val result = HtmlSchema.decodeWithReport(html, HtmlProfile.Default, limits)
        assertTrue(result.blocks.isEmpty())
        val warning = result.warnings.filterIsInstance<HtmlDecodeWarning.InputLimitExceeded>().single()
        assertEquals(10, warning.limit)
        assertEquals(html.length, warning.actual)
    }

    @Test
    fun input_within_limit_decodes_normally() {
        val limits = HtmlDecodeLimits(maxInputChars = 10_000)
        val result = HtmlSchema.decodeWithReport("<p>hello</p>", HtmlProfile.Default, limits)
        assertEquals(1, result.blocks.size)
        assertTrue(result.warnings.none { it is HtmlDecodeWarning.InputLimitExceeded })
    }
}
