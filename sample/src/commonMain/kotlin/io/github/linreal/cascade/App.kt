package io.github.linreal.cascade

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.navigation.AppScreen
import io.github.linreal.cascade.screens.CustomBlocksScreen
import io.github.linreal.cascade.screens.CustomHtmlProfileScreen
import io.github.linreal.cascade.screens.CustomToolbarScreen
import io.github.linreal.cascade.screens.EditorDemoScreen
import io.github.linreal.cascade.screens.comments.CommentsScreen
import io.github.linreal.cascade.screens.external_toolbar.ExternalToolbarScreen
import io.github.linreal.cascade.screens.LandingScreen
import io.github.linreal.cascade.screens.markdownfield.MarkdownFieldScreen
import io.github.linreal.cascade.theme.CascadeSampleColors
import io.github.linreal.cascade.theme.LocalCascadeSampleColors
import io.github.linreal.cascade.theme.sampleTypography

private val AppScreenSaver = Saver<AppScreen, String>(
    save = { it.saveKey },
    restore = { AppScreen.fromSaveKey(it) },
)

@Composable
@Preview
fun App() {
    val systemIsDark = isSystemInDarkTheme()
    var themeOverride by remember { mutableStateOf<Boolean?>(null) }
    val isDark = themeOverride ?: systemIsDark

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = Color(0xFFA78BFA),
            onPrimary = Color(0xFF1B1230),
            background = Color(0xFF120C24),
            surface = Color(0xFF1E1832),
            onBackground = Color(0xFFF4F1FB),
            onSurface = Color(0xFFF4F1FB),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6C3DE8),
            onPrimary = Color.White,
            background = Color(0xFFF6F2FF),
            surface = Color(0xFFFFFFFF),
            onBackground = Color(0xFF1C1238),
            onSurface = Color(0xFF1C1238),
        )
    }

    val sampleColors = if (isDark) CascadeSampleColors.dark() else CascadeSampleColors.light()

    MaterialTheme(colorScheme = colorScheme, typography = sampleTypography()) {
        CompositionLocalProvider(LocalCascadeSampleColors provides sampleColors) {
            var currentScreen by rememberSaveable(stateSaver = AppScreenSaver) {
                mutableStateOf<AppScreen>(AppScreen.Landing)
            }

            Box(
                modifier = Modifier
                    .background(sampleColors.background)
                    .windowInsetsPadding(
                        WindowInsets.safeContent.only(WindowInsetsSides.Vertical)
                    )
                    .padding(horizontal = 18.dp)
                    .fillMaxSize(),
            ) {
                when (currentScreen) {
                    AppScreen.Landing -> LandingScreen(
                        onNavigate = { currentScreen = it },
                    )
                    AppScreen.EditorDemo -> EditorDemoScreen(
                        isDark = isDark,
                        onToggleTheme = { themeOverride = !isDark },
                        onBack = { currentScreen = AppScreen.Landing },
                    )
                    AppScreen.CustomBlocks -> CustomBlocksScreen(
                        isDark = isDark,
                        onToggleTheme = { themeOverride = !isDark },
                        onBack = { currentScreen = AppScreen.Landing },
                    )
                    AppScreen.CustomToolbar -> CustomToolbarScreen(
                        isDark = isDark,
                        onToggleTheme = { themeOverride = !isDark },
                        onBack = { currentScreen = AppScreen.Landing },
                    )
                    AppScreen.ExternalToolbar -> ExternalToolbarScreen(
                        isDark = isDark,
                        onToggleTheme = { themeOverride = !isDark },
                        onBack = { currentScreen = AppScreen.Landing },
                    )
                    AppScreen.CustomHtmlProfile -> CustomHtmlProfileScreen(
                        isDark = isDark,
                        onToggleTheme = { themeOverride = !isDark },
                        onBack = { currentScreen = AppScreen.Landing },
                    )
                    AppScreen.Comments -> CommentsScreen(
                        isDark = isDark,
                        onToggleTheme = { themeOverride = !isDark },
                        onBack = { currentScreen = AppScreen.Landing },
                    )
                    AppScreen.MarkdownField -> MarkdownFieldScreen(
                        isDark = isDark,
                        onToggleTheme = { themeOverride = !isDark },
                        onBack = { currentScreen = AppScreen.Landing },
                    )
                }
            }
        }
    }
}
