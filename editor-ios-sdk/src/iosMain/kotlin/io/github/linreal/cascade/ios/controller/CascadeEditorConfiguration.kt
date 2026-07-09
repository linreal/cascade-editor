@file:OptIn(ExperimentalObjCName::class)

package io.github.linreal.cascade.ios.controller

import io.github.linreal.cascade.editor.CrashPolicy
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
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
    public val readOnly: Boolean,
    public val toolbarMode: CascadeToolbarMode,
    public val slashCommandsEnabled: Boolean,
    public val blockSelectionEnabled: Boolean,
    public val blockDraggingEnabled: Boolean,
    public val isDark: Boolean,
    public val crashPolicy: CascadeCrashPolicy,
) {
    public constructor() : this(
        readOnly = false,
        toolbarMode = CascadeToolbarMode.builtIn,
        slashCommandsEnabled = true,
        blockSelectionEnabled = true,
        blockDraggingEnabled = true,
        isDark = false,
        crashPolicy = CascadeCrashPolicy.containAndReport,
    )
}

internal fun CascadeCrashPolicy.toCoreCrashPolicy(): CrashPolicy = when (this) {
    CascadeCrashPolicy.containAndReport -> CrashPolicy.ContainAndReport
    CascadeCrashPolicy.rethrow -> CrashPolicy.Rethrow
}

/**
 * The theme the hosted editor mounts for this configuration. Extracted so tests
 * can assert the exact mapping the view host composes from when `isDark` flips.
 */
internal fun CascadeEditorConfiguration.resolveEditorTheme(): CascadeEditorTheme =
    if (isDark) CascadeEditorTheme.dark() else CascadeEditorTheme.light()
