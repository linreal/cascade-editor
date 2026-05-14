package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.richtext.LinkActions
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.LinkTarget
import io.github.linreal.cascade.editor.richtext.LinkValidationResult
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import io.github.linreal.cascade.editor.ui.LinkPopupController
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LinkPopupControllerTest {

    private val blockId = BlockId("b1")
    private val target = LinkTarget(blockId, 0, 4)

    @Test
    fun `open creates a popup session when policy allows link editing`() {
        val controller = controller(
            policy = EditorInteractionPolicy.Editable,
            linkState = enabledState(),
        )

        controller.open()

        assertNotNull(controller.session)
    }

    @Test
    fun `open no-ops when policy disallows link editing`() {
        val controller = controller(
            policy = EditorInteractionPolicy.ReadOnly,
            linkState = enabledState(),
        )

        controller.open()

        assertNull(controller.session)
    }

    private fun controller(
        policy: EditorInteractionPolicy,
        linkState: LinkState,
    ): LinkPopupController {
        return LinkPopupController(
            linkActions = RecordingLinkActions(),
            stateHolder = EditorStateHolder(),
            textStates = BlockTextStates(),
            spanStates = BlockSpanStates(),
            linkStateProvider = { linkState },
            policy = policy,
        )
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

    private class RecordingLinkActions : LinkActions {
        override fun applyLink(
            target: LinkTarget,
            url: String,
            title: String?,
        ): LinkValidationResult {
            return LinkValidationResult.Valid("https://example.com")
        }

        override fun removeLink(target: LinkTarget) = Unit
    }
}
