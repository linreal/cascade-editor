package io.github.linreal.cascade.ios.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import io.github.linreal.cascade.editor.ui.CascadeDocumentPreview
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi
import io.github.linreal.cascade.ios.resources.CascadeEditorResourceReader
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader
import platform.UIKit.UIViewController

/**
 * Creates one transparent, non-scrolling UIKit host for this preview.
 *
 * This facade is experimental. Its source and binary API may change before
 * preview mode is stabilized.
 *
 * The native owner must provide bounded width and height constraints. Keep the
 * returned controller for that owning view's lifetime and release it when a
 * reusable list/grid cell is dismantled.
 */
@OptIn(
    ExperimentalCascadePreviewApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalResourceApi::class,
)
public fun CascadeDocumentPreviewController.makeViewController(): UIViewController = onMainThread(
    fallback = { UIViewController() },
) {
    ComposeUIViewController(configure = { opaque = false }) {
        val blocks by blocksSnapshot
        val configurationState by configurationSnapshot
        val hasLinkOpener by hasLinkOpenerSnapshot
        val previewConfig = remember(configurationState) {
            configurationState.toCoreConfig { error ->
                reportInternalError(
                    "CascadeDocumentPreview ${error.context} failed: " +
                        (error.cause.message ?: error.cause.toString())
                )
            }
        }
        val theme = remember(configurationState.isDark) {
            configurationState.resolveEditorTheme()
        }

        CompositionLocalProvider(LocalResourceReader provides CascadeEditorResourceReader) {
            CascadeDocumentPreview(
                blocks = blocks,
                registry = registry,
                theme = theme,
                strings = strings,
                blockStrings = blockStrings,
                config = previewConfig,
                onOpenLink = if (hasLinkOpener) {
                    { target -> openLink(target) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
