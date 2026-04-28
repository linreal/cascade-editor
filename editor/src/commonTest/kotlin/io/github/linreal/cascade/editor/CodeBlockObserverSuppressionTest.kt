package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.slash.SlashCommandTextObserver
import io.github.linreal.cascade.editor.ui.observers.ListAutoDetectObserver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the observer-construction predicates that `TextBlockField` uses to decide
 * whether to build the slash and list-auto-detect observers for a block. The
 * predicates are duplicated as small helpers so this test file does not depend on
 * a Compose runtime; the helpers must stay in lockstep with `TextBlockField`.
 *
 * Covers the spec scenarios:
 *  - Slash typed in Code does not open a slash session (observer not constructed).
 *  - `- ` and `1. ` typed in Code do not convert to lists (predicate suppresses).
 *  - Same-id Paragraph → Code disables both observers; Code → Paragraph re-enables.
 */
class CodeBlockObserverSuppressionTest {

    /**
     * Mirrors the construction predicates in
     * `editor/src/commonMain/.../ui/renderers/TextBlockField.kt`.
     */
    private data class ObserverPredicates(
        val slashSuppressed: Boolean,
        val suppressListAutoDetect: Boolean,
    ) {
        companion object {
            fun forBlock(block: Block): ObserverPredicates {
                val isCurrentlyList = block.type is BlockType.BulletList ||
                    block.type is BlockType.NumberedList
                return ObserverPredicates(
                    slashSuppressed = block.type is BlockType.Code,
                    suppressListAutoDetect = isCurrentlyList || block.type is BlockType.Code,
                )
            }
        }
    }

    /**
     * Builds the same observer pair `TextBlockField` would build for [block]: the
     * slash observer is null when slash is suppressed; the list observer always
     * exists, with its `isListBlock` predicate locked to the current value.
     */
    private fun buildObservers(
        block: Block,
        onListDetected: (BlockType, Int) -> Unit = { _, _ -> },
        initialVisibleText: String = (block.content as? BlockContent.Text)?.text.orEmpty(),
    ): TestObservers {
        val predicates = ObserverPredicates.forBlock(block)
        val slashObserver: SlashCommandTextObserver? = if (predicates.slashSuppressed) {
            null
        } else {
            SlashCommandTextObserver(
                blockId = block.id,
                onOpen = { _, _, _ -> },
                onUpdate = { _, _ -> },
                onClose = { },
                initialVisibleText = initialVisibleText,
            )
        }
        val listObserver = ListAutoDetectObserver(
            isListBlock = { predicates.suppressListAutoDetect },
            onListDetected = onListDetected,
            initialVisibleText = initialVisibleText,
        )
        return TestObservers(slashObserver, listObserver, predicates)
    }

    private class TestObservers(
        val slashObserver: SlashCommandTextObserver?,
        val listObserver: ListAutoDetectObserver,
        val predicates: ObserverPredicates,
    )

    private fun blockOf(type: BlockType, text: String = ""): Block =
        Block(id = BlockId("b1"), type = type, content = BlockContent.Text(text))

    // Predicate baselines

    @Test
    fun `code block suppresses slash and list auto-detect`() {
        val predicates = ObserverPredicates.forBlock(blockOf(BlockType.Code))
        assertTrue(predicates.slashSuppressed)
        assertTrue(predicates.suppressListAutoDetect)
    }

    @Test
    fun `paragraph block suppresses neither`() {
        val predicates = ObserverPredicates.forBlock(blockOf(BlockType.Paragraph))
        assertFalse(predicates.slashSuppressed)
        assertFalse(predicates.suppressListAutoDetect)
    }

    @Test
    fun `bullet list suppresses list auto-detect but not slash`() {
        val predicates = ObserverPredicates.forBlock(blockOf(BlockType.BulletList))
        assertFalse(predicates.slashSuppressed)
        assertTrue(predicates.suppressListAutoDetect)
    }

    @Test
    fun `numbered list suppresses list auto-detect but not slash`() {
        val predicates = ObserverPredicates.forBlock(blockOf(BlockType.NumberedList(1)))
        assertFalse(predicates.slashSuppressed)
        assertTrue(predicates.suppressListAutoDetect)
    }

    @Test
    fun `quote block suppresses neither`() {
        val predicates = ObserverPredicates.forBlock(blockOf(BlockType.Quote))
        assertFalse(predicates.slashSuppressed)
        assertFalse(predicates.suppressListAutoDetect)
    }

    @Test
    fun `todo block suppresses neither`() {
        val predicates = ObserverPredicates.forBlock(blockOf(BlockType.Todo(checked = false)))
        assertFalse(predicates.slashSuppressed)
        assertFalse(predicates.suppressListAutoDetect)
    }

    // Slash observer suppression

    @Test
    fun `code block does not construct a slash observer`() {
        val observers = buildObservers(blockOf(BlockType.Code))
        assertNull(observers.slashObserver, "slash observer must not be constructed for Code blocks")
    }

    @Test
    fun `paragraph block constructs a slash observer that opens on slash`() {
        var openCount = 0
        val block = blockOf(BlockType.Paragraph)
        val slashObserver = SlashCommandTextObserver(
            blockId = block.id,
            onOpen = { _, _, _ -> openCount++ },
            onUpdate = { _, _ -> },
            onClose = { },
            initialVisibleText = "",
        )

        // Sanity check: a real paragraph slash observer reacts to typing `/`.
        slashObserver.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        assertEquals(1, openCount)
        assertTrue(slashObserver.isTracking)

        val observers = buildObservers(block)
        assertNotNull(observers.slashObserver, "paragraph blocks must construct a slash observer")
    }

