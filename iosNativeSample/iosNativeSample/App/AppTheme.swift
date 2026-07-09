import SwiftUI

/// Fixed violet design system mirroring the Compose sample's palette, so the
/// native host looks like the KMP one. Light/dark is driven by the app-level
/// theme toggle (which also flips the editor via `setDarkMode`), not by the
/// system appearance.
struct AppTheme {
    let isDark: Bool

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
    let savedGreen = Color(rgb: 0x34C77B)

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
    let badgeDot = Color(rgb: 0xFF6B4A)
    let heroGradient = [Color(rgb: 0x6C3DE8), Color(rgb: 0x8B5CF6), Color(rgb: 0xA855F7)]
    let tileBlocksBackground: Color
    let tileBlocksIcon: Color
    let tileCommentsBackground: Color
    let tileCommentsIcon: Color

    static let light = AppTheme(
        isDark: false,
        primary: Color(rgb: 0x6C3DE8),
        onPrimary: .white,
        background: Color(rgb: 0xF6F2FF),
        surface: .white,
        onBackground: Color(rgb: 0x1C1238),
        onSurface: Color(rgb: 0x1C1238),
        mutedInk: Color(rgb: 0x4A4360),
        divider: Color(rgb: 0xE4DAFB),
        destructive: Color(rgb: 0xB3261E),
        subtitle: Color(rgb: 0x4A4360),
        cardBackground: .white,
        cardBorder: Color(rgb: 0xEDE6FB),
        cardTitle: Color(rgb: 0x1C1238),
        cardDescription: Color(rgb: 0x6B6580),
        caret: Color(rgb: 0xCBB8EC),
        badgeBackground: .white,
        badgeBorder: Color(rgb: 0xE4DAFB),
        badgeText: Color(rgb: 0x6C3DE8),
        tileBlocksBackground: Color(rgb: 0xF0E9FE),
        tileBlocksIcon: Color(rgb: 0x6C3DE8),
        tileCommentsBackground: Color(rgb: 0xFFEDE7),
        tileCommentsIcon: Color(rgb: 0xFF6B4A)
    )

    static let dark = AppTheme(
        isDark: true,
        primary: Color(rgb: 0xA78BFA),
        onPrimary: Color(rgb: 0x1B1230),
        background: Color(rgb: 0x120C24),
        surface: Color(rgb: 0x1E1832),
        onBackground: Color(rgb: 0xF4F1FB),
        onSurface: Color(rgb: 0xF4F1FB),
        mutedInk: Color(rgb: 0x9B93B8),
        divider: Color(rgb: 0x3A3354),
        destructive: Color(rgb: 0xF2B8B5),
        subtitle: Color(rgb: 0x9B93B8),
        cardBackground: Color(argb: 0x0AFFFFFF),
        cardBorder: Color(argb: 0x14FFFFFF),
        cardTitle: .white,
        cardDescription: Color(rgb: 0x8A82A6),
        caret: Color(rgb: 0x5A5278),
        badgeBackground: Color(argb: 0x248B5CF6),
        badgeBorder: Color(argb: 0x4D8B5CF6),
        badgeText: Color(rgb: 0xC4B5FD),
        tileBlocksBackground: Color(argb: 0x2E8B5CF6),
        tileBlocksIcon: Color(rgb: 0xC4B5FD),
        tileCommentsBackground: Color(argb: 0x29FF6B4A),
        tileCommentsIcon: Color(rgb: 0xFF8A6B)
    )

    static func theme(isDark: Bool) -> AppTheme {
        isDark ? .dark : .light
    }
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
