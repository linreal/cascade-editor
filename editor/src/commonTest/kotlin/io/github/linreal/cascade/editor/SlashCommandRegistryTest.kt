package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.slash.SlashCommandAction
import io.github.linreal.cascade.editor.slash.SlashCommandGroup
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.slash.SlashCommandItem
import io.github.linreal.cascade.editor.slash.SlashCommandMenu
import io.github.linreal.cascade.editor.slash.SlashCommandRegistry
import io.github.linreal.cascade.editor.slash.SlashCommandResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlashCommandRegistryTest {

    private fun action(
        id: String,
        title: String,
        description: String = "",
        keywords: List<String> = emptyList(),
        group: SlashCommandGroup? = null,
    ): SlashCommandAction = SlashCommandAction(
        id = SlashCommandId(id),
        title = title,
        description = description,
        keywords = keywords,
        group = group,
        onExecute = { SlashCommandResult.Done },
    )

    private fun menu(
        id: String,
        title: String,
        description: String = "",
        keywords: List<String> = emptyList(),
        children: List<SlashCommandItem> = emptyList(),
    ): SlashCommandMenu = SlashCommandMenu(
        id = SlashCommandId(id),
        title = title,
        description = description,
        keywords = keywords,
        children = children,
    )

    // --- Registration order ---

    @Test
    fun `root registration returns items in deterministic insertion order`() {
        val registry = SlashCommandRegistry()
        val a = action("a", "Alpha")
        val b = action("b", "Beta")
        val c = action("c", "Charlie")

        registry.register(a)
        registry.register(b)
        registry.register(c)

        val items = registry.getRootItems()
        assertEquals(listOf(a, b, c), items)
    }

    // --- Duplicate ids ---

    @Test
    fun `duplicate ids replace previous items`() {
        val registry = SlashCommandRegistry()
        val v1 = action("x", "Version 1")
        val v2 = action("x", "Version 2")

        registry.register(v1)
        registry.register(v2)

        val items = registry.getRootItems()
        assertEquals(1, items.size)
        assertEquals("Version 2", items[0].title)
    }

    // --- Ranking ---

    @Test
    fun `exact title match ranks above prefix and substring matches`() {
        val registry = SlashCommandRegistry()
        registry.register(action("sub", "Heading", description = "Contains head"))
        registry.register(action("prefix", "Head first"))
        registry.register(action("exact", "Head"))

        val results = registry.search("head")
        assertEquals(3, results.size)
        assertEquals("Head", results[0].title, "exact title match first")
        assertEquals("Head first", results[1].title, "prefix match second")
        assertEquals("Heading", results[2].title, "substring match third")
    }

    @Test
    fun `keyword matches participate in ranking`() {
        val registry = SlashCommandRegistry()
        registry.register(action("kw", "Bullet List", keywords = listOf("ul")))
        registry.register(action("desc", "Some Other", description = "has ul inside"))

        val results = registry.search("ul")
        assertEquals(2, results.size)
        assertEquals("Bullet List", results[0].title, "keyword match ranks higher than description")
    }

    @Test
    fun `exact keyword match ranks above keyword prefix`() {
        val registry = SlashCommandRegistry()
        registry.register(action("prefix", "Numbered List", keywords = listOf("ol-list")))
        registry.register(action("exact", "Ordered List", keywords = listOf("ol")))

        val results = registry.search("ol")
        assertEquals(2, results.size)
        assertEquals("Ordered List", results[0].title, "exact keyword before keyword prefix")
    }

    @Test
    fun `no match returns empty list`() {
        val registry = SlashCommandRegistry()
        registry.register(action("h", "Heading"))

        val results = registry.search("zzz")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `blank query returns all items unfiltered`() {
        val registry = SlashCommandRegistry()
        registry.register(action("a", "Alpha"))
        registry.register(action("b", "Beta"))

        val results = registry.search("")
        assertEquals(2, results.size)
    }

    // --- Path-based search ---

    @Test
    fun `path-based search only searches the addressed submenu level`() {
        val child1 = action("c1", "Child One")
        val child2 = action("c2", "Child Two")
        val rootAction = action("r1", "Root Action")
        val submenu = menu("m1", "Text Menu", children = listOf(child1, child2))

        val registry = SlashCommandRegistry()
        registry.register(rootAction)
        registry.register(submenu)

        // Search inside the submenu — root action should not appear
        val results = registry.search("child", path = listOf(SlashCommandId("m1")))
        assertEquals(2, results.size)
        assertTrue(results.all { it.id.value.startsWith("c") })

        // Root search should not see children
        val rootResults = registry.search("child")
        assertTrue(rootResults.isEmpty())
    }

    @Test
    fun `invalid path returns empty list`() {
        val registry = SlashCommandRegistry()
        registry.register(action("a", "Alpha"))

        val results = registry.search("a", path = listOf(SlashCommandId("nonexistent")))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `path into non-menu item returns empty list`() {
        val registry = SlashCommandRegistry()
        registry.register(action("a", "Alpha"))

        val results = registry.search("", path = listOf(SlashCommandId("a")))
        assertTrue(results.isEmpty())
    }

    // --- Menu items are discoverable ---

    @Test
    fun `menu items are discoverable in the same search path as actions`() {
        val registry = SlashCommandRegistry()
        registry.register(action("a1", "Heading"))
        registry.register(menu("m1", "Text Blocks", description = "collection of text types"))

        val results = registry.search("text")
        assertEquals(1, results.size)
        assertEquals("Text Blocks", results[0].title)
    }

    @Test
    fun `menu and action with same query both appear in results`() {
        val registry = SlashCommandRegistry()
        registry.register(action("a1", "Code Block", keywords = listOf("code")))
        registry.register(menu("m1", "Code Templates", keywords = listOf("code")))

        val results = registry.search("code")
        assertEquals(2, results.size)
    }

    // --- Nested submenu ---

    @Test
    fun `nested submenu search works with multi-segment path`() {
        val leaf = action("leaf", "Deep Item")
        val inner = menu("inner", "Inner Menu", children = listOf(leaf))
        val outer = menu("outer", "Outer Menu", children = listOf(inner))

        val registry = SlashCommandRegistry()
        registry.register(outer)

        // One level deep
        val level1 = registry.search("", path = listOf(SlashCommandId("outer")))
        assertEquals(1, level1.size)
        assertEquals("Inner Menu", level1[0].title)

        // Two levels deep
        val level2 = registry.search(
            "deep",
            path = listOf(SlashCommandId("outer"), SlashCommandId("inner")),
        )
        assertEquals(1, level2.size)
        assertEquals("Deep Item", level2[0].title)
    }

    // --- Deterministic tie-breaking ---

    @Test
    fun `items with same score are sorted alphabetically by title`() {
        val registry = SlashCommandRegistry()
        registry.register(action("b", "Zebra", keywords = listOf("animal")))
        registry.register(action("a", "Apple", keywords = listOf("animal")))

        val results = registry.search("animal")
        assertEquals("Apple", results[0].title)
        assertEquals("Zebra", results[1].title)
    }
}
