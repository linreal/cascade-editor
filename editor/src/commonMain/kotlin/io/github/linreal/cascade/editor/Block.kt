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
     * A bulleted list item.
     */
    public data object BulletList : BlockType

    /**
     * A numbered list item.
     */
    public data object NumberedList : BlockType

    /**
     * A checkbox/todo item.
     */
    public data class Todo(val checked: Boolean = false) : BlockType

    /**
     * A code block with optional language hint.
     */
    public data class Code(val language: String? = null) : BlockType

    /**
     * A quote/blockquote block.
     */
    public data object Quote : BlockType

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
        val spans: List<TextSpan> = emptyList(),
    ) : BlockContent

    /**
     * Empty content (used for dividers, etc.).
     */
    public data object Empty : BlockContent
}

/**
 * Represents a styled span within text content.
 */
@Immutable
public data class TextSpan(
    val start: Int,
    val end: Int,
    val style: TextStyle,
)

/**
 * Text styling options.
 */
public sealed interface TextStyle {
    public data object Bold : TextStyle
    public data object Italic : TextStyle
    public data object Underline : TextStyle
    public data object Strikethrough : TextStyle
    public data object Code : TextStyle
    public data class Link(val url: String) : TextStyle
}
