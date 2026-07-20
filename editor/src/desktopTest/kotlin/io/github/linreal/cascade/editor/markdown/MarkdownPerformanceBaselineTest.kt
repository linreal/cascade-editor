package io.github.linreal.cascade.editor.markdown

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Performance regression guard. Bounds are deliberately generous
 * (they guard against complexity regressions, not micro-performance) on a
 * generated 64 KB mixed-construct field: decode ≤ 250 ms, encode ≤ 250 ms,
 * `analyze` ≤ 750 ms, and one adversarial (10k-delimiter) input ≤ 1 s.
 *
 * Timings are medians over repeated runs after a warm-up and are printed for
 * review.
 */
class MarkdownPerformanceBaselineTest {

    private val profile = MarkdownProfile.Default

    private val document: String = buildString {
        val unit = buildString {
            append("# Section heading with **bold** and _italic_ and `code`\n\n")
            append("A paragraph with a [link](../docs/guide.md) and ~~strike~~ text ")
            append("that runs on for a while to bulk up the field content nicely.\n\n")
            append("- bullet one\n- bullet two\n  - nested item\n- bullet three\n\n")
            append("1. first\n2. second\n3. third\n\n")
            append("- [ ] open task\n- [x] done task\n\n")
            append("> a quoted line of text\n\n")
            append("```\nsome code\nmore code\n```\n\n")
            append("Another paragraph with <u>underline</u> and a trailing sentence.\n\n")
        }
        while (length < 64 * 1024) append(unit)
    }

    private fun medianMillis(runs: Int, action: () -> Unit): Long {
        repeat(3) { action() } // warm up
        val samples = LongArray(runs) {
            val start = System.nanoTime()
            action()
            (System.nanoTime() - start) / 1_000_000
        }
        samples.sort()
        return samples[runs / 2]
    }

    @Test
    fun `decode encode and analyze stay within generous bounds on a 64kb field`() {
        val decodeMs = medianMillis(7) { MarkdownSchema.decode(document, profile) }
        val decoded = MarkdownSchema.decode(document, profile)!!
        val encodeMs = medianMillis(7) { MarkdownSchema.encode(decoded, profile) }
        val analyzeMs = medianMillis(5) { MarkdownSchema.analyze(document, profile) }

        println("[MarkdownPerf] size=${document.length} decode=${decodeMs}ms encode=${encodeMs}ms analyze=${analyzeMs}ms")

        assertTrue(decodeMs <= 250, "decode median ${decodeMs}ms exceeded 250ms")
        assertTrue(encodeMs <= 250, "encode median ${encodeMs}ms exceeded 250ms")
        assertTrue(analyzeMs <= 750, "analyze median ${analyzeMs}ms exceeded 750ms")
    }

    @Test
    fun `adversarial delimiter run completes or aborts within one second`() {
        val adversarial = "*".repeat(10_000)
        val start = System.nanoTime()
        val result = MarkdownSchema.decodeWithReport(adversarial, profile)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        println("[MarkdownPerf] adversarial 10k '*' -> ${if (result.isAborted) "aborted" else "completed"} in ${elapsedMs}ms")
        assertTrue(elapsedMs <= 1_000, "adversarial input took ${elapsedMs}ms (> 1s)")
    }
}
