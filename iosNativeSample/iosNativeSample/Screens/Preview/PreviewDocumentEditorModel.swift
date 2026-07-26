import Combine
import SwiftUI
import UIKit

/// Editing-session logic for a document opened from the preview grid.
///
/// The model loads the library snapshot into a regular editor controller,
/// debounces exports during typing, flushes on exit, and publishes every
/// successful save back to the library so the retained grid preview updates.
@MainActor
final class PreviewDocumentEditorModel: ObservableObject {
    let editor: EditorScreenModel

    @Published private(set) var saveStatus = ""
    @Published private(set) var lastOpenedLink = ""

    private let documentID: String
    private let library: PreviewDocumentLibrary
    private var isLoaded = false
    private var lastPersistedJson: String?
    private var autosaveTask: Task<Void, Never>?
    private var statusClearTask: Task<Void, Never>?
    private var linkClearTask: Task<Void, Never>?

    init(
        documentID: String,
        library: PreviewDocumentLibrary,
        theme: AppTheme
    ) {
        self.documentID = documentID
        self.library = library
        let editor = EditorScreenModel(
            configuration: .standard(isDark: theme.isDark),
            colors: theme.editorColors
        )
        self.editor = editor

        editor.onDocumentChanged = { [weak self] in
            self?.scheduleAutosave()
        }
        editor.onOpenLink = { [weak self] url in
            self?.openLink(url)
        }

        loadInitialDocument()
    }

    private func loadInitialDocument() {
        guard let document = library.document(id: documentID) else {
            saveStatus = "Document unavailable"
            return
        }

        let result = editor.controller.loadJson(json: document.json)
        if result.success {
            isLoaded = true
            lastPersistedJson = document.json
            return
        }

        // Corrupt persisted content is replaced with the deterministic bundle
        // fixture once. A broken fixture is not retried, avoiding a recovery loop.
        guard let seedJson = library.reset(documentID: documentID) else {
            saveStatus = "Could not recover document"
            return
        }
        let recovery = editor.controller.loadJson(json: seedJson)
        isLoaded = recovery.success
        if recovery.success {
            lastPersistedJson = seedJson
        }
        if !recovery.success {
            saveStatus = "Could not load document"
        }
    }

    private func scheduleAutosave() {
        guard isLoaded else { return }
        autosaveTask?.cancel()
        autosaveTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(750))
            guard !Task.isCancelled else { return }
            self?.autosave()
        }
    }

    private func autosave() {
        guard isLoaded else { return }
        setSaveStatus(
            persistCurrentDocument() ? "Saved" : "Save failed"
        )
    }

    @discardableResult
    func saveOnExit() -> Bool {
        autosaveTask?.cancel()
        // Flush even when read-only is currently enabled: the user may have
        // toggled it during the debounce window after an earlier edit.
        guard isLoaded else { return true }
        // A successful Back-button flush makes the onDisappear flush a no-op.
        // A failed first write leaves lastPersistedJson unchanged, so the
        // second lifecycle signal gets one immediate retry.
        let succeeded = persistCurrentDocument()
        if !succeeded {
            setSaveStatus("Save failed")
        }
        return succeeded
    }

    func reset() {
        autosaveTask?.cancel()
        guard let seedJson = library.reset(documentID: documentID) else {
            setSaveStatus("Reset failed")
            return
        }

        isLoaded = false
        let result = editor.controller.reset(toJson: seedJson)
        isLoaded = result.success
        if result.success {
            lastPersistedJson = seedJson
        }
        setSaveStatus(result.success ? "Reset" : "Reset failed")
    }

    private func persistCurrentDocument() -> Bool {
        let json = editor.controller.exportJson()
        if json == lastPersistedJson {
            return true
        }
        guard library.save(json: json, for: documentID) else {
            return false
        }
        lastPersistedJson = json
        return true
    }

    private func setSaveStatus(_ value: String) {
        statusClearTask?.cancel()
        saveStatus = value
        statusClearTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            if self?.saveStatus == value {
                self?.saveStatus = ""
            }
        }
    }

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
}
