import SwiftUI

// The Aira palette, matching the web (globals.css :root) and Android (Color.kt)
// clients: ivory page, paper cards, aubergine primary, sage + lilac accents.
extension Color {
    init(hex: UInt) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: 1)
    }

    static let airaIvory      = Color(hex: 0xF8F4EE)
    static let airaPaper      = Color(hex: 0xFFFCF8)
    static let airaInk        = Color(hex: 0x211D20)
    static let airaMuted      = Color(hex: 0x6A636A)
    static let airaAubergine  = Color(hex: 0x4A234B)
    static let airaAubergine2 = Color(hex: 0x673967)
    static let airaLilac      = Color(hex: 0xE9DDEA)
    static let airaLilacSoft  = Color(hex: 0xF5EEF5)
    static let airaSage       = Color(hex: 0x8FA58E)
    static let airaSageSoft   = Color(hex: 0xE8EFE5)
    static let airaSageDeep   = Color(hex: 0x346147)
    static let airaLine       = Color(hex: 0xE6DDD9)
    static let airaUrgent     = Color(hex: 0xB7332D)
    static let airaUrgentMist = Color(hex: 0xFBEEEC)
    static let airaAmber      = Color(hex: 0x8E5C1D)
}

extension Font {
    /// Serif display to match the brand. System serif needs no bundled font file.
    static func airaSerif(_ size: CGFloat, _ weight: Font.Weight = .medium) -> Font {
        .system(size: size, weight: weight, design: .serif)
    }
}
