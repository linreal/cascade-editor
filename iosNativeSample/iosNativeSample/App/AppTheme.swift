import SwiftUI
import CascadeEditor

/// The user selects a theme family; iOS appearance selects that family's
/// light/dark variant. Both changes update native chrome and CascadeEditor.
enum SampleThemeFamily: CaseIterable, Hashable {
    case violet
    case forest

    func appTheme(isDark: Bool) -> AppTheme {
        switch (self, isDark) {
        case (.violet, false): .violetLight
        case (.violet, true): .violetDark
        case (.forest, false): .forestLight
        case (.forest, true): .forestDark
        }
    }

    var next: SampleThemeFamily {
        let themes = Self.allCases
        let index = themes.firstIndex(of: self) ?? themes.startIndex
        return themes[themes.index(after: index) % themes.endIndex]
    }
}

struct AppTheme {
    let name: String
    let isDark: Bool
    /// Complete SDK palette applied through `CascadeEditorController.setColors`.
    let editorColors: CascadeEditorColors

    // Material-scheme equivalents used by the shared chrome.
    let primary: Color
    let onPrimary: Color
    let background: Color
    let surface: Color
    let onBackground: Color
    let onSurface: Color
    /// Muted icon/label ink (the Compose chrome's onSurfaceVariant).
    let mutedInk: Color
    /// Hairline divider inside grouped chrome containers.
    let divider: Color
    let destructive: Color
    let savedGreen: Color

    // Landing-screen accents.
    let subtitle: Color
    let cardBackground: Color
    let cardBorder: Color
    let cardTitle: Color
    let cardDescription: Color
    let caret: Color
    let badgeBackground: Color
    let badgeBorder: Color
    let badgeText: Color
    let badgeDot: Color
    let heroGradient: [Color]
    let tileBlocksBackground: Color
    let tileBlocksIcon: Color
    let tileCommentsBackground: Color
    let tileCommentsIcon: Color
    let tilePreviewBackground: Color
    let tilePreviewIcon: Color

    static let violetLight = AppTheme(
        name: "Violet Light",
        isDark: false,
        editorColors: violetLightEditorColors(),
        primary: Color(rgb: 0x6C3DE8),
        onPrimary: .white,
        background: Color(rgb: 0xF6F2FF),
        surface: .white,
        onBackground: Color(rgb: 0x1C1238),
        onSurface: Color(rgb: 0x1C1238),
        mutedInk: Color(rgb: 0x4A4360),
        divider: Color(rgb: 0xE4DAFB),
        destructive: Color(rgb: 0xB3261E),
        savedGreen: Color(rgb: 0x34C77B),
        subtitle: Color(rgb: 0x4A4360),
        cardBackground: .white,
        cardBorder: Color(rgb: 0xEDE6FB),
        cardTitle: Color(rgb: 0x1C1238),
        cardDescription: Color(rgb: 0x6B6580),
        caret: Color(rgb: 0xCBB8EC),
        badgeBackground: .white,
        badgeBorder: Color(rgb: 0xE4DAFB),
        badgeText: Color(rgb: 0x6C3DE8),
        badgeDot: Color(rgb: 0xFF6B4A),
        heroGradient: [
            Color(rgb: 0x6C3DE8),
            Color(rgb: 0x8B5CF6),
            Color(rgb: 0xA855F7)
        ],
        tileBlocksBackground: Color(rgb: 0xF0E9FE),
        tileBlocksIcon: Color(rgb: 0x6C3DE8),
        tileCommentsBackground: Color(rgb: 0xFFEDE7),
        tileCommentsIcon: Color(rgb: 0xFF6B4A),
        tilePreviewBackground: Color(rgb: 0xE8F5FF),
        tilePreviewIcon: Color(rgb: 0x2478B5)
    )

