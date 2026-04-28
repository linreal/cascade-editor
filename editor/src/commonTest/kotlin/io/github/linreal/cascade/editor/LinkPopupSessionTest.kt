package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.richtext.LinkActions
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.LinkTarget
import io.github.linreal.cascade.editor.richtext.LinkValidationResult
import io.github.linreal.cascade.editor.richtext.LinkValidationError
import io.github.linreal.cascade.editor.ui.LinkPopupCloseRequest
import io.github.linreal.cascade.editor.ui.LinkPopupSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkPopupSessionTest {

    private val blockId = BlockId("b1")
    private val selectedTarget = LinkTarget(blockId, 2, 9)

    @Test
    fun `selected unlinked text opens with title and empty URL`() {
        val session = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "Example",
                selectionCollapsed = false,
            ),
        )

        assertEquals("Example", session.state.title)
        assertEquals("", session.state.url)
        assertNull(session.state.normalizedUrl)
        assertEquals(LinkValidationError.Blank, session.state.validationError)
        assertFalse(session.state.canApply)
        assertFalse(session.state.canRemove)
    }

    @Test
    fun `selected URL text opens with URL prefilled`() {
        val session = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "example.com/path",
                selectionCollapsed = false,
            ),
        )

        assertEquals("example.com/path", session.state.title)
        assertEquals("example.com/path", session.state.url)
        assertEquals("https://example.com/path", session.state.normalizedUrl)
        assertNull(session.state.validationError)
        assertTrue(session.state.canApply)
    }

    @Test
    fun `collapsed cursor inside existing link captures full link range`() {
        val cursorTarget = LinkTarget(blockId, 5, 5)
        val linkRange = LinkTarget(blockId, 2, 12)
        val session = createSession(
            linkState = linkState(
                target = cursorTarget,
                targetText = "",
                selectionCollapsed = true,
                existingUrl = "https://example.com",
                existingLinkRange = linkRange,
                existingLinkText = "Link title",
                intersectsLink = true,
            ),
        )

        assertEquals(linkRange, session.target)
        assertEquals("Link title", session.state.title)
        assertEquals("https://example.com", session.state.url)
        assertEquals("https://example.com", session.state.existingUrl)
        assertTrue(session.state.canApply)
        assertTrue(session.state.canRemove)
    }

    @Test
    fun `blank title at collapsed cursor applies URL with no explicit title`() {
        val actions = RecordingLinkActions()
        val session = createSession(
            linkState = linkState(
                target = LinkTarget(blockId, 3, 3),
                targetText = "",
                selectionCollapsed = true,
            ),
            actions = actions,
        )

        session.updateUrl("example.com")
        session.apply()

        assertEquals(
            listOf(ApplyCall(LinkTarget(blockId, 3, 3), "example.com", null)),
            actions.applyCalls,
        )
    }

    @Test
    fun `blank title over selected text applies URL with no explicit title`() {
        val actions = RecordingLinkActions()
        val session = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "Example",
                selectionCollapsed = false,
            ),
            actions = actions,
        )

        session.updateTitle("  ")
        session.updateUrl("example.com")
        session.apply()

        assertEquals(
            listOf(ApplyCall(selectedTarget, "example.com", null)),
            actions.applyCalls,
        )
    }

    @Test
    fun `changed title is passed to link actions`() {
        val actions = RecordingLinkActions()
        val session = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "foo",
                selectionCollapsed = false,
            ),
            actions = actions,
        )

        session.updateTitle("bar")
        session.updateUrl("example.com")
        session.apply()

        assertEquals(
            listOf(ApplyCall(selectedTarget, "example.com", "bar")),
            actions.applyCalls,
        )
    }

    @Test
    fun `apply trims surrounding title whitespace`() {
        val actions = RecordingLinkActions()
        val session = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "Example",
                selectionCollapsed = false,
            ),
            actions = actions,
        )

        session.updateTitle("  Padded title  ")
        session.updateUrl("example.com")
        session.apply()

        assertEquals(
            listOf(ApplyCall(selectedTarget, "example.com", "Padded title")),
            actions.applyCalls,
        )
    }

    @Test
    fun `apply preserves intentional internal title whitespace`() {
        val actions = RecordingLinkActions()
        val session = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "Example",
                selectionCollapsed = false,
            ),
            actions = actions,
        )

        session.updateTitle("My  spaced title")
        session.updateUrl("example.com")
        session.apply()

        assertEquals(
            listOf(ApplyCall(selectedTarget, "example.com", "My  spaced title")),
            actions.applyCalls,
        )
    }

    @Test
    fun `blank URL apply does not call link actions or close`() {
        val actions = RecordingLinkActions()
        val closeRequests = mutableListOf<LinkPopupCloseRequest>()
        val session = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "Example",
                selectionCollapsed = false,
            ),
            actions = actions,
            closeRequests = closeRequests,
        )

        session.updateUrl("   ")
        session.apply()

        assertTrue(actions.applyCalls.isEmpty())
        assertTrue(actions.removeCalls.isEmpty())
        assertTrue(closeRequests.isEmpty())
    }

    @Test
    fun `canRemove follows captured existing URL and link intersection`() {
        val existing = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "Example",
                existingUrl = "https://example.com",
                existingLinkRange = selectedTarget,
                existingLinkText = "Example",
                intersectsLink = true,
            ),
        )
        val mixed = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "Example",
                existingUrl = null,
                intersectsLink = true,
            ),
        )
        val clean = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "Example",
                existingUrl = null,
                intersectsLink = false,
            ),
        )

        assertTrue(existing.state.canRemove)
        assertTrue(mixed.state.canRemove)
        assertFalse(clean.state.canRemove)
    }

    @Test
    fun `remove calls link actions for captured target`() {
        val actions = RecordingLinkActions()
        val closeRequests = mutableListOf<LinkPopupCloseRequest>()
        val session = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "Example",
                existingUrl = "https://example.com",
                existingLinkRange = selectedTarget,
                existingLinkText = "Example",
                intersectsLink = true,
            ),
            actions = actions,
            closeRequests = closeRequests,
        )

        session.remove()

        assertEquals(listOf(selectedTarget), actions.removeCalls)
        assertEquals(listOf(LinkPopupCloseRequest.RestoreCapturedSelection), closeRequests)
    }

    @Test
    fun `apply keeps using captured target when editor text later changes`() {
        val actions = RecordingLinkActions()
        val session = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "Example",
                selectionCollapsed = false,
            ),
            actions = actions,
        )

        session.updateTitle("Updated")
        session.updateUrl("example.com")
        session.apply()

        assertEquals(
            listOf(ApplyCall(selectedTarget, "example.com", "Updated")),
            actions.applyCalls,
        )
    }

    @Test
    fun `dismiss closes without document mutation`() {
        val actions = RecordingLinkActions()
        val closeRequests = mutableListOf<LinkPopupCloseRequest>()
        val session = createSession(
            linkState = linkState(
                target = selectedTarget,
                targetText = "Example",
                selectionCollapsed = false,
            ),
            actions = actions,
            closeRequests = closeRequests,
        )

        session.dismiss()

        assertTrue(actions.applyCalls.isEmpty())
        assertTrue(actions.removeCalls.isEmpty())
        assertEquals(listOf(LinkPopupCloseRequest.RestoreCapturedSelection), closeRequests)
    }

    private fun createSession(
        linkState: LinkState,
        actions: RecordingLinkActions = RecordingLinkActions(),
        closeRequests: MutableList<LinkPopupCloseRequest> = mutableListOf(),
    ): LinkPopupSession {
        return LinkPopupSession.create(
            linkState = linkState,
            linkActions = actions,
            textGeneration = 0,
            spanGeneration = 0,
            onRequestClose = closeRequests::add,
        ) ?: error("Expected popup session")
    }

    private fun linkState(
        target: LinkTarget,
        targetText: String,
        selectionCollapsed: Boolean = false,
        existingUrl: String? = null,
        existingLinkRange: LinkTarget? = null,
        existingLinkText: String? = null,
        intersectsLink: Boolean = false,
    ): LinkState {
        return LinkState(
            canLink = true,
            focusedBlockId = blockId,
            target = target,
            targetText = targetText,
            selectionCollapsed = selectionCollapsed,
            existingUrl = existingUrl,
            existingLinkRange = existingLinkRange,
            existingLinkText = existingLinkText,
            isInsideLink = selectionCollapsed && existingUrl != null,
            intersectsLink = intersectsLink,
        )
    }

    private data class ApplyCall(
        val target: LinkTarget,
        val url: String,
        val title: String?,
    )

    private class RecordingLinkActions : LinkActions {
        val applyCalls = mutableListOf<ApplyCall>()
        val removeCalls = mutableListOf<LinkTarget>()

        override fun applyLink(
            target: LinkTarget,
            url: String,
            title: String?,
        ): LinkValidationResult {
            applyCalls += ApplyCall(target, url, title)
            return LinkValidationResult.Valid("https://example.com")
        }

        override fun removeLink(target: LinkTarget) {
            removeCalls += target
        }
    }
}
