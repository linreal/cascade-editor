package io.github.linreal.cascade.editor.slash

/**
 * Central registry and search engine for slash command items.
 *
 * All items are registered at the root level. Submenus are expressed via
 * [SlashCommandMenu.children] and navigated by `path` in [search].
 *
 * **Duplicate-registration policy:** the latest registration wins. If `register(item)` is
 * called with an [SlashCommandItem.id] that already exists, the previous entry is replaced.
 *
 * Thread-safety: internal collections are guarded by a lock so `register()` may be called
 * from different coroutines.
 */
public class SlashCommandRegistry {

    private val lock = Any()

    private val rootItems = linkedMapOf<SlashCommandId, SlashCommandItem>()

    /**
     * Registers a root-level slash command item (action or menu).
     * If an item with the same [SlashCommandItem.id] is already registered, it is replaced.
     */
    public fun register(item: SlashCommandItem) {
        synchronized(lock) {
            rootItems[item.id] = item
        }
    }

    /**
     * Returns all root-level items in registration order.
     */
    public fun getRootItems(): List<SlashCommandItem> {
        synchronized(lock) {
            return rootItems.values.toList()
        }
    }

    /**
     * Searches for items matching [query] at the menu level addressed by [path].
     *
     * - An empty [path] searches root items.
     * - Each element in [path] is a [SlashCommandId] of a [SlashCommandMenu] to drill into.
     * - If any segment in [path] cannot be resolved, an empty list is returned.
     * - An empty [query] returns all items at the addressed level (unfiltered, deterministic order).
     * - Results are ranked deterministically (see [rankItem]).
     */
    public fun search(
        query: String,
        path: List<SlashCommandId> = emptyList(),
    ): List<SlashCommandItem> {
        val items: List<SlashCommandItem>
        synchronized(lock) {
            items = resolveLevel(path) ?: return emptyList()
        }
        if (query.isBlank()) return items
        return items
            .mapNotNull { item -> rankItem(item, query)?.let { score -> item to score } }
            .sortedWith(compareByDescending<Pair<SlashCommandItem, Int>> { it.second }.thenBy { it.first.title })
            .map { it.first }
    }

    // ---- internal helpers ----

    /**
     * Walks [path] from root items and returns the children at the final level,
     * or null if any segment is not a [SlashCommandMenu].
     */
    private fun resolveLevel(path: List<SlashCommandId>): List<SlashCommandItem>? {
        var currentItems: List<SlashCommandItem> = rootItems.values.toList()
        for (segment in path) {
            val menu = currentItems.firstOrNull { it.id == segment } as? SlashCommandMenu
                ?: return null
            currentItems = menu.children
        }
        return currentItems
    }

    /**
     * Scores an item against [query]. Returns null if the item does not match at all.
     *
     * Ranking tiers (higher = better):
     * - 100 exact title match
     * -  80 title starts with query
     * -  60 title contains query
     * -  50 exact keyword match
     * -  40 keyword starts with query
     * -  30 keyword contains query
     * -  10 description contains query
     */
    internal fun rankItem(item: SlashCommandItem, query: String): Int? {
        if (query.isBlank()) return 0
        val q = query.lowercase()
        val titleLower = item.title.lowercase()

        val score = when {
            titleLower == q -> 100
            titleLower.startsWith(q) -> 80
            titleLower.contains(q) -> 60
            item.keywords.any { it.lowercase() == q } -> 50
            item.keywords.any { it.lowercase().startsWith(q) } -> 40
            item.keywords.any { it.lowercase().contains(q) } -> 30
            item.description.lowercase().contains(q) -> 10
            else -> return null
        }
        return score
    }
}
