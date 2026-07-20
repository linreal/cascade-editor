package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import kotlin.test.Test
import kotlin.test.assertSame

class MarkdownProfileTest {
    @Test
    fun defaultPoliciesMatchContract() {
        val profile = MarkdownProfile.Default
        assertSame(UnsupportedSyntax.Preserve, profile.unsupportedSyntax)
        assertSame(NewlineSemantics.CommonMark, profile.newlineSemantics)
        assertSame(SoftBreak.Space, profile.softBreak)
        assertSame(HardBreakEncode.Backslash, profile.hardBreakEncode)
        assertSame(EntityDecode.Standard, profile.entityDecode)
        assertSame(HtmlProfile.Default, (profile.htmlInMarkdown as HtmlInMarkdown.Bridge).htmlProfile)
    }

    @Test
    fun policyBuildersAreImmutable() {
        val base = MarkdownProfile.Default
        val changed = base
            .withUnsupportedSyntax(UnsupportedSyntax.WarnAndDegrade)
            .withNewlineSemantics(NewlineSemantics.HardBreak)
            .withSoftBreak(SoftBreak.LineBreak)
            .withHardBreakEncode(HardBreakEncode.TwoSpaces)
            .withEntityDecode(EntityDecode.None)
            .withHtmlInMarkdown(HtmlInMarkdown.WarnAndStrip)

        assertSame(UnsupportedSyntax.Preserve, base.unsupportedSyntax)
        assertSame(UnsupportedSyntax.WarnAndDegrade, changed.unsupportedSyntax)
        assertSame(NewlineSemantics.HardBreak, changed.newlineSemantics)
        assertSame(SoftBreak.LineBreak, changed.softBreak)
        assertSame(HardBreakEncode.TwoSpaces, changed.hardBreakEncode)
        assertSame(EntityDecode.None, changed.entityDecode)
        assertSame(HtmlInMarkdown.WarnAndStrip, changed.htmlInMarkdown)
    }

    @Test
    fun encoderRegistrationDoesNotMutateSupportSet() {
        val base = MarkdownProfile.Default
        val changed = base.withMarkdownBlockEncoder<BlockType.Paragraph> { _, _, _ -> MarkdownEmit.Skip }
        assertSame(base.supportSet, changed.supportSet)
    }
}