    // List auto-detect suppression

    @Test
    fun `code block list observer ignores the dash-space trigger`() {
        var detected = 0
        val observers = buildObservers(
            block = blockOf(BlockType.Code),
            onListDetected = { _, _ -> detected++ },
        )

        observers.listObserver.onTextChanged("-", isProgrammatic = false)
        observers.listObserver.onTextChanged("- ", isProgrammatic = false)

        assertEquals(0, detected, "Code blocks must not auto-convert on `- `")
    }

    @Test
    fun `code block list observer ignores the numbered-dot-space trigger`() {
        var detected = 0
        val observers = buildObservers(
            block = blockOf(BlockType.Code),
            onListDetected = { _, _ -> detected++ },
        )

        observers.listObserver.onTextChanged("1", isProgrammatic = false)
        observers.listObserver.onTextChanged("1.", isProgrammatic = false)
        observers.listObserver.onTextChanged("1. ", isProgrammatic = false)

        assertEquals(0, detected, "Code blocks must not auto-convert on `1. `")
    }

    @Test
    fun `paragraph block list observer still triggers on dash-space`() {
        var detected: Pair<BlockType, Int>? = null
        val observers = buildObservers(
            block = blockOf(BlockType.Paragraph),
            onListDetected = { type, len -> detected = type to len },
        )

        observers.listObserver.onTextChanged("-", isProgrammatic = false)
        observers.listObserver.onTextChanged("- ", isProgrammatic = false)

        // Sanity check: paragraph blocks still detect bullet triggers.
        assertEquals(BlockType.BulletList to 2, detected)
    }

    // Same-id conversion contract

    @Test
    fun `same-id Paragraph to Code conversion swaps predicates and drops slash observer`() {
        val id = BlockId("shared")
        val before = Block(id = id, type = BlockType.Paragraph, content = BlockContent.Text("/"))
        val after = before.copy(type = BlockType.Code)

        val beforePredicates = ObserverPredicates.forBlock(before)
        val afterPredicates = ObserverPredicates.forBlock(after)

        assertFalse(beforePredicates.slashSuppressed)
        assertFalse(beforePredicates.suppressListAutoDetect)
        assertTrue(afterPredicates.slashSuppressed)
        assertTrue(afterPredicates.suppressListAutoDetect)

        // `remember(...)` keys include slashSuppressed / suppressListAutoDetect, so a
        // change in either drops the prior observer instances. Modeled here by
        // observing that the predicate values differ across the conversion.
        assertEquals(
            ObserverPredicates(slashSuppressed = false, suppressListAutoDetect = false),
            beforePredicates,
        )
        assertEquals(
            ObserverPredicates(slashSuppressed = true, suppressListAutoDetect = true),
            afterPredicates,
        )

        // Building observers with the post-conversion block confirms the slash observer
        // is null even though the BlockId is unchanged.
        val afterObservers = buildObservers(after)
        assertNull(afterObservers.slashObserver)
    }

    @Test
    fun `same-id Code to Paragraph conversion re-enables both observers`() {
        val id = BlockId("shared")
        val before = Block(id = id, type = BlockType.Code, content = BlockContent.Text(""))
        val after = before.copy(type = BlockType.Paragraph)

        val beforePredicates = ObserverPredicates.forBlock(before)
        val afterPredicates = ObserverPredicates.forBlock(after)

        assertTrue(beforePredicates.slashSuppressed)
        assertTrue(beforePredicates.suppressListAutoDetect)
        assertFalse(afterPredicates.slashSuppressed)
        assertFalse(afterPredicates.suppressListAutoDetect)

        // Verify the post-conversion list observer responds to `- ` again.
        var detected: Pair<BlockType, Int>? = null
        val observers = buildObservers(
            block = after,
            onListDetected = { type, len -> detected = type to len },
        )
        observers.listObserver.onTextChanged("-", isProgrammatic = false)
        observers.listObserver.onTextChanged("- ", isProgrammatic = false)
        assertEquals(BlockType.BulletList to 2, detected)

        // And that a fresh slash observer is constructed.
        assertNotNull(observers.slashObserver)
    }

    @Test
    fun `same-id Paragraph to Code stale slash observer would still emit but is dropped by remember key`() {
        // Document the bug the spec calls out: SlashCommandTextObserver mutates its own
        // tracking state before `onOpen`, so reusing a stale observer after a same-id
        // conversion would still emit. The fix is non-construction in TextBlockField.
        val id = BlockId("shared")
        var openCount = 0
        val staleObserver = SlashCommandTextObserver(
            blockId = id,
            onOpen = { _, _, _ -> openCount++ },
            onUpdate = { _, _ -> },
            onClose = { },
            initialVisibleText = "",
        )
        // Simulate the user typing `/` BEFORE conversion — the observer opens a session.
        staleObserver.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        assertEquals(1, openCount)
        assertTrue(staleObserver.isTracking)

        // Same-id Paragraph → Code: TextBlockField's remember key (slashSuppressed) flips
        // to true, so the stale observer is replaced with `null`. Modeled by computing
        // the predicate for the post-conversion block.
        val postConversion = Block(id = id, type = BlockType.Code, content = BlockContent.Text("/"))
        val rebuilt = buildObservers(postConversion)
        assertNull(
            rebuilt.slashObserver,
            "stale paragraph slash observer must not survive same-id Paragraph → Code",
        )

        // Note: the original `staleObserver` would still emit if anyone called into it,
        // but TextBlockField stops invoking it. This test pins the construction-time
        // contract; UI-level routing is verified by manual QA.
    }
}
