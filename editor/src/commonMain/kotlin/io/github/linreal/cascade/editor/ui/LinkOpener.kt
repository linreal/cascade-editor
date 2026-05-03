package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.UriHandler

/**
 * Link opener provided by [CascadeEditor] to text block fields.
 */
internal val LocalLinkOpener: ProvidableCompositionLocal<((String) -> Unit)?> =
    compositionLocalOf { null }

/**
 * Creates the link opener used by editor text hit testing.
 *
 * Consumer-provided openers are called directly so exceptions propagate to the
 * app. The default platform opener swallows failures because platform URL
 * dispatch is outside editor state and must not corrupt editing/history flows.
 */
internal fun createLinkOpener(
    onOpenLink: ((String) -> Unit)?,
    uriHandler: UriHandler,
): (String) -> Unit {
    return onOpenLink ?: { url ->
        try {
            uriHandler.openUri(url)
        } catch (_: Throwable) {
            // Platform open failures must not affect document state or history.
        }
    }
}
