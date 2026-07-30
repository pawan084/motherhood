import SwiftUI

/// Care — appointments, medicines, reminders and the check-in/symptom timeline.
struct CareView: View {
    @EnvironmentObject var state: AppState
    @State private var addKind: CareKind?

    private let cols = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ScreenScaffold(title: "Care", subtitle: "Appointments, medicines, reminders and your timeline.") {
            AiraCard {
                VStack(alignment: .leading, spacing: 10) {
                    SectionLabel(text: "Add")
                    LazyVGrid(columns: cols, spacing: 8) {
                        addButton("Reminder", "bell", .reminder)
                        addButton("Medicine", "pills", .medicine)
                        addButton("Appointment", "calendar", .appointment)
                        addButton("Check-in", "heart", .checkin)
                        addButton("Symptom", "waveform.path.ecg", .symptom)
                    }
                }
            }

            listCard("Medicines due", (state.care?.medicinesDue ?? []),
                     titleFor: { $0.name ?? "Medicine" },
                     subFor: { [$0.dose, $0.schedule, $0.time] })
            listCard("Appointments", (state.care?.appointments ?? []),
                     titleFor: { $0.doctor ?? "Appointment" },
                     subFor: { [$0.place, $0.when] })
            listCard("Reminders", (state.care?.reminders ?? []),
                     titleFor: { $0.title ?? "Reminder" },
                     subFor: { [$0.time, $0.repeatText] })

            if !state.timeline.isEmpty {
                AiraCard {
                    VStack(alignment: .leading, spacing: 8) {
                        SectionLabel(text: "Timeline")
                        ForEach(state.timeline) { t in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(timelineTitle(t)).font(.system(size: 14, weight: .medium)).foregroundColor(.airaInk)
                                let sub = timelineSub(t)
                                if !sub.isEmpty { Text(sub).font(.system(size: 12)).foregroundColor(.airaMuted) }
                            }.padding(.vertical, 3)
                        }
                    }
                }
            }
        }
        .task { if state.care == nil { await state.refresh() } }
        .sheet(item: $addKind) { AddCareSheet(kind: $0) }
    }

    @ViewBuilder private func addButton(_ label: String, _ icon: String, _ kind: CareKind) -> some View {
        Button { addKind = kind } label: {
            HStack(spacing: 8) { Image(systemName: icon); Text(label) }
                .font(.system(size: 13, weight: .semibold)).foregroundColor(.airaAubergine)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(11)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.airaLine))
        }.buttonStyle(.plain)
    }

    @ViewBuilder private func listCard(_ title: String, _ items: [CareItem],
                                       titleFor: @escaping (CareItem) -> String,
                                       subFor: @escaping (CareItem) -> [String?]) -> some View {
        if !items.isEmpty {
            AiraCard {
                VStack(alignment: .leading, spacing: 8) {
                    SectionLabel(text: title)
                    ForEach(items) { it in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(titleFor(it)).font(.system(size: 15, weight: .medium)).foregroundColor(.airaInk)
                            let sub = subFor(it).compactMap { $0 }.joined(separator: " · ")
                            if !sub.isEmpty { Text(sub).font(.system(size: 13)).foregroundColor(.airaMuted) }
                        }.padding(.vertical, 3)
                    }
                }
            }
        }
    }

    private func timelineTitle(_ t: CareItem) -> String {
        switch t.kind {
        case "checkin": return t.feeling ?? "Check-in"
        case "symptom": return t.what ?? "Symptom"
        default: return t.title ?? t.name ?? t.kind.capitalized
        }
    }
    private func timelineSub(_ t: CareItem) -> String {
        switch t.kind {
        case "checkin":
            return [t.sleepHours.map { "\(Int($0))h sleep" }, t.note].compactMap { $0 }.joined(separator: " · ")
        case "symptom":
            return [t.severity, t.started].compactMap { $0 }.joined(separator: " · ")
        default:
            return ""
        }
    }
}
