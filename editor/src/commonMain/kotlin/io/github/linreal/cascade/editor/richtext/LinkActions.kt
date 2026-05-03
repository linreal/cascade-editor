package io.github.linreal.cascade.editor.richtext

import androidx.compose.runtime.Stable

/**
 * Target-based link mutation API.
 *
 * Implementations operate on an explicitly captured [LinkTarget] and never
 * read live editor selection. This is the surface custom popup sessions and
 * link toolbars use to mutate documents safely after a target has been frozen.
 *
 * For consumers that want "act on the current cursor" semantics without
 * capturing a target first, see [LinkChromeActions].
 */
@Stable
public interface LinkActions {
    /**
     * Applies or edits a link at [target].
     *
     * Invalid URLs return [LinkValidationResult.Invalid] and do not mutate editor
     * state. A non-blank [title] replaces the target text exactly; blank or null
     * titles preserve selected text, or insert the normalized URL at collapsed
     * cursors. A [LinkValidationResult.Valid] value means URL validation passed;
     * the action may still no-op if [target] is stale or the target block no
     * longer exists.
     */
    public fun applyLink(
        target: LinkTarget,
        url: String,
        title: String? = null,
    ): LinkValidationResult

    /**
     * Removes link spans from [target] while preserving visible text and non-link spans.
     */
    public fun removeLink(target: LinkTarget)
}
