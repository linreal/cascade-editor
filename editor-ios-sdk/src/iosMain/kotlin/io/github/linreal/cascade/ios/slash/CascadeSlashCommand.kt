@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.slash

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Outcome a native slash-command handler returns.
 *
 * Construct one of the three shared cases: [done], [keepOpen], or [failure].
 * From Swift these are reached through the companion, e.g. `.companion.done` or
 * `.companion.failure(message: "…")`.
 */
@ObjCName("CascadeSlashCommandResult", exact = true)
public class CascadeSlashCommandResult private constructor(
    internal val kind: Kind,
    internal val failureMessage: String?,
) {
    internal enum class Kind { Done, KeepOpen, Failure }

    public companion object {
        /** The command finished; the slash menu should close. */
        public val done: CascadeSlashCommandResult = CascadeSlashCommandResult(Kind.Done, null)

        /** The command finished but the slash menu should stay open. */
        public val keepOpen: CascadeSlashCommandResult = CascadeSlashCommandResult(Kind.KeepOpen, null)

        /**
         * The command failed; the slash menu closes and [message], when present, is
         * reported through
         * [CascadeEditorController.onInternalError][io.github.linreal.cascade.ios.controller.CascadeEditorController.onInternalError].
         */
        public fun failure(message: String? = null): CascadeSlashCommandResult =
            CascadeSlashCommandResult(Kind.Failure, message)
    }
}

/**
 * Swift-facing description of a native slash command.
 *
 * A command is turned into a slash-menu action whose synchronous [handler] runs
 * when the user selects it. The handler receives a [CascadeSlashCommandContext]
 * scoped to the block that owns the `/` trigger and returns a
 * [CascadeSlashCommandResult].
 *
 * @property id Unique id. Registering an id that already exists (a prior native
 *   command or a built-in) overrides it — see
 *   [CascadeEditorController.registerSlashCommand][io.github.linreal.cascade.ios.controller.CascadeEditorController.registerSlashCommand].
 * @property title Human-readable name shown in the slash menu.
 * @property description Short description shown in the slash menu.
 * @property keywords Extra search terms for the slash menu.
 * @property handler Synchronous work performed when the command is chosen. It owns
 *   the query text (the `/…` token is left in place for it to edit or remove via
 *   [CascadeSlashCommandContext.replaceQueryText]).
 */
@ObjCName("CascadeSlashCommand", exact = true)
public class CascadeSlashCommand public constructor(
    public val id: String,
    public val title: String,
    public val description: String,
    public val keywords: List<String> = emptyList(),
    public val handler: (CascadeSlashCommandContext) -> CascadeSlashCommandResult,
)
