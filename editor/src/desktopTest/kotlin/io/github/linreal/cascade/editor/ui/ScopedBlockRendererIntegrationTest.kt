package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderScope
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.registry.ScopedBlockRenderer
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.rememberEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class ScopedBlockRendererIntegrationTest {

    @Test
    fun `scoped renderer receives a working scope from CascadeEditor`() = runComposeUiTest {
        val block = paragraphBlock("paragraph", "source")
        var receivedScope: BlockRenderScope? = null
        val registry = createEditorRegistry().apply {
            registerRenderer(
                "paragraph",
                object : ScopedBlockRenderer<BlockType> {
                    @Composable
                    override fun Render(
                        block: Block,
                        isSelected: Boolean,
                        isFocused: Boolean,
                        modifier: Modifier,
                        callbacks: BlockCallbacks,
                        scope: BlockRenderScope,
                    ) {
                        receivedScope = scope
                        BasicText("scoped:${scope.readOnly}", modifier)
                    }
                },
            )
        }

        setContent {
            val holder = rememberEditorState(listOf(block))
            CascadeEditor(
                stateHolder = holder,
                registry = registry,
            )
        }

        waitForIdle()
        onNodeWithText("scoped:false").assertExists()

        val scope = assertNotNull(receivedScope)
        assertEquals(block, scope.getBlock(block.id))
    }

    @Test
    fun `legacy renderer still uses old render path`() = runComposeUiTest {
        val block = paragraphBlock("paragraph", "source")
        val registry = createEditorRegistry().apply {
            registerRenderer(
                "paragraph",
                object : BlockRenderer<BlockType> {
                    @Composable
                    override fun Render(
                        block: Block,
                        isSelected: Boolean,
                        isFocused: Boolean,
                        modifier: Modifier,
                        callbacks: BlockCallbacks,
                    ) {
                        BasicText("legacy", modifier)
                    }
                },
            )
        }

        setContent {
            val holder = rememberEditorState(listOf(block))
            CascadeEditor(
                stateHolder = holder,
                registry = registry,
            )
        }

        waitForIdle()
        onNodeWithText("legacy").assertExists()
    }

    @Test
    fun `scoped renderer sees readOnly config`() = runComposeUiTest {
        val block = paragraphBlock("paragraph", "source")
        var receivedReadOnly: Boolean? = null
        var receivedCanUpdateBlock: Boolean? = null
        val registry = createEditorRegistry().apply {
            registerRenderer(
                "paragraph",
                object : ScopedBlockRenderer<BlockType> {
                    @Composable
                    override fun Render(
                        block: Block,
                        isSelected: Boolean,
                        isFocused: Boolean,
                        modifier: Modifier,
                        callbacks: BlockCallbacks,
                        scope: BlockRenderScope,
                    ) {
                        receivedReadOnly = scope.readOnly
                        receivedCanUpdateBlock = scope.canUpdateBlock
                        BasicText("readOnly:${scope.readOnly}", modifier)
                    }
                },
            )
        }

        setContent {
            val holder = rememberEditorState(listOf(block))
            CascadeEditor(
                stateHolder = holder,
                registry = registry,
                config = CascadeEditorConfig(readOnly = true),
            )
        }

        waitForIdle()
        onNodeWithText("readOnly:true").assertExists()
        assertEquals(true, receivedReadOnly)
        assertEquals(false, receivedCanUpdateBlock)
    }

    @Test
    fun `drag preview uses scoped render path`() = runComposeUiTest {
        val block = paragraphBlock("paragraph", "source")
        val holder = EditorStateHolder(EditorState.withBlocks(listOf(block)))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        holder.bindHistoryRuntime(textStates, spanStates)
        val scope = DefaultBlockRenderScope(
            stateHolder = holder,
            configProvider = { CascadeEditorConfig.Default },
            policyProvider = { EditorInteractionPolicy.Editable },
        )
        val callbacks = defaultCallbacks(holder, textStates, spanStates)
        val registry = createEditorRegistry().apply {
            registerRenderer(
                "paragraph",
                object : ScopedBlockRenderer<BlockType> {
                    @Composable
                    override fun Render(
                        block: Block,
                        isSelected: Boolean,
                        isFocused: Boolean,
                        modifier: Modifier,
                        callbacks: BlockCallbacks,
                        scope: BlockRenderScope,
                    ) {
                        BasicText("preview:${scope.getBlock(block.id)?.id?.value}", modifier)
                    }
                },
            )
        }

        setContent {
            DragPreview(
                block = block,
                dragOffsetY = { 32f },
                initialTouchOffsetY = 8f,
                futureRootIndentationLevel = 0,
                payloadBlockCount = 1,
                registry = registry,
                callbacks = callbacks,
                scope = scope,
            )
        }

        waitForIdle()
        onNodeWithText("preview:paragraph").assertExists()
    }

    private fun defaultCallbacks(
        holder: EditorStateHolder,
        textStates: BlockTextStates,
        spanStates: BlockSpanStates,
    ): BlockCallbacks {
        return io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks(
            dispatchFn = { action -> holder.dispatch(action) },
            stateProvider = { holder.state },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = holder,
        )
    }
}

private fun paragraphBlock(
    id: String,
    text: String,
): Block = Block.paragraph(text).copy(id = BlockId(id))
