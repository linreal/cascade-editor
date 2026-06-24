package io.github.linreal.cascade.screens.customblocks

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderScope
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SampleTableBlockRendererTest {

    @Test
    fun `add row preserves focused cell draft before blur`() = runComposeUiTest {
        val blockId = BlockId("table")
        val initialModel = SampleTableModel(
            rows = listOf(
                listOf("Name", "Role"),
                listOf("Ada", "Engineer"),
            ),
            headerRow = true,
        )
        val blockState = mutableStateOf(initialModel.toBlock(blockId))
        val scope = TestTableRenderScope(blockState)

        setContent {
            MaterialTheme {
                createTableRenderer().Render(
                    block = blockState.value,
                    isSelected = false,
                    isFocused = false,
                    modifier = Modifier,
                    callbacks = NoOpBlockCallbacks,
                    scope = scope,
                )
            }
        }

        waitForIdle()
        onNode(hasSetTextAction() and hasText("Ada"))
            .performTextReplacement("Ada Lovelace")
        waitForIdle()

        assertEquals(
            "Ada",
            SampleTableModel.fromBlock(blockState.value).valueAt(row = 1, column = 0),
            "Typing should remain a local draft until a commit path runs.",
        )

        onNodeWithText("Add row")
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        val updatedModel = SampleTableModel.fromBlock(blockState.value)
        assertEquals("Ada Lovelace", updatedModel.valueAt(row = 1, column = 0))
        assertEquals(3, updatedModel.rowCount)
    }
}

private object NoOpBlockCallbacks : BlockCallbacks {
    override fun dispatch(action: EditorAction) = Unit
    override fun onFocus(blockId: BlockId) = Unit
    override fun onEnter(blockId: BlockId, cursorPosition: Int) = Unit
    override fun onBackspaceAtStart(blockId: BlockId) = Unit
    override fun onDeleteAtEnd(blockId: BlockId) = Unit
    override fun onClick(blockId: BlockId) = Unit
    override fun onLongClick(blockId: BlockId) = Unit
    override fun onDragStart(blockId: BlockId, touchOffsetY: Float) = Unit
    override fun onSlashCommand(
        blockId: BlockId,
        queryRange: SlashQueryRange,
        initialQuery: String,
    ) = Unit
}

private class TestTableRenderScope(
    private val blockState: MutableState<Block>,
) : BlockRenderScope {
    override val state: EditorState
        get() = EditorState.withBlocks(listOf(blockState.value))

    override val config: CascadeEditorConfig
        get() = CascadeEditorConfig.Default

    override val readOnly: Boolean
        get() = false

    override val canUpdateBlock: Boolean
        get() = true

    override val canEditBlockStructure: Boolean
        get() = true

    override val canSelectBlocks: Boolean
        get() = true

    override val canDragBlocks: Boolean
        get() = true

    override fun getBlock(blockId: BlockId): Block? {
        return blockState.value.takeIf { it.id == blockId }
    }

    override fun updateBlock(blockId: BlockId, transform: (Block) -> Block) {
        val current = getBlock(blockId) ?: return
        blockState.value = transform(current).copy(id = current.id)
    }

    override fun replaceBlock(blockId: BlockId, block: Block) {
        val current = getBlock(blockId) ?: return
        blockState.value = block.copy(id = current.id)
    }

    override fun insertBlockBefore(blockId: BlockId, block: Block) = Unit
    override fun insertBlockAfter(blockId: BlockId, block: Block) = Unit
    override fun deleteBlock(blockId: BlockId) = Unit
    override fun focusBlock(blockId: BlockId?) = Unit
}
