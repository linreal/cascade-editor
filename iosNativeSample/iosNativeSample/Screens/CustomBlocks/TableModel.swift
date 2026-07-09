import Foundation

/// Value model behind the interactive table block's payload
/// (`{"rows": [[String]], "headerRow": Bool}`), mirroring the KMP sample's
/// normalization: rows padded to a rectangular grid, at least one row and
/// column, defaulting to the demo table when the payload is unusable.
struct TableModel {
    var rows: [[String]]
    var headerRow: Bool

    var rowCount: Int { rows.count }
    var columnCount: Int { rows.first?.count ?? 1 }

    static let `default` = TableModel(
        rows: [
            ["Name", "Role", "Status"],
            ["Ada", "Engineer", "Active"],
            ["Linus", "Maintainer", "Review"],
        ],
        headerRow: true
    )

    static func fromPayload(_ json: String) -> TableModel {
        let payload = PayloadJson.object(from: json)
        let rawRows = payload["rows"] as? [[Any]] ?? []
        let parsed = rawRows.map { row in
            row.map { cell in cell as? String ?? String(describing: cell) }
        }
        let headerRow = payload["headerRow"] as? Bool ?? false
        return normalized(parsed, headerRow: headerRow)
    }

    func payloadJson() -> String {
        PayloadJson.string(from: ["rows": rows, "headerRow": headerRow])
    }

    func value(row: Int, column: Int) -> String {
        guard rows.indices.contains(row), rows[row].indices.contains(column) else { return "" }
        return rows[row][column]
    }

    mutating func editCell(row: Int, column: Int, value: String) {
        guard rows.indices.contains(row), rows[row].indices.contains(column) else { return }
        rows[row][column] = value
    }

    mutating func addRow() {
        rows.append(Array(repeating: "", count: columnCount))
    }

    mutating func deleteRow(_ row: Int) {
        guard rows.count > 1, rows.indices.contains(row) else { return }
        rows.remove(at: row)
    }

    mutating func addColumn() {
        rows = rows.map { $0 + [""] }
    }

    mutating func deleteColumn(_ column: Int) {
        guard columnCount > 1, (0..<columnCount).contains(column) else { return }
        rows = rows.map { row in
            var cells = row
            cells.remove(at: column)
            return cells
        }
    }

    private static func normalized(_ candidate: [[String]], headerRow: Bool) -> TableModel {
        let nonEmpty = candidate.filter { !$0.isEmpty }
        guard !nonEmpty.isEmpty else { return .default }
        let width = max(1, nonEmpty.map(\.count).max() ?? 1)
        let padded = nonEmpty.map { row in
            row + Array(repeating: "", count: width - row.count)
        }
        return TableModel(rows: padded, headerRow: headerRow)
    }
}
