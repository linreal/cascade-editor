package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.slash.BUILTIN_BLOCK_SLASH_COMMAND_ID_PREFIX
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandFactory
import io.github.linreal.cascade.editor.slash.SlashCommandResult
import io.github.linreal.cascade.editor.slash.builtInBlockSlashCommandId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltInBlockSlashCommandIdTest {

    @Test
    fun idIsPrefixedTypeId() {
        assertEquals(
            "${BUILTIN_BLOCK_SLASH_COMMAND_ID_PREFIX}paragraph",
            builtInBlockSlashCommandId("paragraph").value,
        )
    }

    @Test
    fun matchesTheIdsTheFactoryGenerates() {
        val factory = BuiltInSlashCommandFactory { _, _ -> SlashCommandResult.Done }
        val descriptors = BlockRegistry.createDefault().getAllDescriptors()

        val generatedIds = factory.generate(descriptors).map { it.id }.toSet()
        val derivedIds = descriptors
            .filter { it.slash != null }
            .map { builtInBlockSlashCommandId(it.typeId) }
            .toSet()

        assertTrue(generatedIds.isNotEmpty())
        assertEquals(generatedIds, derivedIds)
    }
}
