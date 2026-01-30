package io.github.linreal.cascade.editor

import androidx.compose.runtime.Immutable

/**
 * Represents a content block in the editor.
 */
@Immutable
public data class Block(
    /**
     * Unique identifier for this block.
     */
    val id: String,

    /**
     * The type of content this block holds.
     */
    val type: BlockType,

    /**
     * The content data for this block.
     */
    val content: BlockContent,
)

/**
 * Defines the type of a block.
 */
public sealed interface BlockType {
    /**
     * A paragraph text block.
     */
    public data object Paragraph : BlockType

    /**
     * A heading block with a specified level (1-6).
     */
    public data class Heading(val level: Int) : BlockType {
        init {
            require(level in 1..6) { "Heading level must be between 1 and 6" }
        }
    }

    /**
     * A horizontal divider.
     */
    public data object Divider : BlockType
}

/**
 * Content data for a block.
 */
public sealed interface BlockContent {
    /**
     * Text content with optional styling spans.
     */
    public data class Text(
        val text: String,
    ) : BlockContent

    /**
     * Empty content (used for dividers, etc.).
     */
    public data object Empty : BlockContent
}
