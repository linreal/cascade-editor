import Combine
import UIKit
import CascadeEditor

/// Observable wrapper around `CascadeEditorController` that turns the
/// controller's pull-based state (`canUndo`, `hasSelection`, …) into
/// `@Published` values the SwiftUI chrome can bind to.
///
/// The controller pushes change signals through its callbacks; the model
/// re-reads the derived properties on every signal instead of tracking deltas,
/// which keeps it correct for any mutation source (typing, undo, loads,
/// configuration changes).
@MainActor
final class EditorScreenModel: ObservableObject {
    let controller: CascadeEditorController

    @Published private(set) var canUndo = false
    @Published private(set) var canRedo = false
    @Published private(set) var hasSelection = false
    @Published private(set) var selectedCount = 0
    @Published private(set) var isReadOnly: Bool
    @Published private(set) var toolbarState = CascadeToolbarState.companion.Empty

    /// Screen-level hook invoked whenever document content changes (Task-level
    /// consumers use it for autosave). Fires on the main thread.
    var onDocumentChanged: (() -> Void)?
    /// Screen-level hook for link taps inside the editor.
    var onOpenLink: ((String) -> Void)?

    /// The hosted editor. Created once per controller and cached so SwiftUI
    /// re-parenting never spawns a second Compose tree over the same state.
    private(set) lazy var editorViewController: UIViewController = controller.makeViewController()

    init(configuration: CascadeEditorConfiguration) {
        controller = CascadeEditorController(initialJson: nil, configuration: configuration)
        isReadOnly = configuration.readOnly

        controller.onStateChanged = { [weak self] in self?.refreshEditorState() }
        controller.onDocumentChanged = { [weak self] in self?.onDocumentChanged?() }
        controller.onToolbarStateChanged = { [weak self] state in self?.toolbarState = state }
        controller.onOpenLink = { [weak self] url in self?.onOpenLink?(url) }
        controller.onInternalError = { message in
            #if DEBUG
            print("CascadeEditor internal error: \(message)")
            #endif
        }
    }

    func setReadOnly(_ value: Bool) {
        controller.setReadOnly(value: value)
    }

    func setDarkMode(_ value: Bool) {
        controller.setDarkMode(value: value)
    }

    func undo() { controller.undo() }
    func redo() { controller.redo() }
    func clearSelection() { controller.clearSelection() }
    func deleteSelectedOrFocused() { controller.deleteSelectedOrFocused() }

    private func refreshEditorState() {
        canUndo = controller.canUndo
        canRedo = controller.canRedo
        hasSelection = controller.hasSelection
        selectedCount = Int(controller.selectedBlockCount)
        isReadOnly = controller.configuration.readOnly
    }
}

extension CascadeEditorConfiguration {
    /// The sample's baseline editor configuration; screens override the pieces
    /// they need (`isDark`, chrome toggles) from here.
    static func standard(
        isDark: Bool,
        readOnly: Bool = false,
        toolbarMode: CascadeToolbarMode = .builtIn,
        slashCommandsEnabled: Bool = true,
        blockSelectionEnabled: Bool = true,
        blockDraggingEnabled: Bool = true
    ) -> CascadeEditorConfiguration {
        CascadeEditorConfiguration(
            readOnly: readOnly,
            toolbarMode: toolbarMode,
            slashCommandsEnabled: slashCommandsEnabled,
            blockSelectionEnabled: blockSelectionEnabled,
            blockDraggingEnabled: blockDraggingEnabled,
            isDark: isDark,
            crashPolicy: .containAndReport
        )
    }
}
