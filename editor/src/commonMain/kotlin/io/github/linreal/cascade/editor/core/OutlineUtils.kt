package io.github.linreal.cascade.editor.core

/**
 * Directional depth delta for structural indentation commands.
 */
internal enum class IndentationDirection(
    internal val delta: Int,
) {
    Forward(1),
    Backward(-1),
}

/**
 * Immutable description of the blocks that participate in a drag operation.
 */
internal data class OutlineDragPayload(
    val primaryRootId: BlockId,
    val primaryRootIndex: Int,
    val primaryRootSupportsIndentation: Boolean,
    val dragRootIds: List<BlockId>,
    val payloadBlockIds: List<BlockId>,
    val payloadBlockIdSet: Set<BlockId>,
    val payloadBlockIndices: List<Int>,
    val payloadBlockIndexSet: Set<Int>,
    val payloadIndexRanges: List<IntRange>,
    val originalRootIndentationLevels: Map<BlockId, Int>,
    val payloadRelativeDepthOffsets: Map<BlockId, Int>,
    val payloadRootIdsByBlockId: Map<BlockId, BlockId>,
)

internal data class IndentationOutlineNormalizationResult(
    val blocks: List<Block>,
    val changedBlockIndices: List<Int>,
)

/**
 * Resolves document-ordered drag roots and their full subtree payload.
 *
 * Drag selection uses different targeting than indentation: unsupported selected blocks
 * still move, but selected descendants of another selected root are de-duplicated so
 * every payload block appears exactly once.
 */
internal fun resolveDragPayload(
    blocks: List<Block>,
    touchedBlockId: BlockId,
    selectedBlockIds: Set<BlockId>,
): OutlineDragPayload? {
    val touchedIndex = blocks.indexOfFirst { it.id == touchedBlockId }
    if (touchedIndex == -1) return null

    val rootIndices = if (touchedBlockId in selectedBlockIds) {
        resolveSelectedDragRootIndices(blocks, selectedBlockIds)
    } else {
        listOf(touchedIndex)
    }
    if (rootIndices.isEmpty()) return null

    val primaryRootIndex = rootIndices.firstOrNull { rootIndex ->
        touchedIndex in rootIndex until subtreeEndExclusive(blocks, rootIndex)
    } ?: touchedIndex

    val rootRanges = rootIndices.associateWith { rootIndex ->
        rootIndex until subtreeEndExclusive(blocks, rootIndex)
    }
    val payloadBlockIndices = blocks.indices
        .filter { index -> rootRanges.values.any { index in it } }
    val payloadBlockIds = payloadBlockIndices.map { index -> blocks[index].id }
    val dragRootIds = rootIndices.map { blocks[it].id }
    val originalRootIndentationLevels = rootIndices.associate { rootIndex ->
        blocks[rootIndex].id to blocks[rootIndex].attributes.indentationLevel
    }
    val payloadRelativeDepthOffsets = linkedMapOf<BlockId, Int>()
    val payloadRootIdsByBlockId = linkedMapOf<BlockId, BlockId>()

    for (rootIndex in rootIndices) {
        val root = blocks[rootIndex]
        val rootDepth = root.attributes.indentationLevel
        val range = rootRanges.getValue(rootIndex)
        for (index in range) {
            val block = blocks[index]
            payloadRelativeDepthOffsets[block.id] =
                block.attributes.indentationLevel - rootDepth
            payloadRootIdsByBlockId[block.id] = root.id
        }
    }

    return OutlineDragPayload(
        primaryRootId = blocks[primaryRootIndex].id,
        primaryRootIndex = primaryRootIndex,
        primaryRootSupportsIndentation = blocks[primaryRootIndex].type.supportsIndentation,
        dragRootIds = dragRootIds,
        payloadBlockIds = payloadBlockIds,
        payloadBlockIdSet = payloadBlockIds.toSet(),
        payloadBlockIndices = payloadBlockIndices,
        payloadBlockIndexSet = payloadBlockIndices.toSet(),
        payloadIndexRanges = payloadBlockIndices.toContiguousRanges(),
        originalRootIndentationLevels = originalRootIndentationLevels,
        payloadRelativeDepthOffsets = payloadRelativeDepthOffsets,
        payloadRootIdsByBlockId = payloadRootIdsByBlockId,
    )
}

/**
 * Moves a previously resolved drag payload to a visual gap and shifts its depths.
 *
 * [futurePrimaryRootDepth] is applied by computing one delta from the primary root's
 * original depth, then applying that same delta to every payload block. This preserves
 * descendant and multi-root relative depth offsets while still letting the hover layer
 * drive the primary root's future lane.
 *
 * Returns the original [blocks] instance when the drop is invalid or a no-op.
 */
