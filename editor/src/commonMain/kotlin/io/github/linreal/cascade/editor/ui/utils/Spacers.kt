package io.github.linreal.cascade.editor.ui.utils

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

public object Spacers {

    @Composable
    public fun Vertical(heightDp: Dp): Unit = UiSpacer(heightDp = heightDp)

    @Composable
    public fun Horizontal(widthDp: Dp): Unit = UiSpacer(widthDp = widthDp)

    @Composable
    public fun ColumnScope.FillVerticalSpacer(): Unit = Spacer(modifier = Modifier.weight(1f))

    @Composable
    public fun RowScope.FillHorizontalSpacer(): Unit = Spacer(modifier = Modifier.weight(1f))

    @Composable
    public fun UiSpacer(
        heightDp: Dp = 0.dp,
        widthDp: Dp = 0.dp,
    ) {
        Spacer(
            modifier = Modifier
                .height(heightDp)
                .width(widthDp)
        )
    }
}