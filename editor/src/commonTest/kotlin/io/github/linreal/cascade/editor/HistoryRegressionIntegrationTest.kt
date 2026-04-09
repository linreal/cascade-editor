package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.action.ConvertBlockType
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import io.github.linreal.cascade.editor.serialization.loadFromJson
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.PendingTextHistoryPush
import io.github.linreal.cascade.editor.state.TextEditHistoryTracker
import io.github.linreal.cascade.editor.state.captureCheckpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryRegressionIntegrationTest {

    @Test
    fun `alternating block text and structural entries preserve the post-structural baseline`() {
        val blockId = BlockId("b1")
        val harness = Harness(
            blockId = blockId,
            text = "ab",
            selection = TextRange(2),
            pendingStyles = setOf(SpanStyle.Underline),
        )
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates),
            nowMs = clock::now,
        )
        harness.stateHolder.registerTextHistoryTracker(blockId, tracker)

        try {
            clock.nowMs = 0L
            harness.setRuntimeText("abc", TextRange(3))
            harness.stateHolder.pushFrom(
                tracker.onUserTextCommit(
                    harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates)
                )
            )

            clock.nowMs = 100L
            harness.stateHolder.dispatchStructuralAction(
                action = ConvertBlockType(blockId, BlockType.Quote),
                textStates = harness.textStates,
                spanStates = harness.spanStates,
            )

            clock.nowMs = 200L
            harness.setRuntimeText("abcd", TextRange(4))
            harness.stateHolder.pushFrom(
                tracker.onUserTextCommit(
                    harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates)
                )
            )

            harness.assertFocusedBlock(
                expectedType = BlockType.Quote,
                expectedText = "abcd",
                expectedSelection = TextRange(4),
                expectedPendingStyles = setOf(SpanStyle.Underline),
            )

            harness.stateHolder.undo()
            harness.assertFocusedBlock(
                expectedType = BlockType.Quote,
                expectedText = "abc",
                expectedSelection = TextRange(3),
                expectedPendingStyles = setOf(SpanStyle.Underline),
            )

            harness.stateHolder.undo()
            harness.assertFocusedBlock(
                expectedType = BlockType.Paragraph,
                expectedText = "abc",
                expectedSelection = TextRange(3),
                expectedPendingStyles = setOf(SpanStyle.Underline),
            )

            harness.stateHolder.undo()
            harness.assertFocusedBlock(
                expectedType = BlockType.Paragraph,
                expectedText = "ab",
                expectedSelection = TextRange(2),
                expectedPendingStyles = setOf(SpanStyle.Underline),
            )

            assertFalse(harness.stateHolder.canUndo)
            assertTrue(harness.stateHolder.canRedo)

            harness.stateHolder.redo()
            harness.assertFocusedBlock(
                expectedType = BlockType.Paragraph,
                expectedText = "abc",
                expectedSelection = TextRange(3),
                expectedPendingStyles = setOf(SpanStyle.Underline),
            )

            harness.stateHolder.redo()
            harness.assertFocusedBlock(
                expectedType = BlockType.Quote,
                expectedText = "abc",
                expectedSelection = TextRange(3),
                expectedPendingStyles = setOf(SpanStyle.Underline),
            )

            harness.stateHolder.redo()
            harness.assertFocusedBlock(
                expectedType = BlockType.Quote,
                expectedText = "abcd",
                expectedSelection = TextRange(4),
                expectedPendingStyles = setOf(SpanStyle.Underline),
            )

            assertTrue(harness.stateHolder.canUndo)
            assertFalse(harness.stateHolder.canRedo)
        } finally {
            harness.stateHolder.unregisterTextHistoryTracker(blockId, tracker)
        }
    }

    @Test
    fun `type split undo split undo typing restores focused selection and pending styles`() {
        val blockId = BlockId("b1")
        val harness = Harness(
            blockId = blockId,
            text = "ab",
            selection = TextRange(2),
            pendingStyles = setOf(SpanStyle.Underline),
        )
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates),
        )
        harness.stateHolder.registerTextHistoryTracker(blockId, tracker)

        try {
            harness.setRuntimeText("abc", TextRange(3))
            harness.stateHolder.pushFrom(
                tracker.onUserTextCommit(
                    harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates)
                )
            )

            harness.callbacks.onEnter(blockId, cursorPosition = 3)

            assertEquals(listOf("abc", ""), harness.visibleTexts())
            assertTrue(harness.stateHolder.canUndo)

            harness.stateHolder.undo()
            harness.assertFocusedBlock(
                expectedType = BlockType.Paragraph,
                expectedText = "abc",
                expectedSelection = TextRange(3),
                expectedPendingStyles = setOf(SpanStyle.Underline),
            )

            harness.stateHolder.undo()
            harness.assertFocusedBlock(
                expectedType = BlockType.Paragraph,
                expectedText = "ab",
                expectedSelection = TextRange(2),
                expectedPendingStyles = setOf(SpanStyle.Underline),
            )

            harness.stateHolder.redo()
            harness.assertFocusedBlock(
                expectedType = BlockType.Paragraph,
                expectedText = "abc",
                expectedSelection = TextRange(3),
                expectedPendingStyles = setOf(SpanStyle.Underline),
            )
        } finally {
            harness.stateHolder.unregisterTextHistoryTracker(blockId, tracker)
        }
    }

    @Test
    fun `loadFromJson clears mixed history after a hybrid sequence`() {
        val blockId = BlockId("b1")
        val loadedId = BlockId("loaded")
        val harness = Harness(
            blockId = blockId,
            text = "ab",
            selection = TextRange(2),
        )
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates),
        )
        harness.stateHolder.registerTextHistoryTracker(blockId, tracker)

        try {
            harness.setRuntimeText("abc", TextRange(3))
            harness.stateHolder.pushFrom(
                tracker.onUserTextCommit(
                    harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates)
                )
            )
            harness.stateHolder.dispatchStructuralAction(
                action = ConvertBlockType(blockId, BlockType.Quote),
                textStates = harness.textStates,
                spanStates = harness.spanStates,
            )

            assertTrue(harness.stateHolder.canUndo)

            val json = DocumentSchema.encodeToString(
                listOf(
                    Block(
                        id = loadedId,
                        type = BlockType.Paragraph,
                        content = BlockContent.Text("loaded"),
                    )
                )
            )

            harness.stateHolder.loadFromJson(json, harness.textStates, harness.spanStates)

            assertFalse(harness.stateHolder.canUndo)
            assertFalse(harness.stateHolder.canRedo)
            assertEquals(listOf("loaded"), harness.visibleTexts())
            assertNull(harness.textStates.get(blockId))
            assertNull(harness.spanStates.get(blockId))

            harness.stateHolder.undo()
            assertEquals(listOf("loaded"), harness.visibleTexts())
        } finally {
            harness.stateHolder.unregisterTextHistoryTracker(blockId, tracker)
        }
    }

    private class Harness(
        private val blockId: BlockId,
        text: String,
        selection: TextRange,
        pendingStyles: Set<SpanStyle> = emptySet(),
    ) {
        val stateHolder = EditorStateHolder(
            EditorState.withBlocks(
                listOf(
                    Block(
                        id = blockId,
                        type = BlockType.Paragraph,
                        content = BlockContent.Text(text),
                    )
                )
            ).copy(focusedBlockId = blockId)
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val callbacks = DefaultBlockCallbacks(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            stateProvider = { stateHolder.state },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = stateHolder,
        )

        init {
            stateHolder.bindHistoryRuntime(textStates, spanStates)
            textStates.getOrCreate(blockId, text)
            textStates.setSelection(blockId, selection)
            spanStates.getOrCreate(blockId, initialSpans = emptyList(), textLength = text.length)
            if (pendingStyles.isNotEmpty()) {
                spanStates.setPendingStyles(blockId, pendingStyles)
            }
        }

        fun setRuntimeText(
            text: String,
            selection: TextRange,
        ) {
            textStates.setText(blockId, text, cursorPosition = selection.end)
            textStates.setSelection(blockId, selection)
            textStates.consumeProgrammaticCommit(blockId)
        }

        fun visibleTexts(): List<String> {
            return stateHolder.state.blocks.mapNotNull { block ->
                val content = block.content as? BlockContent.Text ?: return@mapNotNull null
                textStates.getVisibleText(block.id) ?: content.text
            }
        }

        fun assertFocusedBlock(
            expectedType: BlockType,
            expectedText: String,
            expectedSelection: TextRange,
            expectedPendingStyles: Set<SpanStyle>,
        ) {
            val block = requireNotNull(stateHolder.state.getBlock(blockId))

            assertEquals(blockId, stateHolder.state.focusedBlockId)
            assertEquals(expectedType, block.type)
            assertEquals(expectedText, textStates.getVisibleText(blockId))
            assertEquals(expectedSelection, textStates.getSelection(blockId))
            assertEquals(expectedPendingStyles, spanStates.getPendingStyles(blockId))
        }
    }

    private fun EditorStateHolder.pushFrom(pending: PendingTextHistoryPush?) {
        if (pending == null) return
        pushHistoryEntry(pending.entry, pending.policy)
    }

    private class FakeClock {
        var nowMs: Long = 0L

        fun now(): Long = nowMs
    }
}
