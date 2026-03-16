package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.ui.observers.ListAutoDetectObserver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ListAutoDetectObserverTest {

    private data class Detection(val newType: BlockType, val prefixLength: Int)

    private class TestHarness(
        initialText: String = "",
        private val isListBlock: Boolean = false,
    ) {
        val detections = mutableListOf<Detection>()
        val observer = ListAutoDetectObserver(
            isListBlock = { isListBlock },
            onListDetected = { type, len -> detections.add(Detection(type, len)) },
            initialVisibleText = initialText,
        )
    }

    // --- Bullet trigger ---

    @Test
    fun `typing dash then space at start triggers BulletList`() {
        val h = TestHarness("")
        // User types '-'
        h.observer.onTextChanged("-", isProgrammatic = false)
        assertTrue(h.detections.isEmpty())
        // User types ' '
        h.observer.onTextChanged("- ", isProgrammatic = false)
        assertEquals(1, h.detections.size)
        assertEquals(BlockType.BulletList, h.detections[0].newType)
        assertEquals(2, h.detections[0].prefixLength)
    }

    @Test
    fun `typing dash space with trailing text triggers and reports correct prefix length`() {
        val h = TestHarness("-")
        // Text goes from "-" to "- " (space inserted at position 1)
        h.observer.onTextChanged("- Hello", isProgrammatic = false)
        // This is a multi-char insertion (6 chars), should NOT trigger
        assertTrue(h.detections.isEmpty())
    }

    @Test
    fun `typing dash space preserves trailing text`() {
        // User has typed "-H" then positions cursor after '-' and types space
        // This would be: prev="-H", current="- H" — single space insertion at position 1
        val h = TestHarness("-H")
        h.observer.onTextChanged("- H", isProgrammatic = false)
        assertEquals(1, h.detections.size)
        assertEquals(BlockType.BulletList, h.detections[0].newType)
        assertEquals(2, h.detections[0].prefixLength)
    }

    @Test
    fun `dash space in middle of text does not trigger`() {
        val h = TestHarness("Hello-")
        h.observer.onTextChanged("Hello- ", isProgrammatic = false)
        assertTrue(h.detections.isEmpty())
    }

    @Test
    fun `dash space on already BulletList block does not trigger`() {
        val h = TestHarness("", isListBlock = true)
        h.observer.onTextChanged("-", isProgrammatic = false)
        h.observer.onTextChanged("- ", isProgrammatic = false)
        assertTrue(h.detections.isEmpty())
    }

    @Test
    fun `dash space on already NumberedList block does not trigger`() {
        val h = TestHarness("", isListBlock = true)
        h.observer.onTextChanged("-", isProgrammatic = false)
        h.observer.onTextChanged("- ", isProgrammatic = false)
        assertTrue(h.detections.isEmpty())
    }

    @Test
    fun `pasting dash space does not trigger`() {
        // Paste = multi-char insertion: prev="" → current="- " is 2 chars inserted
        val h = TestHarness("")
        h.observer.onTextChanged("- ", isProgrammatic = false)
        // insertedLength=2, not single char → no trigger
        assertTrue(h.detections.isEmpty())
    }

    @Test
    fun `programmatic dash space does not trigger`() {
        val h = TestHarness("-")
        h.observer.onTextChanged("- ", isProgrammatic = true)
        assertTrue(h.detections.isEmpty())
    }

    // --- Numbered trigger ---

    @Test
    fun `typing 1 dot space triggers NumberedList with number 1`() {
        val h = TestHarness("1.")
        h.observer.onTextChanged("1. ", isProgrammatic = false)
        assertEquals(1, h.detections.size)
        val type = h.detections[0].newType
        assertIs<BlockType.NumberedList>(type)
        assertEquals(1, type.number)
        assertEquals(3, h.detections[0].prefixLength)
    }

    @Test
    fun `typing 3 dot space triggers NumberedList with number 3`() {
        val h = TestHarness("3.")
        h.observer.onTextChanged("3. ", isProgrammatic = false)
        assertEquals(1, h.detections.size)
        val type = h.detections[0].newType
        assertIs<BlockType.NumberedList>(type)
        assertEquals(3, type.number)
        assertEquals(3, h.detections[0].prefixLength)
    }

    @Test
    fun `typing 12 dot space triggers NumberedList with number 12`() {
        val h = TestHarness("12.")
        h.observer.onTextChanged("12. ", isProgrammatic = false)
        assertEquals(1, h.detections.size)
        val type = h.detections[0].newType
        assertIs<BlockType.NumberedList>(type)
        assertEquals(12, type.number)
        assertEquals(4, h.detections[0].prefixLength)
    }

    @Test
    fun `typing 0 dot space does not trigger`() {
        val h = TestHarness("0.")
        h.observer.onTextChanged("0. ", isProgrammatic = false)
        assertTrue(h.detections.isEmpty())
    }

    @Test
    fun `numbered trigger in middle of text does not trigger`() {
        val h = TestHarness("Hello 1.")
        h.observer.onTextChanged("Hello 1. ", isProgrammatic = false)
        assertTrue(h.detections.isEmpty())
    }

    @Test
    fun `numbered trigger with trailing text preserves it`() {
        val h = TestHarness("1.Hello")
        h.observer.onTextChanged("1. Hello", isProgrammatic = false)
        assertEquals(1, h.detections.size)
        assertEquals(3, h.detections[0].prefixLength)
    }

    @Test
    fun `numbered trigger on list block does not trigger`() {
        val h = TestHarness("1.", isListBlock = true)
        h.observer.onTextChanged("1. ", isProgrammatic = false)
        assertTrue(h.detections.isEmpty())
    }

    @Test
    fun `pasting numbered trigger does not trigger`() {
        val h = TestHarness("")
        h.observer.onTextChanged("1. ", isProgrammatic = false)
        // 3 chars inserted → not single char
        assertTrue(h.detections.isEmpty())
    }

    @Test
    fun `programmatic numbered trigger does not trigger`() {
        val h = TestHarness("1.")
        h.observer.onTextChanged("1. ", isProgrammatic = true)
        assertTrue(h.detections.isEmpty())
    }

    // --- Edge cases ---

    @Test
    fun `deletion does not trigger`() {
        val h = TestHarness("- ")
        h.observer.onTextChanged("-", isProgrammatic = false)
        assertTrue(h.detections.isEmpty())
    }

    @Test
    fun `replacement does not trigger`() {
        val h = TestHarness("x")
        h.observer.onTextChanged("-", isProgrammatic = false)
        // deleted 1, inserted 1 → deletedLength != 0
        assertTrue(h.detections.isEmpty())
    }

    @Test
    fun `non-space single char insertion does not trigger`() {
        val h = TestHarness("-")
        h.observer.onTextChanged("-x", isProgrammatic = false)
        assertTrue(h.detections.isEmpty())
    }
}
