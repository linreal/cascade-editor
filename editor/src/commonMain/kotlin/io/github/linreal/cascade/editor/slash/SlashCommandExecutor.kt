package io.github.linreal.cascade.editor.slash

import io.github.linreal.cascade.editor.action.ConvertBlockType
import io.github.linreal.cascade.editor.action.NavigateSlashSubmenu
import io.github.linreal.cascade.editor.action.ReplaceBlock
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.SlashCommandState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Creates the built-in block command executor lambda.
 *
 * This lambda is used by [BuiltInSlashCommandFactory] to generate slash command actions
 * for built-in block types. It only depends on [EditorStateHolder] and [BlockRegistry],
 * not on [SlashCommandRegistry], so it can be created before the merged registry is available.
 *
 * When behavior is [ConvertInPlace][BuiltInBlockSlashBehavior.ConvertInPlace]:
 * for a text target the anchor block's type is changed in-place via [ConvertBlockType].
 * Query text is already removed (all built-in actions use [SlashQueryTextPolicy.RemoveBeforeExecute]),
 * so remaining text and spans are preserved as-is. For a non-text target (e.g. a custom
 * block whose default content is [BlockContent.Custom]/[BlockContent.Empty]) the anchor's
 * text content is not a valid shape for the new type: when the anchor is blank after
 * query removal the whole block is replaced with the descriptor's default block —
 * preserving the anchor's id and position — and when text remains the anchor keeps it
 * and the new block is inserted after the anchor instead, so converting never silently
 * discards user-typed text.
 *
 * When behavior is [AlwaysInsert][BuiltInBlockSlashBehavior.AlwaysInsert]:
 * a new block is created from the descriptor factory and inserted above the anchor
 * via [SlashCommandEditor.insertBlockBeforeAnchor]. This keeps focus on the anchor
 * block, which is desirable for non-text insertions like dividers.
 */
internal fun createBuiltInSlashExecutor(
    stateHolder: EditorStateHolder,
    blockRegistry: BlockRegistry,
): suspend SlashCommandContext.(String, BuiltInBlockSlashBehavior) -> SlashCommandResult =
    { typeId, behavior ->
        val descriptor = blockRegistry.getDescriptor(typeId)
        if (descriptor == null) {
            SlashCommandResult.Failure("Unknown block type: $typeId")
        } else {
            when (behavior) {
                is BuiltInBlockSlashBehavior.ConvertInPlace -> {
                    val target = descriptor.createBlock(anchorBlockId)
                    if (target.content is BlockContent.Text) {
                        // Text target: swap type only, keeping the anchor's text/spans.
                        stateHolder.dispatch(ConvertBlockType(anchorBlockId, target.type))
                    } else if (editor.getAnchorVisibleText().isNullOrBlank()) {
                        // Non-text target on a blank anchor: the anchor's text is not a
                        // valid shape for the new type. Replace the whole block,
                        // preserving id and position.
                        editor.replaceAnchorBlock(target, preserveAnchorId = true, requestFocus = false)
                        // SlashCommandEditorHost currently rebuilds a replacement with
                        // the anchor id and otherwise preserves only type/content. Restore
                        // descriptor-provided attributes so custom indentation/defaults are
                        // not lost by this built-in conversion path.
                        stateHolder.dispatch(ReplaceBlock(anchorBlockId, target.copy(id = anchorBlockId)))
                    } else {
                        // Non-text target with text remaining after query removal:
                        // converting would discard that text, so keep the anchor and
                        // insert the new block right after it instead.
                        editor.insertBlockAfterAnchor(descriptor.createBlock(), requestFocus = false)
                    }
                    SlashCommandResult.Done
                }
                is BuiltInBlockSlashBehavior.AlwaysInsert -> {
                    val newBlock = descriptor.createBlock()
                    editor.insertBlockBeforeAnchor(newBlock)
                    SlashCommandResult.Done
                }
            }
        }
    }

