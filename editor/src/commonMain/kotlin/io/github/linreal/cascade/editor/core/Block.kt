package io.github.linreal.cascade.editor.core

import androidx.compose.runtime.Immutable

/**
 * Immutable data class representing a single block in the editor.
 *
 * @property id Unique identifier for this block
 * @property type The type of block (paragraph, heading, etc.)
 * @property content The content stored in this block
 */
@Immutable
public data class Block(
    val id: BlockId,
    val type: BlockType,
    val content: BlockContent
) {
    /**
     * Creates a copy with a new type.
     */
    public fun withType(newType: BlockType): Block = copy(type = newType)

    /**
     * Creates a copy with new content.
     */
    public fun withContent(newContent: BlockContent): Block = copy(content = newContent)
    
    public companion object {
        /**
         * Creates a new paragraph block with the given text and optional spans.
         */
        public fun paragraph(
            text: String = "",
            spans: List<TextSpan> = emptyList(),
        ): Block = Block(
            id = BlockId.generate(),
            type = BlockType.Paragraph,
            content = BlockContent.Text(text = text, spans = spans)
        )

        /**
         * Creates a new heading block with the given level and text.
         */
        public fun heading(level: Int, text: String = ""): Block = Block(
            id = BlockId.generate(),
            type = BlockType.Heading(level),
            content = BlockContent.Text(text)
        )

        /**
         * Creates a new todo block.
         */
        public fun todo(text: String = "", checked: Boolean = false): Block = Block(
            id = BlockId.generate(),
            type = BlockType.Todo(checked),
            content = BlockContent.Text(text)
        )

        /**
         * Creates a new bullet list item block.
         */
        public fun bulletList(text: String = ""): Block = Block(
            id = BlockId.generate(),
            type = BlockType.BulletList,
            content = BlockContent.Text(text)
        )

        /**
         * Creates a new divider block.
         */
        public fun divider(): Block = Block(
            id = BlockId.generate(),
            type = BlockType.Divider,
            content = BlockContent.Empty
        )
    }
}
