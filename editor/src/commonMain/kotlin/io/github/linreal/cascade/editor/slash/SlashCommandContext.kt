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
public class SlashCommandContext(
    public val anchorBlockId: BlockId,
    public val query: String,
    public val queryRange: SlashQueryRange,
    public val editor: SlashCommandEditor,
)

/**
 * Safe editor operations available to slash commands during execution.
 *
 * Implementations must guarantee that text, spans, and snapshot state remain aligned
 * after each operation. Commands must not dispatch [EditorAction] directly.
 *
 */
public interface SlashCommandEditor {

    /** Returns the current anchor block, or null if it was deleted. */
    public fun getAnchorBlock(): Block?

    /** Returns the visible text of the anchor block, or null if unavailable. */
    public fun getAnchorVisibleText(): String?

    /**
     * Replaces the query range (`/…`) in the anchor block with [replacement].
     * Cursor is placed at the end of the replacement.
     */
    public fun replaceQueryText(replacement: String)

    /**
     * Replaces the entire visible text of the anchor block.
     */
    public fun updateAnchorText(newText: String)

    /**
     * Replaces the anchor block with [newBlock].
     * When [preserveAnchorId] is true the anchor's block ID is kept on the replacement.
     */
    public fun replaceAnchorBlock(newBlock: Block, preserveAnchorId: Boolean = true)

    /**
     * Inserts [block] immediately after the anchor block and optionally focuses it.
     */
    public fun insertBlockAfterAnchor(block: Block, focus: Boolean = true)

    /**
     * Moves editor focus to the given block.
     */
    public fun focusBlock(blockId: BlockId)

    /**
     * Closes the slash menu. Called automatically after successful execution,
     * but commands may call it early for immediate visual feedback.
     */
    public fun closeMenu()
}
