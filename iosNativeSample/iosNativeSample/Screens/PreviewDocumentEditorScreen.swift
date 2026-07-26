import SwiftUI

/// Full editor opened from a preview card. Changes are persisted back into the
/// shared document library, so navigating back reveals the refreshed preview.
struct PreviewDocumentEditorScreen: View {
    @Binding var selectedTheme: SampleThemeFamily
    @StateObject private var model: PreviewDocumentEditorModel

    init(
        documentID: String,
        library: PreviewDocumentLibrary,
        selectedTheme: Binding<SampleThemeFamily>,
        initialIsDark: Bool
    ) {
        _selectedTheme = selectedTheme
        _model = StateObject(
            wrappedValue: PreviewDocumentEditorModel(
                documentID: documentID,
                library: library,
                theme: selectedTheme.wrappedValue.appTheme(isDark: initialIsDark)
            )
        )
    }

    var body: some View {
        PreviewDocumentEditorContent(
            selectedTheme: $selectedTheme,
            model: model,
            editor: model.editor
        )
    }
}

/// Observes the session and its child editor without creating a second owner.
/// The parent StateObject lazily constructs one session for the destination
/// identity, and that session exclusively owns its Cascade editor controller.
private struct PreviewDocumentEditorContent: View {
    @Binding var selectedTheme: SampleThemeFamily
    @ObservedObject var model: PreviewDocumentEditorModel
    @ObservedObject var editor: EditorScreenModel

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    private var isDark: Bool { colorScheme == .dark }
    private var theme: AppTheme { selectedTheme.appTheme(isDark: isDark) }

    var body: some View {
        VStack(spacing: 0) {
            header
            ZStack(alignment: .top) {
                CascadeEditorHost(model: editor)
                VStack(spacing: 6) {
                    SavedPill(theme: theme, status: model.saveStatus)
                    OpenedLinkPill(theme: theme, link: model.lastOpenedLink)
                }
                .padding(.top, 8)
                .animation(.default, value: model.saveStatus)
                .animation(.default, value: model.lastOpenedLink)
            }
        }
        .background(theme.background)
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase != .active {
                model.saveOnExit()
            }
        }
        .onChange(of: selectedTheme) { _, newValue in
            editor.applyTheme(newValue.appTheme(isDark: isDark))
        }
        .onChange(of: colorScheme) { _, newValue in
            editor.applyTheme(selectedTheme.appTheme(isDark: newValue == .dark))
        }
        .onDisappear {
            model.saveOnExit()
        }
    }

    @ViewBuilder
    private var header: some View {
        if editor.hasSelection {
            SelectionTopBar(
                theme: theme,
                selectedCount: editor.selectedCount,
                isReadOnly: editor.isReadOnly,
                onCancelSelection: { editor.clearSelection() },
                onDeleteSelected: { editor.deleteSelectedOrFocused() }
            )
        } else {
            EditorTopBar(
                theme: theme,
                isReadOnly: editor.isReadOnly,
                canUndo: editor.canUndo,
                canRedo: editor.canRedo,
                onBack: {
                    if model.saveOnExit() {
                        dismiss()
                    }
                },
                onUndo: { editor.undo() },
                onRedo: { editor.redo() },
                onToggleReadOnly: { editor.setReadOnly(!editor.isReadOnly) },
                onToggleTheme: { selectedTheme = selectedTheme.next },
                onReset: { model.reset() }
            )
        }
    }
}