    static let violetDark = AppTheme(
        name: "Violet Dark",
        isDark: true,
        editorColors: violetDarkEditorColors(),
        primary: Color(rgb: 0xA78BFA),
        onPrimary: Color(rgb: 0x1B1230),
        background: Color(rgb: 0x120C24),
        surface: Color(rgb: 0x1E1832),
        onBackground: Color(rgb: 0xF4F1FB),
        onSurface: Color(rgb: 0xF4F1FB),
        mutedInk: Color(rgb: 0x9B93B8),
        divider: Color(rgb: 0x3A3354),
        destructive: Color(rgb: 0xF2B8B5),
        savedGreen: Color(rgb: 0x34C77B),
        subtitle: Color(rgb: 0x9B93B8),
        cardBackground: Color(argb: 0x0AFFFFFF),
        cardBorder: Color(argb: 0x14FFFFFF),
        cardTitle: .white,
        cardDescription: Color(rgb: 0x8A82A6),
        caret: Color(rgb: 0x5A5278),
        badgeBackground: Color(argb: 0x248B5CF6),
        badgeBorder: Color(argb: 0x4D8B5CF6),
        badgeText: Color(rgb: 0xC4B5FD),
        badgeDot: Color(rgb: 0xFF6B4A),
        heroGradient: [
            Color(rgb: 0x6C3DE8),
            Color(rgb: 0x8B5CF6),
            Color(rgb: 0xA855F7)
        ],
        tileBlocksBackground: Color(argb: 0x2E8B5CF6),
        tileBlocksIcon: Color(rgb: 0xC4B5FD),
        tileCommentsBackground: Color(argb: 0x29FF6B4A),
        tileCommentsIcon: Color(rgb: 0xFF8A6B),
        tilePreviewBackground: Color(argb: 0x2E38A3DB),
        tilePreviewIcon: Color(rgb: 0x7CC7F2)
    )

    static let forestLight = AppTheme(
        name: "Forest Light",
        isDark: false,
        editorColors: forestLightEditorColors(),
        primary: Color(rgb: 0x147D64),
        onPrimary: .white,
        background: Color(rgb: 0xF2F8F4),
        surface: .white,
        onBackground: Color(rgb: 0x102A23),
        onSurface: Color(rgb: 0x102A23),
        mutedInk: Color(rgb: 0x526B63),
        divider: Color(rgb: 0xD5E5DE),
        destructive: Color(rgb: 0xB3261E),
        savedGreen: Color(rgb: 0x2F8F68),
        subtitle: Color(rgb: 0x526B63),
        cardBackground: .white,
        cardBorder: Color(rgb: 0xDDEBE5),
        cardTitle: Color(rgb: 0x102A23),
        cardDescription: Color(rgb: 0x5D756D),
        caret: Color(rgb: 0x9BC7B8),
        badgeBackground: Color(rgb: 0xE5F1EC),
        badgeBorder: Color(rgb: 0xC8E0D6),
        badgeText: Color(rgb: 0x147D64),
        badgeDot: Color(rgb: 0xC66A16),
        heroGradient: [
            Color(rgb: 0x0F684F),
            Color(rgb: 0x147D64),
            Color(rgb: 0x38A383)
        ],
        tileBlocksBackground: Color(rgb: 0xDCEFE7),
        tileBlocksIcon: Color(rgb: 0x147D64),
        tileCommentsBackground: Color(rgb: 0xFFF0D9),
        tileCommentsIcon: Color(rgb: 0xC66A16),
        tilePreviewBackground: Color(rgb: 0xE1F2F4),
        tilePreviewIcon: Color(rgb: 0x167080)
    )

