package io.github.linreal.cascade.editor

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.theme.CascadeEditorTypography
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi
import io.github.linreal.cascade.editor.ui.createEditorRegistry
import io.github.linreal.cascade.editor.ui.renderers.resolveTextBlockStyle
import io.github.linreal.cascade.editor.ui.renderers.resolveTodoIndicatorGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@OptIn(ExperimentalCascadePreviewApi::class)
class BuiltInPreviewVisualTest {

    private val typography = CascadeEditorTypography.default()

    @Test
    fun `static text style resolver covers every built-in text type`() {
        assertEquals(typography.body, resolveTextBlockStyle(BlockType.Paragraph, typography))
        assertEquals(typography.body, resolveTextBlockStyle(BlockType.BulletList, typography))
        assertEquals(
            typography.body,
            resolveTextBlockStyle(BlockType.NumberedList(number = 1), typography),
        )
        assertEquals(typography.body, resolveTextBlockStyle(BlockType.Todo(), typography))
        assertEquals(
            typography.body.copy(fontStyle = FontStyle.Italic),
            resolveTextBlockStyle(BlockType.Quote, typography),
        )
        assertEquals(typography.code, resolveTextBlockStyle(BlockType.Code, typography))

        val expectedHeadings = listOf(
            typography.heading1,
            typography.heading2,
            typography.heading3,
            typography.heading4,
            typography.heading5,
            typography.heading6,
        )
        expectedHeadings.forEachIndexed { index, expected ->
            assertEquals(
                expected,
                resolveTextBlockStyle(BlockType.Heading(index + 1), typography),
            )
        }
    }

    @Test
    fun `todo indicator geometry preserves root and nested editor shapes`() {
        assertEquals(
            expected = Triple(20.dp, 5.dp, 2.dp),
            actual = resolveTodoIndicatorGeometry(0).run { Triple(size, corner, stroke) },
        )
        assertEquals(
            expected = Triple(18.dp, 99.dp, 2.dp),
            actual = resolveTodoIndicatorGeometry(1).run { Triple(size, corner, stroke) },
        )
        assertEquals(
            expected = Triple(18.dp, 5.dp, 2.dp),
            actual = resolveTodoIndicatorGeometry(2).run { Triple(size, corner, stroke) },
        )
    }

    @Test
    fun `editor registry installs dedicated preview renderers for all built-ins`() {
        val registry = createEditorRegistry()
        val textTypes = listOf(
            BlockType.Paragraph,
            BlockType.Heading(1),
            BlockType.Heading(2),
            BlockType.Heading(3),
            BlockType.Heading(4),
            BlockType.Heading(5),
            BlockType.Heading(6),
            BlockType.BulletList,
            BlockType.NumberedList(number = 1),
            BlockType.Quote,
            BlockType.Code,
        )

        val sharedTextRenderer = assertNotNull(
            registry.getPreviewRenderer(BlockType.Paragraph.typeId),
        )
        textTypes.forEach { type ->
            assertSame(sharedTextRenderer, registry.getPreviewRenderer(type.typeId))
        }
        assertNotNull(registry.getPreviewRenderer(BlockType.Todo().typeId))
        assertNotNull(registry.getPreviewRenderer(BlockType.Divider.typeId))
    }
}
