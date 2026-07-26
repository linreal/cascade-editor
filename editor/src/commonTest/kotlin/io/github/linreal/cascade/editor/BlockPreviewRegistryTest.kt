package io.github.linreal.cascade.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockPreviewRenderer
import io.github.linreal.cascade.editor.registry.BlockPreviewScope
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.ui.CascadeDocumentPreviewConfig
import io.github.linreal.cascade.editor.ui.DefaultBlockPreviewScope
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCascadePreviewApi::class)
class BlockPreviewRegistryTest {
    @Test
    fun `empty registry has neither an exact preview renderer nor fallback`() {
        val registry = BlockRegistry.create()

        assertNull(registry.getPreviewRenderer(TestBlockType))
        assertNull(registry.getPreviewRenderer(TestBlockType.typeId))
    }

    @Test
    fun `matching preview renderer is returned by identity`() {
        val registry = BlockRegistry.create()
        val renderer = TestPreviewRenderer<TestBlockType>()

        registry.registerPreviewRenderer(TestBlockType.typeId, renderer)

        assertSame(renderer, registry.getPreviewRenderer(TestBlockType.typeId))
        assertSame(renderer, registry.getPreviewRenderer(TestBlockType))
    }

    @Test
    fun `editor renderer never crosses into the preview channel`() {
        val registry = BlockRegistry.create()
        val editorRenderer = TestEditorRenderer()
        registry.registerRenderer(TestBlockType.typeId, editorRenderer)

        assertNull(registry.getPreviewRenderer(TestBlockType.typeId))
        assertNull(registry.getPreviewRenderer(TestBlockType))
        assertSame(editorRenderer, registry.getRenderer(TestBlockType.typeId))
    }

    @Test
    fun `fallback handles unknown and unregistered custom preview types`() {
        val registry = BlockRegistry.create()
        val fallback = TestPreviewRenderer<BlockType>()
        registry.setUnknownBlockPreviewRenderer(fallback)

        val unknown = UnknownBlockType(
            typeId = "future:block",
            rawTypeJson = """{"type":"future:block"}""",
        )

        assertSame(fallback, registry.getPreviewRenderer(unknown))
        assertSame(fallback, registry.getPreviewRenderer(TestBlockType))
        assertNull(registry.getPreviewRenderer(TestBlockType.typeId))
    }

    @Test
    fun `preview registration and fallback replacement increment revision`() {
        val registry = BlockRegistry.create()
        val startRevision = registry.revision

        registry.registerPreviewRenderer(TestBlockType.typeId, TestPreviewRenderer())
        assertTrue(registry.revision > startRevision)

        val afterRegistration = registry.revision
        registry.setUnknownBlockPreviewRenderer(TestPreviewRenderer<BlockType>())
        assertTrue(registry.revision > afterRegistration)
    }

    @Test
    fun `scope retains immutable input and lookup uses first duplicate id`() {
        val first = Block(
            id = BlockId("duplicate"),
            type = BlockType.Paragraph,
            content = BlockContent.Text("First"),
        )
        val duplicate = first.copy(content = BlockContent.Text("Second"))
        val immutableInput = listOf(first, duplicate)
        val scope = DefaultBlockPreviewScope(
            blocks = immutableInput,
            config = CascadeDocumentPreviewConfig.Default,
            onOpenLink = null,
        )

        assertSame(immutableInput, scope.blocks)
        assertSame(first, scope.getBlock(first.id))
        assertNull(scope.getBlock(BlockId("missing")))
    }

    @Test
    fun `scope opens links only when policy and host callback allow it`() {
        val openedTargets = mutableListOf<String>()
        val enabledScope = DefaultBlockPreviewScope(
            blocks = emptyList(),
            config = CascadeDocumentPreviewConfig.Default,
            onOpenLink = openedTargets::add,
        )
        val disabledScope = DefaultBlockPreviewScope(
            blocks = emptyList(),
            config = CascadeDocumentPreviewConfig.Default.copy(linksEnabled = false),
            onOpenLink = openedTargets::add,
        )
        val missingOpenerScope = DefaultBlockPreviewScope(
            blocks = emptyList(),
            config = CascadeDocumentPreviewConfig.Default,
            onOpenLink = null,
        )

        enabledScope.openLink("https://example.test/enabled")
        enabledScope.openLink("  ../trimmed.md  ")
        enabledScope.openLink("   ")
        disabledScope.openLink("https://example.test/disabled")
        missingOpenerScope.openLink("https://example.test/missing")

        assertTrue(enabledScope.canOpenLinks)
        assertFalse(disabledScope.canOpenLinks)
        assertFalse(missingOpenerScope.canOpenLinks)
        assertEquals(
            listOf("https://example.test/enabled", "../trimmed.md"),
            openedTargets,
        )
        assertFalse(openedTargets.contains("https://example.test/disabled"))
    }

    private data object TestBlockType : CustomBlockType {
        override val typeId: String = "custom:test-preview"
        override val displayName: String = "Test Preview"
    }

    private class TestPreviewRenderer<T : BlockType> : BlockPreviewRenderer<T> {
        @Composable
        override fun RenderPreview(
            block: Block,
            modifier: Modifier,
            scope: BlockPreviewScope,
        ) = Unit
    }

    private class TestEditorRenderer : BlockRenderer<TestBlockType> {
        @Composable
        override fun Render(
            block: Block,
            isSelected: Boolean,
            isFocused: Boolean,
            modifier: Modifier,
            callbacks: BlockCallbacks,
        ) = Unit
    }
}
