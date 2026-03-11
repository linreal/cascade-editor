package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.github.linreal.cascade.editor.core.BlockId

/**
 * Provides the anchor block ID for the currently active slash session.
 *
 * Keeping this local to the anchor ID (instead of the full slash state)
 * avoids broad recomposition on query updates while still allowing
 * text fields to synchronize observer tracking with external close/open events.
 */
internal val LocalSlashSessionAnchorBlockId: ProvidableCompositionLocal<BlockId?> =
    compositionLocalOf { null }
