package io.github.linreal.cascade.ios.block

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitViewController
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderScope
import io.github.linreal.cascade.editor.registry.ScopedBlockRenderer
import io.github.linreal.cascade.ios.controller.CascadeEditorController
import platform.UIKit.UIViewController

/**
 * Hosts a native custom block view inside the editor.
 *
 * The renderer owns a height state seeded from the registration estimate and
 * updated when the context reports a preferred height, and builds a
 * [CascadeCustomBlockContext] bound to the live [BlockRenderScope] so native
 * mutations route through editor history. A [LaunchedEffect] keyed on the
 * editor-side signals the block depends on notifies the native view (via
 * [CascadeCustomBlockContext.onChange]) so it can refresh after undo/redo,
 * loads, or theme/read-only changes.
 *
 * A failing renderer factory is contained: the error is reported and an empty
 * placeholder view controller is hosted instead of crashing the host app.
 */
internal class NativeCustomBlockRenderer(
    private val registration: CascadeCustomBlockRegistration,
    private val controller: CascadeEditorController,
) : ScopedBlockRenderer<NativeCustomBlockType> {

    // The drag ghost would instantiate a second live UIViewController for the
    // same block mid-gesture, and interop views ignore the ghost's draw-phase
    // translation — the editor shows a placeholder ghost instead.
    override val supportsDragPreview: Boolean get() = false

    @Composable
    override fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks,
        scope: BlockRenderScope,
    ) {
        val isDark = controller.configurationSnapshot.value.isDark
        // rememberSaveable (keyed per LazyColumn item) keeps the last reported
        // preferred height while the block is scrolled off-screen, so it re-enters
        // at its real size instead of jumping from the registration estimate.
        var height by rememberSaveable(block.id.value, stateSaver = autoSaver()) {
            mutableStateOf(clampBlockHeight(registration.estimatedHeight))
        }
        val context = remember(block.id.value) {
            CascadeCustomBlockContext(
                blockId = block.id.value,
                typeId = registration.typeId,
                scope = scope,
                reportError = { message -> controller.reportInternalError(message) },
                isDarkProvider = { controller.configurationSnapshot.value.isDark },
                applyPreferredHeight = { height = it },
                buildBlock = { typeId, payloadJson ->
                    buildInsertableBlock(typeId, payloadJson, controller.registry) { candidate ->
                        candidate in controller.nativeRegistrations
                    }
                },
            )
        }

        LaunchedEffect(block.content, isFocused, isSelected, scope.readOnly, isDark) {
            // onChange is native (Swift) code; fireOnChange contains a throw so it can
            // neither cancel this effect nor escape into the Compose runtime.
            context.fireOnChange()
        }

        UIKitViewController(
            factory = {
                try {
                    registration.rendererFactory(context)
                } catch (throwable: Throwable) {
                    controller.reportInternalError(
                        "Custom block renderer '${registration.typeId}' failed to build: " +
                            (throwable.message ?: throwable.toString())
                    )
                    UIViewController()
                }
            },
            // The native host owns its height (via the preferred-height contract)
            // but must always span the editor's content width, like built-in blocks.
            modifier = modifier.fillMaxWidth().height(height.dp),
        )
    }
}
