package io.github.linreal.cascade.editor.registry

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.slash.BuiltInBlockSlashBehavior
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandSpec
import io.github.linreal.cascade.editor.slash.SlashCommandGroup

/**
 * Central registry for block types, descriptors, and renderers.
 *
 * The registry serves as a single point of registration for:
 * - Block descriptors (metadata for slash commands)
 * - Block renderers (composable UI for each type)
 *
 * Use [createDefault] to get a registry pre-populated with built-in types.
 */
public class BlockRegistry {
    private val descriptors = mutableMapOf<String, BlockDescriptor>()
    private val renderers = mutableMapOf<String, BlockRenderer<*>>()

    /**
     * Registers a block descriptor.
     */
    public fun registerDescriptor(descriptor: BlockDescriptor) {
        descriptors[descriptor.typeId] = descriptor
    }

    /**
     * Registers a block renderer.
     */
    public fun <T : BlockType> registerRenderer(typeId: String, renderer: BlockRenderer<T>) {
        renderers[typeId] = renderer
    }

    /**
     * Registers both a descriptor and renderer for a block type.
     */
    public fun <T : BlockType> register(descriptor: BlockDescriptor, renderer: BlockRenderer<T>) {
        registerDescriptor(descriptor)
        registerRenderer(descriptor.typeId, renderer)
    }

    /**
     * Gets a descriptor by type ID.
     */
    public fun getDescriptor(typeId: String): BlockDescriptor? = descriptors[typeId]

    /**
     * Gets a renderer by type ID.
     */
    public fun getRenderer(typeId: String): BlockRenderer<*>? = renderers[typeId]

    /**
     * Gets all registered descriptors.
     */
    public fun getAllDescriptors(): List<BlockDescriptor> = descriptors.values.toList()

    /**
     * Searches for descriptors matching a query (for slash commands).
     * Results are sorted by relevance.
     */
    public fun search(query: String): List<BlockDescriptor> {
        return descriptors.values
            .filter { it.matches(query) }
            .sortedByDescending { it.relevanceScore(query) }
    }


    /**
     * Creates a block by type ID using the registered factory.
     * Returns null if the type is not registered.
     */
    public fun createBlock(typeId: String): Block? {
        return getDescriptor(typeId)?.createBlock()
    }

    /**
     * Checks if a type ID is registered.
     */
    public fun isRegistered(typeId: String): Boolean = typeId in descriptors

    public companion object {
        /**
         * Creates a registry with all built-in block types pre-registered.
         * Renderers are NOT included - use this with your own renderers.
         */
        public fun createDefault(): BlockRegistry = BlockRegistry().apply {
            registerBuiltInDescriptors()
        }

        /**
         * Creates an empty registry.
         */
        public fun create(): BlockRegistry = BlockRegistry()
    }
}

/**
 * Shared group definitions for built-in slash menu items.
 */
private val BasicBlocksGroup = SlashCommandGroup(label = "Basic Blocks", order = 0)
private val MediaGroup = SlashCommandGroup(label = "Media", order = 10)

/**
 * Registers all built-in block type descriptors.
 */
