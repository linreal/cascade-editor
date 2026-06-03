package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.linreal.cascade.editor.CascadeError
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.state.rememberEditorState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runtime gate for the per-block render guard and the nested-scroll embedding
 * contract. Compose forbids try/catch around composition, so this verifies the
 * phases we CAN guard — measure/draw — actually contain a throwing renderer on the real
 * Compose runtime, and documents how the editor behaves inside an unbounded-height parent.
 */
@OptIn(ExperimentalTestApi::class)
class RenderErrorBoundaryTest {

    /** Throws during the measure phase (an ordinary lambda, so the guard can catch it). */
    private object ThrowingMeasureRenderer : BlockRenderer<BlockType> {
        @Composable
        override fun Render(
            block: Block,
            isSelected: Boolean,
            isFocused: Boolean,
            modifier: Modifier,
            callbacks: BlockCallbacks,
        ) {
            Box(modifier.layout { _, _ -> error("boom in measure") })
        }
    }

    @Test
    fun throwing_renderer_is_contained_and_reported_not_crashing_host() = runComposeUiTest {
        val reported = mutableListOf<CascadeError>()
        val registry = createEditorRegistry().apply {
            registerRenderer("paragraph", ThrowingMeasureRenderer)
        }
        setContent {
            val holder = rememberEditorState(listOf(Block.paragraph("hello")))
            CascadeEditor(
                stateHolder = holder,
                registry = registry,
                config = CascadeEditorConfig(onInternalError = { reported += it }),
            )
        }
        waitForIdle()
        // Reaching here without runComposeUiTest reporting an uncaught exception means the
        // block guard contained the failure; assert it was also reported to the host hook.
        assertTrue(
            reported.any { it.context.startsWith("blockMeasure") || it.context.startsWith("blockDraw") },
            "expected a contained block render failure to be reported, got: $reported",
        )
    }

    @Test
    fun editor_in_unbounded_height_scroll_parent_pins_nested_scroll_degradation() = runComposeUiTest {
        setContent {
            val holder = rememberEditorState(listOf(Block.paragraph("a"), Block.paragraph("b")))
            LazyColumn {
                item {
                    CascadeEditor(stateHolder = holder)
                }
            }
        }
        waitForIdle()
        onNodeWithText("a", substring = true).assertExists(
            "first block should still compose under an unbounded-height parent",
        )
        onNodeWithText("b", substring = true).assertDoesNotExist()
    }
}
