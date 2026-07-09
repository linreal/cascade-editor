import SwiftUI

/// Floating "Saved" indicator shown below the top bar while autosave runs,
/// mirroring the Compose sample's SavedPill.
struct SavedPill: View {
    let theme: AppTheme
    let status: String

    var body: some View {
        if !status.isEmpty {
            HStack(spacing: 8) {
                ZStack {
                    Circle()
                        .fill(theme.savedGreen.opacity(0.18))
                        .frame(width: 14, height: 14)
                    Circle()
                        .fill(theme.savedGreen)
                        .frame(width: 8, height: 8)
                }
                Text(status)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(theme.mutedInk)
            }
            .padding(.horizontal, 15)
            .padding(.vertical, 8)
            .background(theme.surface)
            .clipShape(Capsule())
            .shadow(color: .black.opacity(0.15), radius: 6, y: 2)
            .transition(.opacity)
        }
    }
}

/// Floating indicator echoing the last opened link, styled like the Saved pill.
struct OpenedLinkPill: View {
    let theme: AppTheme
    let link: String

    var body: some View {
        if !link.isEmpty {
            HStack(spacing: 8) {
                Circle()
                    .fill(theme.primary)
                    .frame(width: 8, height: 8)
                Text("Opened: \(link)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(theme.primary)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .padding(.horizontal, 15)
            .padding(.vertical, 8)
            .background(theme.surface)
            .clipShape(Capsule())
            .shadow(color: .black.opacity(0.15), radius: 6, y: 2)
            .transition(.opacity)
        }
    }
}
