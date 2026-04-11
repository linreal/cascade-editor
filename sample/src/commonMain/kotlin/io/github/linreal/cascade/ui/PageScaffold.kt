package io.github.linreal.cascade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun PageScaffold(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 760.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pageColor = MaterialTheme.colorScheme.background
    val canvasColor = if (pageColor.luminance() > 0.5f) {
        Color(0xFFE9ECF0)
    } else {
        Color(0xFF06060C)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(canvasColor),
        contentAlignment = Alignment.TopCenter,
    ) {
        val horizontalPadding = if (maxWidth >= 600.dp) 20.dp else 0.dp
        Column(
            modifier = Modifier
                .widthIn(max = maxContentWidth)
                .fillMaxSize()
                .background(pageColor)
                .padding(horizontal = horizontalPadding),
            content = content,
        )
    }
}
