package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.ui.renderers.formatOrderedListPrefix
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderedListPrefixFormatterTest {

    @Test
    fun `formats decimal prefixes at root depth`() {
        assertEquals("3.", formatOrderedListPrefix(number = 3, depth = 0))
    }

    @Test
    fun `formats lower alpha prefixes at depth one`() {
        assertEquals("b.", formatOrderedListPrefix(number = 2, depth = 1))
    }

    @Test
    fun `formats lower roman prefixes at depth two`() {
        assertEquals("iv.", formatOrderedListPrefix(number = 4, depth = 2))
    }

    @Test
    fun `formats upper alpha prefixes at depth three`() {
        assertEquals("A.", formatOrderedListPrefix(number = 1, depth = 3))
    }

    @Test
    fun `formats lower alpha overflow as spreadsheet sequence`() {
        assertEquals("aa.", formatOrderedListPrefix(number = 27, depth = 1))
    }

    @Test
    fun `formats upper alpha overflow as spreadsheet sequence`() {
        assertEquals("AA.", formatOrderedListPrefix(number = 27, depth = 3))
    }

    @Test
    fun `falls back to decimal outside supported roman range`() {
        assertEquals("4000.", formatOrderedListPrefix(number = 4000, depth = 2))
    }
}
