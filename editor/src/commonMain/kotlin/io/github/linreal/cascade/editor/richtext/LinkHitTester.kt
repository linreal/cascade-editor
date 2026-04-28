package io.github.linreal.cascade.editor.richtext

import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan

/**
 * Pure visible-coordinate link hit testing for editable text blocks.
 */
internal object LinkHitTester {

    /**
     * Resolves the normalized link URL at [visibleOffset], or `null` when the
     * offset is outside link coverage or the stored URL no longer passes policy.
     *
     * The offset uses visible-text coordinates and the same half-open range
     * contract as [TextSpan]: `start <= offset < end`.
     */
    internal fun linkUrlAtVisibleOffset(
        spans: List<TextSpan>,
        visibleOffset: Int,
    ): String? {
        val link = spans.firstOrNull { span ->
            span.style is SpanStyle.Link &&
                span.start <= visibleOffset &&
                visibleOffset < span.end
        }?.style as? SpanStyle.Link

        return link?.let { LinkUrlPolicy.validate(it.url).normalizedUrl }
    }

    /**
     * Resolves a URL for a pointer tap only when link opening is allowed for the
     * current editor interaction mode.
     */
    internal fun linkUrlForTap(
        isFocused: Boolean,
        isDragging: Boolean,
        hasBlockSelection: Boolean,
        hasTextSelection: Boolean = false,
        visibleOffset: Int?,
        spans: List<TextSpan>,
    ): String? {
        if (isFocused || isDragging || hasBlockSelection || hasTextSelection || visibleOffset == null) {
            return null
        }
        return linkUrlAtVisibleOffset(spans, visibleOffset)
    }
}
