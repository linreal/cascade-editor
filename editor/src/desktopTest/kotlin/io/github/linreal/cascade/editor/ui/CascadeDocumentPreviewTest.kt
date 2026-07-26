package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasNoScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.CascadeError
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.registry.BlockPreviewRenderer
import io.github.linreal.cascade.editor.registry.BlockPreviewScope
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalCascadePreviewApi::class)
class CascadeDocumentPreviewTest {

    @Test
    fun `empty input and bounded ordered prefix render without a trailing block`() =
        runComposeUiTest {
            setContent {
                CascadeDocumentPreview(
                    blocks = emptyList(),
                    config = CascadeDocumentPreviewConfig.Unbounded,
                )
                CascadeDocumentPreview(
                    blocks = listOf(
                        paragraph("one", "One"),
                        paragraph("two", "Two"),
                        paragraph("three", "Three"),
                    ),
                    config = CascadeDocumentPreviewConfig.Default.copy(maxBlocks = 2),
                )
            }

            waitForIdle()
            onNodeWithText("One").assertExists()
            onNodeWithText("Two").assertExists()
            onNodeWithText("Three").assertDoesNotExist()
        }

    @Test
    fun `duplicate block IDs are safe and static root has no editor or scroll semantics`() =
        runComposeUiTest {
            val duplicateId = BlockId("duplicate")
            setContent {
                CascadeDocumentPreview(
                    blocks = listOf(
                        Block.paragraph("Duplicate").copy(id = duplicateId),
                        Block.paragraph("Duplicate").copy(id = duplicateId),
                    ),
                    config = CascadeDocumentPreviewConfig.Unbounded,
                )
            }

            waitForIdle()
            onAllNodesWithText("Duplicate").assertCountEquals(2)
            onAllNodes(hasSetTextAction()).assertCountEquals(0)
            onRoot().assert(hasNoScrollAction())
        }

    @Test
    fun `registry revision replaces a visible fallback without using editor renderers`() =
        runComposeUiTest {
            val registry = createEditorRegistry()
            val block = Block(
                id = BlockId("custom"),
                type = TestPreviewType,
                content = BlockContent.Custom(TestPreviewType.typeId),
            )
            setContent {
                CascadeDocumentPreview(
                    blocks = listOf(block),
                    registry = registry,
                    config = CascadeDocumentPreviewConfig.Unbounded,
                )
            }

            waitForIdle()
            onNodeWithText("Unsupported block type: ${TestPreviewType.typeId}").assertExists()

            runOnIdle {
                registry.registerPreviewRenderer(TestPreviewType.typeId, TestPreviewRenderer)
            }
            waitForIdle()
            onNodeWithText("Custom preview").assertExists()
            onNodeWithText("Unsupported block type: ${TestPreviewType.typeId}")
                .assertDoesNotExist()
        }

    @Test
    fun `one failing custom measure is contained and later blocks still render`() =
        runComposeUiTest {
            val reported = mutableListOf<CascadeError>()
            var measureAttempts = 0
            val hostHeight = mutableStateOf(180.dp)
            val reportError: (CascadeError) -> Unit = reported::add
            val previewConfig = mutableStateOf(
                CascadeDocumentPreviewConfig.Unbounded.copy(
                    onInternalError = reportError,
                ),
            )
            val registry = createEditorRegistry().apply {
                registerPreviewRenderer(
                    TestPreviewType.typeId,
                    ThrowingMeasurePreviewRenderer { measureAttempts++ },
                )
            }
            setContent {
                Box(modifier = Modifier.height(hostHeight.value)) {
                    CascadeDocumentPreview(
                        blocks = listOf(
                            Block(
                                id = BlockId("broken"),
                                type = TestPreviewType,
                                content = BlockContent.Custom(TestPreviewType.typeId),
                            ),
                            paragraph("after", "After failure"),
                        ),
                        registry = registry,
                        config = previewConfig.value,
                    )
                }
            }

            waitForIdle()
            onNodeWithText("After failure").assertExists()
            assertTrue(reported.any { it.context == "previewBlockMeasure:${TestPreviewType.typeId}" })

            runOnIdle { hostHeight.value = 220.dp }
            waitForIdle()
            assertTrue(measureAttempts == 1, "measure attempts: $measureAttempts")
            assertTrue(reported.size == 1, "reported failures: $reported")

            runOnIdle {
                previewConfig.value = previewConfig.value.copy(
                    maxLinesPerTextBlock = 2,
                )
                hostHeight.value = 260.dp
            }
            waitForIdle()
            val recoveredHeight = onNodeWithText("Recovered preview")
                .fetchSemanticsNode()
                .boundsInRoot
                .height
            assertTrue(recoveredHeight > 0f, "config change left renderer quarantined")
            assertTrue(measureAttempts == 1, "recovered renderer threw again")
            assertTrue(reported.size == 1, "recovery duplicated failure reporting")
        }

