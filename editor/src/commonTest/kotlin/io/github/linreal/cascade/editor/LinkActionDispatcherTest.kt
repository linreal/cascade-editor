package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.LinkActionDispatcher
import io.github.linreal.cascade.editor.richtext.LinkTarget
import io.github.linreal.cascade.editor.richtext.LinkValidationError
import io.github.linreal.cascade.editor.richtext.LinkValidationResult
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LinkActionDispatcherTest {

    private val blockId = BlockId("b1")
    private val textStates = BlockTextStates()
    private val spanStates = BlockSpanStates()
    private val dispatchedActions = mutableListOf<EditorAction>()
    private val dispatcher = LinkActionDispatcher(
        dispatchFn = { dispatchedActions.add(it) },
        textStates = textStates,
        spanStates = spanStates,
    )

    @Test
    fun `applyLink links selected text without replacing visible title`() {
        setupBlock("link")

        val result = dispatcher.applyLink(
            target = LinkTarget(blockId, 0, 4),
            url = "example.com",
        )

        assertIs<LinkValidationResult.Valid>(result)
        assertEquals("https://example.com", result.normalizedUrl)
        assertEquals("link", textStates.getVisibleText(blockId))
        assertEquals(
            listOf(TextSpan(0, 4, SpanStyle.Link("https://example.com"))),
            spanStates.getSpans(blockId),
        )
        assertSyncedContent(
            text = "link",
            spans = listOf(TextSpan(0, 4, SpanStyle.Link("https://example.com"))),
        )
    }

    @Test
    fun `applyLink inserts normalized URL as title at collapsed cursor when title is blank`() {
        setupBlock("ab")

        dispatcher.applyLink(
            target = LinkTarget(blockId, 2, 2),
            url = "example.com",
            title = " ",
        )

        val expectedText = "abhttps://example.com"
        assertEquals(expectedText, textStates.getVisibleText(blockId))
        assertEquals(
            listOf(TextSpan(2, expectedText.length, SpanStyle.Link("https://example.com"))),
            spanStates.getSpans(blockId),
        )
        assertSyncedContent(
            text = expectedText,
            spans = listOf(TextSpan(2, expectedText.length, SpanStyle.Link("https://example.com"))),
        )
    }

    @Test
    fun `applyLink replaces selected text with non-blank title`() {
        setupBlock("foo")

        dispatcher.applyLink(
            target = LinkTarget(blockId, 0, 3),
            url = "https://example.com",
            title = "bar",
        )

        assertEquals("bar", textStates.getVisibleText(blockId))
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Link("https://example.com"))),
            spanStates.getSpans(blockId),
        )
        assertSyncedContent(
            text = "bar",
            spans = listOf(TextSpan(0, 3, SpanStyle.Link("https://example.com"))),
        )
    }

    @Test
    fun `applyLink changes URL without changing existing title`() {
        setupBlock(
            text = "foo",
            spans = listOf(TextSpan(0, 3, SpanStyle.Link("https://old.example"))),
        )

        dispatcher.applyLink(
            target = LinkTarget(blockId, 0, 3),
            url = "new.example",
        )

        assertEquals("foo", textStates.getVisibleText(blockId))
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Link("https://new.example"))),
            spanStates.getSpans(blockId),
        )
    }

    @Test
    fun `applyLink changes existing title and URL in one mutation`() {
        setupBlock(
            text = "old",
            spans = listOf(TextSpan(0, 3, SpanStyle.Link("https://old.example"))),
        )

        dispatcher.applyLink(
            target = LinkTarget(blockId, 0, 3),
            url = "new.example",
            title = "new",
        )

        assertEquals("new", textStates.getVisibleText(blockId))
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Link("https://new.example"))),
            spanStates.getSpans(blockId),
        )
    }

    @Test
    fun `removeLink removes only link spans and preserves visible text plus non-link spans`() {
        setupBlock(
            text = "hello",
            spans = listOf(
                TextSpan(0, 5, SpanStyle.Link("https://example.com")),
                TextSpan(1, 4, SpanStyle.Bold),
            ),
        )

        dispatcher.removeLink(LinkTarget(blockId, 0, 5))

        assertEquals("hello", textStates.getVisibleText(blockId))
        assertEquals(listOf(TextSpan(1, 4, SpanStyle.Bold)), spanStates.getSpans(blockId))
        assertSyncedContent(
            text = "hello",
            spans = listOf(TextSpan(1, 4, SpanStyle.Bold)),
        )
    }

    @Test
    fun `applyLink does not mutate document when URL validation fails`() {
        setupBlock("foo")

        val result = dispatcher.applyLink(
            target = LinkTarget(blockId, 0, 3),
            url = "   ",
        )

        assertEquals(LinkValidationResult.Invalid(LinkValidationError.Blank), result)
        assertEquals("foo", textStates.getVisibleText(blockId))
        assertTrue(spanStates.getSpans(blockId).isEmpty())
        assertTrue(dispatchedActions.isEmpty())
    }

    @Test
    fun `applyLink clamps stale target range to current text`() {
        setupBlock("abc")

        dispatcher.applyLink(
            target = LinkTarget(blockId, 1, 100),
            url = "example.com",
        )

        assertEquals("abc", textStates.getVisibleText(blockId))
        assertEquals(
            listOf(TextSpan(1, 3, SpanStyle.Link("https://example.com"))),
            spanStates.getSpans(blockId),
        )
    }

    @Test
    fun `applyLink no-ops mutation when target block opts out of spans but still validates URL`() {
        val (codeDispatcher, stateHolder) = newCodeBlockFixture()
        setupBlock("println(x)")

        val result = codeDispatcher.applyLink(
            target = LinkTarget(blockId, 0, 7),
            url = "example.com",
        )

        assertIs<LinkValidationResult.Valid>(result)
        assertEquals("https://example.com", result.normalizedUrl)
        // Runtime text/spans untouched, no UpdateBlockContent dispatched.
        assertEquals("println(x)", textStates.getVisibleText(blockId))
        assertTrue(spanStates.getSpans(blockId).isEmpty())
        assertTrue(dispatchedActions.isEmpty())
        // No history entry captured for a no-op.
        assertEquals(false, stateHolder.canUndo)
    }

    @Test
    fun `removeLink no-ops when target block opts out of spans`() {
        val (codeDispatcher, stateHolder) = newCodeBlockFixture()
        // Seed a stale link span on the runtime spans for defensive coverage.
        setupBlock(
            text = "println(x)",
            spans = listOf(TextSpan(0, 7, SpanStyle.Link("https://example.com"))),
        )

        codeDispatcher.removeLink(LinkTarget(blockId, 0, 7))

        assertEquals("println(x)", textStates.getVisibleText(blockId))
        assertEquals(
            listOf(TextSpan(0, 7, SpanStyle.Link("https://example.com"))),
            spanStates.getSpans(blockId),
        )
        assertTrue(dispatchedActions.isEmpty())
        assertEquals(false, stateHolder.canUndo)
    }

    @Test
    fun `applyLink no-ops safely when captured block is missing`() {
        val result = dispatcher.applyLink(
            target = LinkTarget(blockId, 0, 3),
            url = "example.com",
        )

        assertIs<LinkValidationResult.Valid>(result)
        assertTrue(dispatchedActions.isEmpty())
    }

    private fun setupBlock(
        text: String,
        spans: List<TextSpan> = emptyList(),
    ) {
        textStates.getOrCreate(blockId, text)
        spanStates.getOrCreate(blockId, spans, text.length)
    }

    /**
     * Builds a fresh dispatcher wired to a state holder hosting a single Code block
     * for [blockId]. Used by spans-gating tests so the dispatcher can resolve the
     * target block's current type from the snapshot.
     */
    private fun newCodeBlockFixture(): Pair<LinkActionDispatcher, EditorStateHolder> {
        val holder = EditorStateHolder()
        holder.setState(
            EditorState.withBlocks(
                listOf(Block(blockId, BlockType.Code, BlockContent.Text("println(x)")))
            ).copy(focusedBlockId = blockId)
        )
        val codeDispatcher = LinkActionDispatcher(
            dispatchFn = { dispatchedActions.add(it) },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = holder,
        )
        return codeDispatcher to holder
    }

    private fun assertSyncedContent(
        text: String,
        spans: List<TextSpan>,
    ) {
        val action = dispatchedActions.last() as UpdateBlockContent
        assertEquals(blockId, action.blockId)
        val content = action.content as BlockContent.Text
        assertEquals(text, content.text)
        assertEquals(spans, content.spans)
    }
}
