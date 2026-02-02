package io.github.linreal.cascade.editor.core

/**
 * Sealed hierarchy representing the content of a block.
 */
public sealed interface BlockContent {
    /**
     * Text content for blocks like paragraphs, headings, lists.
     */
    public data class Text(val text: String) : BlockContent

    /**
     * Image content with URI and optional alt text.
     */
    public data class Image(val uri: String, val altText: String? = null) : BlockContent

    /**
     * Empty content for blocks like dividers.
     */
    public data object Empty : BlockContent

    /**
     * Custom content for extension blocks.
     * @param typeId Identifier matching the custom block type
     * @param data Arbitrary data map for the custom content
     */
    public data class Custom(
        val typeId: String,
        val data: Map<String, Any?> = emptyMap()
    ) : BlockContent
}