    @Test
    fun `custom placement failure is contained before later blocks are placed`() =
        runComposeUiTest {
            val reported = mutableListOf<CascadeError>()
            val registry = createEditorRegistry().apply {
                registerPreviewRenderer(TestPreviewType.typeId, ThrowingPlacePreviewRenderer)
            }
            setContent {
                CascadeDocumentPreview(
                    blocks = listOf(
                        Block(
                            id = BlockId("broken-place"),
                            type = TestPreviewType,
                            content = BlockContent.Custom(TestPreviewType.typeId),
                        ),
                        paragraph("after-place", "After placement failure"),
                    ),
                    registry = registry,
                    config = CascadeDocumentPreviewConfig.Unbounded.copy(
                        onInternalError = reported::add,
                    ),
                )
            }

            waitForIdle()
            onNodeWithText("After placement failure").assertExists()
            assertTrue(
                reported.single().context == "previewBlockPlace:${TestPreviewType.typeId}",
                "reported failures: $reported",
            )
        }

    @Test
    fun `draw and reporter failures are contained and reported once per block`() =
        runComposeUiTest {
            var reportAttempts = 0
            val registry = createEditorRegistry().apply {
                registerPreviewRenderer(TestPreviewType.typeId, ThrowingDrawPreviewRenderer)
            }
            setContent {
                CascadeDocumentPreview(
                    blocks = listOf(
                        Block(
                            id = BlockId("broken-draw"),
                            type = TestPreviewType,
                            content = BlockContent.Custom(TestPreviewType.typeId),
                        ),
                        paragraph("after-draw", "After draw failure"),
                    ),
                    registry = registry,
                    config = CascadeDocumentPreviewConfig.Unbounded.copy(
                        onInternalError = {
                            reportAttempts++
                            error("host reporter failure")
                        },
                    ),
                )
            }

            waitForIdle()
            onNodeWithText("After draw failure").assertExists()
            assertTrue(reportAttempts == 1, "report attempts: $reportAttempts")
        }

    @Test
    fun `valid built-in link activates host once without also activating card`() =
        runComposeUiTest {
            val opened = mutableListOf<String>()
            var cardClicks = 0
            val linkText = "Open guide"
            setContent {
                Box(modifier = Modifier.clickable { cardClicks++ }) {
                    CascadeDocumentPreview(
                        blocks = listOf(
                            Block.paragraph(
                                text = linkText,
                                spans = listOf(
                                    TextSpan(
                                        start = 0,
                                        end = linkText.length,
                                        style = SpanStyle.Link("  ../guide.md  "),
                                    ),
                                ),
                            ),
                            Block.paragraph("Plain text"),
                        ),
                        config = CascadeDocumentPreviewConfig.Unbounded,
                        onOpenLink = opened::add,
                    )
                }
            }

            waitForIdle()
            onNodeWithText("Open guide", useUnmergedTree = true)
                .performTouchInput { click(Offset(4f, center.y)) }
            waitForIdle()
            assertTrue(opened == listOf("../guide.md"), "opened targets: $opened")
            assertTrue(cardClicks == 0, "link click also activated card")

            onNodeWithText("Plain text", useUnmergedTree = true)
                .performTouchInput { click(Offset(4f, center.y)) }
            waitForIdle()
            assertTrue(cardClicks == 1, "plain text did not activate card")
        }

    @Test
    fun `missing link opener leaves styled link available to outer card click`() =
        runComposeUiTest {
            var cardClicks = 0
            val text = "Card-owned link"
            setContent {
                Box(modifier = Modifier.clickable { cardClicks++ }) {
                    CascadeDocumentPreview(
                        blocks = listOf(
                            Block.paragraph(
                                text = text,
                                spans = listOf(
                                    TextSpan(
                                        0,
                                        text.length,
                                        SpanStyle.Link("https://example.test"),
                                    ),
                                ),
                            ),
                        ),
                        config = CascadeDocumentPreviewConfig.Unbounded,
                    )
                }
            }

            waitForIdle()
            onNodeWithText(text, useUnmergedTree = true)
                .performTouchInput { click(Offset(4f, center.y)) }
            waitForIdle()
            assertTrue(cardClicks == 1, "styled link swallowed the outer card click")
        }

    @Test
    fun `links disabled keep link styling static and do not expose click action`() =
        runComposeUiTest {
            val text = "Disabled link"
            setContent {
                CascadeDocumentPreview(
                    blocks = listOf(
                        Block.paragraph(
                            text = text,
                            spans = listOf(
                                TextSpan(0, text.length, SpanStyle.Link("https://example.test")),
                            ),
                        ),
                    ),
                    config = CascadeDocumentPreviewConfig.Unbounded.copy(linksEnabled = false),
                    onOpenLink = { error("disabled link must not activate") },
                )
            }

            waitForIdle()
            onNodeWithText(text).assertHasNoClickAction()
        }

