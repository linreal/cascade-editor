import SwiftUI

/// Read-only color-palette block: named swatch row with hex labels.
struct PaletteBlockView: View {
    @ObservedObject var model: BlockContextModel

    var body: some View {
        let payload = PayloadJson.object(from: model.context.payloadJson)
        let name = payload["name"] as? String ?? "Palette"
        let colorsValue = payload["colors"] as? String ?? ""
        let hexes = colorsValue
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "#", with: "") }
            .filter { !$0.isEmpty }
        let isDark = model.context.isDark

        VStack(alignment: .leading, spacing: 12) {
            Text(name)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(isDark ? Color(rgb: 0xF4F1FB) : Color(rgb: 0x1C1238))
            HStack {
                ForEach(hexes, id: \.self) { hex in
                    VStack(spacing: 4) {
                        Circle()
                            .fill(Color(rgb: UInt32(hex, radix: 16) ?? 0x888888))
                            .frame(width: 40, height: 40)
                        Text("#\(hex)")
                            .font(.system(size: 10))
                            .foregroundStyle(isDark ? Color(rgb: 0x9B93B8) : Color(rgb: 0x6B6580))
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(isDark ? Color(rgb: 0x2A2340) : Color(rgb: 0xF0EAFB))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
