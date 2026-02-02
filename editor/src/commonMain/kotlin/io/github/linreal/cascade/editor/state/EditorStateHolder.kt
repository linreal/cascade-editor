package io.github.linreal.cascade.editor.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.core.Block

/**
 * Compose-friendly mutable holder for editor state.
 *
 * Provides a stable reference that can be passed down the composition tree
 * while the underlying state changes trigger recomposition.
 *
 * Uses unidirectional data flow: state changes only through dispatched actions.
 */
@Stable
public class EditorStateHolder(initialState: EditorState = EditorState.Empty) {
    private var _state by mutableStateOf(initialState)

    /**
     * The current immutable state snapshot.
     */
    public val state: EditorState get() = _state

    /**
     * Dispatches an action to update the state.
     * This is the only way to modify state, ensuring unidirectional data flow.
     */
    public fun dispatch(action: EditorAction) {
        _state = action.reduce(_state)
    }

    /**
     * Replaces the entire state. Use with caution.
     * Prefer dispatching actions for state changes.
     */
    public fun setState(newState: EditorState) {
        _state = newState
    }
}

/**
 * Creates and remembers an [EditorStateHolder] with the given initial blocks.
 */
@Composable
public fun rememberEditorState(initialBlocks: List<Block> = emptyList()): EditorStateHolder {
    return remember {
        EditorStateHolder(EditorState.withBlocks(initialBlocks))
    }
}

/**
 * Creates and remembers an [EditorStateHolder] with the given initial state.
 */
@Composable
public fun rememberEditorState(initialState: EditorState): EditorStateHolder {
    return remember {
        EditorStateHolder(initialState)
    }
}