    @Test
    fun `line limits reduce measured text while selection remains non-editable`() =
        runComposeUiTest {
            val text = "One two three four five six seven eight nine ten eleven twelve"
            setContent {
                Column {
                    CascadeDocumentPreview(
                        blocks = listOf(Block.paragraph(text)),
                        modifier = Modifier
                            .width(120.dp)
                            .testTag("bounded-preview"),
                        config = CascadeDocumentPreviewConfig.Default.copy(
                            maxBlocks = null,
                            maxLinesPerTextBlock = 1,
                            textSelectionEnabled = true,
                        ),
                    )
                    CascadeDocumentPreview(
                        blocks = listOf(Block.paragraph(text)),
                        modifier = Modifier
                            .width(120.dp)
                            .testTag("unbounded-preview"),
                        config = CascadeDocumentPreviewConfig.Unbounded,
                    )
                }
            }

            waitForIdle()
            val boundedHeight = onNodeWithTag("bounded-preview")
                .fetchSemanticsNode()
                .boundsInRoot
                .height
            val unboundedHeight = onNodeWithTag("unbounded-preview")
                .fetchSemanticsNode()
                .boundsInRoot
                .height
            assertTrue(
                boundedHeight < unboundedHeight,
                "bounded=$boundedHeight, unbounded=$unboundedHeight",
            )
            onAllNodes(hasSetTextAction()).assertCountEquals(0)
        }

