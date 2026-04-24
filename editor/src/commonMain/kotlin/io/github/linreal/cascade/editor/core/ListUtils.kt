package io.github.linreal.cascade.editor.core

/**
 * Scans [blocks] for numbered-list runs and assigns sequential numbers starting
 * from 1 within each outline sequence.
 *
 * A numbered-list sequence is scoped by indentation depth and derived parent.
 * Deeper descendants do not break an ancestor-depth numbered run, while a
 * same-depth non-numbered block resets the run at that depth.
 *
 * Blocks that are not [BlockType.NumberedList] are returned unchanged.
 * Blocks whose number is already correct are returned as the same instance
 * (referential equality preserved). If no numbers need changing, [blocks] is
 * returned unchanged.
 */
internal fun renumberNumberedLists(blocks: List<Block>): List<Block> {
    if (blocks.isEmpty()) return blocks

    val depthCount = BlockAttributes.MAX_INDENTATION_LEVEL + 1
    val ancestors = arrayOfNulls<BlockId>(depthCount)
    val numberingRuns = Array(depthCount) { NumberingRun() }
    val result = ArrayList<Block>(blocks.size)
    var changed = false

    for (block in blocks) {
        val depth = block.attributes.indentationLevel
        val type = block.type
        if (!type.supportsIndentation) {
            numberingRuns.forEach(NumberingRun::reset)
            clearAncestors(ancestors)
            result.add(block)
            continue
        }

        val parentKey = parentKeyFor(depth, ancestors)

        if (type is BlockType.NumberedList) {
            val expectedNumber = numberingRuns[depth].nextNumber(parentKey)
            if (type.number == expectedNumber) {
                result.add(block)
            } else {
                changed = true
                result.add(block.copy(type = BlockType.NumberedList(number = expectedNumber)))
            }
        } else {
            numberingRuns[depth].breakSequence(parentKey)
            result.add(block)
        }

        updateAncestors(depth, block.id, ancestors)
    }

    return if (changed) result else blocks
}

private object RootOutlineParent

private class NumberingRun(
    private var parentKey: Any? = null,
    private var nextNumber: Int = 1,
) {
    fun nextNumber(newParentKey: Any): Int {
        if (parentKey != newParentKey) {
            parentKey = newParentKey
            nextNumber = 1
        }
        return nextNumber++
    }

    fun breakSequence(newParentKey: Any) {
        parentKey = newParentKey
        nextNumber = 1
    }

    fun reset() {
        parentKey = null
        nextNumber = 1
    }
}

private fun parentKeyFor(depth: Int, ancestors: Array<BlockId?>): Any {
    if (depth == 0) return RootOutlineParent

    for (candidateDepth in (depth - 1) downTo 0) {
        ancestors[candidateDepth]?.let { return it }
    }

    return RootOutlineParent
}

private fun updateAncestors(depth: Int, blockId: BlockId, ancestors: Array<BlockId?>) {
    ancestors[depth] = blockId

    for (staleDepth in (depth + 1)..ancestors.lastIndex) {
        ancestors[staleDepth] = null
    }
}

private fun clearAncestors(ancestors: Array<BlockId?>) {
    for (index in ancestors.indices) {
        ancestors[index] = null
    }
}