private fun BlockRegistry.registerBuiltInDescriptors() {
    // Paragraph — text-capable, ReplaceAnchorWhenBlank
    registerDescriptor(
        BlockDescriptor(
            typeId = "paragraph",
            displayName = "Paragraph",
            description = "Plain text paragraph",
            keywords = listOf("text", "p"),
            slash = BuiltInSlashCommandSpec(
                group = BasicBlocksGroup,
                behavior = BuiltInBlockSlashBehavior.ReplaceAnchorWhenBlank,
            ),
            factory = { id ->
                Block(id, BlockType.Paragraph, BlockContent.Text(""))
            }
        )
    )

    // Headings 1-6 — text-capable, ReplaceAnchorWhenBlank
    for (level in 1..6) {
        registerDescriptor(
            BlockDescriptor(
                typeId = "heading_$level",
                displayName = "Heading $level",
                description = "Heading level $level",
                keywords = listOf("h$level", "heading", "title"),
                slash = BuiltInSlashCommandSpec(
                    group = BasicBlocksGroup,
                    behavior = BuiltInBlockSlashBehavior.ReplaceAnchorWhenBlank,
                ),
                factory = { id ->
                    Block(id, BlockType.Heading(level), BlockContent.Text(""))
                }
            )
        )
    }

    // Todo — text-capable, ReplaceAnchorWhenBlank
    registerDescriptor(
        BlockDescriptor(
            typeId = "todo",
            displayName = "To-do",
            description = "Task with checkbox",
            keywords = listOf("checkbox", "task", "check", "todo"),
            slash = BuiltInSlashCommandSpec(
                group = BasicBlocksGroup,
                behavior = BuiltInBlockSlashBehavior.ReplaceAnchorWhenBlank,
            ),
            factory = { id ->
                Block(id, BlockType.Todo(checked = false), BlockContent.Text(""))
            }
        )
    )

    // Bullet List — text-capable, ReplaceAnchorWhenBlank
    registerDescriptor(
        BlockDescriptor(
            typeId = "bullet_list",
            displayName = "Bullet List",
            description = "Unordered list item",
            keywords = listOf("list", "bullet", "ul", "unordered"),
            slash = BuiltInSlashCommandSpec(
                group = BasicBlocksGroup,
                behavior = BuiltInBlockSlashBehavior.ReplaceAnchorWhenBlank,
            ),
            factory = { id ->
                Block(id, BlockType.BulletList, BlockContent.Text(""))
            }
        )
    )

    // Numbered List — text-capable, ReplaceAnchorWhenBlank
    registerDescriptor(
        BlockDescriptor(
            typeId = "numbered_list",
            displayName = "Numbered List",
            description = "Ordered list item",
            keywords = listOf("list", "number", "ol", "ordered"),
            slash = BuiltInSlashCommandSpec(
                group = BasicBlocksGroup,
                behavior = BuiltInBlockSlashBehavior.ReplaceAnchorWhenBlank,
            ),
            factory = { id ->
                Block(id, BlockType.NumberedList, BlockContent.Text(""))
            }
        )
    )

    // Quote — text-capable, ReplaceAnchorWhenBlank
    registerDescriptor(
        BlockDescriptor(
            typeId = "quote",
            displayName = "Quote",
            description = "Quoted text block",
            keywords = listOf("blockquote", "citation"),
            slash = BuiltInSlashCommandSpec(
                group = BasicBlocksGroup,
                behavior = BuiltInBlockSlashBehavior.ReplaceAnchorWhenBlank,
            ),
            factory = { id ->
                Block(id, BlockType.Quote, BlockContent.Text(""))
            }
        )
    )

    // Code — text-capable but isConvertible=false → AlwaysInsert
    registerDescriptor(
        BlockDescriptor(
            typeId = "code",
            displayName = "Code",
            description = "Code block with syntax highlighting",
            keywords = listOf("code", "snippet", "programming"),
            slash = BuiltInSlashCommandSpec(
                group = BasicBlocksGroup,
                behavior = BuiltInBlockSlashBehavior.AlwaysInsert,
            ),
            factory = { id ->
                Block(id, BlockType.Code(), BlockContent.Text(""))
            }
        )
    )

    // Divider — non-text, AlwaysInsert
    registerDescriptor(
        BlockDescriptor(
            typeId = "divider",
            displayName = "Divider",
            description = "Horizontal line separator",
            keywords = listOf("hr", "line", "separator", "horizontal"),
            slash = BuiltInSlashCommandSpec(
                group = MediaGroup,
                behavior = BuiltInBlockSlashBehavior.AlwaysInsert,
            ),
            factory = { id ->
                Block(id, BlockType.Divider, BlockContent.Empty)
            }
        )
    )

    // Image — non-text, AlwaysInsert
    registerDescriptor(
        BlockDescriptor(
            typeId = "image",
            displayName = "Image",
            description = "Embedded image",
            keywords = listOf("picture", "photo", "img"),
            slash = BuiltInSlashCommandSpec(
                group = MediaGroup,
                behavior = BuiltInBlockSlashBehavior.AlwaysInsert,
            ),
            factory = { id ->
                Block(id, BlockType.Image, BlockContent.Image("", null))
            }
        )
    )
}
