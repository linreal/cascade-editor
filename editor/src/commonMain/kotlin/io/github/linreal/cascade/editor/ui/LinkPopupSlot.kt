package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Composable

/**
 * Controls editor-owned link popup rendering.
 *
 * [Default] lets CascadeEditor render its built-in popup, [Custom] reuses the
 * editor-managed popup session with consumer-provided UI, and [None] disables
 * editor-owned popup UI so the app can use [LocalLinkState] and [LocalLinkActions]
 * directly.
 */
public sealed interface LinkPopupSlot {
    public data object Default : LinkPopupSlot
    public data object None : LinkPopupSlot

    /**
     * Consumer-rendered popup content for the editor-managed popup session.
     *
     * [Custom] holds an opaque composable lambda. CascadeEditor never compares
     * two [Custom] instances for equality, so unstable callers do not cause
     * extra recomposition; however, the editor still passes the slot to a
     * [LaunchedEffect] keyed on identity, so prefer `remember { LinkPopupSlot.Custom { ... } }`
     * when the slot is rebuilt frequently.
     */
    public class Custom(
        public val content: @Composable (
            state: LinkPopupState,
            actions: LinkPopupActions,
        ) -> Unit,
    ) : LinkPopupSlot
}
