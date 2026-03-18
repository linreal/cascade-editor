package io.github.linreal.cascade.editor.slash

import io.github.linreal.cascade.editor.registry.BlockDescriptor

/**
 * Generates [SlashCommandAction] instances from [BlockDescriptor]s that carry
 * [BuiltInSlashCommandSpec] metadata.
 *
 * The factory is pure and deterministic — it does not mutate editor state.
 * Each generated action delegates execution to the provided [builtInExecutor] lambda,
 * which encapsulates built-in block semantics (convert-in-place vs. insert).
 *
 * @property builtInExecutor Suspending lambda invoked at execution time with the descriptor's
 *   [typeId] and [BuiltInBlockSlashBehavior]. The lambda receives the same [SlashCommandContext]
 *   receiver as a regular slash action.
 */
internal class BuiltInSlashCommandFactory(
    private val builtInExecutor: suspend SlashCommandContext.(
        typeId: String,
        behavior: BuiltInBlockSlashBehavior,
    ) -> SlashCommandResult,
) {

    /**
     * Generates slash command actions from [descriptors].
     *
     * Only descriptors with non-null [BlockDescriptor.slash] are included.
     * The output order matches the input order (filtered), making the result deterministic
     * when the input is deterministic.
     *
     * Generated IDs follow the format `builtin.block.<typeId>`.
     */
    public fun generate(descriptors: List<BlockDescriptor>): List<SlashCommandAction> {
        return descriptors.mapNotNull { descriptor ->
            val spec = descriptor.slash ?: return@mapNotNull null
            val typeId = descriptor.typeId
            val behavior = spec.behavior

            SlashCommandAction(
                id = SlashCommandId("$ID_PREFIX$typeId"),
                title = descriptor.displayName,
                description = descriptor.description,
                keywords = descriptor.keywords,
                icon = spec.icon ?: descriptor.icon?.let(::SlashCommandIconKey),
                queryTextPolicy = SlashQueryTextPolicy.RemoveBeforeExecute,
                onExecute = { builtInExecutor(typeId, behavior) },
            )
        }
    }

    internal companion object {
        /** Stable prefix for all built-in block slash command IDs. */
        internal const val ID_PREFIX: String = "builtin.block."
    }
}
