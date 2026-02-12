package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.StyleStatus
import io.github.linreal.cascade.editor.state.BlockSpanStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockSpanStatesTest {

    private val blockA = BlockId("block-a")
    private val blockB = BlockId("block-b")
    private val blockC = BlockId("block-c")

    private fun BlockSpanStates.create(
        blockId: BlockId,
        spans: List<TextSpan> = emptyList(),
        textLength: Int = spans.maxOfOrNull { it.end } ?: 0,
    ) = getOrCreate(blockId = blockId, initialSpans = spans, textLength = textLength)

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Lifecycle                                                       ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `getOrCreate - creates new state with empty spans`() {
        val holder = BlockSpanStates()
        val state = holder.create(blockA)
        assertEquals(emptyList(), state.value)
    }

    @Test
    fun `getOrCreate - creates new state with initial spans`() {
        val holder = BlockSpanStates()
        val spans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val state = holder.create(blockA, spans)
        assertEquals(spans, state.value)
    }

    @Test
    fun `getOrCreate - returns same state on second call (idempotent)`() {
        val holder = BlockSpanStates()
        val spans1 = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val spans2 = listOf(TextSpan(0, 5, SpanStyle.Italic))

        val state1 = holder.create(blockA, spans1)
        val state2 = holder.create(blockA, spans2)

        // Same instance, initial spans preserved
        assertEquals(spans1, state1.value)
        assertEquals(spans1, state2.value)
    }

    @Test
    fun `get - returns null for absent block`() {
        val holder = BlockSpanStates()
        assertNull(holder.get(blockA))
    }

    @Test
    fun `get - returns state for existing block`() {
        val holder = BlockSpanStates()
        val spans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        holder.create(blockA, spans)
        val state = holder.get(blockA)
        assertNotNull(state)
        assertEquals(spans, state.value)
    }

    @Test
    fun `getSpans - returns empty list for absent block`() {
        val holder = BlockSpanStates()
        assertEquals(emptyList(), holder.getSpans(blockA))
    }

    @Test
    fun `getSpans - returns current spans for existing block`() {
        val holder = BlockSpanStates()
        val spans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        holder.create(blockA, spans)
        assertEquals(spans, holder.getSpans(blockA))
    }

    @Test
    fun `set - updates spans for existing block`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 3, SpanStyle.Bold)))
        val newSpans = listOf(TextSpan(0, 5, SpanStyle.Italic))
        holder.set(blockA, newSpans, textLength = 5)
        assertEquals(newSpans, holder.getSpans(blockA))
    }

    @Test
    fun `set - no-op for absent block`() {
        val holder = BlockSpanStates()
        holder.set(blockA, listOf(TextSpan(0, 3, SpanStyle.Bold)), textLength = 3)
        assertNull(holder.get(blockA))
    }

    @Test
    fun `getOrCreate - normalizes and clamps initial spans by text length`() {
        val holder = BlockSpanStates()
        val state = holder.create(
            blockA,
            spans = listOf(
                TextSpan(3, 8, SpanStyle.Bold),
                TextSpan(0, 3, SpanStyle.Bold),
                TextSpan(4, 4, SpanStyle.Italic),
            ),
            textLength = 5
        )

        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), state.value)
    }

    @Test
    fun `set - normalizes and clamps spans by text length`() {
        val holder = BlockSpanStates()
        holder.create(blockA, textLength = 5)

        holder.set(
            blockA,
            spans = listOf(
                TextSpan(2, 10, SpanStyle.Bold),
                TextSpan(0, 2, SpanStyle.Bold),
                TextSpan(4, 4, SpanStyle.Italic),
            ),
            textLength = 5
        )

        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), holder.getSpans(blockA))
    }

    @Test
    fun `getOrCreate - defensive copy protects against source list mutation`() {
        val holder = BlockSpanStates()
        val source = mutableListOf(TextSpan(0, 3, SpanStyle.Bold))
        holder.create(blockA, source, textLength = 10)

        source += TextSpan(3, 5, SpanStyle.Italic)

        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), holder.getSpans(blockA))
    }

    @Test
    fun `set - defensive copy protects against source list mutation`() {
        val holder = BlockSpanStates()
        holder.create(blockA, textLength = 10)
        val source = mutableListOf(TextSpan(0, 3, SpanStyle.Bold))

        holder.set(blockA, source, textLength = 10)
        source += TextSpan(3, 5, SpanStyle.Italic)

        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), holder.getSpans(blockA))
    }

    @Test
    fun `remove - removes state and pending styles`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 3, SpanStyle.Bold)))
        holder.setPendingStyles(blockA, setOf(SpanStyle.Italic))
        holder.remove(blockA)
        assertNull(holder.get(blockA))
        assertNull(holder.getPendingStyles(blockA))
    }

    @Test
    fun `cleanup - removes stale blocks`() {
        val holder = BlockSpanStates()
        holder.create(blockA)
        holder.create(blockB)
        holder.create(blockC)
        holder.setPendingStyles(blockC, setOf(SpanStyle.Bold))

        holder.cleanup(setOf(blockA)) // only blockA survives

        assertNotNull(holder.get(blockA))
        assertNull(holder.get(blockB))
        assertNull(holder.get(blockC))
        assertNull(holder.getPendingStyles(blockC))
    }

    @Test
    fun `cleanup - removes pending styles for blocks without state entries`() {
        val holder = BlockSpanStates()
        holder.setPendingStyles(blockA, setOf(SpanStyle.Bold))
        holder.cleanup(emptySet())
        assertNull(holder.getPendingStyles(blockA))
    }

    @Test
    fun `clear - removes all states and pending styles`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 3, SpanStyle.Bold)))
        holder.create(blockB)
        holder.setPendingStyles(blockA, setOf(SpanStyle.Italic))
        holder.clear()

        assertNull(holder.get(blockA))
        assertNull(holder.get(blockB))
        assertNull(holder.getPendingStyles(blockA))
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Edit Adjustment                                                 ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `adjustForUserEdit - delegates to SpanAlgorithms`() {
        val holder = BlockSpanStates()
        // "Hello" with Bold on [0,5)
        holder.create(blockA, listOf(TextSpan(0, 5, SpanStyle.Bold)))

        // Insert 3 chars at position 5 (appending)
        holder.adjustForUserEdit(blockA, editStart = 5, deletedLength = 0, insertedLength = 3)

        // Bold span should remain [0,5) — end uses "before" bias
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), holder.getSpans(blockA))
    }

    @Test
    fun `adjustForUserEdit - deletion collapses span`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(2, 5, SpanStyle.Bold)))

        // Delete the entire bolded range
        holder.adjustForUserEdit(blockA, editStart = 2, deletedLength = 3, insertedLength = 0)

        assertEquals(emptyList(), holder.getSpans(blockA))
    }

    @Test
    fun `adjustForUserEdit - no-op for absent block`() {
        val holder = BlockSpanStates()
        // Should not throw
        holder.adjustForUserEdit(blockA, editStart = 0, deletedLength = 0, insertedLength = 5)
    }

    @Test
    fun `adjustForUserEdit - insertion in middle shifts following spans`() {
        val holder = BlockSpanStates()
        holder.create(
            blockA,
            listOf(
                TextSpan(0, 3, SpanStyle.Bold),
                TextSpan(5, 8, SpanStyle.Italic),
            )
        )

        // Insert 2 chars at position 4 (between the two spans)
        holder.adjustForUserEdit(blockA, editStart = 4, deletedLength = 0, insertedLength = 2)

        assertEquals(
            listOf(
                TextSpan(0, 3, SpanStyle.Bold),
                TextSpan(7, 10, SpanStyle.Italic),
            ),
            holder.getSpans(blockA)
        )
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Split                                                           ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `split - divides spans between source and new block`() {
        val holder = BlockSpanStates()
        holder.create(
            blockA,
            listOf(
                TextSpan(0, 3, SpanStyle.Bold),
                TextSpan(5, 8, SpanStyle.Italic),
            )
        )

        holder.split(blockA, blockB, position = 4)

        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), holder.getSpans(blockA))
        assertEquals(listOf(TextSpan(1, 4, SpanStyle.Italic)), holder.getSpans(blockB))
    }

    @Test
    fun `split - crossing span is clipped into both blocks`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(2, 8, SpanStyle.Bold)))

        holder.split(blockA, blockB, position = 5)

        assertEquals(listOf(TextSpan(2, 5, SpanStyle.Bold)), holder.getSpans(blockA))
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), holder.getSpans(blockB))
    }

    @Test
    fun `split - new block state is created`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 10, SpanStyle.Bold)))

        holder.split(blockA, blockB, position = 5)

        assertNotNull(holder.get(blockB))
    }

    @Test
    fun `split - reuses existing target state instance`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 6, SpanStyle.Bold)))
        val existingTarget = holder.create(blockB, listOf(TextSpan(0, 1, SpanStyle.Italic)))

        holder.split(blockA, blockB, position = 3)

        val targetAfter = holder.get(blockB)
        assertNotNull(targetAfter)
        assertTrue(existingTarget === targetAfter)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), targetAfter.value)
    }

    @Test
    fun `split - clears stale pending styles on reused target block`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 6, SpanStyle.Bold)))
        holder.create(blockB)
        holder.setPendingStyles(blockB, setOf(SpanStyle.Italic))

        holder.split(blockA, blockB, position = 3)

        assertNull(holder.getPendingStyles(blockB))
    }

    @Test
    fun `split - clears pending styles on source`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 5, SpanStyle.Bold)))
        holder.setPendingStyles(blockA, setOf(SpanStyle.Italic))

        holder.split(blockA, blockB, position = 3)

        assertNull(holder.getPendingStyles(blockA))
    }

    @Test
    fun `split - no-op for absent source block`() {
        val holder = BlockSpanStates()
        holder.split(blockA, blockB, position = 5)
        assertNull(holder.get(blockA))
        assertNull(holder.get(blockB))
    }

    @Test
    fun `split at position 0 - all spans go to new block`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 5, SpanStyle.Bold)))

        holder.split(blockA, blockB, position = 0)

        assertEquals(emptyList(), holder.getSpans(blockA))
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), holder.getSpans(blockB))
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Merge                                                           ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `mergeInto - shifts source spans and merges with target`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 3, SpanStyle.Bold)))    // target
        holder.create(blockB, listOf(TextSpan(0, 4, SpanStyle.Italic)))  // source

        holder.mergeInto(sourceId = blockB, targetId = blockA, targetTextLength = 5)

        assertEquals(
            listOf(
                TextSpan(0, 3, SpanStyle.Bold),
                TextSpan(5, 9, SpanStyle.Italic),
            ),
            holder.getSpans(blockA)
        )
    }

    @Test
    fun `mergeInto - boundary merging of same style`() {
        val holder = BlockSpanStates()
        // Target: "Hello" with Bold [0,5)
        holder.create(blockA, listOf(TextSpan(0, 5, SpanStyle.Bold)))
        // Source: "World" with Bold [0,5)
        holder.create(blockB, listOf(TextSpan(0, 5, SpanStyle.Bold)))

        holder.mergeInto(sourceId = blockB, targetId = blockA, targetTextLength = 5)

        // Should merge into single Bold [0,10)
        assertEquals(listOf(TextSpan(0, 10, SpanStyle.Bold)), holder.getSpans(blockA))
    }

    @Test
    fun `mergeInto - source state is removed`() {
        val holder = BlockSpanStates()
        holder.create(blockA)
        holder.create(blockB, listOf(TextSpan(0, 3, SpanStyle.Bold)))
        holder.setPendingStyles(blockB, setOf(SpanStyle.Bold))

        holder.mergeInto(sourceId = blockB, targetId = blockA, targetTextLength = 5)

        assertNull(holder.get(blockB))
        assertNull(holder.getPendingStyles(blockB))
    }

    @Test
    fun `mergeInto - empty source`() {
        val holder = BlockSpanStates()
        val targetSpans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        holder.create(blockA, targetSpans)
        holder.create(blockB) // empty spans

        holder.mergeInto(sourceId = blockB, targetId = blockA, targetTextLength = 5)

        assertEquals(targetSpans, holder.getSpans(blockA))
    }

    @Test
    fun `mergeInto - creates target if absent`() {
        val holder = BlockSpanStates()
        holder.create(blockB, listOf(TextSpan(0, 3, SpanStyle.Bold)))

        holder.mergeInto(sourceId = blockB, targetId = blockA, targetTextLength = 5)

        assertNotNull(holder.get(blockA))
        assertEquals(listOf(TextSpan(5, 8, SpanStyle.Bold)), holder.getSpans(blockA))
    }

    @Test
    fun `mergeInto - no-op for absent source`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 3, SpanStyle.Bold)))

        holder.mergeInto(sourceId = blockB, targetId = blockA, targetTextLength = 5)

        // Target unchanged
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Bold)), holder.getSpans(blockA))
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Style Operations                                                ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `applyStyle - adds new span`() {
        val holder = BlockSpanStates()
        holder.create(blockA)

        holder.applyStyle(blockA, rangeStart = 0, rangeEnd = 5, SpanStyle.Bold, textLength = 10)

        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), holder.getSpans(blockA))
    }

    @Test
    fun `applyStyle - no-op for absent block`() {
        val holder = BlockSpanStates()
        holder.applyStyle(blockA, rangeStart = 0, rangeEnd = 5, SpanStyle.Bold, textLength = 10)
        assertNull(holder.get(blockA))
    }

    @Test
    fun `removeStyle - clips existing span`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 10, SpanStyle.Bold)))

        holder.removeStyle(blockA, rangeStart = 3, rangeEnd = 7, SpanStyle.Bold)

        assertEquals(
            listOf(
                TextSpan(0, 3, SpanStyle.Bold),
                TextSpan(7, 10, SpanStyle.Bold),
            ),
            holder.getSpans(blockA)
        )
    }

    @Test
    fun `removeStyle - no-op for absent block`() {
        val holder = BlockSpanStates()
        holder.removeStyle(blockA, rangeStart = 0, rangeEnd = 5, SpanStyle.Bold)
        assertNull(holder.get(blockA))
    }

    @Test
    fun `toggleStyle - applies when absent`() {
        val holder = BlockSpanStates()
        holder.create(blockA)

        holder.toggleStyle(blockA, rangeStart = 0, rangeEnd = 5, SpanStyle.Bold, textLength = 10)

        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), holder.getSpans(blockA))
    }

    @Test
    fun `toggleStyle - removes when fully active`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 10, SpanStyle.Bold)))

        holder.toggleStyle(blockA, rangeStart = 0, rangeEnd = 10, SpanStyle.Bold, textLength = 10)

        assertEquals(emptyList(), holder.getSpans(blockA))
    }

    @Test
    fun `toggleStyle - no-op for absent block`() {
        val holder = BlockSpanStates()
        holder.toggleStyle(blockA, rangeStart = 0, rangeEnd = 5, SpanStyle.Bold, textLength = 10)
        assertNull(holder.get(blockA))
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Queries                                                         ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `queryStyleStatus - returns Absent for missing block`() {
        val holder = BlockSpanStates()
        assertEquals(
            StyleStatus.Absent,
            holder.queryStyleStatus(blockA, rangeStart = 0, rangeEnd = 5, SpanStyle.Bold)
        )
    }

    @Test
    fun `queryStyleStatus - delegates correctly`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 10, SpanStyle.Bold)))

        assertEquals(
            StyleStatus.FullyActive,
            holder.queryStyleStatus(blockA, rangeStart = 2, rangeEnd = 8, SpanStyle.Bold)
        )
        assertEquals(
            StyleStatus.Partial,
            holder.queryStyleStatus(blockA, rangeStart = 5, rangeEnd = 15, SpanStyle.Bold)
        )
        assertEquals(
            StyleStatus.Absent,
            holder.queryStyleStatus(blockA, rangeStart = 0, rangeEnd = 5, SpanStyle.Italic)
        )
    }

    @Test
    fun `activeStylesAt - returns empty set for missing block`() {
        val holder = BlockSpanStates()
        assertEquals(emptySet(), holder.activeStylesAt(blockA, position = 3))
    }

    @Test
    fun `activeStylesAt - delegates correctly`() {
        val holder = BlockSpanStates()
        holder.create(
            blockA,
            listOf(
                TextSpan(0, 5, SpanStyle.Bold),
                TextSpan(3, 8, SpanStyle.Italic),
            )
        )

        assertEquals(setOf(SpanStyle.Bold), holder.activeStylesAt(blockA, position = 1))
        assertEquals(setOf(SpanStyle.Bold, SpanStyle.Italic), holder.activeStylesAt(blockA, position = 4))
        assertEquals(setOf(SpanStyle.Italic), holder.activeStylesAt(blockA, position = 6))
        assertEquals(emptySet(), holder.activeStylesAt(blockA, position = 9))
    }

    // ╔══════════════════════════════════════════════════════════════════╗
    // ║  Pending Styles                                                  ║
    // ╚══════════════════════════════════════════════════════════════════╝

    @Test
    fun `getPendingStyles - returns null when not set`() {
        val holder = BlockSpanStates()
        assertNull(holder.getPendingStyles(blockA))
    }

    @Test
    fun `setPendingStyles and getPendingStyles - round-trip`() {
        val holder = BlockSpanStates()
        val styles = setOf(SpanStyle.Bold, SpanStyle.Italic)
        holder.setPendingStyles(blockA, styles)
        assertEquals(styles, holder.getPendingStyles(blockA))
    }

    @Test
    fun `setPendingStyles - defensive copy protects against source set mutation`() {
        val holder = BlockSpanStates()
        val source = linkedSetOf<SpanStyle>(SpanStyle.Bold)
        holder.setPendingStyles(blockA, source)

        source += SpanStyle.Italic

        assertEquals(setOf(SpanStyle.Bold), holder.getPendingStyles(blockA))
    }

    @Test
    fun `clearPendingStyles - removes pending`() {
        val holder = BlockSpanStates()
        holder.setPendingStyles(blockA, setOf(SpanStyle.Bold))
        holder.clearPendingStyles(blockA)
        assertNull(holder.getPendingStyles(blockA))
    }

    @Test
    fun `resolveStylesForInsertion - returns pending and clears them`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 5, SpanStyle.Bold)))
        val pending = setOf(SpanStyle.Italic)
        holder.setPendingStyles(blockA, pending)

        val resolved = holder.resolveStylesForInsertion(blockA, position = 3)

        assertEquals(pending, resolved)
        assertNull(holder.getPendingStyles(blockA)) // cleared after resolve
    }

    @Test
    fun `resolveStylesForInsertion - falls back to position minus 1`() {
        val holder = BlockSpanStates()
        holder.create(
            blockA,
            listOf(
                TextSpan(0, 5, SpanStyle.Bold),
                TextSpan(3, 8, SpanStyle.Italic),
            )
        )

        // No pending styles, position 4 → inherits from position 3
        val resolved = holder.resolveStylesForInsertion(blockA, position = 4)
        assertEquals(setOf(SpanStyle.Bold, SpanStyle.Italic), resolved)
    }

    @Test
    fun `resolveStylesForInsertion - position 0 returns empty set`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 5, SpanStyle.Bold)))

        val resolved = holder.resolveStylesForInsertion(blockA, position = 0)
        assertEquals(emptySet(), resolved)
    }

    @Test
    fun `resolveStylesForInsertion - pending overrides natural fallback`() {
        val holder = BlockSpanStates()
        holder.create(blockA, listOf(TextSpan(0, 5, SpanStyle.Bold)))
        // Set empty pending styles (user explicitly cleared formatting)
        holder.setPendingStyles(blockA, emptySet())

        val resolved = holder.resolveStylesForInsertion(blockA, position = 3)

        // Should use pending (empty) not the natural Bold
        assertEquals(emptySet(), resolved)
    }

    @Test
    fun `resolveStylesForInsertion - absent block returns empty set`() {
        val holder = BlockSpanStates()
        val resolved = holder.resolveStylesForInsertion(blockA, position = 5)
        assertEquals(emptySet(), resolved)
    }

    @Test
    fun `resolveStylesForInsertion - end of styled range inherits style`() {
        val holder = BlockSpanStates()
        // "Hello" with Bold [0,5) — typing at position 5 should inherit Bold from position 4
        holder.create(blockA, listOf(TextSpan(0, 5, SpanStyle.Bold)))

        val resolved = holder.resolveStylesForInsertion(blockA, position = 5)
        assertEquals(setOf(SpanStyle.Bold), resolved)
    }

    @Test
    fun `resolveStylesForInsertion - after styled range does not inherit`() {
        val holder = BlockSpanStates()
        // Bold [0,3) then gap, position 5 → inherits from position 4 which has nothing
        holder.create(blockA, listOf(TextSpan(0, 3, SpanStyle.Bold)))

        val resolved = holder.resolveStylesForInsertion(blockA, position = 5)
        assertEquals(emptySet(), resolved)
    }
}
