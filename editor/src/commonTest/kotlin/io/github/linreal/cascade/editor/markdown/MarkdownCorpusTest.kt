package io.github.linreal.cascade.editor.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Data-driven corpus suite: iterates the reviewable
 * [MarkdownCorpusFixtures] manifest, asserting no-throw, the expected `analyze`
 * recommendation, and the expected warning-impact categories per fixture.
 */
class MarkdownCorpusTest {

    @Test
    fun `every corpus fixture matches its expected recommendation and warning categories`() {
        for (entry in MarkdownCorpusFixtures.Entries) {
            val profile = MarkdownCorpusFixtures.profileFor(entry.profile)
            val report = MarkdownSchema.analyze(entry.source, profile)

            assertEquals(
                entry.expectedRecommendation,
                report.recommendedMode,
                "recommendation for '${entry.name}'",
            )

            val presentImpacts = buildSet {
                report.decodeResult.warnings.forEach { add(it.impact) }
                report.encodeResult?.warnings?.forEach { add(it.impact) }
            }
            for (expected in entry.expectedImpacts) {
                assertTrue(
                    expected in presentImpacts,
                    "corpus '${entry.name}' expected impact $expected; present=$presentImpacts",
                )
            }
            if (entry.expectedRecommendation == MarkdownEditModeRecommendation.Native) {
                assertTrue(
                    presentImpacts.none {
                        it == MarkdownFidelityImpact.OpaquePreservation ||
                            it == MarkdownFidelityImpact.DataLoss ||
                            it == MarkdownFidelityImpact.Fatal
                    },
                    "native corpus '${entry.name}' must have no lossy impacts; present=$presentImpacts",
                )
                // A Native fixture must round-trip: decode → encode → re-decode
                // is structurally stable.
                val profile = MarkdownCorpusFixtures.profileFor(entry.profile)
                val decoded = MarkdownSchema.decode(entry.source, profile)!!
                val reencoded = MarkdownSchema.encode(decoded, profile)!!
                val redecoded = MarkdownSchema.decode(reencoded, profile)!!
                assertMarkdownSemanticallyEquals(decoded, redecoded)
            }
        }
    }
}
