package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockType

/**
 * Default `listOutline` group encoder: claims contiguous runs of `BulletList`,
 * `NumberedList`, **and** `Todo` blocks (task items are list items, so `Todo`
 * must join the outline group to nest correctly).
 *
 * Canonical forms: `- ` bullets, normalized `n.` markers
 * from the block's stored number, `- [ ]` / `- [x]` task items, tight lists
 * (no blank lines between items). Indentation is **marker-relative**: a child
 * is indented by its parent's marker width (2 columns for `- ` and task
 * items — the `[x] ` checkbox is item *content* — and `digits + 2` for
 * ordered markers), so the output re-parses to the same nesting.
 *
 * Skipped editor depths have no `cascade-indent-N` analog in Markdown: they
 * encode best-effort at the deepest reachable nesting and re-decode shallower,
 * with a [MarkdownEncodeWarning.DroppedAttribute] warning.
 */
internal object DefaultMarkdownListOutlineEncoder : MarkdownBlockGroupEncoder {

    override fun groupKey(block: Block): Any? =
        if (block.type.isListOutlineType) LIST_OUTLINE_GROUP_KEY else null

    override fun encodeGroup(ctx: MarkdownEncodeContext, blocks: List<Block>): MarkdownEmit {
        if (blocks.isEmpty()) return MarkdownEmit.Skip
        val lines = ArrayList<String>(blocks.size)
        appendNodes(
            ctx = ctx,
            nodes = blocks.toListOutlineForest(),
            indent = "",
            expectedDepth = BlockAttributes.MIN_INDENTATION_LEVEL,
            lines = lines,
        )
        return MarkdownEmit.Raw(lines.joinToString("\n"))
    }

    /**
     * Builds a parent/child forest from the flat editor outline using the
     * nearest preceding shallower item as the parent (same construction as the
     * HTML codec's list outline encoder).
     */
    private fun List<Block>.toListOutlineForest(): List<ListNode> {
        val roots = mutableListOf<ListNode>()
        val stack = mutableListOf<ListNode>()
        for (block in this) {
            val depth = block.attributes.indentationLevel
            while (stack.isNotEmpty() && stack.last().depth >= depth) {
                stack.removeAt(stack.lastIndex)
            }
            val node = ListNode(block = block, depth = depth)
            val parent = stack.lastOrNull()
            if (parent == null) roots += node else parent.children += node
            stack += node
        }
        return roots
    }

    private fun appendNodes(
        ctx: MarkdownEncodeContext,
        nodes: List<ListNode>,
        indent: String,
        expectedDepth: Int,
        lines: MutableList<String>,
    ) {
        for (node in nodes) {
            if (node.depth > expectedDepth) {
                ctx.warn(
                    MarkdownEncodeWarning.DroppedAttribute(
                        attr = "indentationLevel",
                        reason = "skipped outline depth ${node.depth} has no Markdown " +
                            "encoding; the item re-decodes at depth $expectedDepth",
                        blockId = node.block.id,
                    ),
                )
            }
            val marker = markerFor(node.block.type)
            val continuationIndent = indent + " ".repeat(marker.continuationWidth)
            val inline = ctx.encodeInlineLines(node.block)
            val firstLine = indent + marker.text + inline.first()
            lines += if (inline.first().isEmpty()) firstLine.trimEnd(' ') else firstLine
            for (index in 1 until inline.size) {
                lines += continuationIndent + inline[index]
            }
            appendNodes(
                ctx = ctx,
                nodes = node.children,
                indent = continuationIndent,
                // A skipped-depth node emits at expectedDepth, so descendants
                // must be measured against the emitted depth, not the stored
                // one — otherwise they shift silently without a warning.
                expectedDepth = minOf(node.depth, expectedDepth) + 1,
                lines = lines,
            )
        }
    }

    private fun markerFor(type: BlockType): Marker = when (type) {
        is BlockType.Todo ->
            // The "[x] " checkbox is item content, not marker: continuation
            // and child indentation is relative to the "- " marker alone.
            Marker(text = if (type.checked) "- [x] " else "- [ ] ", continuationWidth = 2)

        is BlockType.NumberedList -> {
            val text = "${type.number}. "
            Marker(text = text, continuationWidth = text.length)
        }

        else -> Marker(text = "- ", continuationWidth = 2)
    }

    private class Marker(val text: String, val continuationWidth: Int)

    private class ListNode(
        val block: Block,
        val depth: Int,
        val children: MutableList<ListNode> = mutableListOf(),
    )

    private const val LIST_OUTLINE_GROUP_KEY: String = "listOutline"
}

internal val BlockType.isListOutlineType: Boolean
    get() = this == BlockType.BulletList || this is BlockType.NumberedList || this is BlockType.Todo