    static let forestDark = AppTheme(
        name: "Forest Dark",
        isDark: true,
        editorColors: forestDarkEditorColors(),
        primary: Color(rgb: 0x67D4AF),
        onPrimary: Color(rgb: 0x07231A),
        background: Color(rgb: 0x071A14),
        surface: Color(rgb: 0x102820),
        onBackground: Color(rgb: 0xE5F5EF),
        onSurface: Color(rgb: 0xE5F5EF),
        mutedInk: Color(rgb: 0x8EAFA3),
        divider: Color(rgb: 0x29483E),
        destructive: Color(rgb: 0xF2B8B5),
        savedGreen: Color(rgb: 0x67D4AF),
        subtitle: Color(rgb: 0x8EAFA3),
        cardBackground: Color(rgb: 0x102820),
        cardBorder: Color(argb: 0x1FFFFFFF),
        cardTitle: Color(rgb: 0xE5F5EF),
        cardDescription: Color(rgb: 0x92AFA5),
        caret: Color(rgb: 0x486E61),
        badgeBackground: Color(argb: 0x2938A383),
        badgeBorder: Color(argb: 0x4D38A383),
        badgeText: Color(rgb: 0x8FE0C4),
        badgeDot: Color(rgb: 0xD99A4E),
        heroGradient: [
            Color(rgb: 0x0B4D3A),
            Color(rgb: 0x147D64),
            Color(rgb: 0x38A383)
        ],
        tileBlocksBackground: Color(argb: 0x2938A383),
        tileBlocksIcon: Color(rgb: 0x8FE0C4),
        tileCommentsBackground: Color(argb: 0x29D99A4E),
        tileCommentsIcon: Color(rgb: 0xE8B66B),
        tilePreviewBackground: Color(argb: 0x293AA7B5),
        tilePreviewIcon: Color(rgb: 0x83D8E2)
    )
}

/// Full 25-slot palettes demonstrate the native bridge without relying on the
/// SDK's built-in light/dark colors. Values are standard 0xAARRGGBB Int64s.
private func violetLightEditorColors() -> CascadeEditorColors {
    let colors = CascadeEditorColors(isDark: false)
    colors.primary = 0xFF6C3DE8
    colors.onPrimary = 0xFFFFFFFF
    colors.text = 0xFF1C1238
    colors.popupBackground = 0xFFFFFFFF
    colors.unknownBlockBackground = 0xFFF0E9FE
    colors.toolbarIcon = 0xFF5A5470
    colors.toolbarIconDisabled = 0xFFB7ADD0
    colors.slashItemTitle = 0xFF1C1238
    colors.slashChevron = 0xFFCBB8EC
    colors.unknownBlockText = 0xFF6B6580
    colors.uiDivider = 0xFFECE4FB
    colors.contentDivider = 0xFFE4DAFB
    colors.slashSelectedItem = 0xFFF0E9FE
    colors.inlineCodeBackground = 0xFFEDE6FB
    colors.highlight = 0xCCFFEB3B
    colors.cursor = 0xFF6C3DE8
    colors.textSelectionBackground = 0x666C3DE8
    colors.quoteBorder = 0xFFCBB8EC
    colors.quoteBackground = 0x0A6C3DE8
    colors.selectionOverlay = 0x226C3DE8
    colors.linkText = 0xFF6C3DE8
    colors.error = 0xFFB3261E
    colors.codeBlockBackground = 0x0F6C3DE8
    colors.toolbarBackground = 0xFFFFFFFF
    colors.placeholderText = 0xFF746D85
    return colors
}

private func violetDarkEditorColors() -> CascadeEditorColors {
    let colors = CascadeEditorColors(isDark: true)
    colors.primary = 0xFFA78BFA
    colors.onPrimary = 0xFF1B1230
    colors.text = 0xFFF4F1FB
    colors.popupBackground = 0xFF1E1832
    colors.unknownBlockBackground = 0xFF251C3D
    colors.toolbarIcon = 0xFFA99FC4
    colors.toolbarIconDisabled = 0xFF5A5278
    colors.slashItemTitle = 0xFFFFFFFF
    colors.slashChevron = 0xFF5A5278
    colors.unknownBlockText = 0xFF8A82A6
    colors.uiDivider = 0xFF3A3354
    colors.contentDivider = 0xFF3A3354
    colors.slashSelectedItem = 0xFF302451
    colors.inlineCodeBackground = 0x338B5CF6
    colors.highlight = 0x99FFEB3B
    colors.cursor = 0xFFA78BFA
    colors.textSelectionBackground = 0x668B5CF6
    colors.quoteBorder = 0xFF6F6690
    colors.quoteBackground = 0x1F8B5CF6
    colors.selectionOverlay = 0x3D8B5CF6
    colors.linkText = 0xFFC4B5FD
    colors.error = 0xFFF2B8B5
    colors.codeBlockBackground = 0x1F8B5CF6
    colors.toolbarBackground = 0xFF26203A
    colors.placeholderText = 0xFFAAA2BE
    return colors
}

