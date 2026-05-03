package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.LinkTarget
import io.github.linreal.cascade.editor.richtext.StyleStatus
import io.github.linreal.cascade.editor.ui.linkToolbarButtonPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RichTextToolbarLinkButtonTest {

    private val blockId = BlockId("b1")
    private val target = LinkTarget(blockId, 0, 4)

    @Test
    fun `link button is disabled when link state disallows linking`() {
        val presentation = linkToolbarButtonPresentation(LinkState.Empty)

        assertFalse(presentation.enabled)
        assertEquals(StyleStatus.Absent, presentation.status)
    }

    @Test
    fun `link button is active when one existing URL is resolved`() {
        val presentation = linkToolbarButtonPresentation(
            linkState(
                existingUrl = "https://example.com",
                intersectsLink = true,
            )
        )

        assertTrue(presentation.enabled)
        assertEquals(StyleStatus.FullyActive, presentation.status)
    }

    @Test
    fun `link button is partial when target intersects links without one resolved URL`() {
        val presentation = linkToolbarButtonPresentation(
            linkState(
                existingUrl = null,
                intersectsLink = true,
            )
        )

        assertTrue(presentation.enabled)
        assertEquals(StyleStatus.Partial, presentation.status)
    }

    @Test
    fun `link button is absent when target has no link intersection`() {
        val presentation = linkToolbarButtonPresentation(
            linkState(
                existingUrl = null,
                intersectsLink = false,
            )
        )

        assertTrue(presentation.enabled)
        assertEquals(StyleStatus.Absent, presentation.status)
    }

    private fun linkState(
        existingUrl: String?,
        intersectsLink: Boolean,
    ): LinkState {
        return LinkState(
            canLink = true,
            focusedBlockId = blockId,
            target = target,
            targetText = "link",
            selectionCollapsed = false,
            existingUrl = existingUrl,
            existingLinkRange = existingUrl?.let { target },
            existingLinkText = existingUrl?.let { "link" },
            isInsideLink = false,
            intersectsLink = intersectsLink,
        )
    }
}
