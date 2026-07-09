import SwiftUI
import CascadeEditor

/// Full editing experience: shared chrome over a persisted document with
/// debounced autosave, link opening, and corrupt-storage recovery.
struct EditorDemoScreen: View {
    @Binding var isDark: Bool
    @Environment(\.dismiss) private var dismiss
    @StateObject private var editor: EditorScreenModel
    @StateObject private var demo: EditorDemoModel

    init(isDark: Binding<Bool>) {
        _isDark = isDark
        let editorModel = EditorScreenModel(configuration: .standard(isDark: isDark.wrappedValue))
        _editor = StateObject(wrappedValue: editorModel)
        _demo = StateObject(wrappedValue: EditorDemoModel(editor: editorModel))
    }

    private var theme: AppTheme { AppTheme.theme(isDark: isDark) }

    var body: some View {
        VStack(spacing: 0) {
            header
            ZStack(alignment: .top) {
                CascadeEditorHost(model: editor)
                VStack(spacing: 6) {
                    SavedPill(theme: theme, status: demo.saveStatus)
                    OpenedLinkPill(theme: theme, link: demo.lastOpenedLink)
                }
                .padding(.top, 8)
                .animation(.default, value: demo.saveStatus)
                .animation(.default, value: demo.lastOpenedLink)
            }
        }
        .background(theme.background)
        .onChange(of: isDark) { _, newValue in
            editor.setDarkMode(newValue)
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
                    demo.saveOnExit()
                    dismiss()
                },
                onUndo: { editor.undo() },
                onRedo: { editor.redo() },
                onToggleReadOnly: { editor.setReadOnly(!editor.isReadOnly) },
                onToggleTheme: { isDark.toggle() },
                onReset: { demo.reset() }
            )
        }
    }
}
