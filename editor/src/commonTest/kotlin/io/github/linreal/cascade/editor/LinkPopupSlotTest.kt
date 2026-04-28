package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.richtext.LinkValidationError
import io.github.linreal.cascade.editor.ui.LinkPopupActions
import io.github.linreal.cascade.editor.ui.LinkPopupSlot
import io.github.linreal.cascade.editor.ui.LinkPopupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkPopupSlotTest {

    @Test
    fun `default and none variants are public slot values`() {
        assertIs<LinkPopupSlot>(LinkPopupSlot.Default)
        assertIs<LinkPopupSlot>(LinkPopupSlot.None)
    }

    @Test
    fun `custom variant stores composable content callback`() {
        val slot = LinkPopupSlot.Custom { _, _ -> }

        assertIs<LinkPopupSlot.Custom>(slot)
    }

    @Test
    fun `link popup state exposes UI ready validation and action flags`() {
        val state = LinkPopupState(
            title = "Example",
            url = "",
            normalizedUrl = null,
            validationError = LinkValidationError.Blank,
            existingUrl = null,
            canApply = false,
            canRemove = true,
        )

        assertEquals("Example", state.title)
        assertEquals("", state.url)
        assertNull(state.normalizedUrl)
        assertEquals(LinkValidationError.Blank, state.validationError)
        assertFalse(state.canApply)
        assertTrue(state.canRemove)
    }

    @Test
    fun `link popup actions expose mutation callbacks`() {
        val calls = mutableListOf<String>()
        val actions = object : LinkPopupActions {
            override fun updateTitle(title: String) {
                calls += "title:$title"
            }

            override fun updateUrl(url: String) {
                calls += "url:$url"
            }

            override fun apply() {
                calls += "apply"
            }

            override fun remove() {
                calls += "remove"
            }

            override fun dismiss() {
                calls += "dismiss"
            }
        }

        actions.updateTitle("Title")
        actions.updateUrl("example.com")
        actions.apply()
        actions.remove()
        actions.dismiss()

        assertEquals(
            listOf("title:Title", "url:example.com", "apply", "remove", "dismiss"),
            calls,
        )
    }
}
