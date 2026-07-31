@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.preview

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.markdown.ExperimentalCascadeMarkdownApi
import io.github.linreal.cascade.editor.markdown.MarkdownSchema
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.serialization.DocumentDecodeWarning
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorColors as CoreCascadeEditorColors
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import io.github.linreal.cascade.editor.ui.createEditorRegistry
import io.github.linreal.cascade.ios.controller.CascadeDocumentLoadResult
import io.github.linreal.cascade.ios.controller.CascadeMarkdownLoadResult
import io.github.linreal.cascade.ios.controller.toCascadeMarkdownLoadResult
import io.github.linreal.cascade.ios.controller.toCascadeWarningMessage
import io.github.linreal.cascade.ios.theme.CascadeEditorColors
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val MAIN_THREAD_ERROR: String =
    "CascadeDocumentPreviewController must be used on the main thread"

/**
 * Lightweight Swift/UIKit facade over the static Compose document preview.
 *
 * This facade is experimental. Its source and binary API may change before
 * preview mode is stabilized.
 *
 * This controller owns only decoded immutable blocks, preview configuration,
 * localization defaults, and a dedicated preview-renderer registry. It never
 * creates editor state, text/span runtime holders, history, focus, or an
 * editable controller.
 *
 * Native custom editor registrations are deliberately not shared with this
 * controller. Unknown/custom blocks use the bounded core preview fallback;
 * editor renderers and UIKit custom editor views are never mounted implicitly.
 */
@ObjCName("CascadeDocumentPreviewController", exact = true)
public class CascadeDocumentPreviewController public constructor(
    configuration: CascadeDocumentPreviewConfiguration,
) {
    public constructor() : this(configuration = CascadeDocumentPreviewConfiguration())

    internal val blocksSnapshot: MutableState<List<Block>> = mutableStateOf(emptyList())
    internal val configurationSnapshot: MutableState<CascadeDocumentPreviewConfiguration> =
        mutableStateOf(configuration)
    internal val registry: BlockRegistry = createEditorRegistry()
    internal val strings: CascadeEditorStrings = CascadeEditorStrings.default()
    internal val blockStrings: CascadeEditorBlockStrings = CascadeEditorBlockStrings.default()
    internal val hasLinkOpenerSnapshot: MutableState<Boolean> = mutableStateOf(false)
    internal val customColorsSnapshot: MutableState<CoreCascadeEditorColors?> =
        mutableStateOf(null)

    private var currentConfiguration: CascadeDocumentPreviewConfiguration = configuration
    private var openLinkCallback: ((String) -> Unit)? = null
    private var internalErrorCallback: ((String) -> Unit)? = null

    public val configuration: CascadeDocumentPreviewConfiguration
        get() = onMainThread(
            fallback = { CascadeDocumentPreviewConfiguration() },
            block = { currentConfiguration },
        )

    public var onOpenLink: ((String) -> Unit)?
        get() = onMainThread(
            fallback = { null },
            block = { openLinkCallback },
        )
        set(value) {
            onMainThread(fallback = {}) {
                openLinkCallback = value
                hasLinkOpenerSnapshot.value = value != null
            }
        }

    public var onInternalError: ((String) -> Unit)?
        get() = onMainThread(
            fallback = { null },
            block = { internalErrorCallback },
        )
        set(value) {
            onMainThread(fallback = {}) {
                internalErrorCallback = value
            }
        }

    /**
     * Replaces the immutable preview snapshot with a decoded JSON document.
     *
     * A parse failure returns `success = false` and preserves the currently
     * displayed document. Non-fatal decode warnings are returned alongside the
     * successfully decoded snapshot.
     */
    public fun loadJson(json: String): CascadeDocumentLoadResult = onMainThread(
        fallback = {
            CascadeDocumentLoadResult(
                success = false,
                warningMessages = listOf(MAIN_THREAD_ERROR),
            )
        },
    ) {
        val result = DocumentSchema.decodeFromStringWithReport(json)
        val failed = result.warnings.any { warning ->
            warning is DocumentDecodeWarning.DocumentParseFailed
        }
        if (!failed) {
            blocksSnapshot.value = result.blocks
        }
        CascadeDocumentLoadResult(
            success = !failed,
            warningMessages = result.warnings.map { warning ->
                warning.toCascadeWarningMessage()
            },
        )
    }

    /**
     * Replaces the immutable preview snapshot with decoded Markdown.
     *
     * An aborted decode preserves the currently displayed snapshot. Successful
     * loads may still report fidelity loss from content outside the default
     * Markdown profile.
     */
    @ExperimentalCascadeMarkdownApi
    public fun loadMarkdown(markdown: String): CascadeMarkdownLoadResult = onMainThread(
        fallback = {
            CascadeMarkdownLoadResult(
                success = false,
                warningMessages = listOf(MAIN_THREAD_ERROR),
                hasDataLoss = false,
            )
        },
    ) {
        val result = MarkdownSchema.decodeWithReport(markdown)
        result.blocks?.let { blocks ->
            blocksSnapshot.value = blocks
        }
        result.toCascadeMarkdownLoadResult()
    }

    /** Replaces the presentation configuration observed by a mounted preview. */
    public fun updateConfiguration(value: CascadeDocumentPreviewConfiguration): Unit =
        onMainThread(fallback = {}) {
            updateConfigurationOnMain(value)
        }

    /** Convenience update for hosts following a native light/dark theme. */
    public fun setDarkMode(value: Boolean): Unit = onMainThread(fallback = {}) {
        updateConfigurationOnMain(currentConfiguration.copy(isDark = value))
    }

    /**
     * Applies a complete custom color palette to the mounted preview.
     *
     * Values are snapshotted immediately, so mutating [colors] afterwards has no
     * effect until this method is called again. The custom palette remains active
     * across [setDarkMode] calls.
     */
    public fun setColors(colors: CascadeEditorColors): Unit = onMainThread(fallback = {}) {
        customColorsSnapshot.value = colors.snapshot()
    }

    /**
     * Removes the custom palette and resumes the built-in light/dark presets
     * selected by [setDarkMode].
     */
    public fun clearCustomColors(): Unit = onMainThread(fallback = {}) {
        customColorsSnapshot.value = null
    }

    internal fun openLink(target: String): Unit = onMainThread(fallback = {}) {
        val callback = openLinkCallback ?: return@onMainThread
        try {
            callback(target)
        } catch (throwable: Throwable) {
            reportInternalError(
                "CascadeDocumentPreviewController callback onOpenLink failed: " +
                    (throwable.message ?: throwable.toString())
            )
        }
    }

    internal inline fun <T> onMainThread(
        fallback: () -> T,
        block: () -> T,
    ): T {
        if (!NSThread.isMainThread) {
            reportInternalError(MAIN_THREAD_ERROR)
            return fallback()
        }
        return block()
    }

    internal fun reportInternalError(message: String): Unit {
        if (NSThread.isMainThread) {
            deliverInternalError(message)
        } else {
            dispatch_async(dispatch_get_main_queue()) {
                deliverInternalError(message)
            }
        }
    }

    private fun deliverInternalError(message: String): Unit {
        try {
            internalErrorCallback?.invoke(message)
        } catch (_: Throwable) {
            // Host error-reporting callbacks must never escape into Swift/Obj-C.
        }
    }

    private fun updateConfigurationOnMain(value: CascadeDocumentPreviewConfiguration): Unit {
        if (currentConfiguration == value) return
        currentConfiguration = value
        configurationSnapshot.value = value
    }
}
