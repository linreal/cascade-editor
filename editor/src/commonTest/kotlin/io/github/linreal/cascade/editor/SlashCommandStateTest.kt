package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.CloseSlashCommand
import io.github.linreal.cascade.editor.action.HighlightSlashCommand
import io.github.linreal.cascade.editor.action.NavigateSlashBack
import io.github.linreal.cascade.editor.action.NavigateSlashSubmenu
import io.github.linreal.cascade.editor.action.OpenSlashCommand
import io.github.linreal.cascade.editor.action.UpdateSlashCommandSession
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.SlashQueryRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlashCommandStateTest {

    private fun createTestBlock(id: String, text: String = ""): Block {
        return Block(
            id = BlockId(id),
            type = BlockType.Paragraph,
            content = BlockContent.Text(text),
        )
    }

    private fun stateWithBlock(): EditorState {
        return EditorState.withBlocks(listOf(createTestBlock("b1", "Hello /world")))
    }

    // --- Opening a slash session ---

    @Test
    fun `opening a slash session stores anchor and range and query`() {
        val state = stateWithBlock()
        val range = SlashQueryRange(start = 6, endExclusive = 12)

        val result = OpenSlashCommand(
            anchorBlockId = BlockId("b1"),
            queryRange = range,
            initialQuery = "world",
        ).reduce(state)

        val slash = assertNotNull(result.slashCommandState)
        assertEquals(BlockId("b1"), slash.anchorBlockId)
        assertEquals("world", slash.query)
        assertEquals(range, slash.queryRange)
        assertTrue(slash.navigationPath.isEmpty())
        assertNull(slash.highlightedCommandId)
    }

    @Test
    fun `opening with empty initial query sets empty query`() {
        val state = stateWithBlock()
        val range = SlashQueryRange(start = 6, endExclusive = 7)

        val result = OpenSlashCommand(
            anchorBlockId = BlockId("b1"),
            queryRange = range,
        ).reduce(state)

        val slash = assertNotNull(result.slashCommandState)
        assertEquals("", slash.query)
        assertEquals(range, slash.queryRange)
    }

    // --- Updating the session ---

    @Test
    fun `updating the session changes both query and range atomically`() {
        val state = stateWithBlock()
        val openState = OpenSlashCommand(
            anchorBlockId = BlockId("b1"),
            queryRange = SlashQueryRange(6, 7),
        ).reduce(state)

        val newRange = SlashQueryRange(6, 10)
        val result = UpdateSlashCommandSession(
            query = "hea",
            queryRange = newRange,
        ).reduce(openState)

        val slash = assertNotNull(result.slashCommandState)
        assertEquals("hea", slash.query)
        assertEquals(newRange, slash.queryRange)
        // Anchor and other state untouched
        assertEquals(BlockId("b1"), slash.anchorBlockId)
        assertTrue(slash.navigationPath.isEmpty())
    }

    @Test
    fun `update is no-op when no session is active`() {
        val state = stateWithBlock()
        val result = UpdateSlashCommandSession(
            query = "hea",
            queryRange = SlashQueryRange(6, 10),
        ).reduce(state)

        assertNull(result.slashCommandState)
    }

    // --- Submenu navigation ---

    @Test
    fun `navigating into a submenu updates navigation path`() {
        val state = stateWithBlock()
        val openState = OpenSlashCommand(
            anchorBlockId = BlockId("b1"),
            queryRange = SlashQueryRange(6, 7),
        ).reduce(state)

        val menuId = SlashCommandId("menu.text")
        val result = NavigateSlashSubmenu(menuId).reduce(openState)

        val slash = assertNotNull(result.slashCommandState)
        assertEquals(listOf(menuId), slash.navigationPath)
        assertNull(slash.highlightedCommandId, "highlight should reset on submenu entry")
    }

    @Test
    fun `navigating deeper appends to navigation path`() {
        val state = stateWithBlock()
        val menu1 = SlashCommandId("menu.text")
        val menu2 = SlashCommandId("menu.headings")

        var s = OpenSlashCommand(
            anchorBlockId = BlockId("b1"),
            queryRange = SlashQueryRange(6, 7),
        ).reduce(state)
        s = NavigateSlashSubmenu(menu1).reduce(s)
        s = NavigateSlashSubmenu(menu2).reduce(s)

        val slash = assertNotNull(s.slashCommandState)
        assertEquals(listOf(menu1, menu2), slash.navigationPath)
    }

    @Test
    fun `navigate submenu is no-op when no session is active`() {
        val state = stateWithBlock()
        val result = NavigateSlashSubmenu(SlashCommandId("menu.text")).reduce(state)
        assertNull(result.slashCommandState)
    }

    // --- Back navigation ---

    @Test
    fun `navigate back pops one level from navigation path`() {
        val state = stateWithBlock()
        val menu1 = SlashCommandId("menu.text")
        val menu2 = SlashCommandId("menu.headings")

        var s = OpenSlashCommand(
            anchorBlockId = BlockId("b1"),
            queryRange = SlashQueryRange(6, 7),
        ).reduce(state)
        s = NavigateSlashSubmenu(menu1).reduce(s)
        s = NavigateSlashSubmenu(menu2).reduce(s)
        s = NavigateSlashBack.reduce(s)

        val slash = assertNotNull(s.slashCommandState)
        assertEquals(listOf(menu1), slash.navigationPath)
        assertNull(slash.highlightedCommandId, "highlight should reset on back")
    }

    @Test
    fun `navigate back at root is no-op`() {
        val state = stateWithBlock()
        val openState = OpenSlashCommand(
            anchorBlockId = BlockId("b1"),
            queryRange = SlashQueryRange(6, 7),
        ).reduce(state)

        val result = NavigateSlashBack.reduce(openState)

        // State unchanged — still at root
        assertEquals(openState, result)
    }

    @Test
    fun `navigate back is no-op when no session is active`() {
        val state = stateWithBlock()
        val result = NavigateSlashBack.reduce(state)
        assertNull(result.slashCommandState)
    }

    // --- Closing the session ---

    @Test
    fun `closing clears the entire session`() {
        val state = stateWithBlock()
        var s = OpenSlashCommand(
            anchorBlockId = BlockId("b1"),
            queryRange = SlashQueryRange(6, 7),
        ).reduce(state)
        s = NavigateSlashSubmenu(SlashCommandId("menu.text")).reduce(s)
        s = HighlightSlashCommand(SlashCommandId("cmd.heading1")).reduce(s)

        val result = CloseSlashCommand.reduce(s)
        assertNull(result.slashCommandState)
    }

    @Test
    fun `closing when already closed is no-op`() {
        val state = stateWithBlock()
        val result = CloseSlashCommand.reduce(state)
        assertNull(result.slashCommandState)
    }

    // --- Highlight changes ---

    @Test
    fun `highlight changes do not disturb query or range state`() {
        val state = stateWithBlock()
        val range = SlashQueryRange(6, 10)
        val openState = OpenSlashCommand(
            anchorBlockId = BlockId("b1"),
            queryRange = range,
            initialQuery = "hea",
        ).reduce(state)

        val cmdId = SlashCommandId("cmd.heading1")
        val result = HighlightSlashCommand(cmdId).reduce(openState)

        val slash = assertNotNull(result.slashCommandState)
        assertEquals(cmdId, slash.highlightedCommandId)
        assertEquals("hea", slash.query)
        assertEquals(range, slash.queryRange)
        assertEquals(BlockId("b1"), slash.anchorBlockId)
    }

    @Test
    fun `highlight can be cleared to null`() {
        val state = stateWithBlock()
        var s = OpenSlashCommand(
            anchorBlockId = BlockId("b1"),
            queryRange = SlashQueryRange(6, 7),
        ).reduce(state)
        s = HighlightSlashCommand(SlashCommandId("cmd.heading1")).reduce(s)
        s = HighlightSlashCommand(null).reduce(s)

        val slash = assertNotNull(s.slashCommandState)
        assertNull(slash.highlightedCommandId)
    }

    @Test
    fun `highlight is no-op when no session is active`() {
        val state = stateWithBlock()
        val result = HighlightSlashCommand(SlashCommandId("cmd.heading1")).reduce(state)
        assertNull(result.slashCommandState)
    }

    // --- Cross-cutting: navigation does not affect query ---

    @Test
    fun `submenu navigation preserves query and range`() {
        val state = stateWithBlock()
        val range = SlashQueryRange(6, 10)
        var s = OpenSlashCommand(
            anchorBlockId = BlockId("b1"),
            queryRange = range,
            initialQuery = "hea",
        ).reduce(state)
        s = NavigateSlashSubmenu(SlashCommandId("menu.text")).reduce(s)

        val slash = assertNotNull(s.slashCommandState)
        assertEquals("hea", slash.query)
        assertEquals(range, slash.queryRange)
    }
}
