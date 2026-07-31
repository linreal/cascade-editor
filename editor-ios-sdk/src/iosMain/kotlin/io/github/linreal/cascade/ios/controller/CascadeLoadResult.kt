@file:OptIn(
    ExperimentalObjCName::class,
    io.github.linreal.cascade.editor.markdown.ExperimentalCascadeMarkdownApi::class,
)

package io.github.linreal.cascade.ios.controller

import io.github.linreal.cascade.editor.markdown.MarkdownDecodeResult
import io.github.linreal.cascade.editor.markdown.MarkdownEncodeResult
import io.github.linreal.cascade.editor.markdown.MarkdownFidelityImpact
import io.github.linreal.cascade.editor.markdown.MarkdownWarning
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@ObjCName("CascadeDocumentLoadResult", exact = true)
public data class CascadeDocumentLoadResult(
    public val success: Boolean,
    public val warningMessages: List<String>,
)

public typealias CascadeHtmlLoadResult = CascadeDocumentLoadResult

/**
 * Swift-facing result of a Markdown document load.
 *
 * [success] is false when the call is rejected or the codec aborts; neither
 * case applies a document. A successful result may still report fidelity loss;
 * hosts that retain the original Markdown should inspect [hasDataLoss] before
 * replacing that source.
 */
@ObjCName("CascadeMarkdownLoadResult", exact = true)
public data class CascadeMarkdownLoadResult(
    public val success: Boolean,
    public val warningMessages: List<String>,
    public val hasDataLoss: Boolean,
)

/**
 * Swift-facing result of a Markdown export.
 *
 * [markdown] is non-null exactly when [success] is true. [hasDataLoss] lets a
 * host reject a completed but lossy encode without parsing diagnostic strings.
 */
@ObjCName("CascadeMarkdownExportResult", exact = true)
public data class CascadeMarkdownExportResult(
    public val success: Boolean,
    public val markdown: String?,
    public val warningMessages: List<String>,
    public val hasDataLoss: Boolean,
)

internal fun MarkdownDecodeResult.toCascadeMarkdownLoadResult(): CascadeMarkdownLoadResult =
    CascadeMarkdownLoadResult(
        success = isSuccess,
        warningMessages = warnings.map(MarkdownWarning::toCascadeMarkdownWarningMessage),
        hasDataLoss = warnings.any { warning ->
            warning.impact == MarkdownFidelityImpact.DataLoss
        },
    )

internal fun MarkdownEncodeResult.toCascadeMarkdownExportResult(): CascadeMarkdownExportResult =
    CascadeMarkdownExportResult(
        success = isSuccess,
        markdown = markdown,
        warningMessages = warnings.map(MarkdownWarning::toCascadeMarkdownWarningMessage),
        hasDataLoss = warnings.any { warning ->
            warning.impact == MarkdownFidelityImpact.DataLoss
        },
    )

private fun MarkdownWarning.toCascadeMarkdownWarningMessage(): String = "$impact: $this"
