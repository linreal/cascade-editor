package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CascadeEditorPlaceholderTest {

    @Test
    fun `new empty document shows localized placeholder and follows live text`() =
        runComposeUiTest {
            val block = Block.paragraph().copy(id = BlockId("empty"))
            val textStates = BlockTextStates()
            val placeholder = "Commencez ici\u2026"

            setContent {
                val holder = rememberEditorState(listOf(block))
                CascadeEditor(
                    stateHolder = holder,
                    textStates = textStates,
                    strings = CascadeEditorStrings.default().copy(
                        emptyDocumentPlaceholder = placeholder,
                    ),
                    config = CascadeEditorConfig(
                        emptyDocumentPlaceholderEnabled = true,
                    ),
                    toolbar = ToolbarSlot.None,
                    modifier = Modifier.width(320.dp).height(200.dp),
                )
            }

            waitForIdle()
            onNodeWithText(placeholder).assertExists()

            runOnIdle {
                textStates.setText(block.id, "A")
            }
            onNodeWithText(placeholder).assertDoesNotExist()

            runOnIdle {
                textStates.setText(block.id, "")
            }
            onNodeWithText(placeholder).assertExists()
        }

    @Test
    fun `non-empty and trailing scaffold documents do not show placeholder`() =
        runComposeUiTest {
            setContent {
                Column {
                    CascadeEditor(
                        stateHolder = rememberEditorState(listOf(Block.paragraph("Existing"))),
                        config = CascadeEditorConfig(
                            emptyDocumentPlaceholderEnabled = true,
                        ),
                        toolbar = ToolbarSlot.None,
                        modifier = Modifier.width(320.dp).height(160.dp),
                    )
                    CascadeEditor(
                        stateHolder = rememberEditorState(listOf(Block.divider())),
                        config = CascadeEditorConfig(
                            emptyDocumentPlaceholderEnabled = true,
                        ),
                        toolbar = ToolbarSlot.None,
                        modifier = Modifier.width(320.dp).height(160.dp),
                    )
                }
            }

            waitForIdle()
            onAllNodesWithText("Start here\u2026").assertCountEquals(0)
        }

    @Test
    fun `empty heading shows placeholder when enabled`() =
        runComposeUiTest {
            setContent {
                CascadeEditor(
                    stateHolder = rememberEditorState(listOf(Block.heading(level = 1))),
                    config = CascadeEditorConfig(
                        emptyDocumentPlaceholderEnabled = true,
                    ),
                    toolbar = ToolbarSlot.None,
                    modifier = Modifier.width(320.dp).height(160.dp),
                )
            }

            waitForIdle()
            onNodeWithText("Start here\u2026").assertExists()
        }

    @Test
    fun `default-disabled and read-only editors suppress placeholder`() =
        runComposeUiTest {
            setContent {
                Column {
                    CascadeEditor(
                        stateHolder = rememberEditorState(),
                        toolbar = ToolbarSlot.None,
                        modifier = Modifier.width(320.dp).height(160.dp),
                    )
                    CascadeEditor(
                        stateHolder = rememberEditorState(),
                        toolbar = ToolbarSlot.None,
                        config = CascadeEditorConfig(
                            readOnly = true,
                            emptyDocumentPlaceholderEnabled = true,
                        ),
                        modifier = Modifier.width(320.dp).height(160.dp),
                    )
                }
            }

            waitForIdle()
            onAllNodesWithText("Start here\u2026").assertCountEquals(0)
        }
}
