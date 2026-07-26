package io.github.linreal.cascade.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class AppScreenTest {

    @Test
    fun `screen save keys restore every sample screen`() {
        val screens = listOf(
            AppScreen.Landing,
            AppScreen.EditorDemo,
            AppScreen.CustomBlocks,
            AppScreen.CustomToolbar,
            AppScreen.ExternalToolbar,
            AppScreen.CustomHtmlProfile,
            AppScreen.Comments,
            AppScreen.MarkdownField,
            AppScreen.PreviewGallery,
            AppScreen.PreviewDocumentEditor("preview-note-7"),
        )

        for (screen in screens) {
            assertEquals(screen, AppScreen.fromSaveKey(screen.saveKey))
        }
    }

    @Test
    fun `preview document ids survive characters used by the save-key separator`() {
        val screen = AppScreen.PreviewDocumentEditor("preview_document:odd:id")

        assertEquals(screen, AppScreen.fromSaveKey(screen.saveKey))
    }

    @Test
    fun `preview document key without an id restores the gallery`() {
        assertEquals(
            AppScreen.PreviewGallery,
            AppScreen.fromSaveKey("preview_document:"),
        )
    }

    @Test
    fun `unknown save key restores landing screen`() {
        assertEquals(AppScreen.Landing, AppScreen.fromSaveKey("missing"))
    }
}
