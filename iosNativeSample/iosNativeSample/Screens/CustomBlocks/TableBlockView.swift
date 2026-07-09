import SwiftUI

private struct TableCellKey: Hashable {
    let row: Int
    let column: Int
}

/// Palette for the table card, mirroring the KMP sample's fixed light/dark
/// table colors.
private struct TableColors {
    let card: Color
    let cardBorder: Color
    let accentBorder: Color
    let gridBorder: Color
    let cellBorder: Color
    let divider: Color
    let headerBackground: Color
    let trailingBackground: Color
    let iconTileBackground: Color
    let iconTint: Color
    let title: Color
    let headerText: Color
    let bodyText: Color
    let placeholder: Color
    let mutedLabel: Color
    let accent: Color
    let pillBackground: Color
    let rowDeleteIdle: Color

    static func palette(isDark: Bool) -> TableColors {
        if isDark {
            return TableColors(
                card: Color(rgb: 0x1B1430),
                cardBorder: Color(argb: 0x1FFFFFFF),
                accentBorder: Color(rgb: 0xA78BFA),
                gridBorder: Color(argb: 0x1FFFFFFF),
                cellBorder: Color(argb: 0x14FFFFFF),
                divider: Color(argb: 0x14FFFFFF),
                headerBackground: Color(argb: 0x248B5CF6),
                trailingBackground: Color(argb: 0x0AFFFFFF),
                iconTileBackground: Color(argb: 0x2E8B5CF6),
                iconTint: Color(rgb: 0xC4B5FD),
                title: Color(rgb: 0xF4F1FB),
                headerText: Color(rgb: 0xF4F1FB),
                bodyText: Color(rgb: 0xD8D2E8),
                placeholder: Color(rgb: 0x6F6690),
                mutedLabel: Color(rgb: 0x9B93B8),
                accent: Color(rgb: 0xA78BFA),
                pillBackground: Color(argb: 0x1F8B5CF6),
                rowDeleteIdle: Color(rgb: 0x6F6690)
            )
        }
        return TableColors(
            card: .white,
            cardBorder: Color(rgb: 0xEFE9FB),
            accentBorder: Color(rgb: 0x6C3DE8),
            gridBorder: Color(rgb: 0xE9E1F8),
            cellBorder: Color(rgb: 0xEDE6F8),
            divider: Color(rgb: 0xF1ECFB),
            headerBackground: Color(rgb: 0xF4EFFE),
            trailingBackground: Color(rgb: 0xFBF9FF),
            iconTileBackground: Color(rgb: 0xF0E9FE),
            iconTint: Color(rgb: 0x6C3DE8),
            title: Color(rgb: 0x1C1238),
            headerText: Color(rgb: 0x1C1238),
            bodyText: Color(rgb: 0x332A4D),
            placeholder: Color(rgb: 0xBCB2D6),
            mutedLabel: Color(rgb: 0x6B6580),
            accent: Color(rgb: 0x6C3DE8),
            pillBackground: Color(rgb: 0xF1ECFB),
            rowDeleteIdle: Color(rgb: 0xCDBFF0)
        )
    }
}

private let columnWidth: CGFloat = 132
private let trailingColumnWidth: CGFloat = 40
private let cellMinHeight: CGFloat = 44

/// Interactive table block: editable cells, header-row toggle, and add/delete
/// row/column controls.
///
/// Cell edits are held in local drafts and committed to the block payload on
/// submit or focus loss (one undo step per commit rather than per keystroke).
/// Structural changes fold pending drafts in before transforming, so an
/// in-progress edit is not lost when a row or column is added.
struct TableBlockView: View {
    @ObservedObject var model: BlockContextModel
    @State private var drafts: [TableCellKey: String] = [:]
    @FocusState private var focusedCell: TableCellKey?

