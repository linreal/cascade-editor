package io.github.linreal.cascade.editor.registry

import androidx.compose.runtime.Stable
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.ui.CascadeDocumentPreviewConfig
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi

/**
 * Read-only document context supplied to [BlockPreviewRenderer] instances.
 *
 * The scope deliberately exposes no editor state holder, actions, callbacks,
 * focus, history, or mutation functions. [openLink] is the sole interaction
 * seam and obeys the current preview configuration.
 */
@ExperimentalCascadePreviewApi
@Stable
public interface BlockPreviewScope {
    /** Original ordered input snapshot, including blocks hidden by a preview limit. */
    public val blocks: List<Block>

    /** Presentation and interaction policy for this preview. */
    public val config: CascadeDocumentPreviewConfig

    /**
     * Whether this preview can currently delegate link activation to its host.
     *
     * This is true only when links are enabled and the host supplied an opener.
     * Renderers should use it before installing clickable link semantics so a
     * visual-only link cannot swallow an enclosing card click.
     */
    public val canOpenLinks: Boolean

    /** Returns the input block with [blockId], or `null` when it is absent. */
    public fun getBlock(blockId: BlockId): Block?

    /**
     * Requests host link activation.
     *
     * Implementations safely do nothing when links are disabled or the host did
     * not provide an opener.
     */
    public fun openLink(target: String)
}
