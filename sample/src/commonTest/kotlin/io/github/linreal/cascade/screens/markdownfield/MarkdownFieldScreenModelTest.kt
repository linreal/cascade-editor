package io.github.linreal.cascade.screens.markdownfield

import io.github.linreal.cascade.editor.core.BlockContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownFieldScreenModelTest {

    @Test
    fun `unsupported document opens raw and leaves the stored string untouched`() {
        val source = "Costs:\n\n| item | usd |\n| - | - |\n| a | 1 |\n"
        val store = FakeMarkdownStore(source)
        val model = MarkdownFieldScreenModel(store)

        model.open()

        assertEquals(MarkdownFieldMode.RawFallback, model.mode)
        assertTrue(model.preservedBlockCount >= 1)
        assertEquals(0, store.writeCount, "opening must never write")
        assertEquals(source, store.content)
    }

    @Test
    fun `native edit with an external revision bump is rejected into the conflict path`() {
        val store = FakeMarkdownStore("# Title\n\nBody\n")
        val model = MarkdownFieldScreenModel(store)
        model.open()
        assertEquals(MarkdownFieldMode.Native, model.mode)

        // Edit a text block live.
        val paragraph = model.editorState.state.blocks.first {
            (it.content as? BlockContent.Text)?.text == "Body"
        }
        model.textStates.getOrCreate(paragraph.id, "Body")
        model.textStates.setText(paragraph.id, "Body edited")

        // Someone else changes the field.
        store.bumpExternally("# Title\n\nExternally changed\n")
        val externalContent = store.content

        val outcome = model.save()

        assertEquals(MarkdownSaveOutcome.Conflict, outcome)
        assertEquals(externalContent, store.content, "our save must not overwrite the external change")
        assertTrue(model.saveError != null)
        // The user's live edit is retained (both states kept).
        assertEquals("Body edited", model.textStates.getVisibleText(paragraph.id))
    }

    @Test
    fun `native edit with matching revision persists canonical markdown exactly once`() {
        val store = FakeMarkdownStore("# Title\n\nBody\n")
        val model = MarkdownFieldScreenModel(store)
        model.open()

        val paragraph = model.editorState.state.blocks.first {
            (it.content as? BlockContent.Text)?.text == "Body"
        }
        model.textStates.getOrCreate(paragraph.id, "Body")
        model.textStates.setText(paragraph.id, "Body edited")

        val outcome = model.save()

        assertEquals(MarkdownSaveOutcome.Saved, outcome)
        assertEquals(1, store.writeCount, "must persist exactly once")
        // Exact canonical payload, not just a substring.
        assertEquals("# Title\n\nBody edited\n", store.content)
        assertEquals(null, model.saveError)
    }

    @Test
    fun `opening then saving without editing writes nothing`() {
        val store = FakeMarkdownStore("# Title\n\nBody\n")
        val model = MarkdownFieldScreenModel(store)
        model.open()

        val outcome = model.save()

        assertEquals(MarkdownSaveOutcome.NoChange, outcome)
        assertEquals(0, store.writeCount)
    }

    @Test
    fun `a lossy native edit is rejected and nothing is written`() {
        val store = FakeMarkdownStore("# Title\n\nBody\n")
        val model = MarkdownFieldScreenModel(store)
        model.open()

        val paragraph = model.editorState.state.blocks.first {
            (it.content as? BlockContent.Text)?.text == "Body"
        }
        model.textStates.getOrCreate(paragraph.id, "Body")
        // Trailing spaces do not round-trip (the inline phase trims them) → DataLoss.
        model.textStates.setText(paragraph.id, "Body trailing   ")

        val outcome = model.save()

        assertEquals(MarkdownSaveOutcome.RejectedLossy, outcome)
        assertEquals(0, store.writeCount)
        assertTrue(model.saveError != null)
        // Live state retained (both states kept).
        assertEquals("Body trailing   ", model.textStates.getVisibleText(paragraph.id))
    }
}
