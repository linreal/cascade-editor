package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.ui.RichTextToolbarConfig
import io.github.linreal.cascade.editor.ui.ToolbarButtonSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RichTextToolbarConfigTest {

    @Test
    fun `default toolbar config shows link button`() {
        assertTrue(RichTextToolbarConfig.Default.showLink)
    }

    @Test
    fun `showLink can hide the default toolbar link button independently of other buttons`() {
        val config = RichTextToolbarConfig(
            buttons = listOf(ToolbarButtonSpec(SpanStyle.Bold, "Bold")),
            showIndentation = false,
            showLink = false,
        )

        assertFalse(config.showLink)
        assertFalse(config.showIndentation)
        assertEquals(listOf(SpanStyle.Bold), config.buttons.map { it.style })
    }

    @Test
    fun `toolbar button spec rejects link styles`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            ToolbarButtonSpec(SpanStyle.Link("https://example.com"), "Link")
        }

        assertEquals(
            "SpanStyle.Link cannot be used with ToolbarButtonSpec; use RichTextToolbarConfig.showLink instead.",
            failure.message,
        )
    }
}
