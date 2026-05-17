package io.github.linreal.cascade.editor

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.LinkValidationResult
import io.github.linreal.cascade.editor.richtext.StyleStatus
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import io.github.linreal.cascade.editor.ui.CascadeEditorToolbarController
import io.github.linreal.cascade.editor.ui.RichTextToolbarConfig
import io.github.linreal.cascade.editor.ui.rememberCascadeEditorToolbarController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CascadeEditorToolbarControllerTest {

    @Test
    fun `external controller exposes formatting indentation and link surfaces`() = runTest {
        val rootId = BlockId("root")
        val childId = BlockId("child")
        val stateHolder = EditorStateHolder(
            EditorState.withBlocks(
                listOf(
                    textBlock(rootId, "Root"),
                    textBlock(childId, "Child"),
                )
            ).copy(focusedBlockId = childId)
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        initializeTextRuntime(textStates, spanStates, rootId, "Root")
        initializeTextRuntime(
            textStates = textStates,
            spanStates = spanStates,
            blockId = childId,
            text = "Child",
            selectionStart = 0,
            selectionEnd = 5,
        )
        stateHolder.bindHistoryRuntime(textStates, spanStates)

        val controller = composeToolbarController(
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
        )

        assertTrue(controller.formattingState.value.canFormat)
        assertEquals(
            StyleStatus.Absent,
            controller.formattingState.value.styleStatusOf(SpanStyle.Bold),
        )

        controller.formattingActions.toggleStyle(SpanStyle.Bold)

        assertEquals(
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
            spanStates.getSpans(childId),
        )
        assertEquals(
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
            snapshotSpans(stateHolder, childId),
        )

        assertTrue(controller.indentationState.value.canIndentForward)

        controller.indentationActions.indentForward()

        assertEquals(1, stateHolder.state.blocks[1].attributes.indentationLevel)

        val linkResult = controller.linkActions.applyLinkAtCurrentTarget("example.com", "Child")

        assertEquals(LinkValidationResult.Valid("https://example.com"), linkResult)
        assertTrue(
            spanStates.getSpans(childId).any { span ->
                span.style == SpanStyle.Link("https://example.com") &&
                    span.start == 0 &&
                    span.end == 5
            }
        )
        assertTrue(
            snapshotSpans(stateHolder, childId).any { span ->
                span.style == SpanStyle.Link("https://example.com") &&
                    span.start == 0 &&
                    span.end == 5
            }
        )
    }

    @Test
    fun `read only controller exposes disabled state and blocks mutations`() = runTest {
        val rootId = BlockId("root")
        val childId = BlockId("child")
        val stateHolder = EditorStateHolder(
            EditorState.withBlocks(
                listOf(
                    textBlock(rootId, "Root"),
                    textBlock(childId, "Child"),
                )
            ).copy(focusedBlockId = childId)
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        initializeTextRuntime(textStates, spanStates, rootId, "Root")
        initializeTextRuntime(
            textStates = textStates,
            spanStates = spanStates,
            blockId = childId,
            text = "Child",
            selectionStart = 0,
            selectionEnd = 5,
        )
        stateHolder.bindHistoryRuntime(textStates, spanStates)

        val controller = composeToolbarController(
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            config = CascadeEditorConfig(readOnly = true),
        )

        assertFalse(controller.formattingState.value.canFormat)
        assertFalse(controller.indentationState.value.canIndentForward)
        assertFalse(controller.linkState.value.canLink)
        assertNotNull(controller.linkActions.currentTarget())

        controller.formattingActions.toggleStyle(SpanStyle.Bold)
        controller.indentationActions.indentForward()
        val linkResult = controller.linkActions.applyLinkAtCurrentTarget("example.com", "Child")

        assertEquals(LinkValidationResult.Valid("https://example.com"), linkResult)
        assertTrue(spanStates.getSpans(childId).isEmpty())
        assertEquals(0, stateHolder.state.blocks[1].attributes.indentationLevel)
    }

    @Test
    fun `custom tracked styles drive formatting state`() = runTest {
        val rootId = BlockId("root")
        val childId = BlockId("child")
        val stateHolder = EditorStateHolder(
            EditorState.withBlocks(
                listOf(
                    textBlock(rootId, "Root"),
                    textBlock(childId, "Child"),
                )
            ).copy(focusedBlockId = childId)
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        initializeTextRuntime(textStates, spanStates, rootId, "Root")
        initializeTextRuntime(
            textStates = textStates,
            spanStates = spanStates,
            blockId = childId,
            text = "Child",
            selectionStart = 0,
            selectionEnd = 5,
        )
        stateHolder.bindHistoryRuntime(textStates, spanStates)

        val customStyles = listOf(SpanStyle.Bold, SpanStyle.StrikeThrough)
        val controller = composeToolbarController(
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            trackedStyles = customStyles,
        )

        assertEquals(
            customStyles.toSet(),
            controller.formattingState.value.styles.keys,
        )
    }

    @Test
    fun `default tracked styles match toolbar slot custom defaults`() = runTest {
        val rootId = BlockId("root")
        val childId = BlockId("child")
        val stateHolder = EditorStateHolder(
            EditorState.withBlocks(
                listOf(
                    textBlock(rootId, "Root"),
                    textBlock(childId, "Child"),
                )
            ).copy(focusedBlockId = childId)
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        initializeTextRuntime(textStates, spanStates, rootId, "Root")
        initializeTextRuntime(
            textStates = textStates,
            spanStates = spanStates,
            blockId = childId,
            text = "Child",
            selectionStart = 0,
            selectionEnd = 5,
        )
        stateHolder.bindHistoryRuntime(textStates, spanStates)

        val controller = composeToolbarController(
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
        )

        val expectedStyles =
            RichTextToolbarConfig.Default.buttons.map { it.style }.toSet()
        assertEquals(
            expectedStyles,
            controller.formattingState.value.styles.keys,
        )
    }

    private suspend fun composeToolbarController(
        stateHolder: EditorStateHolder,
        textStates: BlockTextStates,
        spanStates: BlockSpanStates,
        config: CascadeEditorConfig = CascadeEditorConfig.Default,
        trackedStyles: List<SpanStyle>? = null,
    ): CascadeEditorToolbarController = coroutineScope {
        var controller: CascadeEditorToolbarController? = null
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(UnitApplier(), recomposer)
        val recomposerJob = launch { recomposer.runRecomposeAndApplyChanges() }

        try {
            composition.setContent {
                controller = if (trackedStyles == null) {
                    rememberCascadeEditorToolbarController(
                        stateHolder = stateHolder,
                        textStates = textStates,
                        spanStates = spanStates,
                        config = config,
                    )
                } else {
                    rememberCascadeEditorToolbarController(
                        stateHolder = stateHolder,
                        textStates = textStates,
                        spanStates = spanStates,
                        trackedStyles = trackedStyles,
                        config = config,
                    )
                }
            }
            recomposer.awaitIdle()
            requireNotNull(controller)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposerJob.cancel()
        }
    }

    private class UnitApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    private fun textBlock(
        id: BlockId,
        text: String,
    ): Block = Block(
        id = id,
        type = BlockType.Paragraph,
        content = BlockContent.Text(text),
    )

    private fun initializeTextRuntime(
        textStates: BlockTextStates,
        spanStates: BlockSpanStates,
        blockId: BlockId,
        text: String,
        selectionStart: Int = 0,
        selectionEnd: Int = selectionStart,
    ) {
        val textFieldState = textStates.getOrCreate(blockId, text)
        textFieldState.edit {
            selection = TextRange(selectionStart + 1, selectionEnd + 1)
        }
        spanStates.getOrCreate(blockId, emptyList(), text.length)
    }

    private fun snapshotSpans(
        stateHolder: EditorStateHolder,
        blockId: BlockId,
    ): List<TextSpan> {
        val content = stateHolder.state.getBlock(blockId)?.content
        return (content as BlockContent.Text).spans
    }
}
