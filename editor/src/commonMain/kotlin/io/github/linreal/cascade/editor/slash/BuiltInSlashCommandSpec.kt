package io.github.linreal.cascade.editor.slash

import androidx.compose.runtime.Immutable

/**
 * Slash-menu-specific metadata attached to a [BlockDescriptor][io.github.linreal.cascade.editor.registry.BlockDescriptor].
 *
 * Only descriptors with a non-null `slash` field will appear as built-in slash menu entries.
 * Custom slash commands registered directly with [SlashCommandRegistry] are not affected.
 *
 * @property behavior Controls how the built-in command behaves on execution (convert in-place vs insert below).
 * @property icon Slash-menu-specific icon override. When null, the built-in command factory
 *   falls back to [BlockDescriptor.icon][io.github.linreal.cascade.editor.registry.BlockDescriptor.icon]
 *   wrapped in [SlashCommandIconKey].
 */
@Immutable
public data class BuiltInSlashCommandSpec(
    val behavior: BuiltInBlockSlashBehavior,
    val icon: SlashCommandIconKey? = null,
)

/**
 * Determines how a built-in block slash command transforms the anchor block on execution.
 */
public sealed interface BuiltInBlockSlashBehavior {

    /**
     * Always convert the anchor block's type in-place, preserving remaining text and spans
     * after query removal. Use for text-capable convertible types (paragraph, heading, todo, etc.).
     */
    public data object ConvertInPlace : BuiltInBlockSlashBehavior

    /**
     * Always insert a new block below the anchor, regardless of anchor content.
     * Use for non-convertible types (divider, image) or types that should never
     * modify the anchor block.
     */
    public data object AlwaysInsert : BuiltInBlockSlashBehavior
}
