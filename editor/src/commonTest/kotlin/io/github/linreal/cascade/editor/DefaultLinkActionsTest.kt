package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.richtext.DefaultLinkActions
import io.github.linreal.cascade.editor.richtext.LinkActions
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.LinkTarget
import io.github.linreal.cascade.editor.richtext.LinkValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultLinkActionsTest {

    private val blockId = BlockId("b1")
    private val target = LinkTarget(blockId, 0, 4)

    @Test
    fun `applyLink validates but does not delegate when current link state disallows linking`() {
        val delegate = RecordingLinkActions()
        val actions = DefaultLinkActions(
            stateProvider = { LinkState.Empty },
            delegate = delegate,
        )

        val result = actions.applyLink(
            target = target,
            url = "example.com",
            title = "Example",
        )

        assertEquals(LinkValidationResult.Valid("https://example.com"), result)
        assertTrue(delegate.applyCalls.isEmpty())
        assertTrue(delegate.removeCalls.isEmpty())
    }

    @Test
    fun `applyLink delegates when current link state allows linking`() {
        val delegate = RecordingLinkActions(
            applyResult = LinkValidationResult.Valid("https://delegated.example"),
        )
        val actions = DefaultLinkActions(
            stateProvider = { enabledState() },
            delegate = delegate,
        )

        val result = actions.applyLink(
            target = target,
            url = "example.com",
            title = "Example",
        )

        assertEquals(LinkValidationResult.Valid("https://delegated.example"), result)
        assertEquals(
            listOf(ApplyCall(target, "example.com", "Example")),
            delegate.applyCalls,
        )
        assertTrue(delegate.removeCalls.isEmpty())
    }

    @Test
    fun `removeLink does not delegate when current link state disallows linking`() {
        val delegate = RecordingLinkActions()
        val actions = DefaultLinkActions(
            stateProvider = { LinkState.Empty },
            delegate = delegate,
        )

        actions.removeLink(target)

        assertTrue(delegate.applyCalls.isEmpty())
        assertTrue(delegate.removeCalls.isEmpty())
    }

    @Test
    fun `removeLink delegates when current link state allows linking`() {
        val delegate = RecordingLinkActions()
        val actions = DefaultLinkActions(
            stateProvider = { enabledState() },
            delegate = delegate,
        )

        actions.removeLink(target)

        assertTrue(delegate.applyCalls.isEmpty())
        assertEquals(listOf(target), delegate.removeCalls)
    }

    @Test
    fun `applyLink returns validation feedback but does not delegate when canLink is false but target exists`() {
        val delegate = RecordingLinkActions()
        val actions = DefaultLinkActions(
            stateProvider = { disabledStateWithTarget() },
            delegate = delegate,
        )

        val result = actions.applyLinkAtCurrentTarget("   ", "Example")

        assertEquals(
            LinkValidationResult.Invalid(io.github.linreal.cascade.editor.richtext.LinkValidationError.Blank),
            result,
        )
        assertTrue(delegate.applyCalls.isEmpty())
        assertTrue(delegate.removeCalls.isEmpty())
    }

    @Test
    fun `currentTarget returns null when state has no target`() {
        val actions = DefaultLinkActions(
            stateProvider = { LinkState.Empty },
            delegate = RecordingLinkActions(),
        )
        assertNull(actions.currentTarget())
    }

    @Test
    fun `currentTarget returns the live state target when present`() {
        val actions = DefaultLinkActions(
            stateProvider = { enabledState() },
            delegate = RecordingLinkActions(),
        )
        assertEquals(target, actions.currentTarget())
    }

    @Test
    fun `applyLinkAtCurrentTarget returns null when current state has no target`() {
        val delegate = RecordingLinkActions()
        val actions = DefaultLinkActions(
            stateProvider = { LinkState.Empty },
            delegate = delegate,
        )

        val result = actions.applyLinkAtCurrentTarget("example.com", "Example")

        assertNull(result)
        assertTrue(delegate.applyCalls.isEmpty())
        assertTrue(delegate.removeCalls.isEmpty())
    }

    @Test
    fun `applyLinkAtCurrentTarget delegates with the current state target`() {
        val delegate = RecordingLinkActions(
            applyResult = LinkValidationResult.Valid("https://delegated.example"),
        )
        val actions = DefaultLinkActions(
            stateProvider = { enabledState() },
            delegate = delegate,
        )

        val result = actions.applyLinkAtCurrentTarget("example.com", "Example")

        assertEquals(LinkValidationResult.Valid("https://delegated.example"), result)
        assertEquals(
            listOf(ApplyCall(target, "example.com", "Example")),
            delegate.applyCalls,
        )
        assertTrue(delegate.removeCalls.isEmpty())
    }

    @Test
    fun `removeLinkAtCurrentTarget no-ops when current state has no target`() {
        val delegate = RecordingLinkActions()
        val actions = DefaultLinkActions(
            stateProvider = { LinkState.Empty },
            delegate = delegate,
        )

        actions.removeLinkAtCurrentTarget()

        assertTrue(delegate.applyCalls.isEmpty())
        assertTrue(delegate.removeCalls.isEmpty())
    }

    @Test
    fun `removeLinkAtCurrentTarget delegates with the current state target`() {
        val delegate = RecordingLinkActions()
        val actions = DefaultLinkActions(
            stateProvider = { enabledState() },
            delegate = delegate,
        )

        actions.removeLinkAtCurrentTarget()

        assertTrue(delegate.applyCalls.isEmpty())
        assertEquals(listOf(target), delegate.removeCalls)
    }

    private fun enabledState(): LinkState {
        return LinkState(
            canLink = true,
            focusedBlockId = blockId,
            target = target,
            targetText = "link",
            selectionCollapsed = false,
            existingUrl = null,
            existingLinkRange = null,
            existingLinkText = null,
            isInsideLink = false,
            intersectsLink = false,
        )
    }

    private fun disabledStateWithTarget(): LinkState {
        // canLink is false but target exists — covers the chrome-validation-only path.
        return LinkState(
            canLink = false,
            focusedBlockId = blockId,
            target = target,
            targetText = "link",
            selectionCollapsed = false,
            existingUrl = null,
            existingLinkRange = null,
            existingLinkText = null,
            isInsideLink = false,
            intersectsLink = false,
        )
    }

    private data class ApplyCall(
        val target: LinkTarget,
        val url: String,
        val title: String?,
    )

    private class RecordingLinkActions(
        private val applyResult: LinkValidationResult =
            LinkValidationResult.Valid("https://example.com"),
    ) : LinkActions {
        val applyCalls = mutableListOf<ApplyCall>()
        val removeCalls = mutableListOf<LinkTarget>()

        override fun applyLink(
            target: LinkTarget,
            url: String,
            title: String?,
        ): LinkValidationResult {
            applyCalls += ApplyCall(target, url, title)
            return applyResult
        }

        override fun removeLink(target: LinkTarget) {
            removeCalls += target
        }
    }
}
