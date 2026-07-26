// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CascadeEditorLocalSmoke",
    platforms: [
        .iOS(.v16),
    ],
    products: [
        .library(
            name: "CascadeEditor",
            targets: ["CascadeEditor"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "CascadeEditor",
            path: "Artifacts/CascadeEditor.xcframework"
        ),
    ]
)
