package io.github.linreal.cascade.editor.core

import androidx.compose.runtime.Immutable

/**
 * A styled range within a block's visible text.
 *
 * Uses half-open interval semantics: [start, end) in visible-coordinate space.
 * Visible coordinates exclude any sentinel characters (e.g. ZWSP) used internally
 * by the text field — algorithm layers handle the mapping.
 *
 * @property start Inclusive start index in visible text
 * @property end Exclusive end index in visible text
 * @property style The style applied to this range
 */
@Immutable
public data class TextSpan(
    val start: Int,
    val end: Int,
    val style: SpanStyle,
) {
    init {
        require(start >= 0) { "start must be non-negative, got $start" }
        require(end >= start) { "end ($end) must be >= start ($start)" }
    }
}
