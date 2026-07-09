import SwiftUI
import CascadeEditor

/// Screen logic for the comments demo: a chromeless composer editor plus the
/// in-memory feed. Sending flattens the composer's rich-text snapshot into a
/// single bubble and resets the composer to one empty paragraph.
@MainActor
final class CommentsModel: ObservableObject {
    let editor: EditorScreenModel

    @Published private(set) var comments: [Comment]
    @Published private(set) var canSend = false

    private var nextId: Int

    init(editor: EditorScreenModel) {
        self.editor = editor
        let seeded = seedComments()
        comments = seeded
        nextId = (seeded.map(\.id).max() ?? 0) + 1
        editor.onDocumentChanged = { [weak self] in self?.refreshCanSend() }
        resetComposer()
    }

    /// Flattens the composer document into one comment: block texts joined
    /// with newlines and every block's spans shifted into the combined
    /// coordinate space (all offsets in UTF-16 units, matching the SDK's).
    func send() {
        let snapshot = editor.controller.exportRichText()
        var combined = ""
        var spans: [CommentSpan] = []
        for (index, block) in snapshot.blocks.enumerated() {
            if index > 0 { combined += "\n" }
            let base = combined.utf16.count
            for span in block.spans {
                spans.append(
                    CommentSpan(
                        start: base + Int(span.start),
                        end: base + Int(span.end),
                        kind: span.commentSpanKind
                    )
                )
            }
            combined += block.text
        }
        guard !combined.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

        comments.append(
            Comment(
                id: nextId,
                authorName: SelfAuthor.name,
                initials: SelfAuthor.initials,
                avatarColor: SelfAuthor.color,
                timestamp: "Now",
                isOwn: true,
                text: combined,
                spans: spans
            )
        )
        nextId += 1
        resetComposer()
        editor.controller.clearFocus()
    }

    /// Returns the composer to a single empty paragraph (clears history too).
    private func resetComposer() {
        _ = editor.controller.loadJson(json: Self.emptyDocumentJson)
        refreshCanSend()
    }

    private func refreshCanSend() {
        let text = editor.controller.exportPlainText()
        canSend = !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private static let emptyDocumentJson: String =
        CascadeEditorDocumentBuilder().paragraph(text: "").buildJson()
}

extension CascadeRichTextSpan {
    /// Maps the SDK span onto the feed's renderer-agnostic span kind.
    var commentSpanKind: CommentSpan.Kind {
        switch kind {
        case .bold: return .bold
        case .italic: return .italic
        case .underline: return .underline
        case .strikeThrough: return .strikeThrough
        case .inlineCode: return .inlineCode
        case .highlight: return .highlight(argb: argb)
        case .link: return .link
        default: return .bold
        }
    }
}
