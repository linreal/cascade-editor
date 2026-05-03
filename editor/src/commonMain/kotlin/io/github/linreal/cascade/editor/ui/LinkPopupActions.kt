package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Stable

/**
 * Actions available to custom link popup content.
 *
 * Implementations mutate the active popup session and dispatch link edits
 * through the editor's link action layer.
 */
@Stable
public interface LinkPopupActions {
    public fun updateTitle(title: String)
    public fun updateUrl(url: String)
    public fun apply()
    public fun remove()
    public fun dismiss()
}
