package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** The `listOutline` group encoder (bullets, numbered, todos). */
class MarkdownListOutlineEncodeTest {

    private fun listBlock(type: BlockType, text: String, depth: Int = 0): Block = Block(
        id = BlockId.generate(),
        type = type,
        content = BlockContent.Text(text),
        attributes = BlockAttributes(indentationLevel = depth),
    )

    private fun encode(blocks: List<Block>): MarkdownEncodeResult =
        MarkdownEncodeEngine.encodeWithReport(blocks, MarkdownProfile.Default)

    private fun MarkdownEncodeResult.requireMarkdown(): String =
        markdown ?: fail("expected a successful encode, got aborted with warnings=$warnings")

    @Test
    fun mixedOutlineNestsMarkerRelatively() {
        val markdown = encode(
            listOf(
                listBlock(BlockType.BulletList, "a", depth = 0),
                listBlock(BlockType.Todo(checked = true), "b", depth = 1),
                listBlock(BlockType.NumberedList(1), "c", depth = 1),
            ),
        ).requireMarkdown()
        assertEquals("- a\n  - [x] b\n  1. c\n", markdown)
    }

    @Test
    fun orderedParentIndentsChildrenByMarkerWidth() {
        val markdown = encode(
            listOf(
                listBlock(BlockType.NumberedList(1), "a", depth = 0),
                listBlock(BlockType.BulletList, "b", depth = 1),
            ),
        ).requireMarkdown()
        assertEquals("1. a\n   - b\n", markdown)
    }

    @Test
    fun numberedMarkersUseStoredNumbers() {
        val markdown = encode(
            listOf(
                listBlock(BlockType.NumberedList(1), "a"),
                listBlock(BlockType.NumberedList(2), "b"),
                listBlock(BlockType.NumberedList(3), "c"),
            ),
        ).requireMarkdown()
        assertEquals("1. a\n2. b\n3. c\n", markdown)
    }

    @Test
    fun todoFormsEncodeCanonically() {
        val markdown = encode(
            listOf(
                listBlock(BlockType.Todo(checked = false), "open"),
                listBlock(BlockType.Todo(checked = true), "done"),
            ),
        ).requireMarkdown()
        assertEquals("- [ ] open\n- [x] done\n", markdown)
    }

    @Test
    fun listRunIsOneTightUnitBetweenOtherBlocks() {
        val markdown = encode(
            listOf(
                Block.paragraph("x"),
                listBlock(BlockType.BulletList, "a"),
                listBlock(BlockType.BulletList, "b"),
                Block.paragraph("y"),
            ),
        ).requireMarkdown()
        assertEquals("x\n\n- a\n- b\n\ny\n", markdown)
    }

    @Test
    fun skippedDepthEncodesBestEffortWithWarning() {
        val result = encode(
            listOf(
                listBlock(BlockType.BulletList, "a", depth = 0),
                listBlock(BlockType.BulletList, "b", depth = 2),
            ),
        )
        assertEquals("- a\n  - b\n", result.requireMarkdown())
        val warning = result.warnings
            .filterIsInstance<MarkdownEncodeWarning.DroppedAttribute>()
            .single()
        assertEquals("indentationLevel", warning.attr)
    }

    @Test
    fun descendantsOfASkippedDepthNodeWarnAgainstTheEmittedDepth() {
        // b(2) emits at nesting 1, so c(3) re-decodes at depth 2 and must warn
        // too — silence would hide the shift for every descendant.
        val result = encode(
            listOf(
                listBlock(BlockType.BulletList, "a", depth = 0),
                listBlock(BlockType.BulletList, "b", depth = 2),
                listBlock(BlockType.BulletList, "c", depth = 3),
            ),
        )
        assertEquals("- a\n  - b\n    - c\n", result.requireMarkdown())
        val warnings = result.warnings
            .filterIsInstance<MarkdownEncodeWarning.DroppedAttribute>()
            .filter { it.attr == "indentationLevel" }
        assertEquals(2, warnings.size)
    }

    @Test
    fun hardBreakInsideListItemKeepsContinuationIndent() {
        val markdown = encode(
            listOf(listBlock(BlockType.BulletList, "a\nb")),
        ).requireMarkdown()
        assertEquals("- a\\\n  b\n", markdown)
    }

    @Test
    fun outlineRoundTripsThroughDecode() {
        val original = listOf(
            listBlock(BlockType.BulletList, "a", depth = 0),
            listBlock(BlockType.Todo(checked = true), "b", depth = 1),
            listBlock(BlockType.Todo(checked = false), "c", depth = 1),
            listBlock(BlockType.BulletList, "d", depth = 2),
            listBlock(BlockType.NumberedList(1), "e", depth = 0),
            listBlock(BlockType.NumberedList(2), "f", depth = 0),
        )
        val markdown = encode(original).requireMarkdown()
        val decoded = MarkdownDecodeEngine.decode(markdown, MarkdownProfile.Default).blocks
            ?: fail("re-decode failed")
        assertEquals(original.map { it.type }, decoded.map { it.type })
        assertEquals(
            original.map { it.attributes.indentationLevel },
            decoded.map { it.attributes.indentationLevel },
        )
        assertEquals(
            original.map { (it.content as BlockContent.Text).text },
            decoded.map { (it.content as BlockContent.Text).text },
        )
    }

    @Test
    fun emptyItemTextEmitsBareMarker() {
        val markdown = encode(listOf(listBlock(BlockType.BulletList, ""))).requireMarkdown()
        assertEquals("-\n", markdown)
        assertTrue(MarkdownDecodeEngine.decode(markdown, MarkdownProfile.Default).isSuccess)
    }
}
