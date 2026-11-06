package io.github.linreal.cascade.editor.richtext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneNumberLinksIosTest {

    @Test
    fun `detector returns utf16 range and tel target`() {
        val text = "Call 😀 +1 (415) 555-2671 today"

        val match = detectPlatformPhoneLinks(text).single()

        assertEquals(text.indexOf("+1"), match.start)
        assertEquals("+1 (415) 555-2671", text.substring(match.start, match.end))
        assertEquals("tel:+14155552671", match.target)
    }

    @Test
    fun `detector returns multiple non-overlapping matches`() {
        val matches = detectPlatformPhoneLinks(
            "US +1 (415) 555-2671, UK +44 20 7946 0958",
        )

        assertEquals(2, matches.size)
        assertTrue(matches.zipWithNext().all { (first, second) -> first.end <= second.start })
    }
}
