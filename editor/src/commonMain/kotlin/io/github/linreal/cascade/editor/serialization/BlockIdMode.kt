package io.github.linreal.cascade.editor.serialization

/**
 * Controls how block IDs are handled during document decode.
 */
public enum class BlockIdMode {
    /** Use the ID from the serialized JSON. Generate if missing/empty. */
    Preserve,

    /** Always generate a fresh [io.github.linreal.cascade.editor.core.BlockId] regardless of the value in JSON. */
    Regenerate,
}
