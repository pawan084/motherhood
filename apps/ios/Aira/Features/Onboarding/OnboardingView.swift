import SwiftUI

/// Onboarding — the chat-led questions, one decision per step: journey, the
/// basics, then priorities. Mirrors the other clients' three-step flow.
struct OnboardingView: View {
    @EnvironmentObject var state: AppState
    @State private var step = 0
    @State private var journey = ""
    @State private var name = ""
    @State private var language = "English"
    @State private var weeks = ""
    @State private var goals: [String] = []
    @State private var busy = false

    private let journeys: [(value: String, label: String, detail: String)] = [
        ("trying", "Trying to conceive", "Planning and preconception support"),
        ("pregnant", "Pregnant", "Week-by-week maternal guidance"),
        ("postpartum", "Postpartum", "Recovery and newborn rhythm"),
        ("exploring", "Exploring", "Look around before deciding"),
    ]
    private let priorities = ["Better sleep", "Less overwhelm", "Nutrition",
                              "Movement", "Medicine routine", "Visit preparation"]
    private let cols = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack(spacing: 6) {
                    ForEach(0..<3, id: \.self) { i in
                        Capsule().fill(i <= step ? Color.airaAubergine : Color.airaLine).frame(height: 4)
                    }
                }
                if step == 0 { journeyStep }
                else if step == 1 { basicsStep }
                else { prioritiesStep }
            }
            .padding(20)
        }
        .background(Color.airaIvory.ignoresSafeArea())
    }

    @ViewBuilder private var journeyStep: some View {
        SectionLabel(text: "1 of 3 · Your journey", color: .airaAubergine)
        Text("Where are you right now?").font(.airaSerif(30)).foregroundColor(.airaInk)
        Text("Choose a starting point. Nothing here is a diagnosis — you can change it any time.")
            .font(.system(size: 15)).foregroundColor(.airaMuted)
        ForEach(journeys, id: \.value) { j in
            Button { journey = j.value; step = 1 } label: {
                AiraCard {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(j.label).font(.system(size: 15, weight: .semibold)).foregroundColor(.airaInk)
                            Text(j.detail).font(.system(size: 13)).foregroundColor(.airaMuted)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundColor(.airaMuted)
                    }
                }
            }.buttonStyle(.plain)
        }
    }

    @ViewBuilder private var basicsStep: some View {
        SectionLabel(text: "2 of 3 · The basics", color: .airaAubergine)
        Text("A little about you.").font(.airaSerif(30)).foregroundColor(.airaInk)
        AiraCard {
            VStack(alignment: .leading, spacing: 12) {
                field("What should Aira call you?") { TextField("Optional", text: $name) }
                if journey == "pregnant" {
                    field("How many weeks are you?") {
                        TextField("e.g. 24", text: $weeks).keyboardType(.numberPad)
                    }
                }
            }
        }
        HStack(spacing: 10) {
            PrimaryButton(label: "Continue") { step = 2 }
            GhostButton(label: "Back") { step = 0 }
        }
    }

    @ViewBuilder private var prioritiesStep: some View {
        SectionLabel(text: "3 of 3 · Your priorities", color: .airaAubergine)
        Text("What would feel most helpful?").font(.airaSerif(30)).foregroundColor(.airaInk)
        Text("Choose up to three. Aira keeps Today focused on them.")
            .font(.system(size: 15)).foregroundColor(.airaMuted)
        LazyVGrid(columns: cols, spacing: 8) {
            ForEach(priorities, id: \.self) { p in
                let on = goals.contains(p)
                Button { toggleGoal(p) } label: {
                    Text(p).font(.system(size: 14))
                        .frame(maxWidth: .infinity, alignment: .leading).padding(12)
                        .background(on ? Color.airaLilacSoft : Color.airaPaper)
                        .foregroundColor(on ? .airaAubergine : .airaInk)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? Color.airaAubergine : Color.airaLine))
                }.buttonStyle(.plain)
            }
        }
        PrimaryButton(label: busy ? "Setting up…" : "Open my Today") { finish() }
            .disabled(busy)
    }

    @ViewBuilder private func field<Content: View>(_ label: String,
                                                   @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label).font(.system(size: 13, weight: .semibold)).foregroundColor(.airaInk)
            content()
                .padding(10)
                .background(Color.airaPaper)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.airaLine))
        }
    }

    private func toggleGoal(_ p: String) {
        if goals.contains(p) { goals.removeAll { $0 == p } }
        else if goals.count < 3 { goals.append(p) }
    }

    private func finish() {
        busy = true
        Task {
            await state.completeOnboarding(
                journey: journey.isEmpty ? "exploring" : journey,
                name: name, language: language, priorities: goals, weeks: Int(weeks))
            busy = false
        }
    }
}
