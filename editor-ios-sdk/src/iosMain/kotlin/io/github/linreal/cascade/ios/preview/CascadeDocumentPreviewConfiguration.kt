@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.preview

import io.github.linreal.cascade.editor.CascadeErrorReporter
import io.github.linreal.cascade.editor.theme.CascadeEditorColors
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.ui.CascadeDocumentPreviewConfig
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi
import io.github.linreal.cascade.ios.controller.CascadeCrashPolicy
import io.github.linreal.cascade.ios.controller.toCoreCrashPolicy
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

private const val DEFAULT_MAX_BLOCKS: Int = 4
private const val DEFAULT_MAX_LINES_PER_TEXT_BLOCK: Int = 3
private const val DEFAULT_TEXT_SCALE: Double = 1.0

/**
 * Swift-friendly presentation policy for a bounded native document preview.
 *
 * This facade is experimental. Its source and binary API may change before
 * preview mode is stabilized.
 *
 * The native facade intentionally exposes positive, non-null limits and a
 * Swift-native [Double] text scale rather than leaking Compose nullable-number
 * or text-overflow types into Swift. Non-positive limits are normalized to
 * `1`. A non-positive, non-finite, or core-unrepresentable text scale is
 * normalized to `1.0`. Validation therefore never throws across the
 * Swift/Objective-C boundary.
 */
@ObjCName("CascadeDocumentPreviewConfiguration", exact = true)
public data class CascadeDocumentPreviewConfiguration(
    public val maxBlocks: Int,
    public val maxLinesPerTextBlock: Int,
    public val textScale: Double,
    public val textSelectionEnabled: Boolean,
    public val linksEnabled: Boolean,
    public val isDark: Boolean,
    public val crashPolicy: CascadeCrashPolicy,
) {
    /**
     * Compatibility initializer for hosts compiled against the original
     * bounded-preview configuration shape.
     */
    public constructor(
        maxBlocks: Int,
        maxLinesPerTextBlock: Int,
        textSelectionEnabled: Boolean,
        linksEnabled: Boolean,
        isDark: Boolean,
        crashPolicy: CascadeCrashPolicy,
    ) : this(
        maxBlocks = maxBlocks,
        maxLinesPerTextBlock = maxLinesPerTextBlock,
        textScale = DEFAULT_TEXT_SCALE,
        textSelectionEnabled = textSelectionEnabled,
        linksEnabled = linksEnabled,
        isDark = isDark,
        crashPolicy = crashPolicy,
    )

    public constructor() : this(
        maxBlocks = DEFAULT_MAX_BLOCKS,
        maxLinesPerTextBlock = DEFAULT_MAX_LINES_PER_TEXT_BLOCK,
        textScale = DEFAULT_TEXT_SCALE,
        textSelectionEnabled = false,
        linksEnabled = true,
        isDark = false,
        crashPolicy = CascadeCrashPolicy.containAndReport,
    )
}

@OptIn(ExperimentalCascadePreviewApi::class)
internal fun CascadeDocumentPreviewConfiguration.toCoreConfig(
    onInternalError: CascadeErrorReporter? = null,
): CascadeDocumentPreviewConfig = CascadeDocumentPreviewConfig(
    maxBlocks = maxBlocks.coerceAtLeast(1),
    maxLinesPerTextBlock = maxLinesPerTextBlock.coerceAtLeast(1),
    textScale = textScale.toCoreTextScale(),
    textSelectionEnabled = textSelectionEnabled,
    linksEnabled = linksEnabled,
    crashPolicy = crashPolicy.toCoreCrashPolicy(),
    onInternalError = onInternalError,
)

private fun Double.toCoreTextScale(): Float =
    toFloat().takeIf { it.isFinite() && it > 0f } ?: DEFAULT_TEXT_SCALE.toFloat()

internal fun CascadeDocumentPreviewConfiguration.resolveEditorTheme(
    customColors: CascadeEditorColors? = null,
): CascadeEditorTheme {
    val preset = if (isDark) CascadeEditorTheme.dark() else CascadeEditorTheme.light()
    return if (customColors == null) preset else preset.copy(colors = customColors)
}
