package io.github.linreal.cascade.editor.markdown

/**
 * Reviewable corpus fixtures, kept as **data** separate from the
 * assertion logic in `MarkdownCorpusTest`. (KMP `commonTest` has no portable
 * filesystem, so a single Kotlin manifest is the cross-platform equivalent of
 * one expectations file per fixture — every corpus document, its profile, and
 * its expected `analyze` recommendation + warning-impact categories are visible
 * here and reviewed as data.)
 *
 * Corpora are synthetic but plausible (pasted GitHub/Notion/ChatGPT-style docs,
 * Tasks.org/MyBrain task notes, and hard-break notes). No source-identity
 * claim is made — only no-throw, the expected recommendation, and the
 * expected warning categories.
 */
internal enum class MarkdownCorpusProfileKey { StrictGfm, StrictHardBreak }

internal data class MarkdownCorpusEntry(
    val name: String,
    val profile: MarkdownCorpusProfileKey,
    val source: String,
    val expectedRecommendation: MarkdownEditModeRecommendation,
    /** Warning-impact categories that MUST be present in the analyze report. */
    val expectedImpacts: Set<MarkdownFidelityImpact>,
)

internal object MarkdownCorpusFixtures {

    val Entries: List<MarkdownCorpusEntry> = listOf(
        MarkdownCorpusEntry(
            name = "github-standup",
            profile = MarkdownCorpusProfileKey.StrictGfm,
            source = "# Standup\n\nShipped **auth**, reviewed the [PR](../pr/42.md).\n\n" +
                "- done\n- next\n\n> ship notes tomorrow\n",
            expectedRecommendation = MarkdownEditModeRecommendation.Native,
            expectedImpacts = emptySet(),
        ),
        MarkdownCorpusEntry(
            name = "notion-pasted-table",
            profile = MarkdownCorpusProfileKey.StrictGfm,
            source = "Budget:\n\n| item | usd |\n| - | - |\n| infra | 42 |\n| tools | 7 |\n",
            expectedRecommendation = MarkdownEditModeRecommendation.RawFallback,
            expectedImpacts = setOf(MarkdownFidelityImpact.OpaquePreservation),
        ),
        MarkdownCorpusEntry(
            name = "chatgpt-inline-html",
            profile = MarkdownCorpusProfileKey.StrictGfm,
            source = "A note with <span class=\"x\">inline html</span> pasted in.\n",
            expectedRecommendation = MarkdownEditModeRecommendation.RawFallback,
            expectedImpacts = setOf(MarkdownFidelityImpact.DataLoss),
        ),
        MarkdownCorpusEntry(
            name = "tasks-org-note",
            profile = MarkdownCorpusProfileKey.StrictGfm,
            source = "## Groceries\n\n- [ ] milk\n- [x] eggs\n- [ ] bread\n",
            expectedRecommendation = MarkdownEditModeRecommendation.Native,
            expectedImpacts = emptySet(),
        ),
        MarkdownCorpusEntry(
            name = "strict-hardbreak-memo",
            profile = MarkdownCorpusProfileKey.StrictHardBreak,
            source = "monday: plan sprint\n\ntuesday: review PRs\n\nwednesday: ship release\n",
            expectedRecommendation = MarkdownEditModeRecommendation.Native,
            expectedImpacts = emptySet(),
        ),
        MarkdownCorpusEntry(
            name = "hardbreak-raw-html",
            profile = MarkdownCorpusProfileKey.StrictHardBreak,
            source = "a memo with <b>inline html</b> pasted in\n",
            expectedRecommendation = MarkdownEditModeRecommendation.RawFallback,
            expectedImpacts = setOf(MarkdownFidelityImpact.DataLoss),
        ),
        MarkdownCorpusEntry(
            name = "gfm-document-scale",
            profile = MarkdownCorpusProfileKey.StrictGfm,
            source = buildString {
                append("# Release checklist\n\n")
                append("Track progress with **bold**, _italic_, `code`, and ~~struck~~ notes.\n\n")
                append("## Tasks\n\n")
                append("- [ ] cut the branch\n")
                append("- [x] update the changelog\n")
                append("- [ ] tag the build\n")
                append("  - [ ] macOS\n")
                append("  - [ ] linux\n\n")
                append("## Steps\n\n")
                append("1. build\n")
                append("2. sign\n")
                append("3. publish\n\n")
                append("See the [runbook](../docs/runbook.md) and <https://example.com/status>.\n\n")
                append("> Remember to announce in the channel.\n\n")
                append("```\n./gradlew publish --no-daemon\n```\n\n")
                append("Nested notes:\n\n")
                append("- outer\n")
                append("  - inner one\n")
                append("  - inner two\n\n")
                append("---\n\n")
                append("Final paragraph with a relative link [here](./notes.md).\n")
            },
            expectedRecommendation = MarkdownEditModeRecommendation.Native,
            expectedImpacts = emptySet(),
        ),
    )

    fun profileFor(key: MarkdownCorpusProfileKey): MarkdownProfile = when (key) {
        MarkdownCorpusProfileKey.StrictGfm -> MarkdownReferenceProfiles.StrictGfmFieldProfile
        MarkdownCorpusProfileKey.StrictHardBreak -> MarkdownReferenceProfiles.StrictHardBreakFieldProfile
    }
}
