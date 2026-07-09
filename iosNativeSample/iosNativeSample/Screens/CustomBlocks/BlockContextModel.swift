import SwiftUI
import UIKit
import CascadeEditor

/// Observable wrapper around a block's `CascadeCustomBlockContext`.
///
/// The context is pull-based: it always exposes current values, and its
/// `onChange` fires after any editor-side change (payload, focus, selection,
/// read-only, theme). This model turns that signal into `objectWillChange`, so
/// SwiftUI block views re-read the context on every editor change — including
/// undo/redo and document reloads the native view could not otherwise observe.
@MainActor
final class BlockContextModel: ObservableObject {
    let context: CascadeCustomBlockContext

    init(context: CascadeCustomBlockContext) {
        self.context = context
        context.onChange = { [weak self] in
            self?.objectWillChange.send()
        }
    }
}

/// Hosts a SwiftUI block view inside the editor and keeps the Compose-side
/// host sized to the view's ideal height.
///
/// Compose gives the hosted controller a fixed frame (starting at the
/// registration's estimated height); after every layout pass this controller
/// measures the SwiftUI content's fitting height for the current width and
/// reports it through `setPreferredHeight`, converging in one round trip.
/// The half-point tolerance breaks the report → relayout → report cycle.
final class NativeBlockHostController<Content: View>: UIHostingController<Content> {
    private let model: BlockContextModel
    private var lastReportedHeight: CGFloat = 0

    init(model: BlockContextModel, rootView: Content) {
        self.model = model
        super.init(rootView: rootView)
        view.backgroundColor = .clear
    }

    @available(*, unavailable)
    @MainActor required dynamic init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        reportPreferredHeight()
    }

    private func reportPreferredHeight() {
        let width = view.bounds.width
        guard width > 0 else { return }
        let height = sizeThatFits(in: CGSize(width: width, height: .greatestFiniteMagnitude)).height
        guard height > 0, abs(height - lastReportedHeight) > 0.5 else { return }
        lastReportedHeight = height
        model.context.setPreferredHeight(height: Double(height))
    }
}

/// Builds the `UIViewController` a block registration's renderer factory
/// returns: a measuring hosting controller around a context-observing view.
@MainActor
func makeBlockHost<Content: View>(
    context: CascadeCustomBlockContext,
    @ViewBuilder content: (BlockContextModel) -> Content
) -> UIViewController {
    let model = BlockContextModel(context: context)
    return NativeBlockHostController(model: model, rootView: content(model))
}

/// Minimal JSON-object bridging for custom-block payload strings.
enum PayloadJson {
    static func object(from json: String) -> [String: Any] {
        let parsed = try? JSONSerialization.jsonObject(with: Data(json.utf8))
        return parsed as? [String: Any] ?? [:]
    }

    static func string(from object: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: object),
              let json = String(data: data, encoding: .utf8)
        else {
            return "{}"
        }
        return json
    }
}
