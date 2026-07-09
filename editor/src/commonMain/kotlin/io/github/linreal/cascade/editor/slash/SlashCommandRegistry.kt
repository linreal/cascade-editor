package io.github.linreal.cascade.editor.slash

import androidx.compose.runtime.mutableIntStateOf

/**
 * Central registry and search engine for slash command items.
 *
 * All items are registered at the root level. Submenus are expressed via
 * [SlashCommandMenu.children] and navigated by `path` in [search].
 *
 * **Duplicate-registration policy:** the latest registration wins. If `register(item)` is
 * called with an [SlashCommandItem.id] that already exists, the previous entry is replaced.
 *
 * **Immutability contract:** items are snapshotted at registration time. Mutating an item's
 * fields (e.g. a backing `MutableList` passed as `keywords`) after calling [register] will
 * **not** be reflected in search results. Re-register the item to update the index.
 *
 * **Threading:** this class is **not** thread-safe. All access must happen on the main thread,
 * which is the standard Compose threading model.
 */
public class SlashCommandRegistry {

    private val rootItems = linkedMapOf<SlashCommandId, IndexedItem>()

    // Snapshot-backed change counter. Bumped on every registration so composables that
    // derive from this registry (e.g. the merged slash registry the mounted editor
    // searches) can observe commands registered while the editor is already mounted.
    private val revisionState = mutableIntStateOf(0)
    internal val revision: Int get() = revisionState.intValue

    /**
     * Registers a root-level slash command item (action or menu).
     * If an item with the same [SlashCommandItem.id] is already registered, it is replaced.
     */
    public fun register(item: SlashCommandItem) {
        rootItems[item.id] = IndexedItem(item)
        revisionState.intValue++
    }

    /**
     * Returns all root-level items in registration order.
     */
    public fun getRootItems(): List<SlashCommandItem> {
        return rootItems.values.map { it.item }
    }

    /**
     * Searches for items matching [query] at the menu level addressed by [path].
     *
     * - An empty [path] searches root items.
     * - Each element in [path] is a [SlashCommandId] of a [SlashCommandMenu] to drill into.
     * - If any segment in [path] cannot be resolved, an empty list is returned.
     * - An empty [query] returns all items at the addressed level (unfiltered, deterministic order).
     * - Whitespace in the query is significant and participates in matching.
     * - Results are ranked deterministically (see [rankItem]).
     */
    public fun search(
        query: String,
        path: List<SlashCommandId> = emptyList(),
    ): List<SlashCommandItem> {
        val indexed: List<IndexedItem> = resolveLevel(path) ?: return emptyList()
        if (query.isEmpty()) return indexed.map { it.item }
        val q = query.lowercase()
        return indexed
            .mapNotNull { idx -> rankItem(idx, q)?.let { score -> idx.item to score } }
            .sortedWith(compareByDescending<Pair<SlashCommandItem, Int>> { it.second }.thenBy { it.first.title })
            .map { it.first }
    }

    // ---- internal helpers ----

    /**
     * Walks [path] from root items and returns the indexed children at the final level,
     * or null if any segment is not a [SlashCommandMenu].
     *
     * Children are de-duplicated by [SlashCommandId] (last wins) to match root behavior.
     */
    private fun resolveLevel(path: List<SlashCommandId>): List<IndexedItem>? {
        var currentItems: List<IndexedItem> = rootItems.values.toList()
        for (segment in path) {
            val menu = currentItems.firstOrNull { it.item.id == segment }?.item as? SlashCommandMenu
                ?: return null
            currentItems = deduplicateChildren(menu.children)
        }
        return currentItems
    }

    /**
     * De-duplicates children by [SlashCommandId], last wins.
     */
    private fun deduplicateChildren(children: List<SlashCommandItem>): List<IndexedItem> {
        val deduped = linkedMapOf<SlashCommandId, IndexedItem>()
        for (child in children) {
            deduped[child.id] = IndexedItem(child)
        }
        return deduped.values.toList()
    }

    /**
     * Scores an indexed item against the pre-lowercased [query].
     * Returns null if the item does not match at all.
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
    internal fun rankItem(indexed: IndexedItem, query: String): Int? {
        if (query.isEmpty()) return 0
        val score = when {
            indexed.titleLower == query -> 100
            indexed.titleLower.startsWith(query) -> 80
            indexed.titleLower.contains(query) -> 60
            indexed.keywordsLower.any { it == query } -> 50
            indexed.keywordsLower.any { it.startsWith(query) } -> 40
            indexed.keywordsLower.any { it.contains(query) } -> 30
            indexed.descriptionLower.contains(query) -> 10
            else -> return null
        }
        return score
    }

    /**
     * Overload that accepts a raw [SlashCommandItem] for testing convenience.
     */
    internal fun rankItem(item: SlashCommandItem, query: String): Int? {
        return rankItem(IndexedItem(item), query.lowercase())
    }

    /**
     * Pre-normalized snapshot of a [SlashCommandItem] for efficient search.
     *
     * All fields are defensively copied at construction time so that later
     * mutation of the source item does not corrupt the search index.
     */
    internal class IndexedItem(val item: SlashCommandItem) {
        val titleLower: String = item.title.lowercase()
        val descriptionLower: String = item.description.lowercase()
        val keywordsLower: List<String> = item.keywords.map { it.lowercase() }
    }
}