internal fun moveDragPayload(
    blocks: List<Block>,
    payloadBlockIds: List<BlockId>,
    primaryRootId: BlockId?,
    originalPrimaryRootDepth: Int?,
    futurePrimaryRootDepth: Int,
    visualGap: Int,
): List<Block> {
    if (blocks.isEmpty() || payloadBlockIds.isEmpty() || primaryRootId == null) return blocks

    val payloadIdSet = payloadBlockIds.toSet()
    val payloadIndices = blocks
        .mapIndexedNotNull { index, block -> index.takeIf { block.id in payloadIdSet } }
    if (payloadIndices.size != payloadIdSet.size) return blocks

    val gap = visualGap.coerceIn(0, blocks.size)
    if (gap.isInsidePayload(payloadIndices)) return blocks

    val primaryBlock = blocks.firstOrNull { it.id == primaryRootId } ?: return blocks
    val resolvedOriginalPrimaryRootDepth =
        originalPrimaryRootDepth ?: primaryBlock.attributes.indentationLevel
    val depthDelta = futurePrimaryRootDepth - resolvedOriginalPrimaryRootDepth

    if (depthDelta == 0 && payloadIndices.isContiguousRange()) {
        val firstPayloadIndex = payloadIndices.first()
        val afterPayloadIndex = payloadIndices.last() + 1
        if (gap == firstPayloadIndex || gap == afterPayloadIndex) return blocks
    }

    if (depthDelta != 0) {
        for (block in blocks) {
            if (block.id !in payloadIdSet) continue
            val newDepth = block.attributes.indentationLevel + depthDelta
            if (newDepth !in BlockAttributes.MIN_INDENTATION_LEVEL..BlockAttributes.MAX_INDENTATION_LEVEL) {
                return blocks
            }
        }
    }

    val movedBlocks = ArrayList<Block>(payloadIndices.size)
    val remainingBlocks = ArrayList<Block>(blocks.size - payloadIndices.size)
    var insertionIndex = 0

    for (index in blocks.indices) {
        val block = blocks[index]
        if (block.id in payloadIdSet) {
            movedBlocks += if (depthDelta == 0) {
                block
            } else {
                val newDepth = block.attributes.indentationLevel + depthDelta
                block.withAttributes(block.attributes.copy(indentationLevel = newDepth))
            }
        } else {
            if (index < gap) insertionIndex++
            remainingBlocks += block
        }
    }

    val moved = remainingBlocks.toMutableList().apply {
        addAll(insertionIndex.coerceIn(0, remainingBlocks.size), movedBlocks)
    }

    if (moved == blocks) return blocks
    if (!moved.isValidIndentationOutline()) return blocks
    return moved
}

/**
 * Applies one indentation step to the current reducer targets.
 *
 * This function is intentionally all-or-original-list: if any shifted block would violate
 * outline constraints, the caller gets [blocks] back unchanged. That keeps reducers from
 * persisting partially shifted subtrees.
 *
 * Returns the same [blocks] instance when no legal indentation change is available.
 */
internal fun shiftIndentation(
    blocks: List<Block>,
    focusedBlockId: BlockId?,
    selectedBlockIds: Set<BlockId>,
    direction: IndentationDirection,
): List<Block> {
    if (blocks.isEmpty()) return blocks

    val rootIndices = resolveIndentationTargetRootIndices(
        blocks = blocks,
        focusedBlockId = focusedBlockId,
        selectedBlockIds = selectedBlockIds,
    )
    if (rootIndices.isEmpty()) return blocks
    if (!canShiftIndentation(blocks, rootIndices, direction)) return blocks

    val indexDeltas = mutableMapOf<Int, Int>()
    for (rootIndex in rootIndices) {
        val rootDepth = blocks[rootIndex].attributes.indentationLevel
        // Outdenting a root already at depth 0 is a no-op, but other selected roots
        // in the same command may still be eligible to move.
        val rootDelta = when (direction) {
            IndentationDirection.Forward -> direction.delta
            IndentationDirection.Backward -> {
                if (rootDepth == BlockAttributes.MIN_INDENTATION_LEVEL) 0 else direction.delta
            }
        }
        if (rootDelta == 0) continue

        val endExclusive = subtreeEndExclusive(blocks, rootIndex)
        for (index in rootIndex until endExclusive) {
            // Unsupported blocks can sit in the flat outline, but indent commands
            // must not assign them hidden indentation semantics.
            if (blocks[index].type.supportsIndentation) {
                indexDeltas[index] = rootDelta
            }
        }
    }
    if (indexDeltas.isEmpty()) return blocks

    for ((index, delta) in indexDeltas) {
        val newDepth = blocks[index].attributes.indentationLevel + delta
        if (newDepth !in BlockAttributes.MIN_INDENTATION_LEVEL..BlockAttributes.MAX_INDENTATION_LEVEL) {
            return blocks
        }
    }

    val shiftedBlocks = blocks.mapIndexed { index, block ->
        val delta = indexDeltas[index] ?: return@mapIndexed block
        val newDepth = block.attributes.indentationLevel + delta
        block.withAttributes(block.attributes.copy(indentationLevel = newDepth))
    }

    if (!shiftedBlocks.isValidIndentationOutline()) return blocks
    return shiftedBlocks
}

