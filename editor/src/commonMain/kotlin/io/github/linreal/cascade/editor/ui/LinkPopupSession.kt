package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.linreal.cascade.editor.richtext.LinkActions
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.LinkTarget
import io.github.linreal.cascade.editor.richtext.LinkUrlPolicy

/**
 * Close behavior requested by a popup session.
 */
internal enum class LinkPopupCloseRequest {
    RestoreCapturedSelection,
    KeepCurrentSelection,
}

/**
 * Editor-owned link popup session.
 *
 * The session freezes the target range from the opening [LinkState], owns the
 * title/URL field values, exposes derived validation state, and delegates
 * mutations to [LinkActions] without ever re-resolving the current editor
 * selection.
 */
internal class LinkPopupSession private constructor(
    internal val target: LinkTarget,
    internal val textGeneration: Int,
    internal val spanGeneration: Int,
    private val linkActions: LinkActions,
    private val onRequestClose: (LinkPopupCloseRequest) -> Unit,
    initialTitle: String,
    initialUrl: String,
    private val existingUrl: String?,
    private val canRemove: Boolean,
) : LinkPopupActions {

    internal var state: LinkPopupState by mutableStateOf(
        buildState(
            title = initialTitle,
            url = initialUrl,
            existingUrl = existingUrl,
            canRemove = canRemove,
        )
    )
        private set

    override fun updateTitle(title: String) {
        state = buildState(
            title = title,
            url = state.url,
            existingUrl = existingUrl,
            canRemove = canRemove,
        )
    }

    override fun updateUrl(url: String) {
        state = buildState(
            title = state.title,
            url = url,
            existingUrl = existingUrl,
            canRemove = canRemove,
        )
    }

    override fun apply() {
        val currentState = state
        if (!currentState.canApply) return

        // Strip surrounding whitespace from the applied title so URLs do not
        // commit unintentional leading/trailing spaces into the linked text,
        // but keep intentional INTERNAL whitespace (e.g. "My  spaced title")
        // verbatim. Blank titles fall through to null so the action layer
        // applies the spec-defined fallbacks (preserve selected text /
        // insert URL at collapsed cursor).
        val trimmedTitle = currentState.title.trim()
        val title = trimmedTitle.takeIf { it.isNotEmpty() }
        linkActions.applyLink(
            target = target,
            url = currentState.url,
            title = title,
        )
        onRequestClose(LinkPopupCloseRequest.KeepCurrentSelection)
    }

    override fun remove() {
        if (!state.canRemove) return
        linkActions.removeLink(target)
        onRequestClose(LinkPopupCloseRequest.RestoreCapturedSelection)
    }

    override fun dismiss() {
        onRequestClose(LinkPopupCloseRequest.RestoreCapturedSelection)
    }

    internal companion object {
        /**
         * Creates a popup session from the current link state, returning `null`
         * when the toolbar entry point is no longer allowed to open link UI.
         */
        internal fun create(
            linkState: LinkState,
            linkActions: LinkActions,
            textGeneration: Int,
            spanGeneration: Int,
            onRequestClose: (LinkPopupCloseRequest) -> Unit,
        ): LinkPopupSession? {
            if (!linkState.canLink) return null
            val openingTarget = linkState.target ?: return null
            val existingRange = linkState.existingLinkRange
            val editingFullExistingLink =
                linkState.selectionCollapsed && existingRange != null
            val capturedTarget = if (editingFullExistingLink) {
                existingRange ?: openingTarget
            } else {
                openingTarget
            }
            val initialTitle = if (editingFullExistingLink) {
                linkState.existingLinkText ?: linkState.targetText
            } else {
                linkState.targetText
            }
            val initialUrl = linkState.existingUrl
                ?: linkState.targetText.takeIf { it.looksLikeUrl() }
                ?: ""

            return LinkPopupSession(
                target = capturedTarget,
                textGeneration = textGeneration,
                spanGeneration = spanGeneration,
                linkActions = linkActions,
                onRequestClose = onRequestClose,
                initialTitle = initialTitle,
                initialUrl = initialUrl,
                existingUrl = linkState.existingUrl,
                canRemove = linkState.existingUrl != null || linkState.intersectsLink,
            )
        }

        private fun buildState(
            title: String,
            url: String,
            existingUrl: String?,
            canRemove: Boolean,
        ): LinkPopupState {
            val validation = LinkUrlPolicy.validate(url)
            return LinkPopupState(
                title = title,
                url = url,
                normalizedUrl = validation.normalizedUrl,
                validationError = validation.error,
                existingUrl = existingUrl,
                canApply = validation.normalizedUrl != null,
                canRemove = canRemove,
            )
        }

        // URL prefill heuristic: selected text that has no internal whitespace
        // and looks domain-shaped (contains a dot or an explicit scheme) should
        // prefill the URL field. Plain prose like "Example" should not.
        private fun String.looksLikeUrl(): Boolean {
            val trimmed = trim()
            if (trimmed.isEmpty()) return false
            if (trimmed.any { it.isWhitespace() }) return false
            return trimmed.contains("://") || trimmed.contains('.')
        }
    }
}