    @Test
    fun `text scale multiplies host font scale for built-in and custom preview text`() =
        runComposeUiTest {
            var capturedDensity: Float? = null
            var capturedFontScale: Float? = null
            val registry = createEditorRegistry().apply {
                registerPreviewRenderer(
                    TestPreviewType.typeId,
                    DensityCapturingPreviewRenderer { _, density, fontScale ->
                        capturedDensity = density
                        capturedFontScale = fontScale
                    },
                )
            }
            val customBlock = Block(
                id = BlockId("density"),
                type = TestPreviewType,
                content = BlockContent.Custom(TestPreviewType.typeId),
            )

            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2f, fontScale = 1.5f),
                ) {
                    Column {
                        CascadeDocumentPreview(
                            blocks = listOf(Block.heading(level = 1, text = "Full heading")),
                            modifier = Modifier
                                .width(240.dp)
                                .testTag("full-scale-preview"),
                            config = CascadeDocumentPreviewConfig.Unbounded,
                        )
                        CascadeDocumentPreview(
                            blocks = listOf(Block.heading(level = 1, text = "Compact heading")),
                            modifier = Modifier
                                .width(240.dp)
                                .testTag("compact-scale-preview"),
                            registry = registry,
                            config = CascadeDocumentPreviewConfig.Unbounded.copy(
                                textScale = 0.5f,
                            ),
                        )
                        CascadeDocumentPreview(
                            blocks = listOf(customBlock),
                            registry = registry,
                            config = CascadeDocumentPreviewConfig.Unbounded.copy(
                                textScale = 0.75f,
                            ),
                        )
                    }
                }
            }

            waitForIdle()
            val fullHeight = onNodeWithTag("full-scale-preview")
                .fetchSemanticsNode()
                .boundsInRoot
                .height
            val compactHeight = onNodeWithTag("compact-scale-preview")
                .fetchSemanticsNode()
                .boundsInRoot
                .height
            assertTrue(
                compactHeight < fullHeight,
                "compact=$compactHeight, full=$fullHeight",
            )
            assertTrue(capturedDensity == 2f, "captured density: $capturedDensity")
            assertTrue(capturedFontScale == 1.125f, "captured font scale: $capturedFontScale")
        }

    @Test
    fun `unrepresentable scaled font density falls back to the host values`() =
        runComposeUiTest {
            val captured = mutableMapOf<Float, Pair<Float, Float>>()
            val registry = createEditorRegistry().apply {
                registerPreviewRenderer(
                    TestPreviewType.typeId,
                    DensityCapturingPreviewRenderer { textScale, density, fontScale ->
                        captured[textScale] = density to fontScale
                    },
                )
            }
            val customBlock = Block(
                id = BlockId("extreme-density"),
                type = TestPreviewType,
                content = BlockContent.Custom(TestPreviewType.typeId),
            )

            setContent {
                Column {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = 2f, fontScale = 0.5f),
                    ) {
                        CascadeDocumentPreview(
                            blocks = listOf(customBlock),
                            registry = registry,
                            config = CascadeDocumentPreviewConfig.Unbounded.copy(
                                textScale = Float.MIN_VALUE,
                            ),
                        )
                    }
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = 3f, fontScale = 2f),
                    ) {
                        CascadeDocumentPreview(
                            blocks = listOf(customBlock),
                            registry = registry,
                            config = CascadeDocumentPreviewConfig.Unbounded.copy(
                                textScale = Float.MAX_VALUE,
                            ),
                        )
                    }
                }
            }

            waitForIdle()
            assertTrue(
                captured[Float.MIN_VALUE] == (2f to 0.5f),
                "underflow density: ${captured[Float.MIN_VALUE]}",
            )
            assertTrue(
                captured[Float.MAX_VALUE] == (3f to 2f),
                "overflow density: ${captured[Float.MAX_VALUE]}",
            )
        }

    @Test
    fun `unknown fallback preserves text and hides opaque custom payloads`() =
        runComposeUiTest {
            setContent {
                CascadeDocumentPreview(
                    blocks = listOf(
                        Block(
                            id = BlockId("unknown-text"),
                            type = UnknownBlockType("future.text", """{"type":"future.text"}"""),
                            content = BlockContent.Text("Readable future content"),
                        ),
                        Block(
                            id = BlockId("unknown-opaque"),
                            type = UnknownBlockType(
                                "future.opaque",
                                """{"type":"future.opaque"}""",
                            ),
                            content = BlockContent.Custom(
                                typeId = "future.opaque",
                                data = mapOf("private" to "payload"),
                            ),
                        ),
                    ),
                    config = CascadeDocumentPreviewConfig.Unbounded,
                )
            }

            waitForIdle()
            onNodeWithText("Readable future content").assertExists()
            onNodeWithText("Unsupported block type: future.opaque").assertExists()
            onNodeWithText("payload").assertDoesNotExist()
        }

    @Test
    fun `fixed-height previews measure safely inside an outer lazy grid`() =
        runComposeUiTest {
            val documents = List(8) { index ->
                listOf(
                    paragraph("$index-heading", "Note $index"),
                    paragraph("$index-body", "Static preview body"),
                )
            }
            setContent {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.size(600.dp),
                ) {
                    items(documents) { blocks ->
                        CascadeDocumentPreview(
                            blocks = blocks,
                            modifier = Modifier.height(180.dp),
                        )
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Note 0").assertExists()
        }

    private data object TestPreviewType : CustomBlockType {
        override val typeId: String = "test.preview"
        override val displayName: String = "Test preview"
    }

    private data object TestPreviewRenderer : BlockPreviewRenderer<TestPreviewType> {
        @Composable
        override fun RenderPreview(
            block: Block,
            modifier: Modifier,
            scope: BlockPreviewScope,
        ) {
            BasicText("Custom preview", modifier)
        }
    }

    private class DensityCapturingPreviewRenderer(
        private val onDensity: (
            textScale: Float,
            density: Float,
            fontScale: Float,
        ) -> Unit,
    ) : BlockPreviewRenderer<TestPreviewType> {
        @Composable
        override fun RenderPreview(
            block: Block,
            modifier: Modifier,
            scope: BlockPreviewScope,
        ) {
            val density = LocalDensity.current
            SideEffect {
                onDensity(scope.config.textScale, density.density, density.fontScale)
            }
            BasicText("Density preview", modifier)
        }
    }

    private class ThrowingMeasurePreviewRenderer(
        private val onMeasure: () -> Unit,
    ) :
        BlockPreviewRenderer<TestPreviewType> {
        @Composable
        override fun RenderPreview(
            block: Block,
            modifier: Modifier,
            scope: BlockPreviewScope,
        ) {
            if (scope.config.maxLinesPerTextBlock == null) {
                Box(
                    // Deliberately ignore the supplied modifier: containment belongs
                    // to the host boundary, not renderer cooperation.
                    modifier = Modifier.layout { _, _ ->
                        onMeasure()
                        error("preview measure failure")
                    },
                )
            } else {
                BasicText("Recovered preview", modifier)
            }
        }
    }

    private data object ThrowingPlacePreviewRenderer :
        BlockPreviewRenderer<TestPreviewType> {
        @Composable
        override fun RenderPreview(
            block: Block,
            modifier: Modifier,
            scope: BlockPreviewScope,
        ) {
            Layout(
                content = {},
                modifier = Modifier.size(20.dp),
            ) { _, constraints ->
                layout(
                    width = constraints.minWidth,
                    height = constraints.minHeight,
                ) {
                    error("preview placement failure")
                }
            }
        }
    }

    private data object ThrowingDrawPreviewRenderer :
        BlockPreviewRenderer<TestPreviewType> {
        @Composable
        override fun RenderPreview(
            block: Block,
            modifier: Modifier,
            scope: BlockPreviewScope,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .drawBehind { error("preview draw failure") },
            )
        }
    }
}

private fun paragraph(id: String, text: String): Block =
    Block.paragraph(text).copy(id = BlockId(id))
