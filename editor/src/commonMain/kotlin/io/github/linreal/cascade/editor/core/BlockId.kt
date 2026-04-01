package io.github.linreal.cascade.editor.core

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Type-safe wrapper around a block's unique string identifier.
 *
 * @property value The underlying string identifier.
 */
@JvmInline
public value class BlockId(public val value: String) {
    public companion object {
        /** Generates a new unique [BlockId] using a random UUID. */
        @OptIn(ExperimentalUuidApi::class)
        public fun generate(): BlockId = BlockId(Uuid.random().toString())
    }
}
