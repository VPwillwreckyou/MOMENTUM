import SwiftUI
import UIKit

extension Color {
    init(hex: String) {
        let cleaned = hex.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
        var value: UInt64 = 0
        Scanner(string: cleaned).scanHexInt64(&value)
        let red = Double((value >> 16) & 0xFF) / 255
        let green = Double((value >> 8) & 0xFF) / 255
        let blue = Double(value & 0xFF) / 255
        self.init(.sRGB, red: red, green: green, blue: blue, opacity: 1)
    }
}

extension UIColor {
    convenience init(hex: String) {
        let cleaned = hex.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
        var value: UInt64 = 0
        Scanner(string: cleaned).scanHexInt64(&value)
        self.init(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: 1
        )
    }
}

enum AppTheme {
    private static func adaptive(light: String, dark: String) -> Color {
        Color(uiColor: UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }

    // Matches apps/web/src/styles/tokens.css.
    static let background = adaptive(light: "#f3f5f9", dark: "#06080d")
    static let deep = adaptive(light: "#e9edf4", dark: "#04060a")
    static let surface = adaptive(light: "#ffffff", dark: "#0d121b")
    static let surfaceElevated = adaptive(light: "#f8fafc", dark: "#111825")
    static let surfaceRaised = adaptive(light: "#eef2f7", dark: "#16202f")
    static let primaryText = adaptive(light: "#0c1422", dark: "#eef3fb")
    static let secondaryText = adaptive(light: "#4c5b70", dark: "#a5b3c6")
    static let tertiaryText = adaptive(light: "#6f7c8f", dark: "#6c7b90")
    static let line = adaptive(light: "#e2e8f0", dark: "#ffffff14")

    static let success = Color(hex: "#34d399")
    static let warning = Color(hex: "#fbbf24")
    static let danger = Color(hex: "#fb7185")
    static let info = Color(hex: "#38bdf8")

    static func toneColor(_ tone: ProgressTone, accent: Color) -> Color {
        switch tone {
        case .success: return success
        case .accent: return accent
        case .warning: return warning
        case .danger: return danger
        }
    }

    static func accentGradient(_ accent: String, secondary: String) -> LinearGradient {
        LinearGradient(
            colors: [Color(hex: accent), Color(hex: secondary)],
            startPoint: .topLeading, endPoint: .bottomTrailing
        )
    }

    /// Mirrors the WCAG luminance rule in lib/appearance.ts.
    static func contrastText(for hex: String) -> Color {
        let cleaned = hex.replacingOccurrences(of: "#", with: "")
        var value: UInt64 = 0
        Scanner(string: cleaned).scanHexInt64(&value)
        let components = [
            Double((value >> 16) & 0xFF) / 255,
            Double((value >> 8) & 0xFF) / 255,
            Double(value & 0xFF) / 255,
        ].map { channel -> Double in
            channel <= 0.03928 ? channel / 12.92 : pow((channel + 0.055) / 1.055, 2.4)
        }
        let luminance = 0.2126 * components[0] + 0.7152 * components[1] + 0.0722 * components[2]
        return luminance > 0.55 ? Color(hex: "#071018") : .white
    }
}
