package io.github.linreal.cascade.editor.serialization

/**
 * Controls how duplicate block IDs are handled during document decode.
 */
public enum class DuplicateIdMode {
    /** Replace later occurrences of a duplicate ID with a freshly generated one. */
    RegenerateLaterDuplicates,

    /** Throw [IllegalArgumentException] on the first duplicate ID encountered. */
    FailFast,
}
