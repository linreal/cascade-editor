package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalCascadePreviewApi::class)
class CascadeDocumentPreviewStaticSemanticsTest {

    @Test
    fun `built-in preview text is static and checked todo has no toggle action`() =
        runComposeUiTest {
            setContent {
                CascadeDocumentPreview(
                    blocks = listOf(
                        Block.paragraph("Static paragraph"),
                        Block.todo("Completed task", checked = true),
                        Block.heading(level = 2, text = "Preview heading"),
                    ),
                    config = CascadeDocumentPreviewConfig.Unbounded,
                )
            }

            waitForIdle()
            onNodeWithText("Static paragraph").assertExists()
            onNodeWithText("Completed task").assertExists()
            onNodeWithText("Preview heading").assertExists()
            onAllNodes(hasSetTextAction()).assertCountEquals(0)
            onNode(isToggleable())
                .assertIsToggleable()
                .assertIsOn()
                .assertHasNoClickAction()
        }

    @Test
    fun `blocks whose content did not decode as text still occupy their preview slot`() =
        runComposeUiTest {
            // DocumentSchema decodes a block with no "content" key as
            // BlockContent.Empty for any block type, with no warning.
            val emptyContent = listOf(
                Block(BlockId("p"), BlockType.Paragraph, BlockContent.Empty),
                Block(BlockId("t"), BlockType.Todo(checked = false), BlockContent.Empty),
            )
            setContent {
                Column {
                    CascadeDocumentPreview(
                        blocks = emptyContent,
                        modifier = Modifier.width(200.dp).testTag("empty-content"),
                        config = CascadeDocumentPreviewConfig.Unbounded,
                    )
                    CascadeDocumentPreview(
                        blocks = emptyContent.map { block ->
                            block.copy(content = BlockContent.Text(""))
                        },
                        modifier = Modifier.width(200.dp).testTag("empty-text"),
                        config = CascadeDocumentPreviewConfig.Unbounded,
                    )
                }
            }

            waitForIdle()
            // One checkbox per preview: a todo must keep the checkbox the editor's
            // TodoBlockRenderer draws even when its content did not decode as text.
            onAllNodes(isToggleable()).assertCountEquals(2)
            val emptyContentHeight = onNodeWithTag("empty-content")
                .fetchSemanticsNode()
                .boundsInRoot
                .height
            val emptyTextHeight = onNodeWithTag("empty-text")
                .fetchSemanticsNode()
                .boundsInRoot
                .height
            assertEquals(
                emptyTextHeight,
                emptyContentHeight,
                "non-text content collapsed its blocks instead of rendering empty lines",
            )
        }
}
