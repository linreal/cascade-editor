package io.github.linreal.cascade.editor.markdown

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Differential suite against **CommonMark 0.30**
 * (https://spec.commonmark.org/0.30/) plus one GFM 0.29-gfm strikethrough
 * example. Inputs are verbatim from the spec; the example numbers are the
 * official ones.
 *
 * Classification is a single consistent rule — `encode(decode(input))` is
 * compared to the verbatim input:
 * - **Aligned**: byte-identical round-trip (the input already matches our
 *   canonical form).
 * - **Deviation**: differs, and the difference matches a documented rationale
 *   (documented in `docs/MarkdownSerialization.md`). There is no third state.
 */
class MarkdownDifferentialTest {

    private sealed interface Expectation {
        data object Aligned : Expectation
        data class Deviation(val rationale: String, val check: (String) -> Boolean) : Expectation
    }

    private data class Case(val id: String, val input: String, val expectation: Expectation)

    private val cases: List<Case> = listOf(
        // Aligned: inputs already in our canonical form.
        Case("CM0.30 #219 paragraphs", "aaa\n\nbbb\n", Expectation.Aligned),
        Case("CM0.30 #377 strong emphasis", "**foo bar**\n", Expectation.Aligned),
        Case("GFM0.29 strikethrough", "~~Hi~~ Hello\n", Expectation.Aligned),

        // Documented deviations (see the feature matrix).
        Case(
            "CM0.30 #62 ATX headings",
            "# foo\n## foo\n### foo\n#### foo\n##### foo\n###### foo\n",
            Expectation.Deviation("engine-owned canonical block separation inserts a blank line between blocks") {
                it.contains("# foo") && it.contains("###### foo") && it.contains("\n\n")
            },
        ),
        Case(
            "CM0.30 #43 thematic breaks",
            "***\n---\n___\n",
            Expectation.Deviation("thematic breaks canonicalize to `---` with blank-line separation") {
                it == "---\n\n---\n\n---\n"
            },
        ),
        Case(
            "CM0.30 #350 emphasis",
            "*foo bar*",
            Expectation.Deviation("output always ends with exactly one final newline") {
                it == "*foo bar*\n"
            },
        ),
        Case(
            "CM0.30 #301 bullet list markers",
            "- foo\n- bar\n+ baz\n",
            Expectation.Deviation("bullet markers canonicalize to `-`") {
                it == "- foo\n- bar\n- baz\n"
            },
        ),
        Case(
            "CM0.30 #80 setext headings",
            "Foo *bar*\n=========\n\nFoo *bar*\n---------\n",
            Expectation.Deviation("setext headings are decode-only and re-emit as ATX (`#`/`##`)") {
                it.startsWith("# Foo ") && it.contains("\n## Foo ")
            },
        ),
        Case(
            "CM0.30 #107 indented code",
            "    a simple\n      indented code block\n",
            Expectation.Deviation("indented code is decode-only and re-emits as a fenced block") {
                it.startsWith("```") && it.contains("a simple")
            },
        ),
    )

    @Test
    fun `official examples are byte-identical or match a documented deviation`() {
        for (case in cases) {
            val decoded = MarkdownSchema.decode(case.input, MarkdownProfile.Default)
                ?: fail("${case.id}: decode aborted")
            val encoded = MarkdownSchema.encode(decoded, MarkdownProfile.Default)
                ?: fail("${case.id}: encode aborted")
            when (val expectation = case.expectation) {
                Expectation.Aligned -> assertTrue(
                    encoded == case.input,
                    "${case.id}: expected byte-identical round-trip; got:\n<<<$encoded>>>",
                )
                is Expectation.Deviation -> {
                    assertTrue(
                        encoded != case.input,
                        "${case.id}: marked deviation but round-trip was byte-identical",
                    )
                    assertTrue(
                        expectation.check(encoded),
                        "${case.id}: deviation (${expectation.rationale}); got:\n<<<$encoded>>>",
                    )
                }
            }
        }
    }
}
