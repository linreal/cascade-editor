package io.github.linreal.cascade.editor.core

import androidx.compose.runtime.Immutable

private const val MIN_INDENTATION_LEVEL_VALUE: Int = 0
private const val MAX_INDENTATION_LEVEL_VALUE: Int = 5
private const val DEFAULT_INDENTATION_LEVEL_VALUE: Int = MIN_INDENTATION_LEVEL_VALUE

/**
 * Block-level metadata that belongs to the document model rather than a block type.
 *
 * @property indentationLevel Persistent outline depth for blocks that participate in indentation.
 */
@Immutable
public data class BlockAttributes(
    val indentationLevel: Int = DEFAULT_INDENTATION_LEVEL_VALUE,
) {
    init {
        require(indentationLevel in MIN_INDENTATION_LEVEL_VALUE..MAX_INDENTATION_LEVEL_VALUE) {
            "Block indentation level must be between $MIN_INDENTATION_LEVEL_VALUE and " +
                "$MAX_INDENTATION_LEVEL_VALUE, got $indentationLevel"
        }
    }

    public companion object {
        public const val MIN_INDENTATION_LEVEL: Int = MIN_INDENTATION_LEVEL_VALUE
        public const val MAX_INDENTATION_LEVEL: Int = MAX_INDENTATION_LEVEL_VALUE
        internal const val DEFAULT_INDENTATION_LEVEL: Int = DEFAULT_INDENTATION_LEVEL_VALUE

        public val Default: BlockAttributes = BlockAttributes()
    }
}
