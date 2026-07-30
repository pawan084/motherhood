import SwiftUI

/// Journey — journey-aware content from /v1/journey. A postpartum user never sees
/// fetal-week cards; nothing here guesses at the reader's stage.
struct JourneyView: View {
    @EnvironmentObject var state: AppState

    var body: some View {
        let j = state.journeyData
        ScreenScaffold(
            title: j?.title ?? "Your journey",
            subtitle: j?.weeks.map { "Week \($0) of about 40" } ?? journeyLabel(j?.journey)
        ) {
            AiraCard(background: .airaLilacSoft) {
                VStack(alignment: .leading, spacing: 6) {
                    SectionLabel(text: "This week", color: .airaAubergine)
                    Text((j?.thisWeek.isEmpty == false) ? j!.thisWeek : "Where you are now")
                        .font(.airaSerif(22)).foregroundColor(.airaInk)
                    if let body = j?.body, !body.isEmpty {
                        Text(body).font(.system(size: 14)).foregroundColor(.airaMuted)
                    }
                }
            }

            ForEach(j?.sections ?? []) { s in
                AiraCard {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(s.title).font(.system(size: 17, weight: .medium)).foregroundColor(.airaInk)
                        Text(s.text).font(.system(size: 14)).foregroundColor(.airaMuted)
                    }
                }
            }

            Text("Aira is wellness support, not diagnosis or emergency care.")
                .font(.system(size: 13)).foregroundColor(.airaMuted)
        }
        .task { if state.journeyData == nil { await state.refresh() } }
    }
}
