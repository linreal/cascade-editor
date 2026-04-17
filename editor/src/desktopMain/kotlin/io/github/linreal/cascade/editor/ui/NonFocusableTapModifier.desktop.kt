package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

@Composable
internal actual fun Modifier.nonFocusableTap(
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current

    if (!enabled) return this

    return this
        .indication(interactionSource, indication)
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    interactionSource.emit(press)
                    val released = tryAwaitRelease()
                    if (released) {
                        interactionSource.emit(PressInteraction.Release(press))
                    } else {
                        interactionSource.emit(PressInteraction.Cancel(press))
                    }
                },
                onTap = { onClick() },
            )
        }
        .focusProperties { canFocus = false }
        .semantics {
            this.onClick(action = {
                onClick()
                true
            })
        }
}
