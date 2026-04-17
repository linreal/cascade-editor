package io.github.linreal.cascade

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 860.dp))

    Window(
        onCloseRequest = ::exitApplication,
        title = "Cascade Editor",
        state = windowState,
    ) {
        App()
    }
}
