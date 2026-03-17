package io.github.linreal.cascade.editor.serialization

import io.github.linreal.cascade.editor.core.Block

/**
 * Result of a document decode operation, containing the decoded blocks and any warnings.
 */
public data class DocumentDecodeResult(
    val blocks: List<Block>,
    val warnings: List<DocumentDecodeWarning>,
)
