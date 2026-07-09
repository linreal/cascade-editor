import SwiftUI
import CascadeEditor

/// Editor-as-composer demo: a feed of rich-text bubbles above a pinned
/// composer whose formatting bar fades in while the editor is focused.
struct CommentsScreen: View {
    @Binding var isDark: Bool
    @Environment(\.dismiss) private var dismiss
    @StateObject private var editor: EditorScreenModel
    @StateObject private var model: CommentsModel

    init(isDark: Binding<Bool>) {
        _isDark = isDark
        let editorModel = EditorScreenModel(
            configuration: .standard(
                isDark: isDark.wrappedValue,
                toolbarMode: CascadeToolbarMode.none,
                slashCommandsEnabled: false,
                blockSelectionEnabled: false,
                blockDraggingEnabled: false
            )
        )
        _editor = StateObject(wrappedValue: editorModel)
        _model = StateObject(wrappedValue: CommentsModel(editor: editorModel))
    }

    private var theme: AppTheme { AppTheme.theme(isDark: isDark) }

    var body: some View {
        VStack(spacing: 0) {
            TitledEditorTopBar(
                theme: theme,
                title: "Comments",
                onBack: { dismiss() },
                onToggleTheme: { isDark.toggle() }
            )
            feed
            composer
        }
        .padding(.horizontal, 12)
        .background(theme.background)
        .onChange(of: isDark) { _, newValue in
            editor.setDarkMode(newValue)
        }
    }

    private var feed: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 18) {
                    ForEach(model.comments) { comment in
                        CommentBubble(theme: theme, comment: comment)
                            .id(comment.id)
                    }
                }
                .padding(.top, 16)
                .padding(.bottom, 20)
            }
            .onChange(of: model.comments.count) { _, _ in
                if let last = model.comments.last {
                    withAnimation {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
        }
    }

    private var composer: some View {
        VStack(spacing: 0) {
            if editor.toolbarState.focused {
                FormattingBar(theme: theme, editor: editor)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
            HStack(spacing: 8) {
                CascadeEditorHost(model: editor)
                    .frame(height: 64)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                SendButton(theme: theme, enabled: model.canSend) {
                    model.send()
                }
            }
            .padding(EdgeInsets(top: 4, leading: 6, bottom: 4, trailing: 8))
            .background(theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 24))
        }
        .padding(.top, 8)
        .padding(.bottom, 12)
        .animation(.default, value: editor.toolbarState.focused)
    }
}

private struct CommentBubble: View {
    let theme: AppTheme
    let comment: Comment

    var body: some View {
        let bubbleColor = comment.isOwn ? theme.primary : theme.surface
        let textColor = comment.isOwn ? theme.onPrimary : theme.onSurface
        let inlineCodeBackground = UIColor(textColor).withAlphaComponent(comment.isOwn ? 0.18 : 0.08)
        let linkColor = comment.isOwn ? UIColor(theme.onPrimary) : UIColor(theme.primary)

        HStack(alignment: .top, spacing: 11) {
            if comment.isOwn {
                Spacer(minLength: 40)
            } else {
                Avatar(comment: comment)
            }

            VStack(alignment: comment.isOwn ? .trailing : .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Text(comment.authorName)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(theme.onBackground)
                    Text(comment.timestamp)
                        .font(.system(size: 12))
                        .foregroundStyle(theme.onBackground.opacity(0.5))
                }
                Text(
                    commentAttributedString(
                        text: comment.text,
                        spans: comment.spans,
                        textColor: UIColor(textColor),
                        inlineCodeBackground: inlineCodeBackground,
                        linkColor: linkColor
                    )
                )
                .font(.system(size: 15))
                .lineSpacing(4)
                .padding(.horizontal, 14)
                .padding(.vertical, 11)
                .background(bubbleColor)
                .clipShape(BubbleShape(isOwn: comment.isOwn))
            }
            .frame(maxWidth: 300, alignment: comment.isOwn ? .trailing : .leading)

            if comment.isOwn {
                Avatar(comment: comment)
            } else {
                Spacer(minLength: 40)
            }
        }
        .frame(maxWidth: .infinity, alignment: comment.isOwn ? .trailing : .leading)
    }
}

private struct BubbleShape: Shape {
    let isOwn: Bool

    func path(in rect: CGRect) -> Path {
        let sharpCorner: UIRectCorner = isOwn ? .topRight : .topLeft
        let rounded = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: UIRectCorner.allCorners.subtracting(sharpCorner),
            cornerRadii: CGSize(width: 16, height: 16)
        )
        let sharp = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: sharpCorner,
            cornerRadii: CGSize(width: 4, height: 4)
        )
        var path = Path(rounded.cgPath)
        path = path.intersection(Path(sharp.cgPath))
        return path
    }
}

private struct Avatar: View {
    let comment: Comment

    var body: some View {
        Text(comment.initials)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 36, height: 36)
            .background(comment.avatarColor)
            .clipShape(Circle())
    }
}

/// Formatting bar shown while the composer is focused. Buttons drive the
/// controller's toolbar actions; SwiftUI buttons never become first responder,
/// so tapping them keeps the editor focused.
private struct FormattingBar: View {
    let theme: AppTheme
    @ObservedObject var editor: EditorScreenModel

    var body: some View {
        HStack(spacing: 6) {
            formatButton("bold", "Bold", editor.toolbarState.bold) { editor.controller.toggleBold() }
            formatButton("italic", "Italic", editor.toolbarState.italic) { editor.controller.toggleItalic() }
            formatButton("underline", "Underline", editor.toolbarState.underline) { editor.controller.toggleUnderline() }
            Rectangle()
                .fill(theme.onSurface.opacity(0.12))
                .frame(width: 1, height: 20)
                .padding(.horizontal, 3)
            formatButton("strikethrough", "Strikethrough", editor.toolbarState.strikeThrough) {
                editor.controller.toggleStrikeThrough()
            }
            formatButton("chevron.left.forwardslash.chevron.right", "Inline code", editor.toolbarState.inlineCode) {
                editor.controller.toggleInlineCode()
            }
            Spacer()
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
    }

    private func formatButton(
        _ systemImage: String,
        _ label: String,
        _ state: CascadeStyleState,
        action: @escaping () -> Void
    ) -> some View {
        let enabled = editor.toolbarState.canFormat
        let active = state == CascadeStyleState.active || state == CascadeStyleState.mixed
        let background: Color = !enabled ? .clear : (active ? theme.primary.opacity(0.16) : theme.surface)
        let tint: Color = !enabled
            ? theme.onSurface.opacity(0.3)
            : (active ? theme.primary : theme.onSurface.opacity(0.7))

        return Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(tint)
                .frame(width: 38, height: 38)
                .background(background)
                .clipShape(RoundedRectangle(cornerRadius: 9))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

private struct SendButton: View {
    let theme: AppTheme
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "paperplane.fill")
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(theme.onPrimary)
                .frame(width: 44, height: 44)
                .background(theme.primary.opacity(enabled ? 1 : 0.3))
                .clipShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel("Send comment")
    }
}
