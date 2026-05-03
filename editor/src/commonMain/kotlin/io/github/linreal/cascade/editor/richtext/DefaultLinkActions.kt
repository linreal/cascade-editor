package io.github.linreal.cascade.editor.richtext

import androidx.compose.runtime.Stable

/**
 * Editor-managed [LinkChromeActions] facade exposed through CompositionLocals.
 *
 * It gates mutations through the latest [LinkState] so custom chrome cannot
 * mutate links while the editor is in a state where link operations are
 * unavailable, such as block selection or drag mode. URL validation still
 * runs even when linking is disabled, so chrome popups can show validation
 * feedback without committing changes.
 *
 * The current-target sugar from [LinkChromeActions] is inherited unchanged;
 * gating happens once at the [applyLink] / [removeLink] layer and propagates
 * automatically.
 */
@Stable
internal class DefaultLinkActions(
    private val stateProvider: () -> LinkState,
    private val delegate: LinkActions,
) : LinkChromeActions {

    /**
     * Returns URL validation feedback even when linking is currently unavailable,
     * but only delegates to the mutating action layer when [LinkState.canLink] is true.
     */
    override fun applyLink(
        target: LinkTarget,
        url: String,
        title: String?,
    ): LinkValidationResult {
        if (!stateProvider().canLink) {
            return LinkUrlPolicy.validate(url)
        }
        return delegate.applyLink(target, url, title)
    }

    override fun removeLink(target: LinkTarget) {
        if (!stateProvider().canLink) return
        delegate.removeLink(target)
    }

    override fun currentTarget(): LinkTarget? = stateProvider().target
}
