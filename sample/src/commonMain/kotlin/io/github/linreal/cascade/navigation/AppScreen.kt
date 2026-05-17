package io.github.linreal.cascade.navigation

sealed class AppScreen {
    data object Landing : AppScreen()
    data object EditorDemo : AppScreen()
    data object CustomBlocks : AppScreen()
    data object CustomToolbar : AppScreen()
    data object ExternalToolbar : AppScreen()
    data object CustomHtmlProfile : AppScreen()
}
