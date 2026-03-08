package io.github.linreal.cascade.editor.slash

import androidx.compose.runtime.Immutable
import kotlin.jvm.JvmInline

/**
 * Opaque key for slash command icons.
 * The UI layer maps this to an actual drawable/vector resource.
 */
@JvmInline
public value class SlashCommandIconKey(public val value: String)

/**
 * Grouping label and sort order for slash menu items.
 *
 * @property label Human-readable group header shown in the popup.
 * @property order Lower values sort first. Groups with the same order are sorted by label.
 */
@Immutable
public data class SlashCommandGroup(
    val label: String,
    val order: Int = 0,
)

/**
 * Policy for what happens to the slash query text when a command is executed.
 */
public enum class SlashQueryTextPolicy {
    /** Remove the `/` and query text before the command's `onExecute` runs. */
    RemoveBeforeExecute,

    /** Leave the text in place; the command is responsible for its own edits. */
    KeepText,
}

/**
 * Result returned by a slash command after execution.
 */
public sealed interface SlashCommandResult {
    /** Command completed successfully. The menu should close. */
    public data object Done : SlashCommandResult

    /** Command completed but the menu should remain open (e.g. for chained commands). */
    public data object KeepOpen : SlashCommandResult

    /** Command failed with an optional message. The menu closes and no further mutations occur. */
    public data class Failure(val message: String? = null) : SlashCommandResult
}

/**
 * Sealed hierarchy for items that can appear in the slash menu.
 *
 * Every item has a unique [id], display metadata, and an optional [group].
 */
public sealed interface SlashCommandItem {
    public val id: SlashCommandId
    public val title: String
    public val description: String
    public val keywords: List<String>
    public val icon: SlashCommandIconKey?
    public val group: SlashCommandGroup?
}

/**
 * A leaf command that performs an action when selected.
 *
 * @property queryTextPolicy Controls whether the `/…` text is removed before execution.
 * @property onExecute Suspending lambda invoked with [SlashCommandContext] as receiver.
 */
@Immutable
public data class SlashCommandAction(
    override val id: SlashCommandId,
    override val title: String,
    override val description: String,
    override val keywords: List<String> = emptyList(),
    override val icon: SlashCommandIconKey? = null,
    override val group: SlashCommandGroup? = null,
    val queryTextPolicy: SlashQueryTextPolicy = SlashQueryTextPolicy.RemoveBeforeExecute,
    val onExecute: suspend SlashCommandContext.() -> SlashCommandResult,
) : SlashCommandItem

/**
 * A submenu node that contains child items revealed on selection.
 *
 * @property children The items visible when the user navigates into this menu.
 */
@Immutable
public data class SlashCommandMenu(
    override val id: SlashCommandId,
    override val title: String,
    override val description: String,
    override val keywords: List<String> = emptyList(),
    override val icon: SlashCommandIconKey? = null,
    override val group: SlashCommandGroup? = null,
    val children: List<SlashCommandItem> = emptyList(),
) : SlashCommandItem
