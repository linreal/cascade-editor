@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.controller

import io.github.linreal.cascade.editor.CascadeErrorReporter
import io.github.linreal.cascade.editor.CrashPolicy
import io.github.linreal.cascade.editor.theme.CascadeEditorColors
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@ObjCName("CascadeToolbarMode", exact = true)
public enum class CascadeToolbarMode {
    @ObjCName(name = "builtIn", swiftName = "builtIn")
    builtIn,
    none,
}

@ObjCName("CascadeCrashPolicy", exact = true)
public enum class CascadeCrashPolicy {
    @ObjCName(name = "containAndReport", swiftName = "containAndReport")
    containAndReport,
    rethrow,
}

@ObjCName("CascadeEditorConfiguration", exact = true)
public data class CascadeEditorConfiguration(
    public val readOnly: Boolean = false,
    public val toolbarMode: CascadeToolbarMode = CascadeToolbarMode.builtIn,
    public val slashCommandsEnabled: Boolean = true,
    public val blockSelectionEnabled: Boolean = true,
    public val blockDraggingEnabled: Boolean = true,
    public val isDark: Boolean = false,
    public val crashPolicy: CascadeCrashPolicy = CascadeCrashPolicy.containAndReport,
    public val blockIndentationEnabled: Boolean = true,
    public val emptyDocumentPlaceholderEnabled: Boolean = false,
    public val keyboardInsetsEnabled: Boolean = false,
    public val autoDetectPhoneNumbers: Boolean = false,
) {
    /**
     * Compatibility initializer for hosts using the configuration shape from
     * before automatic phone-number detection was added.
     */
    public constructor(
        readOnly: Boolean,
        toolbarMode: CascadeToolbarMode,
        slashCommandsEnabled: Boolean,
        blockSelectionEnabled: Boolean,
        blockDraggingEnabled: Boolean,
        isDark: Boolean,
        crashPolicy: CascadeCrashPolicy,
        blockIndentationEnabled: Boolean,
        emptyDocumentPlaceholderEnabled: Boolean,
        keyboardInsetsEnabled: Boolean,
    ) : this(
        readOnly = readOnly,
        toolbarMode = toolbarMode,
        slashCommandsEnabled = slashCommandsEnabled,
        blockSelectionEnabled = blockSelectionEnabled,
        blockDraggingEnabled = blockDraggingEnabled,
        isDark = isDark,
        crashPolicy = crashPolicy,
        blockIndentationEnabled = blockIndentationEnabled,
        emptyDocumentPlaceholderEnabled = emptyDocumentPlaceholderEnabled,
        keyboardInsetsEnabled = keyboardInsetsEnabled,
        autoDetectPhoneNumbers = false,
    )
}

internal fun CascadeCrashPolicy.toCoreCrashPolicy(): CrashPolicy = when (this) {
    CascadeCrashPolicy.containAndReport -> CrashPolicy.ContainAndReport
    CascadeCrashPolicy.rethrow -> CrashPolicy.Rethrow
}

internal fun CascadeEditorConfiguration.toCoreEditorConfig(
    onInternalError: CascadeErrorReporter? = null,
): CascadeEditorConfig = CascadeEditorConfig(
    readOnly = readOnly,
    blockSelectionEnabled = blockSelectionEnabled,
    blockDraggingEnabled = blockDraggingEnabled,
    crashPolicy = crashPolicy.toCoreCrashPolicy(),
    onInternalError = onInternalError,
    blockIndentationEnabled = blockIndentationEnabled,
    emptyDocumentPlaceholderEnabled = emptyDocumentPlaceholderEnabled,
    keyboardInsetsEnabled = keyboardInsetsEnabled,
    autoDetectPhoneNumbers = autoDetectPhoneNumbers,
)

/**
 * The theme the hosted editor mounts for this configuration. Extracted so tests
 * can assert the exact mapping the view host composes from when `isDark` flips.
 */
internal fun CascadeEditorConfiguration.resolveEditorTheme(
    customColors: CascadeEditorColors? = null,
): CascadeEditorTheme {
    val preset = if (isDark) CascadeEditorTheme.dark() else CascadeEditorTheme.light()
    return if (customColors == null) preset else preset.copy(colors = customColors)
}
