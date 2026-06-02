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
        )

        for (screen in screens) {
            assertEquals(screen, AppScreen.fromSaveKey(screen.saveKey))
        }
    }

    @Test
    fun `unknown save key restores landing screen`() {
        assertEquals(AppScreen.Landing, AppScreen.fromSaveKey("missing"))
    }
}
