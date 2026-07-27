import CascadeEditor
import UIKit

@MainActor
final class SmokeViewController: UIViewController {
    private static let expectedText = "CascadeEditor packaged resource smoke"

    private let statusLabel: UILabel = {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.accessibilityIdentifier = "cascade.smoke.status"
        label.font = .preferredFont(forTextStyle: .headline)
        label.textAlignment = .center
        label.text = "CASCADE_SMOKE_STARTING"
        return label
    }()

    private lazy var editorController: CascadeEditorController = {
        let json = CascadeEditorDocumentBuilder()
            .paragraph(text: Self.expectedText)
            .buildJson()
        let configuration = CascadeEditorConfiguration(
            readOnly: false,
            toolbarMode: .builtIn,
            slashCommandsEnabled: true,
            blockSelectionEnabled: true,
            blockDraggingEnabled: true,
            isDark: false,
            crashPolicy: .rethrow,
            blockIndentationEnabled: true,
            emptyDocumentPlaceholderEnabled: false
        )
        return CascadeEditorController(initialJson: json, configuration: configuration)
    }()

    private var internalErrors: [String] = []

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        editorController.onInternalError = { [weak self] message in
            self?.internalErrors.append(message)
            self?.setStatus("CASCADE_SMOKE_FAILED \(message)")
        }

        let editorViewController = editorController.makeViewController()
        addChild(editorViewController)
        editorViewController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(statusLabel)
        view.addSubview(editorViewController.view)
        editorViewController.didMove(toParent: self)

        NSLayoutConstraint.activate([
            statusLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            statusLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 30),
            editorViewController.view.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 8),
            editorViewController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            editorViewController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            editorViewController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])

        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { [weak self] in
            self?.completeSmokeCheck()
        }
    }

    private func completeSmokeCheck() {
        guard internalErrors.isEmpty else {
            setStatus("CASCADE_SMOKE_FAILED internal SDK error")
            return
        }
        let exportedText = editorController.exportPlainText()
        guard exportedText == Self.expectedText else {
            setStatus("CASCADE_SMOKE_FAILED unexpected export: \(exportedText)")
            return
        }
        setStatus("CASCADE_SMOKE_READY \(CascadeEditorSdk.shared.version)")
    }

    private func setStatus(_ value: String) {
        statusLabel.text = value
        statusLabel.accessibilityLabel = value
    }
}
