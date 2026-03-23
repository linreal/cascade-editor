package io.github.linreal.cascade

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import io.github.linreal.cascade.navigation.AppScreen
import io.github.linreal.cascade.screens.EditorDemoScreen
import io.github.linreal.cascade.screens.LandingScreen
import io.github.linreal.cascade.screens.PlaceholderScreen

@Composable
@Preview
fun App() {
    val isDark = isSystemInDarkTheme()
    val colorScheme = if (isDark) {
        darkColorScheme(
            background = Color(0xFF16161E),
            surface = Color(0xFF1E1E28),
            primary = Color(0xFF8AB4F8),
            onBackground = Color(0xFFE8E8ED),
            onSurface = Color(0xFFE8E8ED),
        )
    } else {
        lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme) {
        var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Landing) }

        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            when (currentScreen) {
                AppScreen.Landing -> LandingScreen(
                    onNavigate = { currentScreen = it },
                )
                AppScreen.EditorDemo -> EditorDemoScreen(
                    isDark = isDark,
                    onBack = { currentScreen = AppScreen.Landing },
                )
                AppScreen.CustomBlocks -> PlaceholderScreen(
                    title = "Custom Blocks & Commands",
                    description = "Extend the editor with custom block types and slash commands",
                    onBack = { currentScreen = AppScreen.Landing },
                )
                AppScreen.ThemePlayground -> PlaceholderScreen(
                    title = "Theme Playground",
                    description = "Customize colors and typography live",
                    onBack = { currentScreen = AppScreen.Landing },
                )
                AppScreen.CustomToolbar -> PlaceholderScreen(
                    title = "Custom Toolbar",
                    description = "Build your own formatting toolbar",
                    onBack = { currentScreen = AppScreen.Landing },
                )
            }
        }
    }
}
