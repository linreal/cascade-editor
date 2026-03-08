package io.github.linreal.cascade.editor.slash

import androidx.compose.runtime.Immutable

/**
 * Slash-menu-specific metadata attached to a [BlockDescriptor][io.github.linreal.cascade.editor.registry.BlockDescriptor].
 *
 * Only descriptors with a non-null `slash` field will appear as built-in slash menu entries.
 * Custom slash commands registered directly with [SlashCommandRegistry] are not affected.
 *
 * @property group Optional grouping for the slash menu. When null, the item is ungrouped.
 * @property icon Slash-menu-specific icon override. When null, the built-in command factory
 *   falls back to [BlockDescriptor.icon][io.github.linreal.cascade.editor.registry.BlockDescriptor.icon]
 *   wrapped in [SlashCommandIconKey].
 * @property behavior Controls how the built-in command behaves on execution (replace vs insert).
 */
@Immutable
public data class BuiltInSlashCommandSpec(
    val group: SlashCommandGroup? = null,
    val icon: SlashCommandIconKey? = null,
    val behavior: BuiltInBlockSlashBehavior = BuiltInBlockSlashBehavior.ReplaceAnchorWhenBlank,
)

/**
 * Determines how a built-in block slash command transforms the anchor block on execution.
 */
public sealed interface BuiltInBlockSlashBehavior {

    /**
     * If the anchor block is blank after query removal, replace it in-place with the target type.
     * Otherwise, insert a new block of the target type below the anchor.
     */
    public data object ReplaceAnchorWhenBlank : BuiltInBlockSlashBehavior

    /**
     * Always insert a new block below the anchor, regardless of anchor content.
     */
    public data object AlwaysInsert : BuiltInBlockSlashBehavior
}
