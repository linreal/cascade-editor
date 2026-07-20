package io.github.linreal.cascade.editor.markdown

import kotlin.test.Test

/**
 * Generated round-trip property test: every document drawn inside
 * the default profile's support set satisfies `decode(encode(doc)) == doc`
 * modulo `BlockId`s.
 *
 * Reproducibility: the base seed is fixed; each document derives its own seed
 * from it, so a failure prints the offending document and is replayable by
 * re-running the same seed.
 */
class MarkdownRoundTripTest {

    @Test
    fun `default profile round trips 1000 generated support-set documents`() {
        val profile = MarkdownProfile.Default
        val generator = MarkdownSupportSetBlockGenerator(
            profile = profile,
            supportSet = profile.supportSet,
            seed = BASE_SEED,
        )

        repeat(1000) {
            val blocks = generator.nextDocument()
            assertMarkdownSupportSetDocument(profile.supportSet, blocks)

            val encoded = MarkdownSchema.encode(blocks, profile)
                ?: error("encode aborted for in-support document: $blocks")
            val decoded = MarkdownSchema.decode(encoded, profile)
                ?: error("decode aborted for canonical output:\n$encoded")

            assertMarkdownSemanticallyEquals(blocks, decoded)
        }
    }

    private companion object {
        const val BASE_SEED: Long = 0x5CA1AB1E
    }
}
