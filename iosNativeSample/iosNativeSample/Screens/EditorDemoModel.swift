import SwiftUI
import UIKit
import CascadeEditor

/// Screen logic for the editor demo: initial load with corrupt-storage
/// recovery, debounced autosave, save-on-exit, reset, and the two status pills.
@MainActor
final class EditorDemoModel: ObservableObject {
    let editor: EditorScreenModel

    @Published private(set) var saveStatus = ""
    @Published private(set) var lastOpenedLink = ""

    private let store = DocumentStore.editorDemo()
    private var isLoaded = false
    private var autosaveTask: Task<Void, Never>?
    private var statusClearTask: Task<Void, Never>?
    private var linkClearTask: Task<Void, Never>?

    init(editor: EditorScreenModel) {
        self.editor = editor
        editor.onDocumentChanged = { [weak self] in self?.scheduleAutosave() }
        editor.onOpenLink = { [weak self] url in self?.openLink(url) }
        loadInitialDocument()
    }

    /// Loads the autosaved document, falling back to the bundled fixture. A
    /// corrupt saved file (parse failure) deletes storage and reloads the
    /// fixture exactly once — the fixture result is not re-checked, so a bad
    /// fixture cannot cause a recovery loop.
    private func loadInitialDocument() {
        let json = store.read() ?? Self.bundledDocumentJson()
        let result = editor.controller.loadJson(json: json)
        if !result.success {
            store.delete()
            _ = editor.controller.loadJson(json: Self.bundledDocumentJson())
        }
        isLoaded = true
    }

    /// Debounces the document-change signal; the JSON export happens only when
    /// a save actually fires, never per keystroke.
    private func scheduleAutosave() {
        guard isLoaded else { return }
        autosaveTask?.cancel()
        autosaveTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            self?.autosave()
        }
    }

    private func autosave() {
        guard isLoaded, !editor.isReadOnly else { return }
        statusClearTask?.cancel()
        saveStatus = "Saving..."
        store.write(editor.controller.exportJson())
        saveStatus = "Saved"
        statusClearTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            self?.saveStatus = ""
        }
    }

    /// Immediate save used on back navigation, separate from the debounce.
    func saveOnExit() {
        autosaveTask?.cancel()
        guard isLoaded, !editor.isReadOnly else { return }
        store.write(editor.controller.exportJson())
    }

    /// Reset deletes the autosave storage and reloads the bundled fixture
    /// (which also clears editor history).
    func reset() {
        autosaveTask?.cancel()
        store.delete()
        _ = editor.controller.reset(toJson: Self.bundledDocumentJson())
    }

    /// Opens the link natively and shows the pill for three seconds. The
    /// equality check keeps an older timer from clearing a newer link when the
    /// same or another URL is opened in quick succession.
    private func openLink(_ url: String) {
        lastOpenedLink = url
        linkClearTask?.cancel()
        linkClearTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled else { return }
            if self?.lastOpenedLink == url {
                self?.lastOpenedLink = ""
            }
        }
        if let parsed = URL(string: url), UIApplication.shared.canOpenURL(parsed) {
            UIApplication.shared.open(parsed)
        }
    }

    private static func bundledDocumentJson() -> String {
        guard let url = Bundle.main.url(forResource: "default_document", withExtension: "json"),
              let json = try? String(contentsOf: url, encoding: .utf8)
        else {
            return "{\"version\":2,\"blocks\":[]}"
        }
        return json
    }
}
