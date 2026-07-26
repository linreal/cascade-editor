import SwiftUI
import CascadeEditor

/// Full editing experience: shared chrome over a persisted document with
/// debounced autosave, link opening, and corrupt-storage recovery.
struct EditorDemoScreen: View {
    @Binding var selectedTheme: SampleThemeFamily
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @StateObject private var editor: EditorScreenModel
    @StateObject private var demo: EditorDemoModel

    init(selectedTheme: Binding<SampleThemeFamily>, initialIsDark: Bool) {
        _selectedTheme = selectedTheme
        let initialTheme = selectedTheme.wrappedValue.appTheme(isDark: initialIsDark)
        let editorModel = EditorScreenModel(
            configuration: .standard(isDark: initialTheme.isDark),
            colors: initialTheme.editorColors
        )
        _editor = StateObject(wrappedValue: editorModel)
        _demo = StateObject(wrappedValue: EditorDemoModel(editor: editorModel))
    }

    private var isDark: Bool { colorScheme == .dark }
    private var theme: AppTheme { selectedTheme.appTheme(isDark: isDark) }

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
        .onChange(of: selectedTheme) { _, newValue in
            editor.applyTheme(newValue.appTheme(isDark: isDark))
        }
        .onChange(of: colorScheme) { _, newValue in
            editor.applyTheme(selectedTheme.appTheme(isDark: newValue == .dark))
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
                onToggleTheme: { selectedTheme = selectedTheme.next },
                onReset: { demo.reset() }
            )
        }
    }
}
