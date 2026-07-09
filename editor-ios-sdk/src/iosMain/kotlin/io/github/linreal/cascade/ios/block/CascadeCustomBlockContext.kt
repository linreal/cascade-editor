@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.block

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.registry.BlockRenderScope
import io.github.linreal.cascade.ios.model.parseJsonObjectPayloadSafely
import io.github.linreal.cascade.ios.model.toCascadeJsonString
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import platform.Foundation.NSThread

internal const val MIN_BLOCK_HEIGHT: Double = 1.0
internal const val MAX_BLOCK_HEIGHT: Double = 10_000.0
internal const val DEFAULT_BLOCK_HEIGHT: Double = 120.0

private const val CONTEXT_MAIN_THREAD_ERROR: String =
    "CascadeCustomBlockContext must be used on the main thread"

/**
 * Clamps a caller-supplied block height (points) into a sane positive range.
 * Non-finite values fall back to [DEFAULT_BLOCK_HEIGHT].
 */
internal fun clampBlockHeight(raw: Double): Double = when {
    raw.isNaN() || raw.isInfinite() -> DEFAULT_BLOCK_HEIGHT
    raw < MIN_BLOCK_HEIGHT -> MIN_BLOCK_HEIGHT
    raw > MAX_BLOCK_HEIGHT -> MAX_BLOCK_HEIGHT
    else -> raw
}

/**
 * Outcome of building a block for an insert request.
 */
internal sealed interface CascadeBlockBuildOutcome {
    data class Success(val block: Block) : CascadeBlockBuildOutcome
    data class InvalidPayload(val message: String) : CascadeBlockBuildOutcome
    data class UnknownType(val message: String) : CascadeBlockBuildOutcome
}

/**
 * Builds the block inserted by a context/slash insert request.
 *
 * A registered native type id yields a renderable [NativeCustomBlockType] carrying
 * the marshaled payload; a built-in descriptor id yields that descriptor's default
 * block (payload ignored — built-ins have no JSON payload). Any other id is an
 * error. Invalid payload JSON for a native type is rejected using the same
 * coercion as every other custom-block payload path.
 */
internal fun buildInsertableBlock(
    typeId: String,
    payloadJson: String,
    registry: BlockRegistry,
    isNativeCustomType: (String) -> Boolean,
): CascadeBlockBuildOutcome {
    if (isNativeCustomType(typeId)) {
        val parsed = parseJsonObjectPayloadSafely(payloadJson)
        parsed.errorMessage?.let { return CascadeBlockBuildOutcome.InvalidPayload(it) }
        return CascadeBlockBuildOutcome.Success(
            Block(
                id = BlockId.generate(),
                type = NativeCustomBlockType(typeId),
                content = BlockContent.Custom(typeId, parsed.data),
            ),
        )
    }
    val descriptor = registry.getDescriptor(typeId)
        ?: return CascadeBlockBuildOutcome.UnknownType("Unknown block typeId '$typeId'")
    return CascadeBlockBuildOutcome.Success(descriptor.createBlock())
}

/**
 * Result of a context mutation. Errors are also reported through
 * [CascadeEditorController.onInternalError][io.github.linreal.cascade.ios.controller.CascadeEditorController.onInternalError]
 * for diagnosability.
 */
@ObjCName("CascadeCustomBlockMutationResult", exact = true)
public enum class CascadeCustomBlockMutationResult {
    success,

    @ObjCName(name = "readOnly", swiftName = "readOnly")
    readOnly,

    @ObjCName(name = "invalidPayload", swiftName = "invalidPayload")
    invalidPayload,

    @ObjCName(name = "unknownType", swiftName = "unknownType")
    unknownType,

    @ObjCName(name = "blockUnavailable", swiftName = "blockUnavailable")
    blockUnavailable,
}

