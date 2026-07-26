package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Stable
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.registry.BlockPreviewScope
import io.github.linreal.cascade.editor.richtext.LinkUrlPolicy

/**
 * Default read-only scope used by [CascadeDocumentPreview].
 *
 * [blocks] follows the preview API's immutable-input contract and is retained
 * without a full-document copy. The lookup index is built only if a custom
 * renderer asks for a block, keeping built-in previews proportional to their
 * rendered prefix. Duplicate IDs resolve to the first source-order occurrence.
 */
@OptIn(ExperimentalCascadePreviewApi::class)
@Stable
internal class DefaultBlockPreviewScope(
    override val blocks: List<Block>,
    override val config: CascadeDocumentPreviewConfig,
    private val onOpenLink: ((String) -> Unit)?,
) : BlockPreviewScope {
    override val canOpenLinks: Boolean
        get() = config.linksEnabled && onOpenLink != null

    private val blocksById: Map<BlockId, Block> by lazy(LazyThreadSafetyMode.NONE) {
        buildMap {
            blocks.forEach { block ->
                if (block.id !in this) {
                    put(block.id, block)
                }
            }
        }
    }

    override fun getBlock(blockId: BlockId): Block? = blocksById[blockId]

    override fun openLink(target: String) {
        if (!canOpenLinks) return
        val normalizedTarget = LinkUrlPolicy.validateStoredTarget(target).normalizedUrl ?: return
        onOpenLink?.invoke(normalizedTarget)
    }
}
