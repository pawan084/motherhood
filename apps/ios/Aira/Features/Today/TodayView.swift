import SwiftUI

/// Today — the one job: the single next step Aira suggests, as a draft the user
/// decides on (suggest → confirm). Everything else is quieter below it.
struct TodayView: View {
    @EnvironmentObject var state: AppState
    @State private var dismissedStep = false
    @State private var showWhy = false
    @State private var addKind: CareKind?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                hero
                weekVideoCard
                alsoToday
            }
            .padding(18)
        }
        .background(Color.airaIvory.ignoresSafeArea())
        .task { if state.today == nil { await state.refresh() } }
        .sheet(item: $addKind) { AddCareSheet(kind: $0) }
    }

    private var context: String {
        let name = (state.today?.name ?? "").trimmingCharacters(in: .whitespaces)
        let label = journeyLabel(state.today?.journey)
        let stage = state.today?.weeks.map { "Week \($0) · \(label)" } ?? label
        return "\(timeGreeting())\(name.isEmpty ? "" : ", \(name)") · \(stage)"
    }

    // MARK: hero (the suggestion, on the signature aubergine)

    @ViewBuilder private var hero: some View {
        let action = state.today?.nextAction
        let hasStep = action != nil && !dismissedStep
        VStack(alignment: .leading, spacing: 12) {
            Text(context)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(Color(hex: 0xDAC7DD))

            if hasStep, let a = action {
                Pill(text: "✨ Aira suggests", bg: Color.white.opacity(0.14), fg: Color(hex: 0xF3E9F4))
                Text(a.title).font(.airaSerif(28)).foregroundColor(.white)
                Text(a.detail + (a.minutes.map { " · about \($0) min" } ?? ""))
                    .font(.system(size: 15)).foregroundColor(Color(hex: 0xECDFED))

                HStack(spacing: 10) {
                    Button { start(a) } label: {
                        HStack(spacing: 6) {
                            Text(actionPrimaryLabel(a.tool)); Image(systemName: "arrow.right")
                        }
                        .font(.system(size: 15, weight: .semibold))
                        .padding(.vertical, 12).padding(.horizontal, 18)
                        .background(Color.airaPaper).foregroundColor(.airaAubergine)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    Button { withAnimation { dismissedStep = true } } label: {
                        Text("Not now").font(.system(size: 15, weight: .semibold))
                            .padding(.vertical, 12).padding(.horizontal, 16)
                            .foregroundColor(Color(hex: 0xF3E9F4))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.25)))
                    }
                }

                Button { withAnimation { showWhy.toggle() } } label: {
                    Text(showWhy ? "Hide" : "Why this?")
                        .font(.system(size: 13, weight: .semibold)).underline()
                        .foregroundColor(Color(hex: 0xCDB6D0))
                }
                if showWhy {
                    Text(whyText).font(.system(size: 13)).foregroundColor(Color(hex: 0xE3D3E4))
                }
            } else {
                Pill(text: "All caught up", bg: Color.white.opacity(0.14), fg: Color(hex: 0xF3E9F4))
                Text("Nothing needs you right now.").font(.airaSerif(26)).foregroundColor(.white)
                Text("Aira will surface one thing — only when it matters. Until then, rest.")
                    .font(.system(size: 15)).foregroundColor(Color(hex: 0xECDFED))
            }
        }
        .padding(22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [Color(hex: 0x4D2052), Color(hex: 0x723779)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }

    private var whyText: String {
        if let p = state.today?.priorities, !p.isEmpty {
            return "You asked Aira to focus on \(p.joined(separator: ", ").lowercased()), so it starts there."
        }
        if let w = state.today?.weeks { return "Chosen for where you are right now — week \(w)." }
        return "A gentle place to start today. You're always the one who decides."
    }

    private func start(_ a: NextAction) {
        if let kind = CareKind.from(tool: a.tool) { addKind = kind }
        else { state.toast = "That opens on another screen soon." }
    }

    // MARK: your week video

    @ViewBuilder private var weekVideoCard: some View {
        if state.today?.journey == "pregnant", let wv = state.videos?.weekVideo {
            AiraCard(background: .airaLilacSoft) {
                VStack(alignment: .leading, spacing: 6) {
                    SectionLabel(text: "Your week with Aira · \(videoDurationLabel(wv))", color: .airaAubergine)
                    Text(wv.title).font(.system(size: 15, weight: .semibold)).foregroundColor(.airaInk)
                    Button { Task { await state.toggleSaveVideo(wv.id) } } label: {
                        Text(state.savedVideoIds.contains(wv.id) ? "Saved" : "Save for later")
                            .font(.system(size: 13, weight: .semibold)).foregroundColor(.airaAubergine)
                    }.padding(.top, 4)
                }
            }
        }
    }

    // MARK: also today

    @ViewBuilder private var alsoToday: some View {
        AiraCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionLabel(text: "Also today")
                Text("On your plate").font(.airaSerif(20)).foregroundColor(.airaInk)

                let meds = state.care?.medicinesDue ?? []
                let appts = Array((state.care?.appointments ?? []).prefix(2))
                let reminders = Array((state.care?.reminders ?? []).prefix(3))

                if meds.isEmpty && appts.isEmpty && reminders.isEmpty {
                    Text("Nothing scheduled yet. Add a reminder or an appointment and it will appear here.")
                        .font(.system(size: 14)).foregroundColor(.airaMuted)
                }
                ForEach(meds) { m in
                    row(kind: "Medicine", title: m.name ?? "Medicine",
                        subtitle: [m.dose, m.schedule, m.time].compactMap { $0 }.joined(separator: " · "),
                        action: "Mark taken") { Task { await state.markMedicineTaken(m.id) } }
                }
                ForEach(appts) { a in
                    row(kind: "Appointment", title: a.doctor ?? "Appointment",
                        subtitle: [a.place, a.when].compactMap { $0 }.joined(separator: " · "))
                }
                ForEach(reminders) { r in
                    row(kind: "Reminder", title: r.title ?? "Reminder",
                        subtitle: [r.time, r.repeatText].compactMap { $0 }.joined(separator: " · "),
                        action: (r.done ?? false) ? "Done" : "Mark done") {
                        Task { await state.setReminderDone(r.id, done: !(r.done ?? false)) }
                    }
                }
            }
        }
    }

    @ViewBuilder private func row(kind: String, title: String, subtitle: String,
                                  action: String? = nil, tap: (() -> Void)? = nil) -> some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 3) {
                SectionLabel(text: kind)
                Text(title).font(.system(size: 15, weight: .medium)).foregroundColor(.airaInk)
                if !subtitle.isEmpty {
                    Text(subtitle).font(.system(size: 13)).foregroundColor(.airaMuted)
                }
            }
            Spacer()
            if let action, let tap {
                Button(action: tap) {
                    Text(action).font(.system(size: 12, weight: .semibold)).foregroundColor(.airaAubergine)
                }
            }
        }
        .padding(.top, 6)
    }
}
