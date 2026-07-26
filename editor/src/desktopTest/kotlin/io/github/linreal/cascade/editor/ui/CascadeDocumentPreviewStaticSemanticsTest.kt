package io.github.linreal.cascade.editor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.linreal.cascade.editor.core.Block
import kotlin.test.Test

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
}
