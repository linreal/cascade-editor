package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.CascadeErrorReporter
import io.github.linreal.cascade.editor.CrashPolicy
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.guarded
import io.github.linreal.cascade.editor.reportContainedFailure
import io.github.linreal.cascade.editor.registry.BlockPreviewRenderer
import io.github.linreal.cascade.editor.registry.BlockPreviewScope
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.theme.LocalCascadeBlockStrings
import io.github.linreal.cascade.editor.theme.LocalCascadeStrings
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import io.github.linreal.cascade.editor.ui.renderers.LocalOrderedListPrefixStyles
import io.github.linreal.cascade.editor.ui.renderers.UnknownBlockPreviewRenderer
import io.github.linreal.cascade.editor.ui.renderers.resolveOrderedListPrefixStyles

/**
 * Renders an immutable Cascade document snapshot as bounded, static Compose UI.
 *
 * This component is designed for cards, feeds, search results, and other
 * preview surfaces. It creates no [EditorStateHolder][io.github.linreal.cascade.editor.state.EditorStateHolder],
 * text field, editor focus/IME, history, toolbar, drag, slash-command, or
 * internal scroll runtime. Link annotations may still expose their normal
 * accessibility focus semantics. The caller owns both the surrounding card
 * and all scrolling.
 *
 * Preview is not a permission boundary and does not replace
 * [CascadeEditor]'s read-only mode. Use the editor when the surface needs
 * selectable text-field state, document scrolling, or a transition back to
 * editing without remounting.
 *
 * The input is never normalized, renumbered, truncated into a persistence
 * model, or given a trailing paragraph. [config] only controls how much of the
 * immutable input is presented.
 *
 * Consumers rendering many previews should hoist and share a stable [registry],
 * [theme], [strings], and [blockStrings]. Parse persisted JSON, HTML, or Markdown
 * before entering item composition.
 *
 * @param blocks Original ordered document blocks.
 * @param modifier Modifier for the non-scrollable preview root.
 * @param registry Registry containing dedicated preview renderers. Editor
 * renderers are never invoked as a fallback.
 * @param theme Visual theme shared with Cascade editor rendering.
 * @param strings Localized UI strings used by safe preview fallbacks.
 * @param blockStrings Localized block metadata made available to built-in
 * preview infrastructure.
 * @param config Presentation, interaction, and containment policy.
 * @param onOpenLink Optional host-controlled link activation callback.
 */
