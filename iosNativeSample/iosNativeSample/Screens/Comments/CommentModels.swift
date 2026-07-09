import SwiftUI
import UIKit

/// A single comment in the demo feed: plain text plus style runs — the same
/// shape the editor's rich-text snapshot exports, kept independent of SDK
/// types so seed data and sent comments share one renderer.
struct Comment: Identifiable {
    let id: Int
    let authorName: String
    let initials: String
    let avatarColor: Color
    let timestamp: String
    let isOwn: Bool
    let text: String
    let spans: [CommentSpan]
}

/// One style run over [start, end) in UTF-16 units (matching both Kotlin
/// string indices and NSAttributedString ranges).
struct CommentSpan {
    enum Kind {
        case bold
        case italic
        case underline
        case strikeThrough
        case inlineCode
        case highlight(argb: Int64)
        case link
    }

    let start: Int
    let end: Int
    let kind: Kind
}

/// Accumulates plain and styled segments while computing span offsets, so seed
/// data can be authored without manual index arithmetic.
struct CommentTextBuilder {
    private(set) var text = ""
    private(set) var spans: [CommentSpan] = []

    mutating func plain(_ segment: String) {
        text += segment
    }

    mutating func styled(_ segment: String, _ kinds: CommentSpan.Kind...) {
        let start = text.utf16.count
        text += segment
        let end = text.utf16.count
        for kind in kinds {
            spans.append(CommentSpan(start: start, end: end, kind: kind))
        }
    }
}

private func buildComment(
    id: Int,
    authorName: String,
    initials: String,
    avatarColor: Color,
    timestamp: String,
    isOwn: Bool,
    _ build: (inout CommentTextBuilder) -> Void
) -> Comment {
    var builder = CommentTextBuilder()
    build(&builder)
    return Comment(
        id: id,
        authorName: authorName,
        initials: initials,
        avatarColor: avatarColor,
        timestamp: timestamp,
        isOwn: isOwn,
        text: builder.text,
        spans: builder.spans
    )
}

enum SelfAuthor {
    static let name = "You"
    static let initials = "You"
    static let color = Color(rgb: 0x160B2E)
}

private let avatarMaya = Color(rgb: 0x6C3DE8)
private let avatarTheo = Color(rgb: 0xE2B23A)
private let avatarJordan = Color(rgb: 0x34C77B)

/// Seed feed mirroring the KMP sample's comment thread.
func seedComments() -> [Comment] {
    [
        buildComment(
            id: 1, authorName: "Maya Reyes", initials: "MR", avatarColor: avatarMaya,
            timestamp: "9:32 AM", isOwn: false
        ) { builder in
            builder.plain("The new ")
            builder.styled("onboarding flow", .bold)
            builder.plain(" feels so much smoother. Nice work shipping it this sprint.")
        },
        buildComment(
            id: 2, authorName: "Theo Kane", initials: "TK", avatarColor: avatarTheo,
            timestamp: "9:41 AM", isOwn: false
        ) { builder in
            builder.plain("One thing — the ")
            builder.styled("empty state", .italic)
            builder.plain(" on step 2 still says ")
            builder.styled("TODO", .inlineCode)
            builder.plain(". Can we fill that in before the demo?")
        },
        buildComment(
            id: 3, authorName: "Jordan Park", initials: "JP", avatarColor: avatarJordan,
            timestamp: "9:48 AM", isOwn: false
        ) { builder in
            builder.plain("Good catch. I'll have copy ready by ")
            builder.styled("this afternoon", .underline)
            builder.plain(" and ping you to review.")
        },
        buildComment(
            id: 4, authorName: SelfAuthor.name, initials: SelfAuthor.initials,
            avatarColor: SelfAuthor.color, timestamp: "9:50 AM", isOwn: true
        ) { builder in
            builder.plain("Perfect. I'll hold the build until the ")
            builder.styled("copy lands", .bold)
            builder.plain(" — flag me if anything blocks you.")
        },
        buildComment(
            id: 5, authorName: "Theo Kane", initials: "TK", avatarColor: avatarTheo,
            timestamp: "9:53 AM", isOwn: false
        ) { builder in
            builder.plain("Nothing blocking — ")
            builder.styled("thanks both", .italic)
            builder.plain(". Drafting now.")
        },
    ]
}

/// Renders a comment's style runs onto an `AttributedString` for a bubble.
///
/// Bold/italic merge symbolic font traits with whatever font is already in the
/// range, so overlapping runs compose instead of overwriting each other. Links
/// are styled (color + underline) but carry no `.link` attribute — bubbles are
/// intentionally non-interactive.
func commentAttributedString(
    text: String,
    spans: [CommentSpan],
    textColor: UIColor,
    inlineCodeBackground: UIColor,
    linkColor: UIColor
) -> AttributedString {
    let baseFont = UIFont.systemFont(ofSize: 15)
    let result = NSMutableAttributedString(
        string: text,
        attributes: [.font: baseFont, .foregroundColor: textColor]
    )
    let length = result.length

    for span in spans {
        let start = max(0, min(span.start, length))
        let end = max(start, min(span.end, length))
        guard end > start else { continue }
        let range = NSRange(location: start, length: end - start)

        switch span.kind {
        case .bold:
            result.mergeFontTraits(.traitBold, in: range)
        case .italic:
            result.mergeFontTraits(.traitItalic, in: range)
        case .underline:
            result.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: range)
        case .strikeThrough:
            result.addAttribute(.strikethroughStyle, value: NSUnderlineStyle.single.rawValue, range: range)
        case .inlineCode:
            result.addAttribute(.font, value: UIFont.monospacedSystemFont(ofSize: 14, weight: .regular), range: range)
            result.addAttribute(.backgroundColor, value: inlineCodeBackground, range: range)
        case .highlight(let argb):
            result.addAttribute(.backgroundColor, value: UIColor(Color(spanArgb: argb)), range: range)
        case .link:
            result.addAttribute(.foregroundColor, value: linkColor, range: range)
            result.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: range)
        }
    }

    return AttributedString(result)
}

private extension NSMutableAttributedString {
    /// Adds a symbolic trait to every font run inside `range`, preserving the
    /// runs' existing traits and sizes.
    func mergeFontTraits(_ trait: UIFontDescriptor.SymbolicTraits, in range: NSRange) {
        enumerateAttribute(.font, in: range) { value, subrange, _ in
            let current = (value as? UIFont) ?? UIFont.systemFont(ofSize: 15)
            let traits = current.fontDescriptor.symbolicTraits.union(trait)
            if let descriptor = current.fontDescriptor.withSymbolicTraits(traits) {
                addAttribute(.font, value: UIFont(descriptor: descriptor, size: current.pointSize), range: subrange)
            }
        }
    }
}
