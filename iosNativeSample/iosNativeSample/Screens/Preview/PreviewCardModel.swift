import Combine
import Foundation
import CascadeEditor

/// Owns one preview controller for one visible grid cell.
///
/// Document and theme changes update the existing controller rather than
/// replacing its UIKit/Compose host. When SwiftUI releases the cell, this model
/// and its controller are released together.
@MainActor
final class PreviewCardModel: ObservableObject {
    let controller: CascadeDocumentPreviewController

    @Published private(set) var errorMessage = ""

    private var currentJson: String
    private var currentIsDark: Bool

    init(json: String, isDark: Bool) {
        currentJson = json
        currentIsDark = isDark

        let configuration = CascadeDocumentPreviewConfiguration(
            maxBlocks: 4,
            maxLinesPerTextBlock: 3,
            textScale: 0.8,
            textSelectionEnabled: false,
            linksEnabled: false,
            isDark: isDark,
            crashPolicy: CascadeCrashPolicy.containAndReport
        )
        controller = CascadeDocumentPreviewController(configuration: configuration)

        controller.onInternalError = { [weak self] message in
            self?.errorMessage = message
        }
        // Grid cards own the tap target. Keeping this callback nil is a second
        // line of defense alongside linksEnabled=false.
        controller.onOpenLink = nil

        let result = controller.loadJson(json: json)
        if !result.success {
            errorMessage = "The saved preview could not be loaded."
        }
    }

    func update(json: String, isDark: Bool) {
        if json != currentJson {
            let result = controller.loadJson(json: json)
            if result.success {
                currentJson = json
                errorMessage = ""
            } else {
                errorMessage = "The saved preview could not be loaded."
            }
        }

        if isDark != currentIsDark {
            currentIsDark = isDark
            controller.setDarkMode(value: isDark)
        }
    }
}