/**
 * Internal coordinator that executes slash command items.
 *
 * Responsibilities:
 * - Resolves the selected [SlashCommandItem] from the [registry] at the current
 *   navigation level.
 * - Routes [SlashCommandMenu] selections to submenu navigation (updates
 *   [SlashCommandState.navigationPath] without executing).
 * - Applies [SlashQueryTextPolicy] before invoking [SlashCommandAction.onExecute].
 * - Converts uncaught exceptions to [SlashCommandResult.Failure].
 * - Closes or keeps the menu open based on the returned [SlashCommandResult].
 *
 * Built-in block commands are executed via [builtInExecutor], which encapsulates
 * convert-in-place vs. always-insert semantics. Pass this lambda to
 * [BuiltInSlashCommandFactory] during wiring.
 *
 * @property registry The merged slash command registry (built-in + custom) for item resolution.
 * @property stateHolder Snapshot state holder for dispatch and state reads.
 * @property textStates Runtime text state manager.
 * @property spanStates Runtime span state manager.
 * @property executionScope Editor-owned coroutine scope used for async command launches.
 * @property builtInExecutor Lambda for executing built-in block commands, created via
 *           [createBuiltInSlashExecutor].
 */
internal class SlashCommandExecutor(
    private val registry: SlashCommandRegistry,
    private val stateHolder: EditorStateHolder,
    private val textStates: BlockTextStates,
    private val spanStates: BlockSpanStates,
    private val executionScope: CoroutineScope,
    internal val builtInExecutor: suspend SlashCommandContext.(
        typeId: String,
        behavior: BuiltInBlockSlashBehavior,
    ) -> SlashCommandResult,
) {

    /**
     * Executes the slash command item identified by [itemId].
     *
     * - [SlashCommandMenu]: pushes the submenu onto the navigation path without executing.
     * - [SlashCommandAction]: captures the session snapshot, applies query-text policy,
     *   invokes the action, and manages menu lifecycle based on the result.
     *
     * No-op if there is no active slash session or [itemId] cannot be resolved
     * at the current navigation level.
     */
    internal fun execute(itemId: SlashCommandId): Job {
        return executionScope.launch {
            executeNow(itemId)
        }
    }

    /**
     * Executes an already-resolved slash item.
     *
     * Used by UI paths that already have the visible item list and should not
     * depend on registry lookups at execution time.
     */
    internal fun execute(item: SlashCommandItem): Job {
        return executionScope.launch {
            executeNow(item)
        }
    }

    internal suspend fun executeNow(itemId: SlashCommandId) {
        val session = stateHolder.state.slashCommandState ?: return

        val item = resolveItem(itemId, session) ?: return
        executeItem(item, session)
    }

    internal suspend fun executeNow(item: SlashCommandItem) {
        val session = stateHolder.state.slashCommandState ?: return
        executeItem(item, session)
    }

    private suspend fun executeItem(item: SlashCommandItem, session: SlashCommandState) {
        when (item) {
            is SlashCommandMenu -> {
                stateHolder.dispatch(NavigateSlashSubmenu(item.id))
            }
            is SlashCommandAction -> {
                executeAction(item, session)
            }
        }
    }

    // -- Private implementation --

    private fun resolveItem(
        itemId: SlashCommandId,
        session: SlashCommandState,
    ): SlashCommandItem? {
        return registry.search("", session.navigationPath)
            .find { it.id == itemId }
    }

    /**
     * Execution order:
     * 1. Capture current session snapshot (done by caller — [session]).
     * 2. Remove query text when policy is [SlashQueryTextPolicy.RemoveBeforeExecute].
     * 3. Invoke action.
     * 4. Apply result (close/keep menu).
     */
    private suspend fun executeAction(
        action: SlashCommandAction,
        session: SlashCommandState,
    ) {
        stateHolder.runStructuralHistoryTransactionSuspend(textStates, spanStates) {
            val host = SlashCommandEditorHost(
                anchorBlockId = session.anchorBlockId,
                queryRange = session.queryRange,
                stateHolder = stateHolder,
                textStates = textStates,
                spanStates = spanStates,
            )

            // Step 2: apply query text policy
            val queryTextRemoved = action.queryTextPolicy == SlashQueryTextPolicy.RemoveBeforeExecute
            if (queryTextRemoved) {
                host.replaceQueryText("")
            }

            // Step 3: invoke action with captured session context
            val context = SlashCommandContext(
                anchorBlockId = session.anchorBlockId,
                query = session.query,
                queryRange = session.queryRange,
                editor = host,
            )

            val result = try {
                action.onExecute(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SlashCommandResult.Failure(e.message)
            }

            // Step 4: apply result
            when (result) {
                is SlashCommandResult.Done -> host.closeMenu()
                is SlashCommandResult.KeepOpen -> {
                    // Keeping the menu open requires a valid slash session range.
                    // After RemoveBeforeExecute, the tracked slash token no longer exists.
                    if (queryTextRemoved) {
                        host.closeMenu()
                    }
                }
                is SlashCommandResult.Failure -> host.closeMenu()
            }
        }
    }

}
