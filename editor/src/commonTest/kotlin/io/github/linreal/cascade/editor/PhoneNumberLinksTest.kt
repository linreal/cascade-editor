package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.DetectedPhoneLink
import io.github.linreal.cascade.editor.richtext.automaticPhoneLinkSpans
import io.github.linreal.cascade.editor.richtext.phoneNumberToTelTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneNumberLinksTest {

    @Test
    fun `formatted international number becomes tel target`() {
        assertEquals("tel:+14155552671", phoneNumberToTelTarget("+1 (415) 555-2671"))
    }

    @Test
    fun `unsupported dialing syntax is rejected`() {
        assertNull(phoneNumberToTelTarget("415-555-2671 x123"))
        assertNull(phoneNumberToTelTarget("*123#"))
        assertNull(phoneNumberToTelTarget("   "))
    }

    @Test
    fun `detections become transient link spans in range order`() {
        val spans = automaticPhoneLinkSpans(
            text = "Call 222 then 111",
            explicitSpans = emptyList(),
            detections = listOf(
                DetectedPhoneLink(14, 17, "tel:111"),
                DetectedPhoneLink(5, 8, "tel:222"),
            ),
        )

        assertEquals(
            listOf(
                TextSpan(5, 8, SpanStyle.Link("tel:222")),
                TextSpan(14, 17, SpanStyle.Link("tel:111")),
            ),
            spans,
        )
    }

    @Test
    fun `explicit links win while other formatting may overlap`() {
        val detections = listOf(DetectedPhoneLink(5, 17, "tel:4155552671"))

        assertEquals(
            emptyList(),
            automaticPhoneLinkSpans(
                text = "Call 415-555-2671",
                explicitSpans = listOf(TextSpan(5, 17, SpanStyle.Link("https://example.com"))),
                detections = detections,
            ),
        )
        assertEquals(
            listOf(TextSpan(5, 17, SpanStyle.Link("tel:4155552671"))),
            automaticPhoneLinkSpans(
                text = "Call 415-555-2671",
                explicitSpans = listOf(TextSpan(5, 17, SpanStyle.Bold)),
                detections = detections,
            ),
        )
    }

    @Test
    fun `invalid targets ranges and overlapping detections are dropped`() {
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Link("tel:111"))),
            automaticPhoneLinkSpans(
                text = "11122",
                explicitSpans = emptyList(),
                detections = listOf(
                    DetectedPhoneLink(-1, 2, "tel:0"),
                    DetectedPhoneLink(0, 3, "tel:111"),
                    DetectedPhoneLink(2, 5, "tel:122"),
                    DetectedPhoneLink(3, 8, "tel:999"),
                    DetectedPhoneLink(3, 5, "https://example.com"),
                ),
            ),
        )
    }
}
