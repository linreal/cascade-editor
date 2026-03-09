package io.github.linreal.cascade.editor.slash

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.state.SlashQueryRange

/**
 * Execution context provided as the receiver for [SlashCommandAction.onExecute].
 *
 * Holds the session snapshot at execution time and a safe [editor] handle for mutations.
 *
 * @property anchorBlockId The block that owns the `/` trigger.
 * @property query The query text the user typed after `/`.
 * @property queryRange Visible-text range of the full `/…` token.
 * @property editor Safe operations for mutating editor state during command execution.
 */
public data class SlashCommandContext(
    val anchorBlockId: BlockId,
    val query: String,
    val queryRange: SlashQueryRange,
    val editor: SlashCommandEditor,
)

/**
 * Safe editor operations available to slash commands during execution.
 *
 * Implementations must guarantee that text, spans, and snapshot state remain aligned
 * after each operation. Commands must not dispatch [EditorAction] directly.
 */
public interface SlashCommandEditor {

    /** Returns the current anchor block, or null if it was deleted. */
    public fun getAnchorBlock(): Block?

    /** Returns the visible text of the anchor block, or null if unavailable. */
    public fun getAnchorVisibleText(): String?

    /**
     * Replaces the query range (`/…`) in the anchor block with [replacement].
     * When [replacement] is empty the query text is simply removed.
     */
    public fun replaceQueryText(replacement: String = "")

    /**
     * Replaces the entire visible text of the anchor block.
     *
     * @param text The new text content.
     * @param cursorPosition Target cursor offset inside the new text. When null the
     *   implementation places the cursor at the end.
     */
    public fun updateAnchorText(text: String, cursorPosition: Int? = null)

    /**
     * Replaces the anchor block with [block].
     *
     * @param block The replacement block.
     * @param preserveAnchorId When true the anchor's block ID is kept on the replacement.
     * @param requestFocus When true the replacement block receives focus.
     * @param cursorPosition Target cursor offset inside the replacement. Null means end.
     */
    public fun replaceAnchorBlock(
        block: Block,
        preserveAnchorId: Boolean = true,
        requestFocus: Boolean = true,
        cursorPosition: Int? = null,
    )

    /**
     * Inserts [block] immediately after the anchor block.
     *
     * @param block The block to insert.
     * @param requestFocus When true the inserted block receives focus.
     * @param cursorPosition Target cursor offset inside the inserted block. Null means end.
     */
    public fun insertBlockAfterAnchor(
        block: Block,
        requestFocus: Boolean = true,
        cursorPosition: Int? = null,
    )

    /**
     * Moves editor focus to the given block.
     *
     * @param blockId Target block.
     * @param cursorPosition Target cursor offset. Null means end.
     */
    public fun focusBlock(blockId: BlockId, cursorPosition: Int? = null)

    /**
     * Closes the slash menu. Called automatically after successful execution,
     * but commands may call it early for immediate visual feedback.
     */
    public fun closeMenu()
}
