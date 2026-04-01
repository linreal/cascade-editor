package io.github.linreal.cascade.editor.ui

import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.ui.renderers.DividerBlockRenderer
import io.github.linreal.cascade.editor.ui.renderers.TextBlockRenderer
import io.github.linreal.cascade.editor.ui.renderers.TodoBlockRenderer
import io.github.linreal.cascade.editor.ui.renderers.UnknownBlockRenderer

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

    // Register TextBlockRenderer for all text-supporting types (except todo)
    val textTypeIds = listOf(
        "paragraph",
        "heading_1", "heading_2", "heading_3", "heading_4", "heading_5", "heading_6",
        "bullet_list",
        "numbered_list",
        "quote",
    )

    textTypeIds.forEach { typeId ->
        registerRenderer(typeId, textRenderer)
    }

    // Register TodoBlockRenderer for todo blocks (checkbox + text)
    registerRenderer("todo", TodoBlockRenderer())

    // Register DividerBlockRenderer for divider blocks (horizontal line)
    registerRenderer("divider", DividerBlockRenderer())

    // TODO: Register ImageRenderer for "image"

    // Fallback for UnknownBlockType (deserialized blocks with unrecognized typeId)
    setUnknownBlockRenderer(UnknownBlockRenderer)
}