@ExperimentalCascadePreviewApi
@Composable
public fun CascadeDocumentPreview(
    blocks: List<Block>,
    modifier: Modifier = Modifier,
    registry: BlockRegistry = remember { createEditorRegistry() },
    theme: CascadeEditorTheme = remember { CascadeEditorTheme.light() },
    strings: CascadeEditorStrings = remember { CascadeEditorStrings.default() },
    blockStrings: CascadeEditorBlockStrings = remember {
        CascadeEditorBlockStrings.default()
    },
    config: CascadeDocumentPreviewConfig = CascadeDocumentPreviewConfig.Default,
    onOpenLink: ((String) -> Unit)? = null,
) {
    val visibleBlocks = remember(blocks, config.maxBlocks) {
        blocks.previewPrefix(config.maxBlocks)
    }
    val orderedListPrefixStyles = remember(visibleBlocks) {
        resolveOrderedListPrefixStyles(visibleBlocks)
    }
    val hostDensity = LocalDensity.current
    // Keep this at the composition boundary so future built-ins and custom
    // renderers inherit the same scale without rewriting individual styles.
    // Logical density stays unchanged: dp tokens are not scaled directly.
    val previewDensity = remember(hostDensity, config.textScale) {
        hostDensity.withPreviewTextScale(config.textScale)
    }
    val currentOnOpenLink = rememberUpdatedState(onOpenLink)
    val hasLinkOpener = onOpenLink != null
    val previewScope = remember(blocks, config, hasLinkOpener) {
        DefaultBlockPreviewScope(
            blocks = blocks,
            config = config,
            onOpenLink = if (hasLinkOpener) {
                { target -> currentOnOpenLink.value?.invoke(target) }
            } else {
                null
            },
        )
    }
    val textSelectionColors = remember(
        theme.colors.cursor,
        theme.colors.textSelectionBackground,
    ) {
        TextSelectionColors(
            handleColor = theme.colors.cursor,
            backgroundColor = theme.colors.textSelectionBackground,
        )
    }

    CompositionLocalProvider(
        LocalCascadeTheme provides theme,
        LocalCascadeStrings provides strings,
        LocalCascadeBlockStrings provides blockStrings,
        LocalTextSelectionColors provides textSelectionColors,
        LocalOrderedListPrefixStyles provides orderedListPrefixStyles,
        LocalDensity provides previewDensity,
    ) {
        val rootModifier = modifier
            .fillMaxWidth()
            .clipToBounds()

        if (config.textSelectionEnabled) {
            SelectionContainer(modifier = rootModifier) {
                PreviewBlockColumn(
                    blocks = visibleBlocks,
                    registry = registry,
                    scope = previewScope,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            PreviewBlockColumn(
                blocks = visibleBlocks,
                registry = registry,
                scope = previewScope,
                modifier = rootModifier,
            )
        }
    }
}

private fun Density.withPreviewTextScale(textScale: Float): Density {
    if (textScale == 1f) return this

    val scaledFontScale = (fontScale.toDouble() * textScale.toDouble()).toFloat()
    if (!scaledFontScale.isFinite() || scaledFontScale <= 0f) return this

    return Density(
        density = density,
        fontScale = scaledFontScale,
    )
}

@OptIn(ExperimentalCascadePreviewApi::class)
@Composable
private fun PreviewBlockColumn(
    blocks: List<Block>,
    registry: BlockRegistry,
    scope: BlockPreviewScope,
    modifier: Modifier,
) {
    val dimensions = LocalCascadeTheme.current.dimensions
    val registryRevision = registry.revision
    val crashPolicy = scope.config.crashPolicy
    val crashReporter = scope.config.onInternalError

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            // Include source index so malformed duplicate block IDs cannot
            // collide at the never-crash preview boundary.
            key(index, block.id.value) {
                val renderer = remember(registry, registryRevision, block.type) {
                    registry.getPreviewRenderer(block.type)
                        ?: UnknownBlockPreviewRenderer
                }
                val failureState = remember(
                    renderer,
                    registryRevision,
                    block,
                    scope,
                ) {
                    PreviewBlockFailureState(crashReporter)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .guardedPreviewBlockRender(
                            policy = crashPolicy,
                            failureState = failureState,
                            typeId = block.type.typeId,
                        )
                        .padding(
                            horizontal = dimensions.blockHorizontalPadding,
                            vertical = PreviewBlockVerticalPadding,
                        ),
                ) {
                    RenderPreviewBlock(
                        renderer = renderer,
                        block = block,
                        modifier = Modifier.fillMaxWidth(),
                        scope = scope,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalCascadePreviewApi::class)
@Composable
private fun RenderPreviewBlock(
    renderer: BlockPreviewRenderer<*>,
    block: Block,
    modifier: Modifier,
    scope: BlockPreviewScope,
) {
    renderer.RenderPreview(
        block = block,
        modifier = modifier,
        scope = scope,
    )
}

/**
 * Applies the block presentation limit without modifying block snapshots.
 *
 * The original list is retained when already within budget. A bounded prefix
 * copies at most the configured card budget, avoiding work proportional to
 * hidden document content.
 */
internal fun List<Block>.previewPrefix(maxBlocks: Int?): List<Block> {
    if (maxBlocks == null || size <= maxBlocks) return this
    return take(maxBlocks)
}

/**
 * Contains preview measure, placement, and draw failures. Once a mounted
 * renderer fails, subsequent layout/draw passes skip it instead of repeatedly
 * paying the exception cost. Compose does not permit a safe try/catch boundary
 * around a composable invocation, so composition failures remain a renderer
 * trust boundary just as they are in [CascadeEditor].
 */
private fun Modifier.guardedPreviewBlockRender(
    policy: CrashPolicy,
    failureState: PreviewBlockFailureState,
    typeId: String,
): Modifier {
    if (policy == CrashPolicy.Rethrow) return this
    return this
        .layout { measurable, constraints ->
            if (failureState.failed) {
                layout(0, 0) {}
            } else {
                val placeable = guarded(
                    policy = policy,
                    reporter = failureState.reporter,
                    context = "previewBlockMeasure:$typeId",
                    fallback = { null },
                ) {
                    measurable.measure(constraints)
                }
                if (placeable == null) {
                    layout(0, 0) {}
                } else {
                    layout(placeable.width, placeable.height) {
                        if (!failureState.failed) {
                            guarded(
                                policy = policy,
                                reporter = failureState.reporter,
                                context = "previewBlockPlace:$typeId",
                                fallback = {},
                            ) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                    }
                }
            }
        }
        .drawWithContent {
            if (!failureState.failed) {
                guarded(
                    policy = policy,
                    reporter = failureState.reporter,
                    context = "previewBlockDraw:$typeId",
                    fallback = {},
                ) {
                    drawContent()
                }
            }
        }
}

private class PreviewBlockFailureState(
    private val hostReporter: CascadeErrorReporter?,
) {
    var failed: Boolean = false
        private set

    private var hasReported: Boolean = false

    val reporter: CascadeErrorReporter = { error ->
        failed = true
        if (!hasReported) {
            hasReported = true
            reportContainedFailure(hostReporter, error)
        }
    }
}

private val PreviewBlockVerticalPadding = 4.dp