/**
 * Checks whether [targetRootIndices] can shift by one indentation step without
 * materializing a shifted block list.
 *
 * [targetRootIndices] must come from [resolveIndentationTargetRootIndices], so
 * indices are document-ordered supported roots with selected descendants already
 * filtered out.
 */
internal fun canShiftIndentation(
    blocks: List<Block>,
    targetRootIndices: List<Int>,
    direction: IndentationDirection,
): Boolean {
    if (blocks.isEmpty() || targetRootIndices.isEmpty()) return false

    var previousRootIndex = -1
    for (rootIndex in targetRootIndices) {
        if (rootIndex !in blocks.indices || rootIndex <= previousRootIndex) return false
        previousRootIndex = rootIndex
    }

    var rootCursor = 0
    var activeRootDepth: Int? = null
    var activeDelta = 0
    var changed = false

    for (index in blocks.indices) {
        val block = blocks[index]
        val originalDepth = block.attributes.indentationLevel
        val currentActiveRootDepth = activeRootDepth
        if (currentActiveRootDepth != null &&
            (!block.type.supportsIndentation || originalDepth <= currentActiveRootDepth)
        ) {
            activeRootDepth = null
            activeDelta = 0
        }

        if (rootCursor < targetRootIndices.size && targetRootIndices[rootCursor] == index) {
            val rootDelta = when (direction) {
                IndentationDirection.Forward -> direction.delta
                IndentationDirection.Backward -> {
                    if (originalDepth == BlockAttributes.MIN_INDENTATION_LEVEL) 0 else direction.delta
                }
            }
            rootCursor++
            if (rootDelta != 0) {
                activeRootDepth = originalDepth
                activeDelta = rootDelta
            }
        }

        val proposedDepth = if (activeDelta != 0 && block.type.supportsIndentation) {
            changed = true
            originalDepth + activeDelta
        } else {
            originalDepth
        }

        if (proposedDepth !in BlockAttributes.MIN_INDENTATION_LEVEL..BlockAttributes.MAX_INDENTATION_LEVEL) {
            return false
        }

        if (!block.type.supportsIndentation) {
            if (proposedDepth != BlockAttributes.MIN_INDENTATION_LEVEL) return false
        }
    }

    return changed
}

/**
 * Resolves the roots that should receive an indentation command.
 *
 * Block selection wins over focus. In selection mode, unsupported blocks are ignored and
 * selected descendants of an already selected supported root are filtered out so they do not
 * shift twice.
 */
internal fun resolveIndentationTargetRootIndices(
    blocks: List<Block>,
    focusedBlockId: BlockId?,
    selectedBlockIds: Set<BlockId>,
): List<Int> {
    if (selectedBlockIds.isNotEmpty()) {
        val rootIndices = mutableListOf<Int>()
        blocks.forEachIndexed { index, block ->
            if (block.id !in selectedBlockIds || !block.type.supportsIndentation) return@forEachIndexed
            val isDescendantOfSelectedSupportedRoot = rootIndices.any { rootIndex ->
                index < subtreeEndExclusive(blocks, rootIndex)
            }
            if (!isDescendantOfSelectedSupportedRoot) {
                rootIndices += index
            }
        }
        return rootIndices
    }

    val focusedIndex = focusedBlockId?.let { id ->
        blocks.indexOfFirst { it.id == id }
    } ?: -1
    if (focusedIndex == -1 || !blocks[focusedIndex].type.supportsIndentation) return emptyList()
    return listOf(focusedIndex)
}

/**
 * Returns the exclusive end index for the root's current flat-outline subtree.
 *
 * Unsupported blocks are hard outline boundaries in v1. They do not own descendants and
 * also terminate a preceding supported subtree, even when a later supported block carries
 * a deeper persisted depth.
 */
