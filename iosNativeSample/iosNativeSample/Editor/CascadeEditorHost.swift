import SwiftUI
import UIKit

/// Thin `UIViewControllerRepresentable` embedding the SDK's editor view
/// controller. The view controller is created (and cached) by the model, so
/// SwiftUI re-evaluating this representable never remounts the Compose tree.
struct CascadeEditorHost: UIViewControllerRepresentable {
    let model: EditorScreenModel

    func makeUIViewController(context: Context) -> UIViewController {
        model.editorViewController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // All editor updates flow through the controller API; nothing to sync here.
    }
}
