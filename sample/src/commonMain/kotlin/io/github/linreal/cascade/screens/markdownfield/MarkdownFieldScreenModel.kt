package io.github.linreal.cascade.screens.markdownfield

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.linreal.cascade.editor.markdown.ExperimentalCascadeMarkdownApi
import io.github.linreal.cascade.editor.markdown.MarkdownEditModeRecommendation
import io.github.linreal.cascade.editor.markdown.MarkdownFidelityImpact
import io.github.linreal.cascade.editor.markdown.MarkdownProfile
import io.github.linreal.cascade.editor.markdown.MarkdownSchema
import io.github.linreal.cascade.editor.markdown.loadFromMarkdown
import io.github.linreal.cascade.editor.markdown.toMarkdown
import io.github.linreal.cascade.editor.markdown.toMarkdownWithReport
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder

/** Which surface a Markdown field opens in. */
internal enum class MarkdownFieldMode { Native, RawFallback }

/** Outcome of a save attempt. */
internal enum class MarkdownSaveOutcome { Saved, RejectedLossy, Conflict, NoChange }

/**
 * A fake persistence store with a revision counter — enough to demonstrate the
 * conflict guard without real persistence. External edits bump the revision so
 * a stale save is detected.
 */
internal class FakeMarkdownStore(initial: String) {
    var content: String = initial
        private set
    var revision: Int = 0
        private set
    var writeCount: Int = 0
        private set

    fun read(): String = content

    fun write(markdown: String) {
        content = markdown
        revision++
        writeCount++
    }

    /** Simulate an external editor changing the field out from under us. */
    fun bumpExternally(newContent: String) {
        content = newContent
        revision++
    }
}

/**
 * Unit-testable reference for integrating a persisted Markdown String field.
 * [MarkdownFieldScreen] is now an interactive codec playground; this model
 * remains the focused example of the analyze gate, raw fallback, revision
 * conflict handling, and fidelity-safe persistence contract.
 *
 * Contract: hold the original String; `analyze` before opening; open the native
 * editor on `Native` or a raw text field on `RawFallback`; never write on
 * open/analyze/mode switch; on save, persist only a non-null `Success` encode
 * with no `DataLoss`/`Fatal` impact **and** an unchanged source revision — on
 * violation keep both states and surface a save error.
 */
@OptIn(ExperimentalCascadeMarkdownApi::class)
internal class MarkdownFieldScreenModel(
    val store: FakeMarkdownStore,
    private val profile: MarkdownProfile = MarkdownProfile.Default,
) {
    val textStates = BlockTextStates()
    val spanStates = BlockSpanStates()
    val editorState = EditorStateHolder()

    var mode: MarkdownFieldMode by mutableStateOf(MarkdownFieldMode.RawFallback)
        private set

    var rawText: String by mutableStateOf("")
        private set

    var saveError: String? by mutableStateOf(null)
        private set

    var preservedBlockCount: Int by mutableStateOf(0)
        private set

    private var openedRevision: Int = -1

    /**
     * What a save would have produced right after opening — the baseline used
     * to detect a user-visible edit (save is gated on an actual
     * change, so opening and saving without editing writes nothing).
     */
    private var openedBaseline: String = ""

    /**
     * Open the field: analyze the stored String, choose the edit mode, and load
     * the decoded document into the editor (used as the editable surface in
     * `Native` mode and a read-only preview — including any `md.preserved`
     * blocks, which render through the editor's unknown-block fallback — in
     * `RawFallback` mode). No persistence write occurs here.
     */
    fun open() {
        val source = store.read()
        openedRevision = store.revision
        saveError = null

        val report = MarkdownSchema.analyze(source, profile)
        preservedBlockCount = report.preservedBlockCount
        mode = when (report.recommendedMode) {
            MarkdownEditModeRecommendation.Native -> MarkdownFieldMode.Native
            MarkdownEditModeRecommendation.RawFallback -> MarkdownFieldMode.RawFallback
        }
        rawText = source
        // loadFromMarkdown only replaces on a non-aborted decode; a preserved
        // document still decodes successfully, so the editor holds the blocks.
        editorState.loadFromMarkdown(source, textStates, spanStates, profile)
        openedBaseline = when (mode) {
            MarkdownFieldMode.Native -> editorState.toMarkdown(textStates, spanStates, profile) ?: source
            MarkdownFieldMode.RawFallback -> source
        }
    }

    /** Update the raw-fallback buffer (no write). */
    fun updateRawText(text: String) {
        rawText = text
    }

    /**
     * Attempt to save the current edit back to the store, enforcing the fidelity
     * + revision guard. Returns the outcome; on rejection both states are kept
     * and [saveError] is set.
     */
    fun save(): MarkdownSaveOutcome {
        val markdown: String = when (mode) {
            MarkdownFieldMode.Native -> {
                val result = editorState.toMarkdownWithReport(textStates, spanStates, profile)
                val payload = result.markdown
                val lossy = result.warnings.any {
                    it.impact == MarkdownFidelityImpact.DataLoss || it.impact == MarkdownFidelityImpact.Fatal
                }
                if (payload == null || lossy) {
                    saveError = "Save blocked: editing this field would lose Markdown fidelity."
                    return MarkdownSaveOutcome.RejectedLossy
                }
                payload
            }

            MarkdownFieldMode.RawFallback -> rawText
        }

        // Gate on a user-visible edit: opening and saving without editing (or
        // switching modes) must never write.
        if (markdown == openedBaseline) {
            saveError = null
            return MarkdownSaveOutcome.NoChange
        }

        if (store.revision != openedRevision) {
            saveError = "Save blocked: the field changed elsewhere. Both versions were kept."
            return MarkdownSaveOutcome.Conflict
        }

        store.write(markdown)
        openedRevision = store.revision
        openedBaseline = markdown
        saveError = null
        return MarkdownSaveOutcome.Saved
    }
}
