package io.github.linreal.cascade.navigation

sealed class AppScreen {
    data object Landing : AppScreen()
    data object EditorDemo : AppScreen()
    data object CustomBlocks : AppScreen()
    data object CustomToolbar : AppScreen()
    data object ExternalToolbar : AppScreen()
    data object CustomHtmlProfile : AppScreen()
    data object Comments : AppScreen()

    internal val saveKey: String
        get() = when (this) {
            Landing -> "landing"
            EditorDemo -> "editor_demo"
            CustomBlocks -> "custom_blocks"
            CustomToolbar -> "custom_toolbar"
            ExternalToolbar -> "external_toolbar"
            CustomHtmlProfile -> "custom_html_profile"
            Comments -> "comments"
        }

    companion object {
        internal fun fromSaveKey(saveKey: String): AppScreen = when (saveKey) {
            Landing.saveKey -> Landing
            EditorDemo.saveKey -> EditorDemo
            CustomBlocks.saveKey -> CustomBlocks
            CustomToolbar.saveKey -> CustomToolbar
            ExternalToolbar.saveKey -> ExternalToolbar
            CustomHtmlProfile.saveKey -> CustomHtmlProfile
            Comments.saveKey -> Comments
            else -> Landing
        }
    }
}
