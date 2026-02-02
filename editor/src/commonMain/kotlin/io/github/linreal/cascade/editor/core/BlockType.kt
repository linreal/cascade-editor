package io.github.linreal.cascade.editor.core

/**
 * Sealed interface representing the type of a block.
 * Built-in types are defined as nested objects/classes.
 * Custom blocks should implement [CustomBlockType].
 */
public sealed interface BlockType {
    /**
     * Unique identifier for this block type.
     */
    public val typeId: String

    /**
     * Human-readable name for display in UI.
     */
    public val displayName: String

    /**
     * Whether this block type supports text content.
     */
    public val supportsText: Boolean get() = false

    /**
     * Whether this block can be converted to/from other block types.
     * Typically true for text-supporting blocks.
     */
    public val isConvertible: Boolean get() = supportsText

    /**
     * Standard paragraph block.
     */
    public data object Paragraph : BlockType {
        override val typeId: String = "paragraph"
        override val displayName: String = "Paragraph"
        override val supportsText: Boolean = true
    }

    /**
     * Heading block with level 1-6.
     */
    public data class Heading(val level: Int) : BlockType {
        init {
            require(level in 1..6) { "Heading level must be between 1 and 6, got $level" }
        }

        override val typeId: String = "heading_$level"
        override val displayName: String = "Heading $level"
        override val supportsText: Boolean = true
    }

    /**
     * Todo/checkbox block with checked state.
     */
    public data class Todo(val checked: Boolean = false) : BlockType {
        override val typeId: String = "todo"
        override val displayName: String = "To-do"
        override val supportsText: Boolean = true
    }

    /**
     * Bullet list item.
     */
    public data object BulletList : BlockType {
        override val typeId: String = "bullet_list"
        override val displayName: String = "Bullet List"
        override val supportsText: Boolean = true
    }

    /**
     * Numbered list item.
     */
    public data object NumberedList : BlockType {
        override val typeId: String = "numbered_list"
        override val displayName: String = "Numbered List"
        override val supportsText: Boolean = true
    }

    /**
     * Quote block.
     */
    public data object Quote : BlockType {
        override val typeId: String = "quote"
        override val displayName: String = "Quote"
        override val supportsText: Boolean = true
    }

    /**
     * Code block.
     */
    public data class Code(val language: String? = null) : BlockType {
        override val typeId: String = "code"
        override val displayName: String = "Code"
        override val supportsText: Boolean = true
        override val isConvertible: Boolean = false
    }

    /**
     * Horizontal divider/separator.
     */
    public data object Divider : BlockType {
        override val typeId: String = "divider"
        override val displayName: String = "Divider"
        override val supportsText: Boolean = false
        override val isConvertible: Boolean = false
    }

    /**
     * Image block.
     */
    public data object Image : BlockType {
        override val typeId: String = "image"
        override val displayName: String = "Image"
        override val supportsText: Boolean = false
        override val isConvertible: Boolean = false
    }
}

/**
 * Extension point for custom block types.
 * Implement this interface to create your own block types.
 */
public interface CustomBlockType : BlockType
