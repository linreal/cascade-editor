package io.github.linreal.cascade.editor.core

/**
 * Scans [blocks] for consecutive runs of [BlockType.NumberedList] and assigns
 * sequential numbers starting from 1 within each run.
 *
 * Blocks that are not [BlockType.NumberedList] are returned unchanged.
 * Blocks whose number is already correct are returned as the same instance
 * (referential equality preserved).
 */
internal fun renumberNumberedLists(blocks: List<Block>): List<Block> {
    if (blocks.isEmpty()) return blocks

    // Quick scan: check if any numbers need fixing. Returns input list unchanged
    // when there are no numbered lists or all numbers are already correct.
    var scanExpected = 0
    var needsFix = false
    for (block in blocks) {
        val type = block.type
        if (type is BlockType.NumberedList) {
            scanExpected++
            if (type.number != scanExpected) {
                needsFix = true
                break
            }
        } else {
            scanExpected = 0
        }
    }
    if (!needsFix) return blocks

    val result = ArrayList<Block>(blocks.size)
    var runOffset = 0

    for (block in blocks) {
        val type = block.type
        if (type is BlockType.NumberedList) {
            runOffset++
            if (type.number == runOffset) {
                result.add(block)
            } else {
                result.add(block.copy(type = BlockType.NumberedList(number = runOffset)))
            }
        } else {
            runOffset = 0
            result.add(block)
        }
    }

    return result
}
