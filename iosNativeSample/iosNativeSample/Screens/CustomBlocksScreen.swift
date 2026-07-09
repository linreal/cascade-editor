import SwiftUI
import CascadeEditor

/// Native custom blocks and slash commands demo: SwiftUI-rendered table,
/// metric, and palette blocks living inside the editor document.
struct CustomBlocksScreen: View {
    @Binding var isDark: Bool
    @Environment(\.dismiss) private var dismiss
    @StateObject private var editor: EditorScreenModel
    @StateObject private var model: CustomBlocksModel

    init(isDark: Binding<Bool>) {
        _isDark = isDark
        let editorModel = EditorScreenModel(configuration: .standard(isDark: isDark.wrappedValue))
        _editor = StateObject(wrappedValue: editorModel)
        _model = StateObject(wrappedValue: CustomBlocksModel(editor: editorModel))
    }

    private var theme: AppTheme { AppTheme.theme(isDark: isDark) }

    var body: some View {
        VStack(spacing: 0) {
            header
            CascadeEditorHost(model: editor)
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
                onBack: { dismiss() },
                onUndo: { editor.undo() },
                onRedo: { editor.redo() },
                onToggleReadOnly: { editor.setReadOnly(!editor.isReadOnly) },
                onToggleTheme: { isDark.toggle() },
                onReset: { model.reset() }
            )
        }
    }
}
