package io.github.linreal.cascade.editor.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi

/**
 * Stateless renderer for a block in a Cascade document preview.
 *
 * This is intentionally separate from [BlockRenderer]. Preview renderers
 * receive no editor callbacks or mutable editor scope, and an editor renderer
 * is never used automatically when a preview renderer is absent.
 *
 * Configure registries before sharing them across threads. Like the existing
 * editor-renderer channel, registry mutation is intended for the UI thread.
 *
 * [CrashPolicy.ContainAndReport][io.github.linreal.cascade.editor.CrashPolicy.ContainAndReport]
 * contains failures during measurement, placement, and drawing. Compose does
 * not provide a reliable in-tree boundary for exceptions thrown while this
 * composable is being invoked, so composition-time failures remain trusted
 * extension code and should be surfaced during testing.
 *
 * @param T The specific [BlockType] this renderer handles.
 */
@ExperimentalCascadePreviewApi
public interface BlockPreviewRenderer<T : BlockType> {
    /**
     * Renders an immutable block snapshot without editor runtime machinery.
     *
     * @param block Immutable source block to summarize.
     * @param modifier Host-provided sizing modifier. Apply it once to the
     * renderer's root layout.
     * @param scope Read-only document context and link capability.
     */
    @Composable
    public fun RenderPreview(
        block: Block,
        modifier: Modifier,
        scope: BlockPreviewScope,
    )
}
