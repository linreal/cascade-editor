package io.github.linreal.cascade.screens.external_toolbar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import io.github.linreal.cascade.editor.serialization.loadFromJson
import io.github.linreal.cascade.editor.serialization.toJson
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder

/**
 * Runtime state for the external-toolbar sample.
 *
 * The sample keeps this outside the composable tree's plain `remember` state so
 * Android configuration changes can save the current document instead of
 * rebuilding from the initial demo blocks.
 */
internal class ExternalToolbarScreenModel(
    initialDraftJson: String = buildExternalToolbarInitialDraftJson(),
) {
    val textStates = BlockTextStates()
    val spanStates = BlockSpanStates()
    val editorState = EditorStateHolder()

    var isReadOnly by mutableStateOf(false)
        private set

    init {
        loadDraft(initialDraftJson)
    }

    fun toggleReadOnly() {
        isReadOnly = !isReadOnly
    }

    fun toDraftJson(): String = editorState.toJson(textStates, spanStates)

    private fun loadDraft(json: String) {
        val fallbackJson = buildExternalToolbarInitialDraftJson()
        runCatching {
            editorState.loadFromJson(json, textStates, spanStates)
        }.getOrElse {
            editorState.loadFromJson(fallbackJson, textStates, spanStates)
        }
    }

    companion object {
        val Saver: Saver<ExternalToolbarScreenModel, String> = Saver(
            save = { model -> model.toDraftJson() },
            restore = { json -> ExternalToolbarScreenModel(initialDraftJson = json) },
        )
    }
}

private fun buildExternalToolbarInitialDraftJson(): String =
    DocumentSchema.encodeToString(buildExternalToolbarDemoBlocks())
