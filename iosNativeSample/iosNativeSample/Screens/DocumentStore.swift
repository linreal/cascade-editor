import Foundation

/// Plain-file persistence for the editor demo's autosaved document JSON.
struct DocumentStore {
    let fileURL: URL

    static func editorDemo() -> DocumentStore {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return DocumentStore(fileURL: documents.appendingPathComponent("editor_demo_document.json"))
    }

    func read() -> String? {
        try? String(contentsOf: fileURL, encoding: .utf8)
    }

    func write(_ json: String) {
        try? json.write(to: fileURL, atomically: true, encoding: .utf8)
    }

    func delete() {
        try? FileManager.default.removeItem(at: fileURL)
    }
}
