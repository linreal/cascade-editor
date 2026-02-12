package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.SpanAlgorithms
import io.github.linreal.cascade.editor.richtext.StyleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpanAlgorithmsTest {

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Normalization                                                  ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `normalize - empty list returns empty`() {
        assertEquals(emptyList(), SpanAlgorithms.normalize(emptyList(), 10))
    }

    @Test
    fun `normalize - valid spans unchanged`() {
        val spans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val result = SpanAlgorithms.normalize(spans, 10)
        assertEquals(spans, result)
    }

    @Test
    fun `normalize - clamps to text length`() {
        val spans = listOf(TextSpan(0, 100, SpanStyle.Bold))
        val result = SpanAlgorithms.normalize(spans, 10)
        assertEquals(listOf(TextSpan(0, 10, SpanStyle.Bold)), result)
    }

    @Test
    fun `normalize - clamps negative start to zero`() {
        // TextSpan constructor rejects negative start, so we test clamping via
        // a span that starts at 0 but exceeds text length
        val spans = listOf(TextSpan(0, 20, SpanStyle.Bold))
        val result = SpanAlgorithms.normalize(spans, 5)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), result)
    }

    @Test
    fun `normalize - drops empty spans`() {
        val spans = listOf(TextSpan(3, 3, SpanStyle.Bold))
        assertTrue(SpanAlgorithms.normalize(spans, 10).isEmpty())
    }

    @Test
    fun `normalize - drops spans fully beyond text length`() {
        val spans = listOf(TextSpan(10, 15, SpanStyle.Bold))
        assertTrue(SpanAlgorithms.normalize(spans, 5).isEmpty())
    }

    @Test
    fun `normalize - merges overlapping same-style spans`() {
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),
            TextSpan(3, 8, SpanStyle.Bold),
        )
        val result = SpanAlgorithms.normalize(spans, 10)
        assertEquals(listOf(TextSpan(0, 8, SpanStyle.Bold)), result)
    }

    @Test
    fun `normalize - merges adjacent same-style spans`() {
        val spans = listOf(
            TextSpan(0, 3, SpanStyle.Bold),
            TextSpan(3, 6, SpanStyle.Bold),
        )
        val result = SpanAlgorithms.normalize(spans, 10)
        assertEquals(listOf(TextSpan(0, 6, SpanStyle.Bold)), result)
    }

    @Test
    fun `normalize - preserves different-style overlaps`() {
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),
            TextSpan(3, 8, SpanStyle.Italic),
        )
        val result = SpanAlgorithms.normalize(spans, 10)
        assertEquals(2, result.size)
        assertTrue(result.contains(TextSpan(0, 5, SpanStyle.Bold)))
        assertTrue(result.contains(TextSpan(3, 8, SpanStyle.Italic)))
    }

    @Test
    fun `normalize - sorts output by start then end`() {
        val spans = listOf(
            TextSpan(5, 8, SpanStyle.Italic),
            TextSpan(0, 3, SpanStyle.Bold),
        )
        val result = SpanAlgorithms.normalize(spans, 10)
        assertEquals(0, result[0].start)
        assertEquals(5, result[1].start)
    }

    @Test
    fun `normalize - merges three overlapping same-style spans`() {
        val spans = listOf(
            TextSpan(0, 4, SpanStyle.Bold),
            TextSpan(3, 7, SpanStyle.Bold),
            TextSpan(6, 10, SpanStyle.Bold),
        )
        val result = SpanAlgorithms.normalize(spans, 10)
        assertEquals(listOf(TextSpan(0, 10, SpanStyle.Bold)), result)
    }

    @Test
    fun `normalize - does not merge non-adjacent same-style spans`() {
        val spans = listOf(
            TextSpan(0, 3, SpanStyle.Bold),
            TextSpan(5, 8, SpanStyle.Bold),
        )
        val result = SpanAlgorithms.normalize(spans, 10)
        assertEquals(2, result.size)
    }

    @Test
    fun `normalize - textLength zero drops everything`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        assertTrue(SpanAlgorithms.normalize(spans, 0).isEmpty())
    }

    @Test
    fun `normalize - complex multi-style scenario`() {
        // Bold [0,5) + Bold [4,9) should merge to Bold [0,9)
        // Italic [2,7) stays independent
        // Underline [12,15) clamped to [10,10) and dropped for textLength=10
        // ... actually [12,15) with textLength=10 → start clamped to 10, end clamped to 10 → empty
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),
            TextSpan(4, 9, SpanStyle.Bold),
            TextSpan(2, 7, SpanStyle.Italic),
        )
        val result = SpanAlgorithms.normalize(spans, 10)
        assertEquals(2, result.size)
        assertTrue(result.contains(TextSpan(0, 9, SpanStyle.Bold)))
        assertTrue(result.contains(TextSpan(2, 7, SpanStyle.Italic)))
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Edit Adjustment                                                ║
    // ╚══════════════════════════════════════════════════════════════════╝

    // ── Insertions ──

    @Test
    fun `adjustForEdit - insert within span expands it`() {
        // "Hello" with Bold [1,4), insert 1 char at position 2
        val spans = listOf(TextSpan(1, 4, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 2, deletedLength = 0, insertedLength = 1)
        assertEquals(listOf(TextSpan(1, 5, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - insert at span start pushes span right`() {
        val spans = listOf(TextSpan(3, 7, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 3, deletedLength = 0, insertedLength = 1)
        assertEquals(listOf(TextSpan(4, 8, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - insert at span end does not extend`() {
        val spans = listOf(TextSpan(2, 5, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 5, deletedLength = 0, insertedLength = 1)
        assertEquals(listOf(TextSpan(2, 5, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - insert before span shifts entire span`() {
        val spans = listOf(TextSpan(5, 8, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 2, deletedLength = 0, insertedLength = 3)
        assertEquals(listOf(TextSpan(8, 11, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - insert after span leaves it unchanged`() {
        val spans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 5, deletedLength = 0, insertedLength = 2)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - insert at position 0 pushes span starting at 0`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 0, deletedLength = 0, insertedLength = 1)
        assertEquals(listOf(TextSpan(1, 6, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - multi-char insert within span`() {
        val spans = listOf(TextSpan(2, 8, SpanStyle.Italic))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 4, deletedLength = 0, insertedLength = 5)
        assertEquals(listOf(TextSpan(2, 13, SpanStyle.Italic)), result)
    }

    // ── Deletions ──

    @Test
    fun `adjustForEdit - delete within span shrinks it`() {
        val spans = listOf(TextSpan(2, 8, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 3, deletedLength = 2, insertedLength = 0)
        assertEquals(listOf(TextSpan(2, 6, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - delete before span shifts it left`() {
        val spans = listOf(TextSpan(5, 8, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 1, deletedLength = 2, insertedLength = 0)
        assertEquals(listOf(TextSpan(3, 6, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - delete entire span drops it`() {
        val spans = listOf(TextSpan(3, 6, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 3, deletedLength = 3, insertedLength = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `adjustForEdit - delete overlapping span start clips start`() {
        // Span [4, 8), delete [3, 5)
        val spans = listOf(TextSpan(4, 8, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 3, deletedLength = 2, insertedLength = 0)
        assertEquals(listOf(TextSpan(3, 6, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - delete overlapping span end clips end`() {
        // Span [1, 4), delete [3, 5)
        val spans = listOf(TextSpan(1, 4, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 3, deletedLength = 2, insertedLength = 0)
        assertEquals(listOf(TextSpan(1, 3, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - delete containing span entirely drops it`() {
        // Span [4, 6), delete [2, 9)
        val spans = listOf(TextSpan(4, 6, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 2, deletedLength = 7, insertedLength = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `adjustForEdit - delete after span leaves it unchanged`() {
        val spans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 5, deletedLength = 2, insertedLength = 0)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - delete exactly before span start`() {
        // Span [3, 6), delete [1, 3)
        val spans = listOf(TextSpan(3, 6, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 1, deletedLength = 2, insertedLength = 0)
        assertEquals(listOf(TextSpan(1, 4, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - delete exactly after span end`() {
        // Span [0, 3), delete [3, 5)
        val spans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 3, deletedLength = 2, insertedLength = 0)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), result)
    }

    // ── Replacements ──

    @Test
    fun `adjustForEdit - replace within span adjusts end by delta`() {
        // Span [2, 8), replace [4, 6) with 1 char (delta = -1)
        val spans = listOf(TextSpan(2, 8, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 4, deletedLength = 2, insertedLength = 1)
        assertEquals(listOf(TextSpan(2, 7, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - replace within span grows it`() {
        // Span [2, 8), replace [4, 5) with 3 chars (delta = +2)
        val spans = listOf(TextSpan(2, 8, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 4, deletedLength = 1, insertedLength = 3)
        assertEquals(listOf(TextSpan(2, 10, SpanStyle.Bold)), result)
    }

    @Test
    fun `adjustForEdit - replace entire span with larger text drops it`() {
        // Span [3, 5), replace [3, 5) with 4 chars — span collapses since start and end
        // both map to editStart, but editEnd == 5 so start ∈ [3,5) → 3, end ∈ (3,5] → 3
        val spans = listOf(TextSpan(3, 5, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 3, deletedLength = 2, insertedLength = 4)
        assertTrue(result.isEmpty())
    }

    // ── Multiple spans ──

    @Test
    fun `adjustForEdit - multiple spans adjusted correctly`() {
        // "Hello bold and italic" — Bold [6,10), Italic [15,21)
        // Insert 2 chars at position 8 (within Bold)
        val spans = listOf(
            TextSpan(6, 10, SpanStyle.Bold),
            TextSpan(15, 21, SpanStyle.Italic),
        )
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 8, deletedLength = 0, insertedLength = 2)
        assertEquals(
            listOf(
                TextSpan(6, 12, SpanStyle.Bold),  // expanded
                TextSpan(17, 23, SpanStyle.Italic), // shifted
            ),
            result
        )
    }

    @Test
    fun `adjustForEdit - empty spans list returns empty`() {
        val result = SpanAlgorithms.adjustForEdit(emptyList(), editStart = 0, deletedLength = 0, insertedLength = 5)
        assertTrue(result.isEmpty())
    }

    // ── Boundary: zero-length no-op edit ──

    @Test
    fun `adjustForEdit - no-op edit leaves spans unchanged`() {
        val spans = listOf(TextSpan(2, 5, SpanStyle.Bold))
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 3, deletedLength = 0, insertedLength = 0)
        assertEquals(spans, result)
    }

    @Test
    fun `adjustForEdit - coalesces adjacent same-style spans after gap deletion`() {
        val spans = listOf(
            TextSpan(0, 3, SpanStyle.Bold),
            TextSpan(4, 7, SpanStyle.Bold),
        )
        // Delete [3,4) to remove the gap between spans.
        val result = SpanAlgorithms.adjustForEdit(spans, editStart = 3, deletedLength = 1, insertedLength = 0)
        assertEquals(listOf(TextSpan(0, 6, SpanStyle.Bold)), result)
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Split                                                          ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `splitAt - span entirely in first block`() {
        val spans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val (first, second) = SpanAlgorithms.splitAt(spans, position = 5)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), first)
        assertTrue(second.isEmpty())
    }

    @Test
    fun `splitAt - span entirely in second block`() {
        val spans = listOf(TextSpan(5, 8, SpanStyle.Bold))
        val (first, second) = SpanAlgorithms.splitAt(spans, position = 3)
        assertTrue(first.isEmpty())
        assertEquals(listOf(TextSpan(2, 5, SpanStyle.Bold)), second) // shifted
    }

    @Test
    fun `splitAt - span crossing split point is clipped into both`() {
        val spans = listOf(TextSpan(2, 8, SpanStyle.Bold))
        val (first, second) = SpanAlgorithms.splitAt(spans, position = 5)
        assertEquals(listOf(TextSpan(2, 5, SpanStyle.Bold)), first)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), second)
    }

    @Test
    fun `splitAt - span ending exactly at split point stays in first`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val (first, second) = SpanAlgorithms.splitAt(spans, position = 5)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), first)
        assertTrue(second.isEmpty())
    }

    @Test
    fun `splitAt - span starting exactly at split point goes to second`() {
        val spans = listOf(TextSpan(5, 8, SpanStyle.Bold))
        val (first, second) = SpanAlgorithms.splitAt(spans, position = 5)
        assertTrue(first.isEmpty())
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), second)
    }

    @Test
    fun `splitAt - multiple spans across split point`() {
        val spans = listOf(
            TextSpan(0, 3, SpanStyle.Bold),
            TextSpan(2, 7, SpanStyle.Italic),
            TextSpan(6, 10, SpanStyle.Underline),
        )
        val (first, second) = SpanAlgorithms.splitAt(spans, position = 5)

        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Bold), TextSpan(2, 5, SpanStyle.Italic)),
            first
        )
        assertEquals(
            listOf(TextSpan(0, 2, SpanStyle.Italic), TextSpan(1, 5, SpanStyle.Underline)),
            second
        )
    }

    @Test
    fun `splitAt - empty spans returns empty pairs`() {
        val (first, second) = SpanAlgorithms.splitAt(emptyList(), position = 5)
        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
    }

    @Test
    fun `splitAt - split at position zero puts everything in second`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val (first, second) = SpanAlgorithms.splitAt(spans, position = 0)
        assertTrue(first.isEmpty())
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), second)
    }

    @Test
    fun `splitAt - negative position throws`() {
        assertFailsWith<IllegalArgumentException> {
            SpanAlgorithms.splitAt(listOf(TextSpan(0, 5, SpanStyle.Bold)), position = -1)
        }
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Merge                                                          ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `mergeSpans - second block spans shifted by first text length`() {
        val first = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val second = listOf(TextSpan(0, 4, SpanStyle.Italic))
        val result = SpanAlgorithms.mergeSpans(first, second, firstTextLength = 5)
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Bold), TextSpan(5, 9, SpanStyle.Italic)),
            result
        )
    }

    @Test
    fun `mergeSpans - adjacent same-style spans at boundary are merged`() {
        // Block A: Bold [0, 5) with textLength=5
        // Block B: Bold [0, 3) → shifted to [5, 8)
        // Should merge to Bold [0, 8)
        val first = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val second = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val result = SpanAlgorithms.mergeSpans(first, second, firstTextLength = 5)
        assertEquals(listOf(TextSpan(0, 8, SpanStyle.Bold)), result)
    }

    @Test
    fun `mergeSpans - round-trip with splitAt`() {
        val original = listOf(
            TextSpan(2, 8, SpanStyle.Bold),
            TextSpan(4, 12, SpanStyle.Italic),
        )
        val splitPos = 6
        val (firstSpans, secondSpans) = SpanAlgorithms.splitAt(original, splitPos)
        val merged = SpanAlgorithms.mergeSpans(firstSpans, secondSpans, firstTextLength = splitPos)

        // After split+merge, same-style adjacent spans should re-merge
        val normalized = SpanAlgorithms.normalize(merged, 12)
        assertEquals(
            listOf(TextSpan(2, 8, SpanStyle.Bold), TextSpan(4, 12, SpanStyle.Italic)),
            normalized
        )
    }

    @Test
    fun `mergeSpans - empty first block`() {
        val second = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val result = SpanAlgorithms.mergeSpans(emptyList(), second, firstTextLength = 0)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), result)
    }

    @Test
    fun `mergeSpans - empty second block`() {
        val first = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val result = SpanAlgorithms.mergeSpans(first, emptyList(), firstTextLength = 5)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), result)
    }

    @Test
    fun `mergeSpans - negative first text length throws`() {
        assertFailsWith<IllegalArgumentException> {
            SpanAlgorithms.mergeSpans(
                firstSpans = listOf(TextSpan(0, 3, SpanStyle.Bold)),
                secondSpans = listOf(TextSpan(0, 2, SpanStyle.Italic)),
                firstTextLength = -1
            )
        }
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Apply Style                                                    ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `applyStyle - adds new span to empty list`() {
        val result = SpanAlgorithms.applyStyle(emptyList(), 2, 5, SpanStyle.Bold, textLength = 10)
        assertEquals(listOf(TextSpan(2, 5, SpanStyle.Bold)), result)
    }

    @Test
    fun `applyStyle - merges with existing same-style span`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val result = SpanAlgorithms.applyStyle(spans, 3, 8, SpanStyle.Bold, textLength = 10)
        assertEquals(listOf(TextSpan(0, 8, SpanStyle.Bold)), result)
    }

    @Test
    fun `applyStyle - preserves different-style spans`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Italic))
        val result = SpanAlgorithms.applyStyle(spans, 3, 8, SpanStyle.Bold, textLength = 10)
        assertEquals(2, result.size)
        assertTrue(result.contains(TextSpan(0, 5, SpanStyle.Italic)))
        assertTrue(result.contains(TextSpan(3, 8, SpanStyle.Bold)))
    }

    @Test
    fun `applyStyle - empty range is no-op`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val result = SpanAlgorithms.applyStyle(spans, 3, 3, SpanStyle.Italic, textLength = 10)
        assertEquals(spans, result)
    }

    @Test
    fun `applyStyle - fills gap between same-style spans`() {
        val spans = listOf(
            TextSpan(0, 3, SpanStyle.Bold),
            TextSpan(5, 8, SpanStyle.Bold),
        )
        val result = SpanAlgorithms.applyStyle(spans, 3, 5, SpanStyle.Bold, textLength = 10)
        assertEquals(listOf(TextSpan(0, 8, SpanStyle.Bold)), result)
    }

    @Test
    fun `applyStyle - duplicate application is idempotent`() {
        val spans = listOf(TextSpan(2, 6, SpanStyle.Bold))
        val result = SpanAlgorithms.applyStyle(spans, 2, 6, SpanStyle.Bold, textLength = 10)
        assertEquals(listOf(TextSpan(2, 6, SpanStyle.Bold)), result)
    }

    @Test
    fun `applyStyle - reversed range is handled`() {
        val result = SpanAlgorithms.applyStyle(emptyList(), 8, 3, SpanStyle.Bold, textLength = 10)
        assertEquals(listOf(TextSpan(3, 8, SpanStyle.Bold)), result)
    }

    @Test
    fun `applyStyle - negative range clamps to zero without crash`() {
        val result = SpanAlgorithms.applyStyle(emptyList(), -5, 3, SpanStyle.Bold, textLength = 10)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), result)
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Remove Style                                                   ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `removeStyle - removes span entirely within range`() {
        val spans = listOf(TextSpan(2, 5, SpanStyle.Bold))
        val result = SpanAlgorithms.removeStyle(spans, 0, 10, SpanStyle.Bold)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `removeStyle - clips span at start`() {
        val spans = listOf(TextSpan(2, 8, SpanStyle.Bold))
        val result = SpanAlgorithms.removeStyle(spans, 5, 10, SpanStyle.Bold)
        assertEquals(listOf(TextSpan(2, 5, SpanStyle.Bold)), result)
    }

    @Test
    fun `removeStyle - clips span at end`() {
        val spans = listOf(TextSpan(2, 8, SpanStyle.Bold))
        val result = SpanAlgorithms.removeStyle(spans, 0, 5, SpanStyle.Bold)
        assertEquals(listOf(TextSpan(5, 8, SpanStyle.Bold)), result)
    }

    @Test
    fun `removeStyle - splits span around removal range`() {
        val spans = listOf(TextSpan(0, 10, SpanStyle.Bold))
        val result = SpanAlgorithms.removeStyle(spans, 3, 7, SpanStyle.Bold)
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Bold), TextSpan(7, 10, SpanStyle.Bold)),
            result
        )
    }

    @Test
    fun `removeStyle - does not affect different styles`() {
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),
            TextSpan(0, 5, SpanStyle.Italic),
        )
        val result = SpanAlgorithms.removeStyle(spans, 0, 5, SpanStyle.Bold)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Italic)), result)
    }

    @Test
    fun `removeStyle - empty range is no-op`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val result = SpanAlgorithms.removeStyle(spans, 3, 3, SpanStyle.Bold)
        assertEquals(spans, result)
    }

    @Test
    fun `removeStyle - span entirely outside removal range is untouched`() {
        val spans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val result = SpanAlgorithms.removeStyle(spans, 5, 8, SpanStyle.Bold)
        assertEquals(spans, result)
    }

    @Test
    fun `removeStyle - multiple matching spans clipped independently`() {
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),
            TextSpan(7, 12, SpanStyle.Bold),
        )
        val result = SpanAlgorithms.removeStyle(spans, 3, 9, SpanStyle.Bold)
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Bold), TextSpan(9, 12, SpanStyle.Bold)),
            result
        )
    }

    @Test
    fun `removeStyle - reversed range is handled`() {
        val spans = listOf(TextSpan(0, 10, SpanStyle.Bold))
        val result = SpanAlgorithms.removeStyle(spans, 7, 3, SpanStyle.Bold)
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Bold), TextSpan(7, 10, SpanStyle.Bold)),
            result
        )
    }

    @Test
    fun `removeStyle - parameterized style matches exactly`() {
        val yellow = SpanStyle.Highlight(0xFFFFFF00)
        val red = SpanStyle.Highlight(0xFFFF0000)
        val spans = listOf(
            TextSpan(0, 5, yellow),
            TextSpan(0, 5, red),
        )
        val result = SpanAlgorithms.removeStyle(spans, 0, 5, yellow)
        assertEquals(listOf(TextSpan(0, 5, red)), result)
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Toggle Style                                                   ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `toggleStyle - applies when absent`() {
        val result = SpanAlgorithms.toggleStyle(emptyList(), 2, 5, SpanStyle.Bold, textLength = 10)
        assertEquals(listOf(TextSpan(2, 5, SpanStyle.Bold)), result)
    }

    @Test
    fun `toggleStyle - removes when fully active`() {
        val spans = listOf(TextSpan(2, 5, SpanStyle.Bold))
        val result = SpanAlgorithms.toggleStyle(spans, 2, 5, SpanStyle.Bold, textLength = 10)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toggleStyle - applies when partial`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val result = SpanAlgorithms.toggleStyle(spans, 3, 8, SpanStyle.Bold, textLength = 10)
        // Partial → apply, should merge into [0, 8)
        assertEquals(listOf(TextSpan(0, 8, SpanStyle.Bold)), result)
    }

    @Test
    fun `toggleStyle - empty range is no-op`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        val result = SpanAlgorithms.toggleStyle(spans, 3, 3, SpanStyle.Bold, textLength = 10)
        assertEquals(spans, result)
    }

    @Test
    fun `toggleStyle - toggle on fully active sub-range removes only that sub-range`() {
        val spans = listOf(TextSpan(0, 10, SpanStyle.Bold))
        val result = SpanAlgorithms.toggleStyle(spans, 3, 7, SpanStyle.Bold, textLength = 10)
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Bold), TextSpan(7, 10, SpanStyle.Bold)),
            result
        )
    }

    @Test
    fun `toggleStyle - reversed range is handled`() {
        val result = SpanAlgorithms.toggleStyle(emptyList(), 9, 4, SpanStyle.Bold, textLength = 10)
        assertEquals(listOf(TextSpan(4, 9, SpanStyle.Bold)), result)
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Query: Style Status                                            ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `queryStyleStatus - absent when no spans`() {
        assertEquals(
            StyleStatus.Absent,
            SpanAlgorithms.queryStyleStatus(emptyList(), 0, 5, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - fully active when range exactly matches`() {
        val spans = listOf(TextSpan(2, 5, SpanStyle.Bold))
        assertEquals(
            StyleStatus.FullyActive,
            SpanAlgorithms.queryStyleStatus(spans, 2, 5, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - fully active when range is sub-range of span`() {
        val spans = listOf(TextSpan(0, 10, SpanStyle.Bold))
        assertEquals(
            StyleStatus.FullyActive,
            SpanAlgorithms.queryStyleStatus(spans, 3, 7, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - partial when span covers part of range`() {
        val spans = listOf(TextSpan(0, 5, SpanStyle.Bold))
        assertEquals(
            StyleStatus.Partial,
            SpanAlgorithms.queryStyleStatus(spans, 3, 8, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - absent when range does not overlap any span`() {
        val spans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        assertEquals(
            StyleStatus.Absent,
            SpanAlgorithms.queryStyleStatus(spans, 5, 8, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - absent for non-matching style`() {
        val spans = listOf(TextSpan(0, 10, SpanStyle.Italic))
        assertEquals(
            StyleStatus.Absent,
            SpanAlgorithms.queryStyleStatus(spans, 0, 10, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - fully active with multiple spans covering range`() {
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),
            TextSpan(5, 10, SpanStyle.Bold),
        )
        assertEquals(
            StyleStatus.FullyActive,
            SpanAlgorithms.queryStyleStatus(spans, 0, 10, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - partial with gap between spans`() {
        val spans = listOf(
            TextSpan(0, 3, SpanStyle.Bold),
            TextSpan(5, 8, SpanStyle.Bold),
        )
        assertEquals(
            StyleStatus.Partial,
            SpanAlgorithms.queryStyleStatus(spans, 0, 8, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - fully active with overlapping spans`() {
        val spans = listOf(
            TextSpan(0, 6, SpanStyle.Bold),
            TextSpan(4, 10, SpanStyle.Bold),
        )
        assertEquals(
            StyleStatus.FullyActive,
            SpanAlgorithms.queryStyleStatus(spans, 2, 8, SpanStyle.Bold)
        )
    }

    // ── Collapsed cursor queries ──

    @Test
    fun `queryStyleStatus - collapsed cursor inside span is FullyActive`() {
        val spans = listOf(TextSpan(2, 8, SpanStyle.Bold))
        assertEquals(
            StyleStatus.FullyActive,
            SpanAlgorithms.queryStyleStatus(spans, 5, 5, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - collapsed cursor at span start is FullyActive`() {
        val spans = listOf(TextSpan(3, 8, SpanStyle.Bold))
        assertEquals(
            StyleStatus.FullyActive,
            SpanAlgorithms.queryStyleStatus(spans, 3, 3, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - collapsed cursor at span end is Absent`() {
        // Half-open: [3, 8) means position 8 is outside
        val spans = listOf(TextSpan(3, 8, SpanStyle.Bold))
        assertEquals(
            StyleStatus.Absent,
            SpanAlgorithms.queryStyleStatus(spans, 8, 8, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - collapsed cursor outside any span is Absent`() {
        val spans = listOf(TextSpan(5, 8, SpanStyle.Bold))
        assertEquals(
            StyleStatus.Absent,
            SpanAlgorithms.queryStyleStatus(spans, 2, 2, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - reversed range behaves like forward selection`() {
        val spans = listOf(TextSpan(3, 8, SpanStyle.Bold))
        assertEquals(
            StyleStatus.FullyActive,
            SpanAlgorithms.queryStyleStatus(spans, 8, 3, SpanStyle.Bold)
        )
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Query: Active Styles At                                        ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `activeStylesAt - returns empty for empty spans`() {
        assertTrue(SpanAlgorithms.activeStylesAt(emptyList(), 5).isEmpty())
    }

    @Test
    fun `activeStylesAt - returns matching styles`() {
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),
            TextSpan(3, 8, SpanStyle.Italic),
            TextSpan(6, 10, SpanStyle.Underline),
        )
        val active = SpanAlgorithms.activeStylesAt(spans, 4)
        assertEquals(setOf(SpanStyle.Bold, SpanStyle.Italic), active)
    }

    @Test
    fun `activeStylesAt - position at span start is included`() {
        val spans = listOf(TextSpan(3, 8, SpanStyle.Bold))
        val active = SpanAlgorithms.activeStylesAt(spans, 3)
        assertEquals(setOf(SpanStyle.Bold), active)
    }

    @Test
    fun `activeStylesAt - position at span end is excluded`() {
        val spans = listOf(TextSpan(3, 8, SpanStyle.Bold))
        val active = SpanAlgorithms.activeStylesAt(spans, 8)
        assertTrue(active.isEmpty())
    }

    @Test
    fun `activeStylesAt - multiple styles at same position`() {
        val spans = listOf(
            TextSpan(0, 10, SpanStyle.Bold),
            TextSpan(0, 10, SpanStyle.Italic),
            TextSpan(0, 10, SpanStyle.Underline),
        )
        val active = SpanAlgorithms.activeStylesAt(spans, 5)
        assertEquals(setOf(SpanStyle.Bold, SpanStyle.Italic, SpanStyle.Underline), active)
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Composite / Integration Scenarios                              ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `overlapping bold and italic are cumulative`() {
        // Bold [0,5) + Italic [3,8) — both should survive normalization
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),
            TextSpan(3, 8, SpanStyle.Italic),
        )
        val result = SpanAlgorithms.normalize(spans, 10)
        assertEquals(2, result.size)
        assertTrue(result.contains(TextSpan(0, 5, SpanStyle.Bold)))
        assertTrue(result.contains(TextSpan(3, 8, SpanStyle.Italic)))

        // In the overlap region [3,5), both styles are active
        val stylesAt4 = SpanAlgorithms.activeStylesAt(result, 4)
        assertEquals(setOf(SpanStyle.Bold, SpanStyle.Italic), stylesAt4)
    }

    @Test
    fun `apply then remove round-trips back to original`() {
        val original = listOf(TextSpan(0, 3, SpanStyle.Italic))
        val applied = SpanAlgorithms.applyStyle(original, 2, 6, SpanStyle.Bold, textLength = 10)
        val removed = SpanAlgorithms.removeStyle(applied, 2, 6, SpanStyle.Bold)
        assertEquals(original, removed)
    }

    @Test
    fun `split then merge preserves original spans`() {
        val original = listOf(
            TextSpan(0, 10, SpanStyle.Bold),
            TextSpan(3, 7, SpanStyle.Italic),
        )
        val (first, second) = SpanAlgorithms.splitAt(original, 5)
        val merged = SpanAlgorithms.mergeSpans(first, second, firstTextLength = 5)
        val normalized = SpanAlgorithms.normalize(merged, 10)

        assertEquals(original.sortedWith(compareBy({ it.start }, { it.end })), normalized)
    }

    @Test
    fun `typing sequence within styled text`() {
        // Start: "Hello" with Bold [0,5)
        var spans = listOf(TextSpan(0, 5, SpanStyle.Bold))

        // Type 'X' at position 2 (within bold)
        spans = SpanAlgorithms.adjustForEdit(spans, editStart = 2, deletedLength = 0, insertedLength = 1)
        assertEquals(listOf(TextSpan(0, 6, SpanStyle.Bold)), spans)

        // Type 'Y' at position 3 (within bold)
        spans = SpanAlgorithms.adjustForEdit(spans, editStart = 3, deletedLength = 0, insertedLength = 1)
        assertEquals(listOf(TextSpan(0, 7, SpanStyle.Bold)), spans)

        // Delete char at position 1 (backspace within bold)
        spans = SpanAlgorithms.adjustForEdit(spans, editStart = 1, deletedLength = 1, insertedLength = 0)
        assertEquals(listOf(TextSpan(0, 6, SpanStyle.Bold)), spans)
    }

    @Test
    fun `full workflow - apply style then type then query`() {
        // Start with empty spans for "Hello World" (11 chars)
        var spans = emptyList<TextSpan>()

        // Apply bold to "World" [6,11)
        spans = SpanAlgorithms.applyStyle(spans, 6, 11, SpanStyle.Bold, textLength = 11)

        // Type "!" at position 11 — after bold range
        spans = SpanAlgorithms.adjustForEdit(spans, editStart = 11, deletedLength = 0, insertedLength = 1)
        // Bold should NOT extend (end bias = before)
        assertEquals(listOf(TextSpan(6, 11, SpanStyle.Bold)), spans)

        // Verify bold status at various positions
        assertEquals(StyleStatus.Absent, SpanAlgorithms.queryStyleStatus(spans, 0, 6, SpanStyle.Bold))
        assertEquals(StyleStatus.FullyActive, SpanAlgorithms.queryStyleStatus(spans, 6, 11, SpanStyle.Bold))
        assertEquals(StyleStatus.Partial, SpanAlgorithms.queryStyleStatus(spans, 4, 9, SpanStyle.Bold))
    }
}
