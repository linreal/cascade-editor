package io.github.linreal.cascade.editor.richtext

import androidx.compose.runtime.Stable

/**
 * Link mutation API for editor chrome that has access to live [LinkState].
 *
 * Extends [LinkActions] with current-target convenience helpers so toolbar
 * buttons and other chrome can mutate "the current cursor's link" without
 * having to capture the target themselves first.
 *
 * Implementations report their current target through [currentTarget] and
 * provide concrete current-target sugar via the default implementations of
 * [applyLinkAtCurrentTarget] and [removeLinkAtCurrentTarget]. The defaults
 * forward to the target-based [LinkActions] methods, so concrete classes only
 * need to implement [currentTarget] correctly to get consistent behavior.
 */
@Stable
public interface LinkChromeActions : LinkActions {
    /**
     * Returns the latest [LinkTarget] that current-target helpers will operate
     * on, or `null` when no link target is currently available (no focused
     * text block, block selection, drag, etc.).
     */
    public fun currentTarget(): LinkTarget?

    /**
     * Applies or edits a link at the implementation's [currentTarget].
     *
     * Returns `null` only when no current target is available. The
     * [LinkValidationResult] return semantics match
     * [LinkActions.applyLink].
     */
    public fun applyLinkAtCurrentTarget(
        url: String,
        title: String? = null,
    ): LinkValidationResult? = currentTarget()?.let { applyLink(it, url, title) }

    /**
     * Removes link spans at the implementation's [currentTarget]. No-op when
     * [currentTarget] returns `null`.
     */
    public fun removeLinkAtCurrentTarget() {
        currentTarget()?.let { removeLink(it) }
    }
}
