import SwiftUI

/// Read-only metric card block: value, label, and a trend badge.
struct MetricBlockView: View {
    @ObservedObject var model: BlockContextModel

    var body: some View {
        let payload = PayloadJson.object(from: model.context.payloadJson)
        let value = payload["value"] as? String ?? "0"
        let label = payload["label"] as? String ?? ""
        let trend = payload["trend"] as? String ?? "up"
        let trendValue = payload["trendValue"] as? String ?? ""
        let isDark = model.context.isDark
        let trendColor = trend == "up" ? Color(rgb: 0x34A853) : Color(rgb: 0xEA4335)

        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 4) {
                Text(value)
                    .font(.system(size: 32, weight: .bold))
                    .foregroundStyle(isDark ? Color(rgb: 0xF4F1FB) : Color(rgb: 0x1C1238))
                Text(label)
                    .font(.system(size: 14))
                    .foregroundStyle(isDark ? Color(rgb: 0x9B93B8) : Color(rgb: 0x6B6580))
            }
            Spacer()
            if !trendValue.isEmpty {
                Text("\(trend == "up" ? "↑" : "↓") \(trendValue)")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(trendColor)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(trendColor.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 6))
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(isDark ? Color(rgb: 0x2A2340) : Color(rgb: 0xF0EAFB))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
