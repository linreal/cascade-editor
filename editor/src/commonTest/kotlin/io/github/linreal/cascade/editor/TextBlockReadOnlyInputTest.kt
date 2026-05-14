package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import io.github.linreal.cascade.editor.ui.shouldDeliverImeEnter
import io.github.linreal.cascade.editor.ui.shouldFireBackspaceAtStartFromHardwareKey
import io.github.linreal.cascade.editor.ui.shouldProcessSentinelGuard
import io.github.linreal.cascade.editor.ui.renderers.allowsListAutoDetect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextBlockReadOnlyInputTest {

    @Test
    fun `read-only policy suppresses list auto-detect`() {
        assertFalse(
            allowsListAutoDetect(
                blockType = BlockType.Paragraph,
                policy = EditorInteractionPolicy.ReadOnly,
            )
        )
    }

    @Test
    fun `editable paragraph allows list auto-detect`() {
        assertTrue(
            allowsListAutoDetect(
                blockType = BlockType.Paragraph,
                policy = EditorInteractionPolicy.Editable,
            )
        )
    }

    @Test
    fun `editable code block suppresses list auto-detect`() {
        assertFalse(
            allowsListAutoDetect(
                blockType = BlockType.Code,
                policy = EditorInteractionPolicy.Editable,
            )
        )
    }

    @Test
    fun `read-only hardware Backspace at raw zero does not fire onBackspaceAtStart`() {
        assertFalse(
            shouldFireBackspaceAtStartFromHardwareKey(
                selectionStart = 0,
                selectionCollapsed = true,
                readOnly = true,
            )
        )
    }

    @Test
    fun `editable hardware Backspace at raw zero fires onBackspaceAtStart`() {
        assertTrue(
            shouldFireBackspaceAtStartFromHardwareKey(
                selectionStart = 0,
                selectionCollapsed = true,
                readOnly = false,
            )
        )
    }

    @Test
    fun `editable hardware Backspace mid-text does not fire onBackspaceAtStart`() {
        assertFalse(
            shouldFireBackspaceAtStartFromHardwareKey(
                selectionStart = 3,
                selectionCollapsed = true,
                readOnly = false,
            )
        )
    }

    @Test
    fun `non-collapsed selection at raw zero does not fire onBackspaceAtStart`() {
        // Non-collapsed selection means the user is deleting a range; the
        // sentinel-classifier path covers that case via DeletionAtStart.
        assertFalse(
            shouldFireBackspaceAtStartFromHardwareKey(
                selectionStart = 0,
                selectionCollapsed = false,
                readOnly = false,
            )
        )
    }

    @Test
    fun `read-only IME Enter action does not deliver onEnterPressed`() {
        assertFalse(shouldDeliverImeEnter(readOnly = true))
    }

    @Test
    fun `editable IME Enter action delivers onEnterPressed`() {
        assertTrue(shouldDeliverImeEnter(readOnly = false))
    }

    @Test
    fun `read-only sentinel guard short-circuits before classifying buffer changes`() {
        // Defense-in-depth: BasicTextField(readOnly = true) blocks IME/paste
        // writes, but a programmatic TextFieldState.edit call could still
        // disturb the sentinel. The guard must skip classification entirely.
        assertFalse(shouldProcessSentinelGuard(readOnly = true))
    }

    @Test
    fun `editable sentinel guard processes buffer changes`() {
        assertTrue(shouldProcessSentinelGuard(readOnly = false))
    }
}
