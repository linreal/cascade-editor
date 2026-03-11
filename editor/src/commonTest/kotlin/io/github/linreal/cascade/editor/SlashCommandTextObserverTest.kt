package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.slash.SlashCommandTextObserver
import io.github.linreal.cascade.editor.state.SlashQueryRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlashCommandTextObserverTest {

    private data class OpenCall(
        val blockId: BlockId,
        val queryRange: SlashQueryRange,
        val query: String,
    )

    private data class UpdateCall(
        val query: String,
        val queryRange: SlashQueryRange,
    )

    private val blockId = BlockId("test-block")
    private val opens = mutableListOf<OpenCall>()
    private val updates = mutableListOf<UpdateCall>()
    private var closeCount = 0

    private fun createObserver(initialText: String = ""): SlashCommandTextObserver {
        opens.clear()
        updates.clear()
        closeCount = 0
        return SlashCommandTextObserver(
            blockId = blockId,
            onOpen = { id, range, query -> opens.add(OpenCall(id, range, query)) },
            onUpdate = { query, range -> updates.add(UpdateCall(query, range)) },
            onClose = { closeCount++ },
            initialVisibleText = initialText,
        )
    }

    // ---- Session opening ----

    @Test
    fun `typing slash opens session with correct initial range`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)

        assertEquals(1, opens.size)
        assertEquals(blockId, opens[0].blockId)
        assertEquals(SlashQueryRange(5, 6), opens[0].queryRange)
        assertEquals("", opens[0].query)
        assertTrue(observer.isTracking)
    }

    @Test
    fun `typing slash at start of empty text opens session`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)

        assertEquals(1, opens.size)
        assertEquals(SlashQueryRange(0, 1), opens[0].queryRange)
        assertEquals("", opens[0].query)
    }

    @Test
    fun `typing slash in middle of text opens session`() {
        val observer = createObserver("ab")
        observer.onTextChanged("a/b", isProgrammatic = false, cursorPosition = 2)

        assertEquals(1, opens.size)
        assertEquals(SlashQueryRange(1, 2), opens[0].queryRange)
    }

    @Test
    fun `typing non-slash character does not open session`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hellox", isProgrammatic = false, cursorPosition = 6)

        assertEquals(0, opens.size)
        assertFalse(observer.isTracking)
    }

    @Test
    fun `deletion does not open session`() {
        val observer = createObserver("hello/")
        observer.onTextChanged("hello", isProgrammatic = false, cursorPosition = 5)

        assertEquals(0, opens.size)
        assertFalse(observer.isTracking)
    }

    @Test
    fun `replacement does not open session even if result contains slash`() {
        val observer = createObserver("hello")
        // Select "hello" and replace with "/world"
        observer.onTextChanged("/world", isProgrammatic = false, cursorPosition = 6)

        assertEquals(0, opens.size)
    }

    // ---- Session updating ----

    @Test
    fun `typing characters extends range and updates query`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        observer.onTextChanged("hello/h", isProgrammatic = false, cursorPosition = 7)

        assertEquals(1, updates.size)
        assertEquals("h", updates[0].query)
        assertEquals(SlashQueryRange(5, 7), updates[0].queryRange)
    }

    @Test
    fun `typing multiple characters updates query progressively`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("/he", isProgrammatic = false, cursorPosition = 3)
        observer.onTextChanged("/hea", isProgrammatic = false, cursorPosition = 4)

        assertEquals(2, updates.size)
        assertEquals("he", updates[0].query)
        assertEquals(SlashQueryRange(0, 3), updates[0].queryRange)
        assertEquals("hea", updates[1].query)
        assertEquals(SlashQueryRange(0, 4), updates[1].queryRange)
    }

    @Test
    fun `spaces are included in query`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("/hello world", isProgrammatic = false, cursorPosition = 12)

        assertEquals("hello world", updates.last().query)
    }

    // ---- Deletion within session ----

    @Test
    fun `deleting characters shrinks range`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        observer.onTextChanged("hello/abc", isProgrammatic = false, cursorPosition = 9)
        observer.onTextChanged("hello/ab", isProgrammatic = false, cursorPosition = 8)

        val last = updates.last()
        assertEquals("ab", last.query)
        assertEquals(SlashQueryRange(5, 8), last.queryRange)
    }

    @Test
    fun `deleting all query characters leaves just slash`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("/a", isProgrammatic = false, cursorPosition = 2)
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)

        val last = updates.last()
        assertEquals("", last.query)
        assertEquals(SlashQueryRange(0, 1), last.queryRange)
    }

    // ---- Session closing via slash deletion ----

    @Test
    fun `deleting the slash closes session`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("", isProgrammatic = false, cursorPosition = 0)

        assertEquals(1, closeCount)
        assertFalse(observer.isTracking)
    }

    @Test
    fun `deleting slash with remaining query text closes session`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        observer.onTextChanged("hello/abc", isProgrammatic = false, cursorPosition = 9)
        // Select-all delete of "o/abc" → "hell"
        observer.onTextChanged("hell", isProgrammatic = false, cursorPosition = 4)

        assertEquals(1, closeCount)
        assertFalse(observer.isTracking)
    }

    // ---- Cursor movement ----

    @Test
    fun `cursor move after range closes session`() {
        val observer = createObserver("hello world")
        observer.onTextChanged("hello/ world", isProgrammatic = false, cursorPosition = 6)

        // Cursor moves to end of text (position 12, outside range [5, 6))
        observer.onSelectionChanged(12, 12)

        assertEquals(1, closeCount)
    }

    @Test
    fun `cursor move before slash closes session`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        observer.onTextChanged("hello/abc", isProgrammatic = false, cursorPosition = 9)

        observer.onSelectionChanged(2, 2)

        assertEquals(1, closeCount)
    }

    @Test
    fun `cursor within range does not close session`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("/abc", isProgrammatic = false, cursorPosition = 4)

        // Cursor between 'a' and 'b' (position 2), within range [0, 4)
        observer.onSelectionChanged(2, 2)

        assertEquals(0, closeCount)
        assertTrue(observer.isTracking)
    }

    @Test
    fun `cursor at rangeEnd stays within range`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("/abc", isProgrammatic = false, cursorPosition = 4)

        // Cursor at exact rangeEnd (position 4)
        observer.onSelectionChanged(4, 4)

        assertEquals(0, closeCount)
        assertTrue(observer.isTracking)
    }

    @Test
    fun `cursor at slashStart stays within range`() {
        val observer = createObserver("hi")
        observer.onTextChanged("hi/", isProgrammatic = false, cursorPosition = 3)
        observer.onTextChanged("hi/abc", isProgrammatic = false, cursorPosition = 6)

        // Cursor at slashStart (position 2)
        observer.onSelectionChanged(2, 2)

        assertEquals(0, closeCount)
        assertTrue(observer.isTracking)
    }

    @Test
    fun `selection extending outside range closes session`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        observer.onTextChanged("hello/abc", isProgrammatic = false, cursorPosition = 9)

        // Selection from position 3 to 8 (starts before slash range [5, 9))
        observer.onSelectionChanged(3, 8)

        assertEquals(1, closeCount)
    }

    @Test
    fun `selection entirely within range does not close session`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("/abcdef", isProgrammatic = false, cursorPosition = 7)

        // Selection from 2 to 5 — entirely within [0, 7)
        observer.onSelectionChanged(2, 5)

        assertEquals(0, closeCount)
    }

    @Test
    fun `selection change without active session is no-op`() {
        val observer = createObserver("hello")

        observer.onSelectionChanged(2, 4)

        assertEquals(0, closeCount)
    }

    // ---- Programmatic changes ----

    @Test
    fun `programmatic slash insertion does not open session`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/world", isProgrammatic = true, cursorPosition = 11)

        assertEquals(0, opens.size)
        assertFalse(observer.isTracking)
    }

    @Test
    fun `programmatic single-char slash does not open session`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = true, cursorPosition = 6)

        assertEquals(0, opens.size)
        assertFalse(observer.isTracking)
    }

    @Test
    fun `programmatic change preserving slash keeps session`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        assertTrue(observer.isTracking)

        // Programmatic change that still has '/' at position 5
        observer.onTextChanged("hello/world", isProgrammatic = true, cursorPosition = 11)

        assertEquals(0, closeCount)
        assertTrue(observer.isTracking)
    }

    @Test
    fun `programmatic change removing slash closes session`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        assertTrue(observer.isTracking)

        observer.onTextChanged("helloworld", isProgrammatic = true, cursorPosition = 10)

        assertEquals(1, closeCount)
        assertFalse(observer.isTracking)
    }

    @Test
    fun `programmatic change shortening text below slashStart closes session`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        assertTrue(observer.isTracking)

        // Text shortened to before the slash position
        observer.onTextChanged("hi", isProgrammatic = true, cursorPosition = 2)

        assertEquals(1, closeCount)
        assertFalse(observer.isTracking)
    }

    // ---- Paste / multi-character insertion ----

    @Test
    fun `pasted text containing slash does not open session`() {
        val observer = createObserver("hello")
        // Paste "/world" at end → 6 chars inserted
        observer.onTextChanged("hello/world", isProgrammatic = false, cursorPosition = 11)

        assertEquals(0, opens.size)
    }

    @Test
    fun `multi-character insertion does not open session`() {
        val observer = createObserver("")
        observer.onTextChanged("abc/def", isProgrammatic = false, cursorPosition = 7)

        assertEquals(0, opens.size)
    }

    // ---- Focus ----

    @Test
    fun `focus lost closes session`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        assertTrue(observer.isTracking)

        observer.onFocusLost()

        assertEquals(1, closeCount)
        assertFalse(observer.isTracking)
    }

    @Test
    fun `focus lost without session is no-op`() {
        val observer = createObserver("hello")
        observer.onFocusLost()

        assertEquals(0, closeCount)
    }

    // ---- notifySessionClosed ----

    @Test
    fun `notifySessionClosed resets tracking without dispatching close`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        assertTrue(observer.isTracking)

        observer.notifySessionClosed()

        assertFalse(observer.isTracking)
        assertEquals(0, closeCount) // no onClose dispatched
    }

    @Test
    fun `after notifySessionClosed new slash can open session`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("/abc", isProgrammatic = false, cursorPosition = 4)
        observer.notifySessionClosed()

        // Type a new slash after the old query text
        observer.onTextChanged("/abc/", isProgrammatic = false, cursorPosition = 5)

        assertEquals(2, opens.size)
        assertEquals(SlashQueryRange(4, 5), opens[1].queryRange)
    }

    // ---- Edit before slash (range shifting) ----

    @Test
    fun `text insertion before slash shifts range right`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        observer.onTextChanged("hello/abc", isProgrammatic = false, cursorPosition = 9)

        // Insert 'X' at position 2 (before slash at 5)
        observer.onTextChanged("heXllo/abc", isProgrammatic = false, cursorPosition = 3)

        val last = updates.last()
        assertEquals("abc", last.query)
        assertEquals(SlashQueryRange(6, 10), last.queryRange)
    }

    @Test
    fun `text deletion before slash shifts range left`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        observer.onTextChanged("hello/abc", isProgrammatic = false, cursorPosition = 9)

        // Delete 'h' at position 0
        observer.onTextChanged("ello/abc", isProgrammatic = false, cursorPosition = 0)

        val last = updates.last()
        assertEquals("abc", last.query)
        assertEquals(SlashQueryRange(4, 8), last.queryRange)
    }

    @Test
    fun `deletion spanning across slash position closes session`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        observer.onTextChanged("hello/abc", isProgrammatic = false, cursorPosition = 9)

        // Select "lo/a" and delete → "hebc"
        observer.onTextChanged("hebc", isProgrammatic = false, cursorPosition = 2)

        assertEquals(1, closeCount)
        assertFalse(observer.isTracking)
    }

    // ---- Edit within range ----

    @Test
    fun `insertion within query range extends range`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("/abc", isProgrammatic = false, cursorPosition = 4)

        // Insert 'X' between 'a' and 'b' (position 2)
        observer.onTextChanged("/aXbc", isProgrammatic = false, cursorPosition = 3)

        val last = updates.last()
        assertEquals("aXbc", last.query)
        assertEquals(SlashQueryRange(0, 5), last.queryRange)
    }

    @Test
    fun `deletion within query range shrinks range`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("/abcd", isProgrammatic = false, cursorPosition = 5)

        // Delete 'b' (position 2)
        observer.onTextChanged("/acd", isProgrammatic = false, cursorPosition = 2)

        val last = updates.last()
        assertEquals("acd", last.query)
        assertEquals(SlashQueryRange(0, 4), last.queryRange)
    }

    // ---- Edit after range ----

    @Test
    fun `edit after range with cursor outside closes session`() {
        val observer = createObserver("hello world")
        observer.onTextChanged("hello/ world", isProgrammatic = false, cursorPosition = 6)

        // Type 'X' at end (position 12), cursor at 13
        observer.onTextChanged("hello/ worldX", isProgrammatic = false, cursorPosition = 13)

        // Session closes because cursor (13) is outside range [5, 6)
        assertEquals(1, closeCount)
    }

    // ---- Identical text change is no-op ----

    @Test
    fun `identical text change does not trigger any action`() {
        val observer = createObserver("hello")
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)
        val opensBefore = opens.size
        val updatesBefore = updates.size

        // Same text again
        observer.onTextChanged("hello/", isProgrammatic = false, cursorPosition = 6)

        assertEquals(opensBefore, opens.size)
        assertEquals(updatesBefore, updates.size)
        assertEquals(0, closeCount)
    }

    // ---- Edge: cursor position -1 (non-collapsed selection) skips cursor validation ----

    @Test
    fun `non-collapsed cursor position skips cursor validation during text change`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("/abc", isProgrammatic = false, cursorPosition = 4)

        // Text change with non-collapsed selection (cursor = -1) — skip cursor check
        observer.onTextChanged("/abcd", isProgrammatic = false, cursorPosition = -1)

        assertEquals(0, closeCount)
        assertTrue(observer.isTracking)
        assertEquals("abcd", updates.last().query)
    }

    // ---- Rapid successive opens after close ----

    @Test
    fun `new slash after previous session was closed opens fresh session`() {
        val observer = createObserver("")
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)
        observer.onTextChanged("/abc", isProgrammatic = false, cursorPosition = 4)

        // Delete everything including slash
        observer.onTextChanged("", isProgrammatic = false, cursorPosition = 0)
        assertEquals(1, closeCount)

        // Type a new slash
        observer.onTextChanged("/", isProgrammatic = false, cursorPosition = 1)

        assertEquals(2, opens.size)
        assertEquals(SlashQueryRange(0, 1), opens[1].queryRange)
    }
}
