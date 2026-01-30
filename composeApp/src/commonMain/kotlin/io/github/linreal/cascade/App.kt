package io.github.linreal.cascade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import io.github.linreal.cascade.editor.loge
import io.github.linreal.cascade.editor.ui.BackspaceAwareTextField

@Composable
@Preview
fun App() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .background(Color.Gray)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            BackspaceAwareTextField(
                modifier = Modifier.background(Color.Blue),
                onBackspaceAtStart = {
                    loge(tag = "BackspaceAwareTextField", "Backspace at start")
                })
        }
    }
}