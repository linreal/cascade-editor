package io.github.linreal.cascade.editor.registry

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId

/**
 * Categories for organizing block types in the slash command menu.
 */
public enum class BlockCategory {
    BASIC,
    MEDIA,
    ADVANCED,
    INLINE,
    CUSTOM
}

/**
 * Metadata describing a block type for registration, slash commands, and serialization.
 *
 * @property typeId Unique identifier matching [BlockType.typeId]
 * @property displayName Human-readable name shown in UI
 * @property description Brief description for slash command menu
 * @property keywords Search keywords for slash command filtering
 * @property category Category for organizing in menus
 * @property icon Optional icon identifier
 * @property factory Creates a new block instance with a given ID
 */
public data class BlockDescriptor(
    val typeId: String,
    val displayName: String,
    val description: String,
    val keywords: List<String> = emptyList(),
    val category: BlockCategory = BlockCategory.BASIC,
    val icon: String? = null,
    val factory: (BlockId) -> Block
) {
    /**
     * Creates a new block using the factory with a generated ID.
     */
    public fun createBlock(): Block = factory(BlockId.generate())

    /**
     * Creates a new block using the factory with the specified ID.
     */
    public fun createBlock(id: BlockId): Block = factory(id)

    /**
     * Returns true if this descriptor matches the search query.
     * Searches display name, description, and keywords (case-insensitive).
     */
    public fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val lowerQuery = query.lowercase()
        return displayName.lowercase().contains(lowerQuery) ||
                description.lowercase().contains(lowerQuery) ||
                keywords.any { it.lowercase().contains(lowerQuery) }
    }

    /**
     * Returns a relevance score for sorting search results.
     * Higher score = more relevant.
     */
    public fun relevanceScore(query: String): Int {
        if (query.isBlank()) return 0
        val lowerQuery = query.lowercase()

        // Exact match on display name
        if (displayName.lowercase() == lowerQuery) return 100

        // Starts with query
        if (displayName.lowercase().startsWith(lowerQuery)) return 80

        // Contains in display name
        if (displayName.lowercase().contains(lowerQuery)) return 60

        // Exact keyword match
        if (keywords.any { it.lowercase() == lowerQuery }) return 50

        // Keyword starts with
        if (keywords.any { it.lowercase().startsWith(lowerQuery) }) return 40

        // Keyword contains
        if (keywords.any { it.lowercase().contains(lowerQuery) }) return 30

        // Description contains
        if (description.lowercase().contains(lowerQuery)) return 10

        return 0
    }
}
