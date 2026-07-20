package io.github.linreal.cascade.editor.markdown

/** Release-test field profiles built from the supported public policy surface. */
internal object MarkdownReferenceProfiles {
    val StrictGfmFieldProfile: MarkdownProfile = MarkdownProfile.Default.withoutHtmlBridge()

    val StrictHardBreakFieldProfile: MarkdownProfile = MarkdownProfile.Default
        .withNewlineSemantics(NewlineSemantics.HardBreak)
        .withoutHtmlBridge()
}
