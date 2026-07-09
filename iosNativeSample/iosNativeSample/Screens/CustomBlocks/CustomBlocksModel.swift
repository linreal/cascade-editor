import SwiftUI
import CascadeEditor

/// Screen logic for the custom-blocks demo: registers the three native block
/// types and two slash commands, then loads the demo document.
///
/// Registration happens before the initial load so the demo document's custom
/// type ids decode straight into renderable native blocks.
@MainActor
final class CustomBlocksModel: ObservableObject {
    let editor: EditorScreenModel

    init(editor: EditorScreenModel) {
        self.editor = editor
        registerBlocks()
        registerSlashCommands()
        _ = editor.controller.loadJson(json: Self.demoDocumentJson())
    }

    /// In-memory reset: reloads a freshly built demo document (history clears
    /// with the load). There is deliberately no storage on this screen.
    func reset() {
        _ = editor.controller.reset(toJson: Self.demoDocumentJson())
    }

    private func registerBlocks() {
        editor.controller.registerBlock(
            registration: CascadeCustomBlockRegistration(
                typeId: "table",
                displayName: "Table",
                description: "Interactive table with editable cells",
                keywords: ["table", "grid", "rows", "columns", "cells"],
                slashBehavior: .insert,
                defaultPayloadJson: TableModel.default.payloadJson(),
                estimatedHeight: 320,
                rendererFactory: { context in
                    makeBlockHost(context: context) { model in
                        TableBlockView(model: model)
                    }
                }
            )
        )
        editor.controller.registerBlock(
            registration: CascadeCustomBlockRegistration(
                typeId: "metric",
                displayName: "Metric Card",
                description: "Stat card with value, label, and trend indicator",
                keywords: ["metric", "stat", "number", "kpi", "card", "dashboard"],
                slashBehavior: .insert,
                defaultPayloadJson:
                    #"{"value":"1,234","label":"Total Items","trend":"up","trendValue":"8.2%"}"#,
                estimatedHeight: 110,
                rendererFactory: { context in
                    makeBlockHost(context: context) { model in
                        MetricBlockView(model: model)
                    }
                }
            )
        )
        editor.controller.registerBlock(
            registration: CascadeCustomBlockRegistration(
                typeId: "palette",
                displayName: "Color Palette",
                description: "Color swatch palette with hex labels",
                keywords: ["palette", "color", "swatch", "colors", "theme", "design"],
                slashBehavior: .insert,
                defaultPayloadJson:
                    #"{"name":"Custom Palette","colors":"1A73E8,34A853,FBBC04,EA4335"}"#,
                estimatedHeight: 130,
                rendererFactory: { context in
                    makeBlockHost(context: context) { model in
                        PaletteBlockView(model: model)
                    }
                }
            )
        )
    }

    private func registerSlashCommands() {
        editor.controller.registerSlashCommand(
            command: CascadeSlashCommand(
                id: "custom.timestamp",
                title: "Timestamp",
                description: "Insert current date and time",
                keywords: ["date", "time", "now", "timestamp"],
                handler: { context in
                    let formatter = DateFormatter()
                    formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
                    context.replaceQueryText(replacement: formatter.string(from: Date()))
                    return CascadeSlashCommandResult.companion.done
                }
            )
        )
        editor.controller.registerSlashCommand(
            command: CascadeSlashCommand(
                id: "custom.lorem",
                title: "Lorem Ipsum",
                description: "Insert placeholder text",
                keywords: ["lorem", "ipsum", "placeholder", "dummy", "text"],
                handler: { context in
                    context.replaceQueryText(
                        replacement: "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
                            + "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. "
                            + "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris."
                    )
                    return CascadeSlashCommandResult.companion.done
                }
            )
        )
    }

    private static func demoDocumentJson() -> String {
        CascadeEditorDocumentBuilder()
            .heading(level: 1, text: "Custom Blocks & Commands")
            .paragraph(
                text: "This demo shows how to extend CascadeEditor with custom block types and slash commands."
            )
            .heading(level: 2, text: "Interactive Tables")
            .paragraph(
                text: "Table blocks are implemented by the sample app with public custom block APIs."
            )
            .customBlock(typeId: "table", payloadJson: TableModel.default.payloadJson())
            .paragraph(text: "Type /table to insert another table.")
            .divider()
            .heading(level: 2, text: "Metric Cards")
            .paragraph(
                text: "Metric cards are non-editable custom blocks — perfect for dashboards and status displays:"
            )
            .customBlock(
                typeId: "metric",
                payloadJson: #"{"value":"2,847","label":"Downloads","trend":"up","trendValue":"12.5%"}"#
            )
            .customBlock(
                typeId: "metric",
                payloadJson: #"{"value":"99.9%","label":"Uptime","trend":"up","trendValue":"0.1%"}"#
            )
            .customBlock(
                typeId: "metric",
                payloadJson: #"{"value":"142ms","label":"Avg Latency","trend":"down","trendValue":"23%"}"#
            )
            .paragraph(text: "Type /metric to insert a new metric card.")
            .divider()
            .heading(level: 2, text: "Color Palettes")
            .paragraph(
                text: "Color palette blocks showcase full native rendering — circles, hex labels, and layout:"
            )
            .customBlock(
                typeId: "palette",
                payloadJson: #"{"name":"Ocean Breeze","colors":"0077B6,00B4D8,90E0EF,CAF0F8"}"#
            )
            .customBlock(
                typeId: "palette",
                payloadJson: #"{"name":"Sunset Warmth","colors":"FF6B6B,FFA06B,FFD93D,6BCB77"}"#
            )
            .paragraph(text: "Type /palette to insert a new color palette.")
            .divider()
            .heading(level: 2, text: "Custom Slash Commands")
            .paragraph(text: "Type /timestamp to insert the current date and time.")
            .paragraph(text: "Type /lorem to insert placeholder text.")
            .paragraph(text: "These custom commands coexist with all built-in commands in the slash popup.")
            .divider()
            .heading(level: 2, text: "Try It Out")
            .paragraph(text: "Click on any empty paragraph and type / to see all available commands:")
            .paragraph(text: "")
            .paragraph(text: "")
            .buildJson()
    }
}
