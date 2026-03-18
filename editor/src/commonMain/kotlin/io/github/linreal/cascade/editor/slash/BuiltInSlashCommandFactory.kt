package io.github.linreal.cascade.editor.slash

import io.github.linreal.cascade.editor.registry.BlockDescriptor
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings

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
     * When [blockStrings] is provided, localized display names, descriptions, and keywords
     * are resolved per descriptor. Localized keywords are merged with English keywords
     * (additive, not replacing) so English search always works regardless of locale.
     * Missing entries fall back to the descriptor's English values.
     *
     * Generated IDs follow the format `builtin.block.<typeId>`.
     */
    public fun generate(
        descriptors: List<BlockDescriptor>,
        blockStrings: CascadeEditorBlockStrings? = null,
    ): List<SlashCommandAction> {
        return descriptors.mapNotNull { descriptor ->
            val spec = descriptor.slash ?: return@mapNotNull null
            val typeId = descriptor.typeId
            val behavior = spec.behavior
            val localized = blockStrings?.forType(typeId)

            SlashCommandAction(
                id = SlashCommandId("$ID_PREFIX$typeId"),
                title = localized?.displayName ?: descriptor.displayName,
                description = localized?.description ?: descriptor.description,
                keywords = if (localized != null) {
                    (descriptor.keywords + localized.keywords).distinct()
                } else {
                    descriptor.keywords
                },
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
