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
    private var currentThemeName: String

    init(json: String, theme: AppTheme) {
        currentJson = json
        currentThemeName = theme.name

        let configuration = CascadeDocumentPreviewConfiguration(
            maxBlocks: 4,
            maxLinesPerTextBlock: 3,
            textScale: 0.8,
            textSelectionEnabled: false,
            linksEnabled: false,
            isDark: theme.isDark,
            crashPolicy: CascadeCrashPolicy.containAndReport
        )
        controller = CascadeDocumentPreviewController(configuration: configuration)
        controller.setColors(colors: theme.editorColors)

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

    func update(json: String, theme: AppTheme) {
        if json != currentJson {
            let result = controller.loadJson(json: json)
            if result.success {
                currentJson = json
                errorMessage = ""
            } else {
                errorMessage = "The saved preview could not be loaded."
            }
        }

        if theme.name != currentThemeName {
            currentThemeName = theme.name
            controller.setDarkMode(value: theme.isDark)
            controller.setColors(colors: theme.editorColors)
        }
    }
}