private fun subtreeEndExclusive(
    blocks: List<Block>,
    rootIndex: Int,
): Int {
    val root = blocks[rootIndex]
    if (!root.type.supportsIndentation) return rootIndex + 1

    val rootDepth = root.attributes.indentationLevel
    var index = rootIndex + 1
    while (index < blocks.size) {
        val block = blocks[index]
        if (!block.type.supportsIndentation) break
        if (block.attributes.indentationLevel <= rootDepth) break
        index++
    }
    return index
}

/**
 * Returns true when a visual gap lies inside any contiguous payload range.
 *
 * [payloadIndices] must be in ascending document order, which is true for indices
 * collected by scanning the block list from start to end.
 */
internal fun Int.isInsidePayload(payloadIndices: List<Int>): Boolean {
    if (payloadIndices.isEmpty()) return false

    var rangeStart = payloadIndices.first()
    var previous = rangeStart

    for (index in 1 until payloadIndices.size) {
        val cursor = payloadIndices[index]
        if (cursor == previous + 1) {
            previous = cursor
            continue
        }
        if (this > rangeStart && this <= previous) return true
        rangeStart = cursor
        previous = cursor
    }

    return this > rangeStart && this <= previous
}

internal fun Int.isInsidePayloadRanges(payloadIndexRanges: List<IntRange>): Boolean {
    return payloadIndexRanges.any { range -> this > range.first && this <= range.last }
}

private fun List<Int>.isContiguousRange(): Boolean {
    if (isEmpty()) return false
    return last() - first() + 1 == size
}

internal fun List<Int>.toContiguousRanges(): List<IntRange> {
    if (isEmpty()) return emptyList()

    val ranges = mutableListOf<IntRange>()
    var rangeStart = first()
    var previous = rangeStart

    for (index in 1 until size) {
        val current = this[index]
        if (current == previous + 1) {
            previous = current
        } else {
            ranges += rangeStart..previous
            rangeStart = current
            previous = current
        }
    }
    ranges += rangeStart..previous
    return ranges
}

/**
 * Validates the outline invariants that indentation reducers are allowed to create.
 *
 * Supported blocks may use any persisted indentation lane in the supported range.
 * Unsupported blocks are accepted only at depth 0, which prevents hidden hierarchy on
 * block types without indentation behavior.
 */
internal fun List<Block>.isValidIndentationOutline(): Boolean {
    if (isEmpty()) return true

    for (block in this) {
        val depth = block.attributes.indentationLevel
        if (depth !in BlockAttributes.MIN_INDENTATION_LEVEL..BlockAttributes.MAX_INDENTATION_LEVEL) {
            return false
        }

        if (!block.type.supportsIndentation) {
            if (depth != BlockAttributes.MIN_INDENTATION_LEVEL) return false
        }
    }

    return true
}

internal fun normalizeIndentationOutline(blocks: List<Block>): List<Block> {
    return normalizeIndentationOutlineWithReport(blocks).blocks
}

internal fun normalizeIndentationOutlineWithReport(
    blocks: List<Block>,
): IndentationOutlineNormalizationResult {
    if (blocks.isEmpty()) {
        return IndentationOutlineNormalizationResult(blocks, emptyList())
    }

    val normalized = ArrayList<Block>(blocks.size)
    val changedIndices = mutableListOf<Int>()

    blocks.forEachIndexed { index, block ->
        val originalDepth = block.attributes.indentationLevel
        val normalizedDepth = if (!block.type.supportsIndentation) {
            BlockAttributes.DEFAULT_INDENTATION_LEVEL
        } else {
            originalDepth.coerceIn(
                BlockAttributes.MIN_INDENTATION_LEVEL,
                BlockAttributes.MAX_INDENTATION_LEVEL,
            )
        }

        if (normalizedDepth == originalDepth) {
            normalized += block
        } else {
            changedIndices += index
            normalized += block.withAttributes(
                block.attributes.copy(indentationLevel = normalizedDepth)
            )
        }
    }

    return IndentationOutlineNormalizationResult(
        blocks = if (changedIndices.isEmpty()) blocks else normalized,
        changedBlockIndices = changedIndices,
    )
}

private fun resolveSelectedDragRootIndices(
    blocks: List<Block>,
    selectedBlockIds: Set<BlockId>,
): List<Int> {
    val selectedIndices = blocks
        .mapIndexedNotNull { index, block -> index.takeIf { block.id in selectedBlockIds } }
    return selectedIndices.filterNot { selectedIndex ->
        selectedIndices.any { candidateRootIndex ->
            candidateRootIndex < selectedIndex &&
                selectedIndex < subtreeEndExclusive(blocks, candidateRootIndex)
        }
    }
}
