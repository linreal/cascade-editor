import SwiftUI

/// Landing page: brand header, a hero card for the Editor Demo, and module
/// cards for the remaining screens — a native retelling of the KMP sample's
/// landing screen.
struct LandingView: View {
    let theme: AppTheme
    let onNavigate: (SampleDestination) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: 24)
                badge
                Spacer().frame(height: 22)
                title
                Spacer().frame(height: 16)
                Text("A block-based document editor, embedded natively from CascadeEditor.xcframework.")
                    .font(.system(size: 17))
                    .lineSpacing(4)
                    .foregroundStyle(theme.subtitle)
                    .frame(maxWidth: 320, alignment: .leading)
                Spacer().frame(height: 26)
                heroCard
                Spacer().frame(height: 34)
                HStack {
                    Text("EXPLORE")
                        .font(.system(size: 15, weight: .bold))
                        .kerning(2.2)
                        .foregroundStyle(theme.onBackground)
                    Spacer()
                    Text("3 modules")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(theme.cardDescription)
                }
                Spacer().frame(height: 16)
                VStack(spacing: 12) {
                    moduleCard(
                        title: "Comments",
                        description: "Editor as a chat composer with rich-text bubbles",
                        systemImage: "bubble.left",
                        tileBackground: theme.tileCommentsBackground,
                        tileIcon: theme.tileCommentsIcon,
                        destination: .comments
                    )
                    moduleCard(
                        title: "Custom Blocks",
                        description: "Native SwiftUI blocks and slash commands",
                        systemImage: "puzzlepiece",
                        tileBackground: theme.tileBlocksBackground,
                        tileIcon: theme.tileBlocksIcon,
                        destination: .customBlocks
                    )
                }
                Spacer().frame(height: 32)
            }
            .padding(.horizontal, 18)
        }
        .background(theme.background)
    }

    private var badge: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(theme.badgeDot)
                .frame(width: 7, height: 7)
            Text("Native Swift · XCFramework")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(theme.badgeText)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(theme.badgeBackground)
        .clipShape(Capsule())
        .overlay(Capsule().strokeBorder(theme.badgeBorder, lineWidth: 1))
    }

    private var title: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Cascade")
                .foregroundStyle(theme.onBackground)
            Text("Editor")
                .foregroundStyle(theme.primary)
        }
        .font(.system(size: 44, weight: .bold))
    }

    private var heroCard: some View {
        Button {
            onNavigate(.editorDemo)
        } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "doc.richtext")
                        .font(.system(size: 22, weight: .medium))
                    Spacer()
                    Image(systemName: "arrow.up.right")
                        .font(.system(size: 17, weight: .semibold))
                        .opacity(0.85)
                }
                Spacer().frame(height: 22)
                Text("Editor Demo")
                    .font(.system(size: 24, weight: .bold))
                Text("The full editing experience: formatting, links, selection, autosave.")
                    .font(.system(size: 14))
                    .opacity(0.9)
            }
            .foregroundStyle(.white)
            .padding(22)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                LinearGradient(colors: theme.heroGradient, startPoint: .top, endPoint: .bottom)
            )
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .shadow(color: theme.heroGradient[0].opacity(0.4), radius: 14, y: 8)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Editor Demo")
    }

    private func moduleCard(
        title: String,
        description: String,
        systemImage: String,
        tileBackground: Color,
        tileIcon: Color,
        destination: SampleDestination
    ) -> some View {
        Button {
            onNavigate(destination)
        } label: {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(tileIcon)
                    .frame(width: 42, height: 42)
                    .background(tileBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(theme.cardTitle)
                    Text(description)
                        .font(.system(size: 13))
                        .foregroundStyle(theme.cardDescription)
                        .multilineTextAlignment(.leading)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(theme.caret)
            }
            .padding(14)
            .background(theme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(theme.cardBorder, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}
