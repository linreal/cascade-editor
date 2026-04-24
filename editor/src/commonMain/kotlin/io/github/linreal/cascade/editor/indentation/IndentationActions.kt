package io.github.linreal.cascade.editor.indentation

import androidx.compose.runtime.Stable

/**
 * Public action interface for structural block indentation commands.
 *
 * Consumers can read [io.github.linreal.cascade.editor.ui.LocalIndentationActions]
 * from custom editor chrome and call these methods without dispatching reducer
 * actions directly.
 */
@Stable
public interface IndentationActions {
    /**
     * Indents the current supported selection or focused block by one level.
     */
    public fun indentForward()

    /**
     * Outdents the current supported selection or focused block by one level.
     */
    public fun indentBackward()
}
