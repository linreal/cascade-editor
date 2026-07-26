import Combine
import Foundation

struct PreviewDocument: Identifiable, Equatable {
    let id: String
    let title: String
    let subtitle: String
    let seedResource: String
    var json: String
}

private struct PreviewDocumentSeed {
    let id: String
    let title: String
    let subtitle: String
    let resource: String
}

/// App-owned source of truth for the preview-grid integration example.
///
/// Bundled JSON files provide deterministic first-launch content. Each document
/// receives its own editable copy under Documents/preview_showcase, and every
/// successful save updates the published snapshot used by the corresponding
/// preview card.
@MainActor
final class PreviewDocumentLibrary: ObservableObject {
    @Published private(set) var documents: [PreviewDocument] = []
    @Published private(set) var lastErrorMessage = ""

    private let fileManager: FileManager
    private let bundle: Bundle
    private let storageDirectory: URL

    init(
        fileManager: FileManager = .default,
        bundle: Bundle = .main
    ) {
        self.fileManager = fileManager
        self.bundle = bundle

        let documentsDirectory = fileManager.urls(
            for: .documentDirectory,
            in: .userDomainMask
        )[0]
        storageDirectory = documentsDirectory.appendingPathComponent(
            "preview_showcase",
            isDirectory: true
        )

        loadDocuments()
    }

    func document(id: String) -> PreviewDocument? {
        documents.first { $0.id == id }
    }

    /// Persists an editor export and publishes it to the grid only after the
    /// atomic file write succeeds.
    @discardableResult
    func save(json: String, for documentID: String) -> Bool {
        guard let index = documents.firstIndex(where: { $0.id == documentID }) else {
            lastErrorMessage = "Document '\(documentID)' is no longer available."
            return false
        }

        do {
            try ensureStorageDirectory()
            try json.write(
                to: persistedURL(for: documentID),
                atomically: true,
                encoding: .utf8
            )
            documents[index].json = json
            lastErrorMessage = ""
            return true
        } catch {
            lastErrorMessage = "Could not save \(documents[index].title): \(error.localizedDescription)"
            return false
        }
    }

    /// Restores a document's bundled fixture, persists it, and publishes the
    /// restored JSON. The editor uses the returned payload for its hard reset.
    func reset(documentID: String) -> String? {
        guard let index = documents.firstIndex(where: { $0.id == documentID }) else {
            lastErrorMessage = "Document '\(documentID)' is no longer available."
            return nil
        }
        guard let seedJson = bundledJson(resource: documents[index].seedResource) else {
            return nil
        }
        guard save(json: seedJson, for: documentID) else {
            return nil
        }
        return seedJson
    }

    func clearError() {
        lastErrorMessage = ""
    }

    private func loadDocuments() {
        do {
            try ensureStorageDirectory()
        } catch {
            lastErrorMessage = "Could not prepare preview storage: \(error.localizedDescription)"
        }

        documents = Self.seeds.compactMap { seed in
            let storedURL = persistedURL(for: seed.id)
            let storedJson: String?

            if fileManager.fileExists(atPath: storedURL.path) {
                do {
                    storedJson = try String(contentsOf: storedURL, encoding: .utf8)
                } catch {
                    lastErrorMessage = "Could not read \(seed.title): \(error.localizedDescription)"
                    storedJson = nil
                }
            } else {
                storedJson = nil
            }

            guard let json = storedJson ?? bundledJson(resource: seed.resource) else {
                return nil
            }

            if storedJson == nil {
                do {
                    try ensureStorageDirectory()
                    try json.write(to: storedURL, atomically: true, encoding: .utf8)
                } catch {
                    lastErrorMessage = "Could not seed \(seed.title): \(error.localizedDescription)"
                }
            }

            return PreviewDocument(
                id: seed.id,
                title: seed.title,
                subtitle: seed.subtitle,
                seedResource: seed.resource,
                json: json
            )
        }
    }

    private func bundledJson(resource: String) -> String? {
        guard let url = bundle.url(forResource: resource, withExtension: "json") else {
            lastErrorMessage = "Bundled preview document '\(resource).json' is missing."
            return nil
        }
        do {
            return try String(contentsOf: url, encoding: .utf8)
        } catch {
            lastErrorMessage = "Could not read \(resource).json: \(error.localizedDescription)"
            return nil
        }
    }

    private func ensureStorageDirectory() throws {
        try fileManager.createDirectory(
            at: storageDirectory,
            withIntermediateDirectories: true
        )
    }

    private func persistedURL(for documentID: String) -> URL {
        storageDirectory
            .appendingPathComponent(documentID, isDirectory: false)
            .appendingPathExtension("json")
    }

    private static let seeds: [PreviewDocumentSeed] = [
        PreviewDocumentSeed(
            id: "product-brief",
            title: "Product brief",
            subtitle: "Goals, audience, and launch notes",
            resource: "preview_product_brief"
        ),
        PreviewDocumentSeed(
            id: "weekly-plan",
            title: "Weekly plan",
            subtitle: "A lightweight execution checklist",
            resource: "preview_weekly_plan"
        ),
        PreviewDocumentSeed(
            id: "research-notes",
            title: "Research notes",
            subtitle: "Quotes, links, and open questions",
            resource: "preview_research_notes"
        ),
        PreviewDocumentSeed(
            id: "release-checklist",
            title: "Release checklist",
            subtitle: "The final path to production",
            resource: "preview_release_checklist"
        ),
        PreviewDocumentSeed(
            id: "code-review",
            title: "Code review",
            subtitle: "Implementation notes and follow-ups",
            resource: "preview_code_review"
        ),
        PreviewDocumentSeed(
            id: "trip-ideas",
            title: "Trip ideas",
            subtitle: "A small itinerary scratchpad",
            resource: "preview_trip_ideas"
        ),
    ]
}
