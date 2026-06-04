package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Immutable
import io.github.linreal.cascade.editor.CascadeErrorReporter
import io.github.linreal.cascade.editor.CrashPolicy

/**
 * Cross-cutting behavior configuration for [CascadeEditor].
 *
 * This config intentionally does not own editor state, renderer registries,
 * slots, callbacks, theme, or localization. Those remain direct [CascadeEditor]
 * parameters so existing ownership and customization boundaries stay explicit.
 *
 * @param readOnly When `true`, built-in editor interaction surfaces expose a
 *        read-only policy. Built-in surfaces enforce that policy at their
 *        interaction boundaries.
 * @param blockSelectionEnabled When `false`, built-in block-selection gestures
 *        are disabled. Ignored when [readOnly] is `true`, because read-only mode
 *        disables block selection regardless of this value.
 * @param blockDraggingEnabled When `false`, built-in block dragging and drag
 *        affordances are disabled. Ignored when [readOnly] is `true`, because
 *        read-only mode disables block dragging regardless of this value.
 */
@Immutable
public data class CascadeEditorConfig(
    val readOnly: Boolean = false,
    val blockSelectionEnabled: Boolean = true,
    val blockDraggingEnabled: Boolean = true,
    /**
     * How the editor reacts to a contained internal failure (render, span transform,
     * document load). Default contains and reports; pass [CrashPolicy.Rethrow] in tests
     * or debug builds to surface bugs.
     */
    val crashPolicy: CrashPolicy = CrashPolicy.ContainAndReport,
    /**
     * Host hook invoked with every contained internal failure (for routing into the
     * host's own crash reporting). Never invoked under [CrashPolicy.Rethrow].
     */
    val onInternalError: CascadeErrorReporter? = null,
) {
    public companion object {
        /** Editable default used when callers do not provide a config. */
        public val Default: CascadeEditorConfig = CascadeEditorConfig()
    }
}
