package io.github.linreal.cascade.ios.block

import io.github.linreal.cascade.editor.core.CustomBlockType

/**
 * Renderable [CustomBlockType] for a type id registered via
 * [CascadeEditorController.registerBlock][io.github.linreal.cascade.ios.controller.CascadeEditorController.registerBlock].
 *
 * Produced by [NativeCustomBlockCodec] when decoding a persisted document, so a
 * stored custom block resolves to a type the registered renderer can draw rather
 * than to [io.github.linreal.cascade.editor.core.UnknownBlockType].
 *
 * Distinct from the builder-only `NativeDocumentBlockType`: both carry only a
 * type id and produce the same JSON, but this one is the type the editor renders
 * and mutates, whereas the builder's variant is inert marshaling metadata used
 * only to emit document JSON. They share a JSON representation, so a document
 * emitted by the builder decodes into this type once its id is registered.
 */
internal data class NativeCustomBlockType(
    override val typeId: String,
) : CustomBlockType {
    override val displayName: String = typeId
    override val supportsText: Boolean = false
    override val isConvertible: Boolean = false
}
