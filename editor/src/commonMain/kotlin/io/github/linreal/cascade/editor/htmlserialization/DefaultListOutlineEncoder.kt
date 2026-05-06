package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockType

internal object DefaultListOutlineEncoder : BlockGroupEncoder {

    override fun groupKey(block: Block): Any? =
        if (block.type.isListType) LIST_OUTLINE_GROUP_KEY else null

    override fun encodeGroup(ctx: HtmlEncodeContext, blocks: List<Block>): HtmlEmit {
        if (blocks.isEmpty()) return HtmlEmit.Skip
        return HtmlEmit.Raw(buildString {
            appendSiblingRuns(
                ctx = ctx,
                nodes = blocks.toListOutlineForest(),
                expectedDepth = BlockAttributes.MIN_INDENTATION_LEVEL,
            )
        })
    }

    /**
     * Builds a parent/child tree from the flat editor outline using the nearest
     * preceding shallower list item as the parent. This preserves the editor's free
     * indentation model without inventing placeholder list items.
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
            if (parent == null) {
                roots += node
            } else {
                parent.children += node
            }
            stack += node
        }

        return roots
    }

    /**
     * Emits consecutive sibling nodes of the same list tag into one `<ul>` / `<ol>`.
     * Mixed-type siblings become adjacent list containers at the same outline level.
     */
    private fun StringBuilder.appendSiblingRuns(
        ctx: HtmlEncodeContext,
        nodes: List<ListNode>,
        expectedDepth: Int,
    ) {
        var index = 0
        while (index < nodes.size) {
            val tag = nodes[index].block.type.listTag
            append("<")
            append(tag)
            append(">")

            while (index < nodes.size && nodes[index].block.type.listTag == tag) {
                appendListItem(ctx, nodes[index], expectedDepth)
                index++
            }

            append("</")
            append(tag)
            append(">")
        }
    }

    private fun StringBuilder.appendListItem(
        ctx: HtmlEncodeContext,
        node: ListNode,
        expectedDepth: Int,
    ) {
        append("<li")
        appendCascadeIndentationIfNeeded(actualDepth = node.depth, expectedDepth = expectedDepth)
        append(">")
        append(ctx.encodeInline(node.block))
        appendSiblingRuns(ctx = ctx, nodes = node.children, expectedDepth = node.depth + 1)
        append("</li>")
    }

    /**
     * Records semantic list depth only when nested HTML alone would decode to a
     * different editor depth. Ordinary parent/child list outlines therefore stay
     * canonical, while free/skipped editor depths remain lossless.
     */
    private fun StringBuilder.appendCascadeIndentationIfNeeded(
        actualDepth: Int,
        expectedDepth: Int,
    ) {
        if (actualDepth == expectedDepth) return

        val className = "$CASCADE_INDENT_CLASS_PREFIX$actualDepth"
        append(" class=\"")
        append(className)
        append('"')
    }

    private data class ListNode(
        val block: Block,
        val depth: Int,
        val children: MutableList<ListNode> = mutableListOf(),
    )

    private const val LIST_OUTLINE_GROUP_KEY: String = "listOutline"
}

private val BlockType.isListType: Boolean
    get() = this == BlockType.BulletList || this is BlockType.NumberedList

private val BlockType.listTag: String
    get() = if (this is BlockType.NumberedList) "ol" else "ul"

@ExperimentalCascadeHtmlApi
public const val CASCADE_INDENT_CLASS_PREFIX: String = "cascade-indent-"
