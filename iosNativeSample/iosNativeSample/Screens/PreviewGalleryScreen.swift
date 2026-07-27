import SwiftUI

/// Native SwiftUI grid hosting lightweight Compose document previews.
///
/// Each cell owns one preview controller while it is alive. The outer button
/// owns taps and navigation; preview links, selection, and hit testing are
/// disabled so editor interaction begins only after the detail screen opens.
struct PreviewGalleryScreen: View {
    @Binding var selectedTheme: SampleThemeFamily
    @ObservedObject var library: PreviewDocumentLibrary
    let onOpenDocument: (String) -> Void

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss

    private var isDark: Bool { colorScheme == .dark }
    private var theme: AppTheme { selectedTheme.appTheme(isDark: isDark) }
    private let columns = [
        GridItem(.adaptive(minimum: 160, maximum: 260), spacing: 12),
    ]

    var body: some View {
        VStack(spacing: 0) {
            TitledEditorTopBar(
                theme: theme,
                title: "Preview Grid",
                onBack: { dismiss() },
                onToggleTheme: { selectedTheme = selectedTheme.next }
            )
            if !library.lastErrorMessage.isEmpty {
                storageError
            }
            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(library.documents) { document in
                        PreviewDocumentCard(
                            document: document,
                            theme: theme,
                            onOpen: { onOpenDocument(document.id) }
                        )
                    }
                }
                .padding(.horizontal, 12)
                .padding(.top, 8)
                .padding(.bottom, 24)
            }
        }
        .background(theme.background)
    }

    private var storageError: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(theme.destructive)
            Text(library.lastErrorMessage)
                .font(.footnote)
                .foregroundStyle(theme.onSurface)
                .lineLimit(2)
            Spacer(minLength: 4)
            Button {
                library.clearError()
            } label: {
                Image(systemName: "xmark")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(theme.mutedInk)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Dismiss storage error")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(theme.surface)
    }
}

private struct PreviewDocumentCard: View {
    let document: PreviewDocument
    let theme: AppTheme
    let onOpen: () -> Void

    @StateObject private var model: PreviewCardModel

    init(
        document: PreviewDocument,
        theme: AppTheme,
        onOpen: @escaping () -> Void
    ) {
        self.document = document
        self.theme = theme
        self.onOpen = onOpen
        _model = StateObject(
            wrappedValue: PreviewCardModel(json: document.json, theme: theme)
        )
    }

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(document.title)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(theme.cardTitle)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    Image(systemName: "arrow.up.right")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(theme.tilePreviewIcon)
                }
                Text(document.subtitle)
                    .font(.caption)
                    .foregroundStyle(theme.cardDescription)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 4)

                CascadeDocumentPreviewHost(controller: model.controller)
                    .allowsHitTesting(false)
                    .frame(maxWidth: .infinity)
                    .frame(height: 164)
                    .background(theme.background.opacity(theme.isDark ? 0.42 : 0.55))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.top, 10)
                    .overlay(alignment: .topTrailing) {
                        if !model.errorMessage.isEmpty {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.caption)
                                .foregroundStyle(theme.destructive)
                                .padding(.top, 16)
                                .padding(.trailing, 6)
                                .accessibilityHidden(true)
                        }
                    }
            }
            .padding(12)
            .frame(maxWidth: .infinity, minHeight: 248, alignment: .topLeading)
            .background(theme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .strokeBorder(theme.cardBorder, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .contentShape(RoundedRectangle(cornerRadius: 18))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(document.title). \(document.subtitle)")
        .accessibilityValue(
            model.errorMessage.isEmpty
                ? "Preview available"
                : "Preview unavailable. \(model.errorMessage)"
        )
        .accessibilityHint("Opens the editable document")
        .onAppear {
            model.update(json: document.json, theme: theme)
        }
        .onChange(of: document.json) { _, newJson in
            model.update(json: newJson, theme: theme)
        }
        .onChange(of: theme.name) { _, _ in
            model.update(json: document.json, theme: theme)
        }
    }
}
