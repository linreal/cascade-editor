package io.github.linreal.cascade.editor.core

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Type-safe identifier for blocks.
 */
@JvmInline
public value class BlockId(public val value: String) {
    public companion object {
        @OptIn(ExperimentalUuidApi::class)
        public fun generate(): BlockId = BlockId(Uuid.random().toString())
    }
}
