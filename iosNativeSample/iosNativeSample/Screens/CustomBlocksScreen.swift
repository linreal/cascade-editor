import SwiftUI
import CascadeEditor

/// Native custom blocks and slash commands demo: SwiftUI-rendered table,
/// metric, and palette blocks living inside the editor document.
struct CustomBlocksScreen: View {
    @Binding var selectedTheme: SampleThemeFamily
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @StateObject private var editor: EditorScreenModel
    @StateObject private var model: CustomBlocksModel

    init(selectedTheme: Binding<SampleThemeFamily>, initialIsDark: Bool) {
        _selectedTheme = selectedTheme
        let initialTheme = selectedTheme.wrappedValue.appTheme(isDark: initialIsDark)
        let editorModel = EditorScreenModel(
            configuration: .standard(isDark: initialTheme.isDark),
            colors: initialTheme.editorColors
        )
        _editor = StateObject(wrappedValue: editorModel)
        _model = StateObject(wrappedValue: CustomBlocksModel(editor: editorModel))
    }

    private var isDark: Bool { colorScheme == .dark }
    private var theme: AppTheme { selectedTheme.appTheme(isDark: isDark) }

    var body: some View {
        VStack(spacing: 0) {
            header
            CascadeEditorHost(model: editor)
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
                onBack: { dismiss() },
                onUndo: { editor.undo() },
                onRedo: { editor.redo() },
                onToggleReadOnly: { editor.setReadOnly(!editor.isReadOnly) },
                onToggleTheme: { selectedTheme = selectedTheme.next },
                onReset: { model.reset() }
            )
        }
    }
}