    var body: some View {
        let table = TableModel.fromPayload(model.context.payloadJson)
        let colors = TableColors.palette(isDark: model.context.isDark)
        let canMutate = !model.context.readOnly && model.context.canUpdateBlock
        let highlighted = model.context.isFocused || model.context.isSelected

        VStack(spacing: 0) {
            toolbar(table: table, colors: colors, canMutate: canMutate)
            Rectangle()
                .fill(colors.divider)
                .frame(height: 1)
            grid(table: table, colors: colors, canMutate: canMutate)
            footer(colors: colors, canMutate: canMutate)
        }
        .background(colors.card)
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .strokeBorder(
                    highlighted ? colors.accentBorder : colors.cardBorder,
                    lineWidth: highlighted ? 2 : 1
                )
        )
        .onChange(of: focusedCell) { previous, _ in
            if let previous {
                commitDraft(for: previous)
            }
        }
    }

    private func toolbar(table: TableModel, colors: TableColors, canMutate: Bool) -> some View {
        HStack {
            Image(systemName: "tablecells")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(colors.iconTint)
                .frame(width: 30, height: 30)
                .background(colors.iconTileBackground)
                .clipShape(RoundedRectangle(cornerRadius: 9))
            Text("Table")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(colors.title)
                .padding(.leading, 10)
            Spacer()
            Toggle(isOn: Binding(
                get: { table.headerRow },
                set: { enabled in
                    updateTable { $0.headerRow = enabled }
                }
            )) {
                Text("Header row")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(colors.mutedLabel)
            }
            .toggleStyle(.switch)
            .tint(colors.accent)
            .fixedSize()
            .disabled(!canMutate)
            .accessibilityLabel("Toggle header row")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private func grid(table: TableModel, colors: TableColors, canMutate: Bool) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            VStack(spacing: 0) {
                ForEach(0..<table.rowCount, id: \.self) { row in
                    gridRow(row: row, table: table, colors: colors, canMutate: canMutate)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 13))
            .overlay(RoundedRectangle(cornerRadius: 13).strokeBorder(colors.gridBorder, lineWidth: 1))
        }
        .padding(.horizontal, 16)
        .padding(.top, 14)
        .padding(.bottom, 4)
    }

    private func gridRow(row: Int, table: TableModel, colors: TableColors, canMutate: Bool) -> some View {
        let isHeaderRow = row == 0
        let isHeaderStyled = isHeaderRow && table.headerRow
        let background = isHeaderStyled ? colors.headerBackground : Color.clear
        let textColor = isHeaderStyled ? colors.headerText : colors.bodyText

        return HStack(spacing: 0) {
            ForEach(0..<table.columnCount, id: \.self) { column in
                cell(
                    key: TableCellKey(row: row, column: column),
                    table: table,
                    colors: colors,
                    canMutate: canMutate,
                    textColor: textColor,
                    isHeaderStyled: isHeaderStyled,
                    showColumnDelete: isHeaderRow && canMutate && table.columnCount > 1
                )
                .frame(width: columnWidth)
                .frame(minHeight: cellMinHeight)
                .background(background)
                .overlay(alignment: .trailing) {
                    Rectangle().fill(colors.cellBorder).frame(width: 1)
                }
            }
            trailingCell(row: row, colors: colors, canMutate: canMutate, isHeaderRow: isHeaderRow)
        }
        .overlay(alignment: .bottom) {
            if row < table.rowCount - 1 {
                Rectangle().fill(colors.cellBorder).frame(height: 1)
            }
        }
    }

    private func cell(
        key: TableCellKey,
        table: TableModel,
        colors: TableColors,
        canMutate: Bool,
        textColor: Color,
        isHeaderStyled: Bool,
        showColumnDelete: Bool
    ) -> some View {
        let persisted = table.value(row: key.row, column: key.column)
        let weight: Font.Weight = isHeaderStyled ? .semibold : .regular

        return ZStack(alignment: .topTrailing) {
            TextField(
                key.row == 0 ? "Field" : "—",
                text: Binding(
                    get: { drafts[key] ?? persisted },
                    set: { drafts[key] = $0 }
                )
            )
            .font(.system(size: 13.5, weight: weight))
            .foregroundStyle(textColor)
            .tint(colors.accent)
            .focused($focusedCell, equals: key)
            .onSubmit { commitDraft(for: key) }
            .disabled(!canMutate)
            .padding(.leading, 12)
            .padding(.trailing, showColumnDelete ? 26 : 12)
            .padding(.vertical, 11)

            if showColumnDelete {
                Button {
                    updateTable { $0.deleteColumn(key.column) }
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(colors.rowDeleteIdle)
                        .frame(width: 20, height: 20)
                }
                .buttonStyle(.plain)
                .padding(5)
                .accessibilityLabel("Delete column \(key.column + 1)")
            }
        }
    }

    private func trailingCell(row: Int, colors: TableColors, canMutate: Bool, isHeaderRow: Bool) -> some View {
        ZStack {
            colors.trailingBackground
            if !isHeaderRow && canMutate {
                Button {
                    updateTable { $0.deleteRow(row) }
                } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 13))
                        .foregroundStyle(colors.rowDeleteIdle)
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Delete row \(row)")
            }
        }
        .frame(width: trailingColumnWidth)
        .frame(maxHeight: .infinity)
    }

    private func footer(colors: TableColors, canMutate: Bool) -> some View {
        HStack(spacing: 8) {
            addPill(label: "Add row", colors: colors, canMutate: canMutate) {
                updateTable { $0.addRow() }
            }
            addPill(label: "Add column", colors: colors, canMutate: canMutate) {
                updateTable { $0.addColumn() }
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 16)
    }

    private func addPill(
        label: String,
        colors: TableColors,
        canMutate: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: "plus")
                    .font(.system(size: 10, weight: .semibold))
                Text(label)
                    .font(.system(size: 13, weight: .semibold))
            }
            .foregroundStyle(colors.accent)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(colors.pillBackground)
            .clipShape(Capsule())
            .opacity(canMutate ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!canMutate)
        .accessibilityLabel(label)
    }

    /// Commits a single cell draft (if it changed) as one payload replacement.
    private func commitDraft(for key: TableCellKey) {
        guard let draft = drafts.removeValue(forKey: key) else { return }
        var table = TableModel.fromPayload(model.context.payloadJson)
        guard table.value(row: key.row, column: key.column) != draft else { return }
        table.editCell(row: key.row, column: key.column, value: draft)
        _ = model.context.replacePayloadJson(payloadJson: table.payloadJson())
    }

    /// Applies a structural transform on top of the current payload, folding
    /// pending cell drafts in first so an in-flight edit is not dropped.
    private func updateTable(_ transform: (inout TableModel) -> Void) {
        var table = TableModel.fromPayload(model.context.payloadJson)
        for (key, value) in drafts {
            table.editCell(row: key.row, column: key.column, value: value)
        }
        drafts.removeAll()
        transform(&table)
        _ = model.context.replacePayloadJson(payloadJson: table.payloadJson())
    }
}