/**
 * Per-instance handle a native custom block view uses to read editor state for
 * its block and to push changes back into the document.
 *
 * Reads reflect live editor state; mutations route through the editor's
 * history-aware [BlockRenderScope], so once the editor is mounted they are
 * undo/redo-aware and gated by read-only/policy exactly like built-in edits.
 *
 * All members must be used on the main thread. Set [onChange] to be notified
 * on any editor-side change, so the native view can re-read the context and
 * refresh.
 */
@ObjCName("CascadeCustomBlockContext", exact = true)
public class CascadeCustomBlockContext internal constructor(
    public val blockId: String,
    public val typeId: String,
    private val scope: BlockRenderScope,
    private val reportError: (String) -> Unit,
    private val isDarkProvider: () -> Boolean,
    private val applyPreferredHeight: (Double) -> Unit,
    private val buildBlock: (typeId: String, payloadJson: String) -> CascadeBlockBuildOutcome,
) {
    private val id: BlockId = BlockId(blockId)

    /**
     * Set by the native view to observe editor-side changes to this block.
     *
     * The native view is built once and hosted inside the editor; it does not
     * automatically observe editor state, so this callback is its single refresh
     * signal — everything a native block renders from is pulled from this context,
     * and the values change underneath it without UIKit knowing. It is invoked on
     * the main thread after any editor-side change this block depends on:
     *  - the payload changed — whether from this view's own [updatePayloadJson] /
     *    [replacePayloadJson], an undo/redo, or a document load/reset;
     *  - focus or block selection moved to or away from this block;
     *  - the read-only state or the dark-mode theme toggled.
     *
     * On each invocation, re-read the context properties this view depends on
     * ([payloadJson], [isFocused], [isSelected], [readOnly], [isDark]) and update
     * the view to match. The callback takes no arguments because the context
     * always exposes the current values; it may also fire when nothing this
     * particular view cares about changed, so treat it as "re-sync from the
     * context", not "exactly one meaningful change happened".
     */
    public var onChange: (() -> Unit)? = null

    /**
     * Invokes [onChange] with the failure contained. The handler is native (Swift)
     * code; a throw must never cancel the hosting effect or escape into the Compose
     * runtime, so any error is reported through [reportError] instead of propagating.
     */
    internal fun fireOnChange() {
        val handler = onChange ?: return
        try {
            handler()
        } catch (throwable: Throwable) {
            reportError(
                "Custom block '$typeId' onChange failed: ${throwable.message ?: throwable.toString()}"
            )
        }
    }

    /** Current payload as a JSON object string, or `"{}"` when unavailable. */
    public val payloadJson: String
        get() = read(fallback = "{}") { currentData().toCascadeJsonString() }

    public val isFocused: Boolean
        get() = read(fallback = false) { scope.state.focusedBlockId == id }

    public val isSelected: Boolean
        get() = read(fallback = false) { id in scope.state.selectedBlockIds }

    public val readOnly: Boolean
        get() = read(fallback = true) { scope.readOnly }

    public val canUpdateBlock: Boolean
        get() = read(fallback = false) { scope.canUpdateBlock }

    public val isDark: Boolean
        get() = read(fallback = false) { isDarkProvider() }

    /** Merges [payloadJson] into the current payload (undo-aware once mounted). */
    public fun updatePayloadJson(payloadJson: String): CascadeCustomBlockMutationResult = mutate {
        if (isMutationDisabled()) return@mutate CascadeCustomBlockMutationResult.readOnly
        val parsed = parseJsonObjectPayloadSafely(payloadJson)
        parsed.errorMessage?.let {
            reportError(it)
            return@mutate CascadeCustomBlockMutationResult.invalidPayload
        }
        if (scope.getBlock(id) == null) return@mutate CascadeCustomBlockMutationResult.blockUnavailable
        scope.updateBlock(id) { block ->
            block.withContent(BlockContent.Custom(typeId, currentData() + parsed.data))
        }
        CascadeCustomBlockMutationResult.success
    }

    /** Replaces the entire payload with [payloadJson] (undo-aware once mounted). */
    public fun replacePayloadJson(payloadJson: String): CascadeCustomBlockMutationResult = mutate {
        if (isMutationDisabled()) return@mutate CascadeCustomBlockMutationResult.readOnly
        val parsed = parseJsonObjectPayloadSafely(payloadJson)
        parsed.errorMessage?.let {
            reportError(it)
            return@mutate CascadeCustomBlockMutationResult.invalidPayload
        }
        val block = scope.getBlock(id) ?: return@mutate CascadeCustomBlockMutationResult.blockUnavailable
        scope.replaceBlock(id, block.withContent(BlockContent.Custom(typeId, parsed.data)))
        CascadeCustomBlockMutationResult.success
    }

    public fun insertBlockBefore(
        typeId: String,
        payloadJson: String,
    ): CascadeCustomBlockMutationResult = mutate {
        insert(typeId, payloadJson) { block -> scope.insertBlockBefore(id, block) }
    }

    public fun insertBlockAfter(
        typeId: String,
        payloadJson: String,
    ): CascadeCustomBlockMutationResult = mutate {
        insert(typeId, payloadJson) { block -> scope.insertBlockAfter(id, block) }
    }

    public fun deleteBlock(): CascadeCustomBlockMutationResult = mutate {
        if (isMutationDisabled()) return@mutate CascadeCustomBlockMutationResult.readOnly
        if (scope.getBlock(id) == null) return@mutate CascadeCustomBlockMutationResult.blockUnavailable
        scope.deleteBlock(id)
        CascadeCustomBlockMutationResult.success
    }

    public fun focusBlock(): CascadeCustomBlockMutationResult = mutate {
        if (scope.getBlock(id) == null) return@mutate CascadeCustomBlockMutationResult.blockUnavailable
        scope.focusBlock(id)
        CascadeCustomBlockMutationResult.success
    }

    /** Reports the native view's preferred height (points), clamped to a sane range. */
    public fun setPreferredHeight(height: Double) {
        if (!NSThread.isMainThread) {
            reportError(CONTEXT_MAIN_THREAD_ERROR)
            return
        }
        applyPreferredHeight(clampBlockHeight(height))
    }

    private inline fun insert(
        typeId: String,
        payloadJson: String,
        place: (Block) -> Unit,
    ): CascadeCustomBlockMutationResult {
        if (isMutationDisabled()) return CascadeCustomBlockMutationResult.readOnly
        if (scope.getBlock(id) == null) return CascadeCustomBlockMutationResult.blockUnavailable
        return when (val outcome = buildBlock(typeId, payloadJson)) {
            is CascadeBlockBuildOutcome.Success -> {
                place(outcome.block)
                CascadeCustomBlockMutationResult.success
            }
            is CascadeBlockBuildOutcome.InvalidPayload -> {
                reportError(outcome.message)
                CascadeCustomBlockMutationResult.invalidPayload
            }
            is CascadeBlockBuildOutcome.UnknownType -> {
                reportError(outcome.message)
                CascadeCustomBlockMutationResult.unknownType
            }
        }
    }

    private fun isMutationDisabled(): Boolean = scope.readOnly || !scope.canUpdateBlock

    private fun currentData(): Map<String, Any?> =
        (scope.getBlock(id)?.content as? BlockContent.Custom)?.data ?: emptyMap()

    private inline fun mutate(
        block: () -> CascadeCustomBlockMutationResult,
    ): CascadeCustomBlockMutationResult {
        if (!NSThread.isMainThread) {
            reportError(CONTEXT_MAIN_THREAD_ERROR)
            return CascadeCustomBlockMutationResult.blockUnavailable
        }
        return block()
    }

    private inline fun <T> read(fallback: T, block: () -> T): T {
        if (!NSThread.isMainThread) {
            reportError(CONTEXT_MAIN_THREAD_ERROR)
            return fallback
        }
        return block()
    }
}
