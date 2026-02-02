package io.github.linreal.cascade.editor.ui

import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.ui.renderers.TextBlockRenderer

/**
 * Creates a [BlockRegistry] with all built-in descriptors and renderers.
 *
 * This is the recommended way to create a registry for use with [CascadeEditor].
 */
public fun createEditorRegistry(): BlockRegistry {
    return BlockRegistry.createDefault().apply {
        registerBuiltInRenderers()
    }
}

/**
 * Registers built-in renderers for all standard block types.
 */
public fun BlockRegistry.registerBuiltInRenderers() {
    val textRenderer = TextBlockRenderer()

    // Register TextBlockRenderer for all text-supporting types
    val textTypeIds = listOf(
        "paragraph",
        "heading_1", "heading_2", "heading_3", "heading_4", "heading_5", "heading_6",
        "todo",
        "bullet_list",
        "numbered_list",
        "quote",
        "code"
    )

    textTypeIds.forEach { typeId ->
        registerRenderer(typeId, textRenderer)
    }

    // TODO: Register DividerRenderer for "divider"
    // TODO: Register ImageRenderer for "image"
}
