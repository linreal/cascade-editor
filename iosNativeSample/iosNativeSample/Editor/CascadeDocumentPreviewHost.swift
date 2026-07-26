import SwiftUI
import UIKit
import CascadeEditor

/// Thin SwiftUI bridge for the SDK's stateless document-preview controller.
///
/// The owning grid cell retains the controller, so SwiftUI updates do not
/// remount its Compose tree. The host itself has no sizing or interaction
/// policy; callers provide a bounded frame and decide how taps are handled.
struct CascadeDocumentPreviewHost: UIViewControllerRepresentable {
    let controller: CascadeDocumentPreviewController

    func makeUIViewController(context: Context) -> UIViewController {
        controller.makeViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Content and theme changes flow through the preview-controller API.
    }
}
