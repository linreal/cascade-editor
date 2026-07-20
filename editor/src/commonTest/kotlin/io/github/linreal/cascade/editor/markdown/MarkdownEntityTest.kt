package io.github.linreal.cascade.editor.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Entity decoding infrastructure. */
class MarkdownEntityTest {

    private fun decode(
        text: String,
        policy: EntityDecode = EntityDecode.Standard,
    ): Pair<String, List<Triple<String, Int, Int>>> {
        val unknown = mutableListOf<Triple<String, Int, Int>>()
        val decoded = MarkdownEntities.decode(text, policy) { name, start, endExclusive ->
            unknown += Triple(name, start, endExclusive)
        }
        return decoded to unknown
    }

    // Named references

    @Test
    fun knownNamedReferencesDecode() {
        assertEquals("&", decode("&amp;").first)
        assertEquals("a < b", decode("a &lt; b").first)
        assertEquals("\u00A0", decode("&nbsp;").first)
        assertEquals("—", decode("&mdash;").first)
        assertEquals("–", decode("&ndash;").first)
        assertEquals("…", decode("&hellip;").first)
        assertEquals("©", decode("&copy;").first)
        assertEquals("™", decode("&trade;").first)
        assertEquals("→", decode("&rarr;").first)
        assertEquals("←", decode("&larr;").first)
        assertEquals("×", decode("&times;").first)
        assertEquals("°", decode("&deg;").first)
    }

    @Test
    fun unknownNameStaysLiteralAndReportsExactRange() {
        val (text, unknown) = decode("a &nosuch; b")
        assertEquals("a &nosuch; b", text)
        assertEquals(listOf(Triple("nosuch", 2, 10)), unknown)
    }

    @Test
    fun namedReferencesAreCaseSensitive() {
        // "Prime" and "prime" are distinct names; "AMP" is not in the subset.
        assertEquals("′", decode("&prime;").first)
        assertEquals("″", decode("&Prime;").first)
        val (text, unknown) = decode("&AMP;")
        assertEquals("&AMP;", text)
        assertEquals("AMP", unknown.single().first)
    }

    // Numeric references

    @Test
    fun decimalAndHexReferencesDecodeFully() {
        assertEquals("#", decode("&#35;").first)
        assertEquals("A", decode("&#x41;").first)
        assertEquals("A", decode("&#X41;").first)
        assertEquals("😀", decode("&#x1F600;").first)
    }

    @Test
    fun invalidCodePointsBecomeReplacementCharacter() {
        assertEquals("�", decode("&#0;").first)
        assertEquals("�", decode("&#xD800;").first)
        assertEquals("�", decode("&#1114112;").first)
    }

    @Test
    fun malformedNumericsStayLiteralWithoutWarnings() {
        for (input in listOf("&#;", "&#x;", "&#xGG;", "&#12345678;", "&#x1234567;", "&#12")) {
            val (text, unknown) = decode(input)
            assertEquals(input, text)
            assertTrue(unknown.isEmpty(), "no unknown-name report expected for $input")
        }
    }

    @Test
    fun nonEntityAmpersandsStayLiteralWithoutWarnings() {
        for (input in listOf("&", "a & b", "&;", "& amp;", "&am p;", "&<tag>")) {
            val (text, unknown) = decode(input)
            assertEquals(input, text)
            assertTrue(unknown.isEmpty(), "no unknown-name report expected for $input")
        }
    }

    // Policy

    @Test
    fun entityDecodeNoneDisablesDecodingAndWarnings() {
        val (text, unknown) = decode("&amp; &nosuch;", EntityDecode.None)
        assertEquals("&amp; &nosuch;", text)
        assertTrue(unknown.isEmpty())
    }

    // Low-level matcher

    @Test
    fun matchAtReportsConsumedLengthAndName() {
        val match = assertNotNull(MarkdownEntities.matchAt("x&amp;y", 1))
        assertEquals(5, match.length)
        assertEquals("&", match.replacement)
        assertEquals("amp", match.name)

        val unknown = assertNotNull(MarkdownEntities.matchAt("&nosuch;", 0))
        assertEquals(8, unknown.length)
        assertNull(unknown.replacement)
        assertEquals("nosuch", unknown.name)

        assertNull(MarkdownEntities.matchAt("&amp;", 1))
        assertNull(MarkdownEntities.matchAt("& amp;", 0))
    }

    @Test
    fun mixedTextDecodesInOnePass() {
        val (text, unknown) = decode("&copy; 2026 &mdash; a&b &nosuch; &#x41;")
        assertEquals("© 2026 — a&b &nosuch; A", text)
        assertEquals(1, unknown.size)
        assertEquals("nosuch", unknown.single().first)
    }

    // Warning member contract

    @Test
    fun unsupportedEntityWarningIsInformational() {
        val warning = MarkdownDecodeWarning.UnsupportedEntity(
            name = "nosuch",
            range = MarkdownSourceRange(2, 10),
        )
        assertEquals(MarkdownFidelityImpact.Informational, warning.impact)
        assertEquals(MarkdownSourceRange(2, 10), warning.range)
    }
}
