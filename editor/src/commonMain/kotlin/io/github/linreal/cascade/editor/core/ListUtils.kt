package io.github.linreal.cascade.editor.core

/**
 * Scans [blocks] for consecutive runs of [BlockType.NumberedList] and assigns
 * sequential numbers within each run. The first block in a run defines the base
 * number; subsequent blocks get base + 1, base + 2, etc.
 *
 * Blocks that are not [BlockType.NumberedList] are returned unchanged.
 * Blocks whose number is already correct are returned as the same instance
 * (referential equality preserved).
 */
internal fun renumberNumberedLists(blocks: List<Block>): List<Block> {
    if (blocks.isEmpty()) return blocks

    // Quick scan: check if any numbers need fixing. Returns input list unchanged
    // when there are no numbered lists or all numbers are already correct.
    var scanBase = -1
    var scanOffset = 0
    var needsFix = false
    for (block in blocks) {
        val type = block.type
        if (type is BlockType.NumberedList) {
            if (scanBase == -1) {
                scanBase = type.number
                scanOffset = 0
            }
            if (type.number != scanBase + scanOffset) {
                needsFix = true
                break
            }
            scanOffset++
        } else {
            scanBase = -1
        }
    }
    if (!needsFix) return blocks

    val result = ArrayList<Block>(blocks.size)
    var runBase = -1
    var runOffset = 0

    for (block in blocks) {
        val type = block.type
        if (type is BlockType.NumberedList) {
            if (runBase == -1) {
                runBase = type.number
                runOffset = 0
            }
            val expected = runBase + runOffset
            if (type.number == expected) {
                result.add(block)
            } else {
                result.add(block.copy(type = BlockType.NumberedList(number = expected)))
            }
            runOffset++
        } else {
            runBase = -1
            result.add(block)
        }
    }

    return result
}