private func forestLightEditorColors() -> CascadeEditorColors {
    let colors = CascadeEditorColors(isDark: false)
    colors.primary = 0xFF147D64
    colors.onPrimary = 0xFFFFFFFF
    colors.text = 0xFF102A23
    colors.popupBackground = 0xFFFFFFFF
    colors.unknownBlockBackground = 0xFFE5F1EC
    colors.toolbarIcon = 0xFF315A4D
    colors.toolbarIconDisabled = 0xFF9CB3AA
    colors.slashItemTitle = 0xFF102A23
    colors.slashChevron = 0xFF8BA79D
    colors.unknownBlockText = 0xFF5D756D
    colors.uiDivider = 0xFFD5E5DE
    colors.contentDivider = 0xFFD5E5DE
    colors.slashSelectedItem = 0xFFDBEFE7
    colors.inlineCodeBackground = 0x24147D64
    colors.highlight = 0xCCE5C45C
    colors.cursor = 0xFF147D64
    colors.textSelectionBackground = 0x66147D64
    colors.quoteBorder = 0xFF80B7A4
    colors.quoteBackground = 0x14147D64
    colors.selectionOverlay = 0x29147D64
    colors.linkText = 0xFF147D64
    colors.error = 0xFFB3261E
    colors.codeBlockBackground = 0x14147D64
    colors.toolbarBackground = 0xFFFFFFFF
    colors.placeholderText = 0xFF657A73
    return colors
}

private func forestDarkEditorColors() -> CascadeEditorColors {
    let colors = CascadeEditorColors(isDark: true)
    colors.primary = 0xFF67D4AF
    colors.onPrimary = 0xFF07231A
    colors.text = 0xFFE5F5EF
    colors.popupBackground = 0xFF102820
    colors.unknownBlockBackground = 0xFF17372D
    colors.toolbarIcon = 0xFFB9D5CB
    colors.toolbarIconDisabled = 0xFF486E61
    colors.slashItemTitle = 0xFFE5F5EF
    colors.slashChevron = 0xFF597C70
    colors.unknownBlockText = 0xFF8EAFA3
    colors.uiDivider = 0xFF29483E
    colors.contentDivider = 0xFF29483E
    colors.slashSelectedItem = 0xFF173E32
    colors.inlineCodeBackground = 0x3367D4AF
    colors.highlight = 0x99E5C45C
    colors.cursor = 0xFF67D4AF
    colors.textSelectionBackground = 0x6667D4AF
    colors.quoteBorder = 0xFF517F70
    colors.quoteBackground = 0x1F67D4AF
    colors.selectionOverlay = 0x3D67D4AF
    colors.linkText = 0xFF8FE0C4
    colors.error = 0xFFF2B8B5
    colors.codeBlockBackground = 0x1F67D4AF
    colors.toolbarBackground = 0xFF163229
    colors.placeholderText = 0xFFA4BDB4
    return colors
}

extension Color {
    /// Opaque color from a 0xRRGGBB literal.
    init(rgb: UInt32) {
        self.init(
            red: Double((rgb >> 16) & 0xFF) / 255.0,
            green: Double((rgb >> 8) & 0xFF) / 255.0,
            blue: Double(rgb & 0xFF) / 255.0
        )
    }

    /// Color from a 0xAARRGGBB literal (the Compose `Color(0x...)` spelling).
    init(argb: UInt32) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255.0,
            green: Double((argb >> 8) & 0xFF) / 255.0,
            blue: Double(argb & 0xFF) / 255.0,
            opacity: Double((argb >> 24) & 0xFF) / 255.0
        )
    }

    /// Color from the editor's rich-text span `argb` payload (sign-carrying Int64).
    init(spanArgb: Int64) {
        self.init(argb: UInt32(truncatingIfNeeded: spanArgb))
    }
}
