package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.FormattingStateCalculator
import io.github.linreal.cascade.editor.richtext.StyleStatus
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormattingStateCalculatorTest {

    private val blockId = BlockId("block-1")
    private val defaultTracked = listOf(
        SpanStyle.Bold,
        SpanStyle.Italic,
        SpanStyle.Underline,
    )

    private fun compute(
        focusedBlockId: BlockId? = blockId,
        focusedBlockType: BlockType? = BlockType.Paragraph,
        hasBlockSelection: Boolean = false,
        isDragging: Boolean = false,
        selStart: Int = 0,
        selEnd: Int = selStart,
        spans: List<TextSpan> = emptyList(),
        pendingStyles: Set<SpanStyle>? = null,
        trackedStyles: List<SpanStyle> = defaultTracked,
        policy: EditorInteractionPolicy = EditorInteractionPolicy.Editable,
    ) = FormattingStateCalculator.compute(
        focusedBlockId = focusedBlockId,
        focusedBlockType = focusedBlockType,
        hasBlockSelection = hasBlockSelection,
        isDragging = isDragging,
        visibleSelectionStart = selStart,
        visibleSelectionEnd = selEnd,
        spans = spans,
        pendingStyles = pendingStyles,
        trackedStyles = trackedStyles,
        policy = policy,
    )

 // canFormat disabled cases

    @Test
    fun `no focus returns canFormat false`() {
        val result = compute(focusedBlockId = null, focusedBlockType = null)
        assertFalse(result.canFormat)
        assertTrue(result.styles.isEmpty())
    }

    @Test
    fun `non-text block returns canFormat false`() {
        val result = compute(focusedBlockType = BlockType.Divider)
        assertFalse(result.canFormat)
    }

    @Test
    fun `Code block (text-supporting but spans-incapable) returns canFormat false`() {
        val result = compute(
            focusedBlockType = BlockType.Code,
            selStart = 0,
            selEnd = 5,
            spans = emptyList(),
        )
        assertFalse(result.canFormat)
        assertTrue(result.styles.isEmpty())
    }

    @Test
    fun `block selection active returns canFormat false`() {
        val result = compute(hasBlockSelection = true)
        assertFalse(result.canFormat)
    }

    @Test
    fun `dragging returns canFormat false`() {
        val result = compute(isDragging = true)
        assertFalse(result.canFormat)
    }

    @Test
    fun `read-only policy returns canFormat false for spans-supporting focused block`() {
        val result = compute(
            focusedBlockType = BlockType.Paragraph,
            selStart = 0,
            selEnd = 5,
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
            policy = EditorInteractionPolicy.ReadOnly,
        )

        assertFalse(result.canFormat)
        assertTrue(result.styles.isEmpty())
        assertEquals(blockId, result.focusedBlockId)
        assertFalse(result.selectionCollapsed)
    }

    @Test
    fun `Paragraph returns canFormat true`() {
        val result = compute(focusedBlockType = BlockType.Paragraph)
        assertTrue(result.canFormat)
    }

    @Test
    fun `Heading returns canFormat true`() {
        val result = compute(focusedBlockType = BlockType.Heading(1))
        assertTrue(result.canFormat)
    }

    @Test
    fun `Todo returns canFormat true`() {
        val result = compute(focusedBlockType = BlockType.Todo())
        assertTrue(result.canFormat)
    }

 // Collapsed caret + pending styles

    @Test
    fun `collapsed caret with non-empty pending styles marks them FullyActive`() {
        val result = compute(
            selStart = 3, selEnd = 3,
            spans = emptyList(),
            pendingStyles = setOf(SpanStyle.Bold),
        )
        assertEquals(StyleStatus.FullyActive, result.styleStatusOf(SpanStyle.Bold))
        assertEquals(StyleStatus.Absent, result.styleStatusOf(SpanStyle.Italic))
        assertEquals(StyleStatus.Absent, result.styleStatusOf(SpanStyle.Underline))
    }

    @Test
    fun `collapsed caret with empty pending set marks all Absent`() {
        // Empty set means user explicitly cleared all styles
        val result = compute(
            selStart = 3, selEnd = 3,
            spans = listOf(TextSpan(0, 10, SpanStyle.Bold)),
            pendingStyles = emptySet(),
        )
        assertEquals(StyleStatus.Absent, result.styleStatusOf(SpanStyle.Bold))
        assertEquals(StyleStatus.Absent, result.styleStatusOf(SpanStyle.Italic))
    }

    @Test
    fun `pending styles override position-based continuation`() {
        // Bold spans cover the range, but pending says only Italic
        val result = compute(
            selStart = 5, selEnd = 5,
            spans = listOf(TextSpan(0, 10, SpanStyle.Bold)),
            pendingStyles = setOf(SpanStyle.Italic),
        )
        assertEquals(StyleStatus.Absent, result.styleStatusOf(SpanStyle.Bold))
        assertEquals(StyleStatus.FullyActive, result.styleStatusOf(SpanStyle.Italic))
    }

 // Collapsed caret fallback to position - 1

    @Test
    fun `collapsed caret at position 0 returns all Absent`() {
        val result = compute(
            selStart = 0, selEnd = 0,
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        // position - 1 = -1, so no inheritance
        assertEquals(StyleStatus.Absent, result.styleStatusOf(SpanStyle.Bold))
    }

    @Test
    fun `cursor at end of bold text shows Bold FullyActive`() {
        // "Hello" with Bold [0,5), cursor at 5 → position-1 = 4, inside bold
        val result = compute(
            selStart = 5, selEnd = 5,
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        assertEquals(StyleStatus.FullyActive, result.styleStatusOf(SpanStyle.Bold))
    }

    @Test
    fun `cursor in middle of bold text shows Bold FullyActive`() {
        val result = compute(
            selStart = 3, selEnd = 3,
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        assertEquals(StyleStatus.FullyActive, result.styleStatusOf(SpanStyle.Bold))
    }

    @Test
    fun `cursor after bold region shows Bold Absent`() {
        // Bold [0,3), cursor at 5 → position-1 = 4, outside bold
        val result = compute(
            selStart = 5, selEnd = 5,
            spans = listOf(TextSpan(0, 3, SpanStyle.Bold)),
        )
        assertEquals(StyleStatus.Absent, result.styleStatusOf(SpanStyle.Bold))
    }

    @Test
    fun `cursor in multi-styled region shows all active`() {
        val result = compute(
            selStart = 3, selEnd = 3,
            spans = listOf(
                TextSpan(0, 5, SpanStyle.Bold),
                TextSpan(0, 5, SpanStyle.Italic),
            ),
        )
        assertEquals(StyleStatus.FullyActive, result.styleStatusOf(SpanStyle.Bold))
        assertEquals(StyleStatus.FullyActive, result.styleStatusOf(SpanStyle.Italic))
        assertEquals(StyleStatus.Absent, result.styleStatusOf(SpanStyle.Underline))
    }

 // Ranged selection

    @Test
    fun `ranged selection full coverage returns FullyActive`() {
        val result = compute(
            selStart = 0, selEnd = 5,
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        assertEquals(StyleStatus.FullyActive, result.styleStatusOf(SpanStyle.Bold))
    }

    @Test
    fun `ranged selection partial coverage returns Partial`() {
        // Bold [0,3), selection [0,5) → partial
        val result = compute(
            selStart = 0, selEnd = 5,
            spans = listOf(TextSpan(0, 3, SpanStyle.Bold)),
        )
        assertEquals(StyleStatus.Partial, result.styleStatusOf(SpanStyle.Bold))
    }

    @Test
    fun `ranged selection no coverage returns Absent`() {
        val result = compute(
            selStart = 0, selEnd = 5,
            spans = listOf(TextSpan(6, 10, SpanStyle.Bold)),
        )
        assertEquals(StyleStatus.Absent, result.styleStatusOf(SpanStyle.Bold))
    }

    @Test
    fun `ranged selection ignores pending styles`() {
        // Pending says Bold, but range has no bold spans → status from query (Absent)
        val result = compute(
            selStart = 0, selEnd = 5,
            spans = emptyList(),
            pendingStyles = setOf(SpanStyle.Bold),
        )
        assertEquals(StyleStatus.Absent, result.styleStatusOf(SpanStyle.Bold))
    }

 // Reversed selection

    @Test
    fun `reversed selection bounds are normalized`() {
        // start=5, end=0 → normalized to [0,5)
        val result = compute(
            selStart = 5, selEnd = 0,
            spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        assertEquals(StyleStatus.FullyActive, result.styleStatusOf(SpanStyle.Bold))
        assertFalse(result.selectionCollapsed)
    }

 // Output metadata

    @Test
    fun `collapsed selection flag is true for same start and end`() {
        val result = compute(selStart = 3, selEnd = 3)
        assertTrue(result.selectionCollapsed)
    }

    @Test
    fun `collapsed selection flag is false for ranged selection`() {
        val result = compute(selStart = 0, selEnd = 5)
        assertFalse(result.selectionCollapsed)
    }

    @Test
    fun `focusedBlockId is passed through`() {
        val id = BlockId("test-id")
        val result = compute(focusedBlockId = id)
        assertEquals(id, result.focusedBlockId)
    }

    @Test
    fun `focusedBlockId null when no focus`() {
        val result = compute(focusedBlockId = null, focusedBlockType = null)
        assertEquals(null, result.focusedBlockId)
    }
}
