package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.LinkStateCalculator
import io.github.linreal.cascade.editor.richtext.LinkTarget
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkStateCalculatorTest {

    private val blockId = BlockId("b1")
    private val link = SpanStyle.Link("https://example.com")

    @Test
    fun `collapsed cursor strictly inside link exposes existing link state`() {
        val state = compute(
            text = "Hello link",
            selection = TextRange(8),
            spans = listOf(TextSpan(6, 10, link)),
        )

        assertTrue(state.canLink)
        assertEquals(LinkTarget(blockId, 8, 8), state.target)
        assertEquals("https://example.com", state.existingUrl)
        assertEquals(LinkTarget(blockId, 6, 10), state.existingLinkRange)
        assertEquals("link", state.existingLinkText)
        assertTrue(state.isInsideLink)
        assertTrue(state.intersectsLink)
    }

    @Test
    fun `collapsed cursor at link end boundary is outside link`() {
        val state = compute(
            text = "Hello link",
            selection = TextRange(10),
            spans = listOf(TextSpan(6, 10, link)),
        )

        assertTrue(state.canLink)
        assertNull(state.existingUrl)
        assertNull(state.existingLinkRange)
        assertFalse(state.isInsideLink)
        assertFalse(state.intersectsLink)
    }

    @Test
    fun `selection fully inside one link exposes existing URL`() {
        val state = compute(
            text = "Hello link",
            selection = TextRange(7, 9),
            spans = listOf(TextSpan(6, 10, link)),
        )

        assertEquals(LinkTarget(blockId, 7, 9), state.target)
        assertEquals("https://example.com", state.existingUrl)
        assertEquals(LinkTarget(blockId, 6, 10), state.existingLinkRange)
        assertEquals("in", state.targetText)
        assertFalse(state.isInsideLink)
        assertTrue(state.intersectsLink)
    }

    @Test
    fun `selection crossing mixed linked and unlinked text exposes intersection without one URL`() {
        val state = compute(
            text = "Hello link",
            selection = TextRange(0, 8),
            spans = listOf(TextSpan(6, 10, link)),
        )

        assertEquals(LinkTarget(blockId, 0, 8), state.target)
        assertNull(state.existingUrl)
        assertNull(state.existingLinkRange)
        assertTrue(state.intersectsLink)
        assertFalse(state.isInsideLink)
    }

    @Test
    fun `selection crossing multiple link URLs exposes intersection without one URL`() {
        val state = compute(
            text = "one two",
            selection = TextRange(0, 7),
            spans = listOf(
                TextSpan(0, 3, SpanStyle.Link("https://one.example")),
                TextSpan(4, 7, SpanStyle.Link("https://two.example")),
            ),
        )

        assertNull(state.existingUrl)
        assertNull(state.existingLinkRange)
        assertTrue(state.intersectsLink)
    }

    @Test
    fun `block selection disables link state`() {
        val state = compute(
            text = "Hello link",
            selection = TextRange(8),
            spans = listOf(TextSpan(6, 10, link)),
            hasBlockSelection = true,
        )

        assertFalse(state.canLink)
        assertNull(state.target)
        assertNull(state.existingUrl)
        assertFalse(state.intersectsLink)
    }

    @Test
    fun `Code block disables link state`() {
        val state = compute(
            text = "println(x)",
            selection = TextRange(0, 7),
            spans = emptyList(),
            focusedBlockType = BlockType.Code,
        )

        assertFalse(state.canLink)
        assertNull(state.target)
        assertNull(state.existingUrl)
        assertFalse(state.intersectsLink)
        assertEquals(blockId, state.focusedBlockId)
    }

    @Test
    fun `drag state disables link state`() {
        val state = compute(
            text = "Hello link",
            selection = TextRange(8),
            spans = listOf(TextSpan(6, 10, link)),
            isDragging = true,
        )

        assertFalse(state.canLink)
        assertNull(state.target)
        assertEquals(LinkState.Empty, state.copy(focusedBlockId = null))
    }

    @Test
    fun `read-only policy disables link mutation while preserving existing link metadata`() {
        val editable = compute(
            text = "Hello link",
            selection = TextRange(8),
            spans = listOf(TextSpan(6, 10, link)),
        )
        val readOnly = compute(
            text = "Hello link",
            selection = TextRange(8),
            spans = listOf(TextSpan(6, 10, link)),
            policy = EditorInteractionPolicy.ReadOnly,
        )

        assertTrue(editable.canLink)
        assertFalse(readOnly.canLink)
        assertEquals(editable.focusedBlockId, readOnly.focusedBlockId)
        assertEquals(editable.target, readOnly.target)
        assertEquals(editable.targetText, readOnly.targetText)
        assertEquals(editable.existingUrl, readOnly.existingUrl)
        assertEquals(editable.existingLinkRange, readOnly.existingLinkRange)
        assertEquals(editable.existingLinkText, readOnly.existingLinkText)
        assertEquals(editable.isInsideLink, readOnly.isInsideLink)
        assertEquals(editable.intersectsLink, readOnly.intersectsLink)
    }

    @Test
    fun `read-only policy disables canLink for otherwise linkable selection`() {
        val state = compute(
            text = "Hello link",
            selection = TextRange(0, 5),
            spans = emptyList(),
            policy = EditorInteractionPolicy.ReadOnly,
        )

        assertFalse(state.canLink)
        assertEquals(LinkTarget(blockId, 0, 5), state.target)
        assertEquals("Hello", state.targetText)
    }

    private fun compute(
        text: String,
        selection: TextRange,
        spans: List<TextSpan>,
        hasBlockSelection: Boolean = false,
        isDragging: Boolean = false,
        focusedBlockId: BlockId? = blockId,
        focusedBlockType: BlockType? = BlockType.Paragraph,
        policy: EditorInteractionPolicy = EditorInteractionPolicy.Editable,
    ): LinkState {
        return LinkStateCalculator.compute(
            focusedBlockId = focusedBlockId,
            focusedBlockType = focusedBlockType,
            hasBlockSelection = hasBlockSelection,
            isDragging = isDragging,
            visibleText = text,
            visibleSelectionStart = selection.start,
            visibleSelectionEnd = selection.end,
            spans = spans,
            policy = policy,
        )
    }
}
