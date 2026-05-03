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
     * Whether this block type participates in v1 document indentation semantics.
     */
    public val supportsIndentation: Boolean get() = false

    /**
     * Whether this block type supports rich-text spans (bold, italic, link, inline code, etc.).
     *
     * Defaults to [supportsText] so existing text-supporting block types remain spans-capable
     * without explicit overrides. Non-text blocks remain spans-incapable for free.
     *
     * Override to `false` for text-supporting blocks that should opt out of spans
     * (e.g. [Code], where the body is treated as plain monospace text).
     *
     * Span state initialization, formatting toolbar gates, link gates, span reducers,
     * and serialization paths all key on this flag — see Task 2 in
     * `docs/tasks-code-block-support.md` for the gating wiring.
     */
    public val supportsSpans: Boolean get() = supportsText

    /**
     * Standard paragraph block.
     */
    public data object Paragraph : BlockType {
        override val typeId: String = "paragraph"
        override val displayName: String = "Paragraph"
        override val supportsText: Boolean = true
        override val supportsIndentation: Boolean = true
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
        override val supportsIndentation: Boolean = true
    }

    /**
     * Bullet list item.
     */
    public data object BulletList : BlockType {
        override val typeId: String = "bullet_list"
        override val displayName: String = "Bullet List"
        override val supportsText: Boolean = true
        override val supportsIndentation: Boolean = true
    }

    /**
     * Numbered list item with sequential number.
     */
    public data class NumberedList(val number: Int = 1) : BlockType {
        init {
            require(number >= 1) { "NumberedList number must be >= 1, got $number" }
        }

        override val typeId: String = "numbered_list"
        override val displayName: String = "Numbered List"
        override val supportsText: Boolean = true
        override val supportsIndentation: Boolean = true
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
     * Plain code block. Hosts multi-line monospace text without rich-text spans.
     *
     * Opt-out of spans is the only capability difference from a Paragraph: span state,
     * formatting actions, link actions, and span persistence are all gated off via
     * [supportsSpans] = `false`. Indentation is also disabled because code bodies
     * are flat and managed in-text via newlines.
     */
    public data object Code : BlockType {
        override val typeId: String = "code"
        override val displayName: String = "Code"
        override val supportsText: Boolean = true
        override val supportsIndentation: Boolean = false
        override val supportsSpans: Boolean = false
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
}

/**
 * Extension point for custom block types.
 * Implement this interface to create your own block types.
 */
public interface CustomBlockType : BlockType
