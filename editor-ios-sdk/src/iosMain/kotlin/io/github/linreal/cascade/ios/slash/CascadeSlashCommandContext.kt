@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.slash

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.slash.SlashCommandEditor
import io.github.linreal.cascade.ios.block.CascadeBlockBuildOutcome
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Curated editor handle handed to a native slash-command handler during execution.
 *
 * It is a thin wrapper over the editor's slash execution surface, scoped to the
 * anchor block that owns the `/` trigger. It is valid only for the duration of the
 * handler call; do not retain it.
 */
@ObjCName("CascadeSlashCommandContext", exact = true)
public class CascadeSlashCommandContext internal constructor(
    private val editor: SlashCommandEditor,
    private val anchorBlockId: BlockId,
    private val buildBlock: (typeId: String, payloadJson: String) -> CascadeBlockBuildOutcome,
    private val reportError: (String) -> Unit,
) {

    /**
     * Replaces the `/…` query token in the anchor block with [replacement].
     * Passing an empty string removes the token.
     */
    public fun replaceQueryText(replacement: String) {
        editor.replaceQueryText(replacement)
    }

    /**
     * Replaces the entire visible text of the anchor block.
     *
     * @param cursorPosition Target cursor offset in the new text. A negative value
     *   places the cursor at the end.
     */
    public fun updateAnchorText(text: String, cursorPosition: Int) {
        editor.updateAnchorText(text, cursorPosition.takeIf { it >= 0 })
    }

    /**
     * Inserts a block of [typeId] carrying [payloadJson] immediately after the anchor.
     *
     * Returns `false` (and reports the reason through the controller's
     * `onInternalError`) when [payloadJson] is not a valid JSON object or [typeId]
     * is neither a built-in nor a registered native type; the document is not mutated.
     */
    public fun insertBlockAfterAnchor(typeId: String, payloadJson: String): Boolean =
        insert(typeId, payloadJson) { block -> editor.insertBlockAfterAnchor(block) }

    /**
     * Inserts a block of [typeId] carrying [payloadJson] immediately before the anchor,
     * leaving focus on the anchor. Same rejection semantics as [insertBlockAfterAnchor].
     */
    public fun insertBlockBeforeAnchor(typeId: String, payloadJson: String): Boolean =
        insert(typeId, payloadJson) { block -> editor.insertBlockBeforeAnchor(block) }

    /** Moves editor focus to the anchor block. */
    public fun focusAnchor() {
        editor.focusBlock(anchorBlockId)
    }

    /** Closes the slash menu. */
    public fun closeMenu() {
        editor.closeMenu()
    }

    private inline fun insert(
        typeId: String,
        payloadJson: String,
        place: (Block) -> Unit,
    ): Boolean = when (val outcome = buildBlock(typeId, payloadJson)) {
        is CascadeBlockBuildOutcome.Success -> {
            place(outcome.block)
            true
        }
        is CascadeBlockBuildOutcome.InvalidPayload -> {
            reportError(outcome.message)
            false
        }
        is CascadeBlockBuildOutcome.UnknownType -> {
            reportError(outcome.message)
            false
        }
    }
}
