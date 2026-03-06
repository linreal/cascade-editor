package io.github.linreal.cascade.editor.richtext

import androidx.compose.runtime.Stable
import io.github.linreal.cascade.editor.core.SpanStyle

/**
 * Action interface for formatting operations. Implementations resolve
 * the focused block and selection at invocation time and delegate to
 * [SpanActionDispatcher]. No-op when formatting is disallowed.
 */
@Stable
public interface FormattingActions {
    public fun toggleStyle(style: SpanStyle)
    public fun applyStyle(style: SpanStyle)
    public fun removeStyle(style: SpanStyle)
}
