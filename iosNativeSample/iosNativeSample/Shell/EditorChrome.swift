import SwiftUI

// Shared chrome for the sample's editor screens, mirroring the Compose
// sample's EditorChrome: a floating back chip on the left, a grouped
// soft-container of editor actions in the center, and an accented Reset pill
// on the right. A selection variant replaces the bar while blocks are selected.

private let chipCornerRadius: CGFloat = 12
private let groupCornerRadius: CGFloat = 14
private let groupButtonCornerRadius: CGFloat = 10

struct EditorTopBar: View {
    let theme: AppTheme
    let isReadOnly: Bool
    let canUndo: Bool
    let canRedo: Bool
    let onBack: () -> Void
    let onUndo: () -> Void
    let onRedo: () -> Void
    let onToggleReadOnly: () -> Void
    let onToggleTheme: () -> Void
    let onReset: () -> Void

    var body: some View {
        HStack {
            ChromeChip(theme: theme, systemImage: "chevron.left", label: "Back", action: onBack)
            Spacer()
            ChromeActionGroup(theme: theme) {
                GroupButton(
                    theme: theme,
                    systemImage: "arrow.uturn.backward",
                    label: "Undo",
                    enabled: !isReadOnly && canUndo,
                    action: onUndo
                )
                GroupButton(
                    theme: theme,
                    systemImage: "arrow.uturn.forward",
                    label: "Redo",
                    enabled: !isReadOnly && canRedo,
                    action: onRedo
                )
                GroupDivider(theme: theme)
                GroupButton(
                    theme: theme,
                    systemImage: isReadOnly ? "pencil.slash" : "pencil",
                    label: isReadOnly ? "Read-only" : "Editable",
                    action: onToggleReadOnly
                )
                GroupButton(
                    theme: theme,
                    systemImage: theme.isDark ? "sun.max" : "moon",
                    label: theme.isDark ? "Switch to light" : "Switch to dark",
                    action: onToggleTheme
                )
            }
            Spacer()
            ResetPill(theme: theme, enabled: !isReadOnly, action: onReset)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
    }
}

/// Titled variant for screens without undo/redo or reset (the comments demo):
/// back chip plus screen title on the left, theme toggle on the right.
struct TitledEditorTopBar: View {
    let theme: AppTheme
    let title: String
    let onBack: () -> Void
    let onToggleTheme: () -> Void

    var body: some View {
        HStack {
            HStack(spacing: 12) {
                ChromeChip(theme: theme, systemImage: "chevron.left", label: "Back", action: onBack)
                Text(title)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(theme.onBackground)
            }
            Spacer()
            ChromeActionGroup(theme: theme) {
                GroupButton(
                    theme: theme,
                    systemImage: theme.isDark ? "sun.max" : "moon",
                    label: theme.isDark ? "Switch to light" : "Switch to dark",
                    action: onToggleTheme
                )
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
    }
}

struct SelectionTopBar: View {
    let theme: AppTheme
    let selectedCount: Int
    let isReadOnly: Bool
    let onCancelSelection: () -> Void
    let onDeleteSelected: () -> Void

    var body: some View {
        HStack {
            HStack(spacing: 4) {
                Button("Cancel", action: onCancelSelection)
                    .font(.body.weight(.medium))
                    .tint(theme.primary)
                    .accessibilityLabel("Cancel selection")
                Text("\(selectedCount) selected")
                    .font(.headline)
                    .foregroundStyle(theme.onBackground)
            }
            Spacer()
            ChromeChip(
                theme: theme,
                systemImage: "trash",
                label: "Delete selected blocks",
                tint: theme.destructive.opacity(isReadOnly ? 0.38 : 1),
                enabled: !isReadOnly,
                action: onDeleteSelected
            )
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
    }
}

private struct ChromeChip: View {
    let theme: AppTheme
    let systemImage: String
    let label: String
    var tint: Color?
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(tint ?? theme.onSurface)
                .frame(width: 42, height: 42)
                .background(theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: chipCornerRadius))
                .shadow(color: .black.opacity(enabled ? 0.12 : 0), radius: 3, y: 1)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

private struct ChromeActionGroup<Content: View>: View {
    let theme: AppTheme
    @ViewBuilder let content: Content

    var body: some View {
        HStack(spacing: 2) {
            content
        }
        .padding(4)
        .background(theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: groupCornerRadius))
        .shadow(color: .black.opacity(0.12), radius: 3, y: 1)
    }
}

private struct GroupButton: View {
    let theme: AppTheme
    let systemImage: String
    let label: String
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(theme.mutedInk.opacity(enabled ? 1 : 0.38))
                .frame(width: 38, height: 38)
                .contentShape(RoundedRectangle(cornerRadius: groupButtonCornerRadius))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

private struct GroupDivider: View {
    let theme: AppTheme

    var body: some View {
        Rectangle()
            .fill(theme.divider)
            .frame(width: 1, height: 22)
    }
}

private struct ResetPill: View {
    let theme: AppTheme
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: "arrow.counterclockwise")
                    .font(.system(size: 12, weight: .semibold))
                Text("Reset")
                    .font(.footnote.weight(.semibold))
            }
            .foregroundStyle(theme.onPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 9)
            .background(theme.primary.opacity(enabled ? 1 : 0.4))
            .clipShape(Capsule())
            .shadow(color: theme.primary.opacity(enabled ? 0.35 : 0), radius: 6, y: 2)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel("Reset document")
    }
}
