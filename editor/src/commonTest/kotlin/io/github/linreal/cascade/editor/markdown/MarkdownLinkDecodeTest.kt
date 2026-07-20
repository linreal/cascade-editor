package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Inline links, autolinks, and reference resolution. */
class MarkdownLinkDecodeTest {

    private val degradeProfile =
        MarkdownProfile.Default.withUnsupportedSyntax(UnsupportedSyntax.WarnAndDegrade)

    private fun decode(
        input: String,
        profile: MarkdownProfile = MarkdownProfile.Default,
    ): MarkdownDecodeResult = MarkdownDecodeEngine.decode(input, profile)

    private fun MarkdownDecodeResult.firstText(): BlockContent.Text {
        val block = blocks?.firstOrNull()
            ?: fail("expected at least one block, warnings=$warnings")
        return assertIs<BlockContent.Text>(block.content)
    }

    // Inline links

    @Test
    fun relativeTargetSurvivesExactly() {
        val content = decode("[a](../guide.md)").firstText()
        assertEquals("a", content.text)
        assertEquals(listOf(TextSpan(0, 1, SpanStyle.Link("../guide.md"))), content.spans)
    }

    @Test
    fun angleDestinationAllowsSpaces() {
        val content = decode("[a](</url with space>)").firstText()
        assertEquals(listOf(TextSpan(0, 1, SpanStyle.Link("/url with space"))), content.spans)
    }

    @Test
    fun destinationBackslashEscapesAreUnescaped() {
        val content = decode("""[a](/url\(1\))""").firstText()
        assertEquals(listOf(TextSpan(0, 1, SpanStyle.Link("/url(1)"))), content.spans)
    }

    @Test
    fun nestedStylesInsideLinkText() {
        val content = decode("[**bold** text](u)").firstText()
        assertEquals("bold text", content.text)
        assertEquals(
            setOf(
                TextSpan(0, 4, SpanStyle.Bold),
                TextSpan(0, 9, SpanStyle.Link("u")),
            ),
            content.spans.toSet(),
        )
    }

    @Test
    fun specialSchemeTargetsSurviveExactly() {
        val targets = listOf("mailto:user@example.com", "tel:+123", "note://custom", "#heading")
        for (target in targets) {
            val content = decode("[x]($target)").firstText()
            assertEquals(
                listOf(TextSpan(0, 1, SpanStyle.Link(target))),
                content.spans,
                "target $target",
            )
        }
    }

    @Test
    fun emphasisCannotSpanALinkBoundary() {
        val content = decode("*a [b*](u)").firstText()
        // The * inside the link text belongs to the nested parse; the outer
        // opener finds no closer, so both stay literal.
        assertEquals("*a b*", content.text)
        assertEquals(listOf(TextSpan(3, 5, SpanStyle.Link("u"))), content.spans)
    }

    // Autolinks

    @Test
    fun uriAutolinkTextEqualsTarget() {
        val content = decode("<https://x.example>").firstText()
        assertEquals("https://x.example", content.text)
        assertEquals(
            listOf(TextSpan(0, 17, SpanStyle.Link("https://x.example"))),
            content.spans,
        )
    }

    @Test
    fun emailAutolinkGetsMailtoTarget() {
        val content = decode("<user@example.com>").firstText()
        assertEquals("user@example.com", content.text)
        assertEquals(
            listOf(TextSpan(0, 16, SpanStyle.Link("mailto:user@example.com"))),
            content.spans,
        )
    }

    @Test
    fun angleTextWithoutSchemeStaysLiteral() {
        val content = decode("<not a link>").firstText()
        assertEquals("<not a link>", content.text)
        assertTrue(content.spans.isEmpty())
    }

    // Reference links

    @Test
    fun forwardReferenceResolves() {
        val content = decode("[text][ref]\n\n[ref]: /url").firstText()
        assertEquals("text", content.text)
        assertEquals(listOf(TextSpan(0, 4, SpanStyle.Link("/url"))), content.spans)
    }

    @Test
    fun allThreeReferenceFormsPlusAutolinkResolveInOneFixture() {
        val input = "[full][ref] [ref][] [ref] <https://e.example>\n\n[ref]: /url"
        val content = decode(input).firstText()
        assertEquals("full ref ref https://e.example", content.text)
        assertEquals(
            setOf(
                TextSpan(0, 4, SpanStyle.Link("/url")),
                TextSpan(5, 8, SpanStyle.Link("/url")),
                TextSpan(9, 12, SpanStyle.Link("/url")),
                TextSpan(13, 30, SpanStyle.Link("https://e.example")),
            ),
            content.spans.toSet(),
        )
    }

    @Test
    fun unresolvedReferenceStaysLiteralWithoutWarnings() {
        val result = decode("[text][missing]")
        val content = result.firstText()
        assertEquals("[text][missing]", content.text)
        assertTrue(content.spans.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun labelNormalizationAppliesOnLookup() {
        val content = decode("[x][  ThE   LaBeL ]\n\n[the label]: /url").firstText()
        assertEquals(listOf(TextSpan(0, 1, SpanStyle.Link("/url"))), content.spans)
    }

    @Test
    fun closingBracketInsideCodeSpanDoesNotTerminateLinkText() {
        // The bracket-match table treats code spans as opaque (CommonMark
        // precedence): the `]` inside `` `…` `` is code content.
        val content = decode("[`]`](u)").firstText()
        assertEquals("]", content.text)
        assertEquals(
            setOf(
                TextSpan(0, 1, SpanStyle.InlineCode),
                TextSpan(0, 1, SpanStyle.Link("u")),
            ),
            content.spans.toSet(),
        )
    }

    // Nested links

    @Test
    fun innerLinkWinsAndOuterBracketsStayLiteral() {
        val content = decode("[a [b](u2)](u1)").firstText()
        assertEquals("[a b](u1)", content.text)
        assertEquals(listOf(TextSpan(3, 4, SpanStyle.Link("u2"))), content.spans)
    }

    // Titles (WarnAndDegrade semantics here; Preserve escalates)

    @Test
    fun inlineTitleDegradesToLinkWithoutTitle() {
        val result = decode("[a](u \"title\")", degradeProfile)
        val content = result.firstText()
        assertEquals(listOf(TextSpan(0, 1, SpanStyle.Link("u"))), content.spans)
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.DroppedAttribute>()
            .single { it.construct == "link" }
        assertEquals("title", warning.attr)
        assertEquals(MarkdownFidelityImpact.DataLoss, warning.impact)
    }

    // Merge identity

    @Test
    fun adjacentSameUrlLinksMayMerge() {
        val content = decode("[a](u)[b](u)").firstText()
        assertEquals("ab", content.text)
        // URL-exact merge identity: adjacent same-URL links normalize to one.
        assertEquals(listOf(TextSpan(0, 2, SpanStyle.Link("u"))), content.spans)
    }

    @Test
    fun adjacentDifferentUrlLinksStayDistinct() {
        val content = decode("[a](u1)[b](u2)").firstText()
        assertEquals(
            listOf(
                TextSpan(0, 1, SpanStyle.Link("u1")),
                TextSpan(1, 2, SpanStyle.Link("u2")),
            ),
            content.spans,
        )
    }

    @Test
    fun emptyDestinationStaysLiteral() {
        // SpanStyle.Link requires a nonblank target; `[a]()` cannot lower to a
        // link and stays literal text (documented deviation).
        val content = decode("[a]()").firstText()
        assertEquals("[a]()", content.text)
        assertTrue(content.spans.isEmpty())
    }
}
