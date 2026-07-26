package io.github.linreal.cascade.editor.ui

import androidx.compose.ui.text.style.TextOverflow
import io.github.linreal.cascade.editor.CrashPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCascadePreviewApi::class)
class CascadeDocumentPreviewConfigTest {
    @Test
    fun `default uses the centralized bounded grid card policy`() {
        val config = CascadeDocumentPreviewConfig.Default

        assertSame(CascadeDocumentPreviewConfig.GridCard, config)
        assertEquals(4, config.maxBlocks)
        assertEquals(3, config.maxLinesPerTextBlock)
        assertEquals(1f, config.textScale)
        assertEquals(TextOverflow.Ellipsis, config.textOverflow)
        assertFalse(config.textSelectionEnabled)
        assertTrue(config.linksEnabled)
        assertEquals(CrashPolicy.ContainAndReport, config.crashPolicy)
        assertNull(config.onInternalError)
    }

    @Test
    fun `non-positive bounded block limits fail with actionable message`() {
        listOf(-1, 0).forEach { invalidLimit ->
            val failure = assertFailsWith<IllegalArgumentException> {
                CascadeDocumentPreviewConfig(maxBlocks = invalidLimit)
            }

            assertTrue(failure.message.orEmpty().contains("maxBlocks"))
            assertTrue(failure.message.orEmpty().contains("Unbounded"))
        }
    }

    @Test
    fun `non-positive bounded line limits fail with actionable message`() {
        listOf(-1, 0).forEach { invalidLimit ->
            val failure = assertFailsWith<IllegalArgumentException> {
                CascadeDocumentPreviewConfig(maxLinesPerTextBlock = invalidLimit)
            }

            assertTrue(failure.message.orEmpty().contains("maxLinesPerTextBlock"))
            assertTrue(failure.message.orEmpty().contains("Unbounded"))
        }
    }

    @Test
    fun `non-positive or non-finite text scales fail with actionable message`() {
        listOf(
            0f,
            -1f,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
        ).forEach { invalidScale ->
            val failure = assertFailsWith<IllegalArgumentException> {
                CascadeDocumentPreviewConfig(textScale = invalidScale)
            }

            assertTrue(failure.message.orEmpty().contains("textScale"))
            assertTrue(failure.message.orEmpty().contains("finite"))
            assertTrue(failure.message.orEmpty().contains("greater than zero"))
        }
    }

    @Test
    fun `unbounded preset removes both limits without a numeric sentinel`() {
        val config = CascadeDocumentPreviewConfig.Unbounded

        assertNull(config.maxBlocks)
        assertNull(config.maxLinesPerTextBlock)
        assertEquals(TextOverflow.Ellipsis, config.textOverflow)
    }

    @Test
    fun `copy preserves crash and interaction policy when changing limits`() {
        val errors = mutableListOf<Throwable>()
        val reporter = { error: io.github.linreal.cascade.editor.CascadeError ->
            errors += error.cause
        }
        val original = CascadeDocumentPreviewConfig(
            maxBlocks = 8,
            maxLinesPerTextBlock = 5,
            textScale = 0.8f,
            textSelectionEnabled = true,
            linksEnabled = false,
            crashPolicy = CrashPolicy.Rethrow,
            onInternalError = reporter,
        )

        val changed = original.copy(maxBlocks = 2)

        assertEquals(2, changed.maxBlocks)
        assertEquals(5, changed.maxLinesPerTextBlock)
        assertEquals(0.8f, changed.textScale)
        assertTrue(changed.textSelectionEnabled)
        assertFalse(changed.linksEnabled)
        assertEquals(CrashPolicy.Rethrow, changed.crashPolicy)
        assertSame(reporter, changed.onInternalError)
        assertTrue(errors.isEmpty())
    }
}
