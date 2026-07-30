import SwiftUI

/// A soft card — the iOS take on the other clients' AiraCard / .panel.
struct AiraCard<Content: View>: View {
    var background: Color = .airaPaper
    @ViewBuilder var content: Content
    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.airaLine, lineWidth: 1))
    }
}

/// Small uppercase eyebrow / section label.
struct SectionLabel: View {
    let text: String
    var color: Color = .airaMuted
    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 11, weight: .heavy))
            .kerning(0.6)
            .foregroundColor(color)
    }
}

/// A pill / tag.
struct Pill: View {
    let text: String
    var bg: Color = .airaLilac
    var fg: Color = .airaAubergine
    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .padding(.horizontal, 9).padding(.vertical, 4)
            .background(bg).foregroundColor(fg)
            .clipShape(Capsule())
    }
}

/// Primary aubergine action button.
struct PrimaryButton: View {
    let label: String
    var systemImage: String? = "arrow.right"
    var fill: Bool = true
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Text(label).font(.system(size: 15, weight: .semibold))
                if let systemImage { Image(systemName: systemImage) }
            }
            .frame(maxWidth: fill ? .infinity : nil)
            .padding(.vertical, 13).padding(.horizontal, 18)
            .background(Color.airaAubergine)
            .foregroundColor(.airaPaper)
            .clipShape(RoundedRectangle(cornerRadius: 13))
        }
    }
}

/// Secondary / ghost button.
struct GhostButton: View {
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.system(size: 14, weight: .semibold))
                .padding(.vertical, 12).padding(.horizontal, 16)
                .frame(maxWidth: .infinity)
                .foregroundColor(.airaAubergine)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.airaLine, lineWidth: 1))
        }
    }
}

/// Ivory screen scaffold with a serif title + optional subtitle and scrolling body.
struct ScreenScaffold<Content: View>: View {
    let title: String
    var subtitle: String? = nil
    @ViewBuilder var content: Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.airaSerif(30)).foregroundColor(.airaInk)
                    if let subtitle {
                        Text(subtitle).font(.system(size: 15)).foregroundColor(.airaMuted)
                    }
                }
                content
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(Color.airaIvory.ignoresSafeArea())
    }
}
