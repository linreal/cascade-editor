package io.github.linreal.cascade.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color palette for the sample app's bespoke screens (currently the Landing
 * screen), using a violet light/dark design system.
 *
 * This is the sample's own design system and is intentionally separate from
 * the editor's [io.github.linreal.cascade.editor.theme.CascadeEditorColors],
 * which themes the editor surface itself. Use [light] / [dark] for the two
 * built-in variants, provided through [LocalCascadeSampleColors].
 */
@Immutable
data class CascadeSampleColors(
    /** True for the dark variant — lets consumers drop shadows on translucent surfaces. */
    val isDark: Boolean,
    /** Page background. */
    val background: Color,
    /** "Kotlin Multiplatform" badge fill. */
    val badgeBackground: Color,
    /** Badge outline. */
    val badgeBorder: Color,
    /** Badge label text. */
    val badgeText: Color,
    /** Badge status dot (coral accent). */
    val badgeDot: Color,
    /** Headline color for the non-accented part of the wordmark ("Cascade"). */
    val titleInk: Color,
    /** Accent color for the wordmark ("Editor") and brand purple. */
    val titleAccent: Color,
    /** Hero/intro paragraph text. */
    val subtitle: Color,
    /** "EXPLORE" section header. */
    val exploreHeader: Color,
    /** "N modules" counter text. */
    val modulesCount: Color,
    /** Module card fill. */
    val cardBackground: Color,
    /** Module card outline. */
    val cardBorder: Color,
    /** Module card title. */
    val cardTitle: Color,
    /** Module card description. */
    val cardDescription: Color,
    /** Module card trailing chevron. */
    val caret: Color,
    /** Featured hero card gradient stops (top → bottom). Shared across variants. */
    val heroGradient: List<Color>,
    /** Tinted drop-shadow color under the hero card. */
    val heroShadow: Color,
    /** Custom Blocks tile (violet). */
    val tileBlocks: TileColors,
    /** Custom Toolbar tile (coral). */
    val tileToolbar: TileColors,
    /** External Toolbar tile (green). */
    val tileExternal: TileColors,
    /** Custom HTML Profile tile (indigo). */
    val tileHtml: TileColors,
) {
    /** Background/icon pair for a module card's leading icon tile. */
    @Immutable
    data class TileColors(
        val background: Color,
        val icon: Color,
    )

    companion object {
        private val HeroGradient = listOf(
            Color(0xFF6C3DE8),
            Color(0xFF8B5CF6),
            Color(0xFFA855F7),
        )

        /** Light variant. */
        fun light(): CascadeSampleColors = CascadeSampleColors(
            isDark = false,
            background = Color(0xFFF6F2FF),
            badgeBackground = Color(0xFFFFFFFF),
            badgeBorder = Color(0xFFE4DAFB),
            badgeText = Color(0xFF6C3DE8),
            badgeDot = Color(0xFFFF6B4A),
            titleInk = Color(0xFF160B2E),
            titleAccent = Color(0xFF6C3DE8),
            subtitle = Color(0xFF4A4360),
            exploreHeader = Color(0xFF160B2E),
            modulesCount = Color(0xFF8A83A0),
            cardBackground = Color(0xFFFFFFFF),
            cardBorder = Color(0xFFEDE6FB),
            cardTitle = Color(0xFF1C1238),
            cardDescription = Color(0xFF6B6580),
            caret = Color(0xFFCBB8EC),
            heroGradient = HeroGradient,
            heroShadow = Color(0xFF6C3DE8),
            tileBlocks = TileColors(Color(0xFFF0E9FE), Color(0xFF6C3DE8)),
            tileToolbar = TileColors(Color(0xFFFFEDE7), Color(0xFFFF6B4A)),
            tileExternal = TileColors(Color(0xFFE6F7F1), Color(0xFF1F9D74)),
            tileHtml = TileColors(Color(0xFFEDEBFF), Color(0xFF5B5BE0)),
        )

        /** Dark variant. */
        fun dark(): CascadeSampleColors = CascadeSampleColors(
            isDark = true,
            background = Color(0xFF120C24),
            badgeBackground = Color(0x248B5CF6),
            badgeBorder = Color(0x4D8B5CF6),
            badgeText = Color(0xFFC4B5FD),
            badgeDot = Color(0xFFFF6B4A),
            titleInk = Color(0xFFFFFFFF),
            titleAccent = Color(0xFFA78BFA),
            subtitle = Color(0xFF9B93B8),
            exploreHeader = Color(0xFFFFFFFF),
            modulesCount = Color(0xFF6F6690),
            cardBackground = Color(0x0AFFFFFF),
            cardBorder = Color(0x14FFFFFF),
            cardTitle = Color(0xFFFFFFFF),
            cardDescription = Color(0xFF8A82A6),
            caret = Color(0xFF5A5278),
            heroGradient = HeroGradient,
            heroShadow = Color(0xFF6C3DE8),
            tileBlocks = TileColors(Color(0x2E8B5CF6), Color(0xFFC4B5FD)),
            tileToolbar = TileColors(Color(0x29FF6B4A), Color(0xFFFF8A6B)),
            tileExternal = TileColors(Color(0x2948E2A8), Color(0xFF48E2A8)),
            tileHtml = TileColors(Color(0x2E6366F1), Color(0xFFA5B4FC)),
        )
    }
}

/**
 * Provides the active [CascadeSampleColors] variant. Defaults to [CascadeSampleColors.light].
 * Provided in `App()` based on the current light/dark state.
 */
val LocalCascadeSampleColors = staticCompositionLocalOf { CascadeSampleColors.light() }
