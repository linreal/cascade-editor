package io.github.linreal.cascade.editor.ui.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

public object Dividers {

    @Composable
    public fun Horizontal(
        color: Color,
        modifier: Modifier = Modifier,
        thickness: Dp = 1.dp,
    ) {
        Box(
            modifier
                .fillMaxWidth()
                .height(thickness)
                .background(color)
        )
    }
}
