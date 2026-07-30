import SwiftUI

/// The single source of truth for the app — the iOS counterpart to the web
/// AiraApp store and the Android AiraViewModel. All network work is best-effort:
/// a failed refresh keeps the last good data rather than blanking the screen.
@MainActor
final class AppState: ObservableObject {
    @Published var user: User?
    @Published var today: TodayData?
    @Published var journeyData: JourneyData?
    @Published var care: CareData?
    @Published var timeline: [CareItem] = []
    @Published var videos: VideosResponse?
    @Published var savedVideoIds: Set<String> = []
    @Published var consent: [ConsentFeature] = []
    @Published var messages: [ChatMessage] = []

    @Published var booting = true
    @Published var onboarded = false
    @Published var degraded = false
    @Published var urgent: UrgentHelp?
    @Published var toast: String?

    private let api = APIClient.shared

    // MARK: - Boot

    func bootstrap() async {
        do {
            let u = try await api.me()
            user = u
            onboarded = u.onboarded
            if u.onboarded { await refresh() }
        } catch {
            onboarded = false
        }
        booting = false
    }

    func refresh() async {
        await load { self.today = try await self.api.today() }
        await load { self.care = try await self.api.care() }
        await load { self.journeyData = try await self.api.journey() }
        await load {
            let v = try await self.api.videos()
            self.videos = v
            self.savedVideoIds = Set(v.savedIds)
        }
        await load { self.timeline = try await self.api.timeline() }
    }

    private func load(_ block: () async throws -> Void) async {
        do { try await block() } catch { /* keep last good data */ }
    }

    // MARK: - Onboarding

    func completeOnboarding(journey: String, name: String, language: String,
                            priorities: [String], weeks: Int?) async {
        do {
            let t = try await api.onboarding(journey: journey, name: name, language: language,
                                             priorities: priorities, weeks: weeks)
            today = t
            onboarded = true
            user = try? await api.me()
            await refresh()
        } catch {
            toast = error.localizedDescription
        }
    }

    // MARK: - Chat

    func sendMessage(_ text: String) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        // History is the turns BEFORE this one.
        let history = messages.suffix(20).map {
            ["role": $0.role == .me ? "user" : "aira", "content": $0.text]
        }
        messages.append(ChatMessage(role: .me, text: trimmed))
        do {
            let r = try await api.chatTurn(message: trimmed, history: history)
            degraded = r.safety.degraded
            if r.urgent, let help = r.urgentHelp {
                urgent = help
                messages.append(ChatMessage(role: .aira,
                    text: "Let's get you to the right help — opening urgent support."))
            } else if let reply = r.reply {
                messages.append(ChatMessage(role: .aira, text: reply,
                    trustLabel: r.trustLabel, actionCard: r.actionCard))
            } else {
                messages.append(ChatMessage(role: .aira, text: "I'm here with you."))
            }
        } catch {
            if looksUrgentOffline(trimmed) {
                urgent = UrgentHelp(
                    headline: "This needs a person now",
                    message: "Aira can't reach the safety check right now. If this is an emergency, contact your care team or your local emergency number.",
                    careTeam: nil, emergencyContact: nil, showEmergencyServices: true)
            } else {
                messages.append(ChatMessage(role: .aira,
                    text: "I couldn't reach Aira just now — your message is saved."))
            }
        }
    }

    private func looksUrgentOffline(_ text: String) -> Bool {
        let t = text.lowercased()
        return ["heavy bleeding", "chest pain", "cant breathe", "can't breathe",
                "suicid", "isn't moving", "isnt moving", "seizure", "overdose"]
            .contains { t.contains($0) }
    }

    // MARK: - Care tools

    private func write(_ block: () async throws -> Void) async {
        do {
            try await block()
            await load { self.care = try await self.api.care() }
            await load { self.timeline = try await self.api.timeline() }
            toast = "Saved"
        } catch {
            toast = error.localizedDescription
        }
    }

    func addReminder(title: String, time: String?, repeatText: String?) async {
        await write { try await self.api.addReminder(title: title, time: time, repeatText: repeatText) }
    }
    func addMedicine(name: String, dose: String?, schedule: String?, time: String?) async {
        await write { try await self.api.addMedicine(name: name, dose: dose, schedule: schedule, time: time) }
    }
    func addAppointment(doctor: String, place: String?, when: String?) async {
        await write { try await self.api.addAppointment(doctor: doctor, place: place, when: when) }
    }
    func addCheckin(feeling: String?, sleepHours: Double?, note: String?) async {
        await write { try await self.api.addCheckin(feeling: feeling, sleepHours: sleepHours, note: note) }
    }
    func addSymptom(what: String, severity: String?, started: String?) async {
        await write { try await self.api.addSymptom(what: what, severity: severity, started: started) }
    }
    func markMedicineTaken(_ id: String) async {
        await write { try await self.api.markMedicineTaken(id) }
    }
    func setReminderDone(_ id: String, done: Bool) async {
        await write { try await self.api.setReminderDone(id, done: done) }
    }

    // MARK: - Videos (Learn)

    func toggleSaveVideo(_ id: String) async {
        let wasSaved = savedVideoIds.contains(id)
        if wasSaved { savedVideoIds.remove(id) } else { savedVideoIds.insert(id) }
        do {
            if wasSaved { try await api.unsaveVideo(id) } else { try await api.saveVideo(id) }
        } catch {
            // Roll back so the star never lies about the server.
            if wasSaved { savedVideoIds.insert(id) } else { savedVideoIds.remove(id) }
        }
    }

    // MARK: - Consent / account

    func loadConsent() async { await load { self.consent = try await self.api.consent() } }
    func setConsent(_ feature: String, granted: Bool) async {
        do { consent = try await api.setConsent(feature, granted: granted) }
        catch { toast = error.localizedDescription }
    }

    func signOut() {
        api.signOut()
        user = nil; onboarded = false
        today = nil; care = nil; journeyData = nil; videos = nil
        messages = []; savedVideoIds = []
    }

    func deleteAccount() async {
        try? await api.deleteAccount()
        signOut()
    }
}

// A urgent handoff is presented as a sheet, so it needs an identity.
extension UrgentHelp: Identifiable {
    public var id: String { headline + message }
}

struct ChatMessage: Identifiable {
    enum Role { case me, aira }
    let id = UUID()
    let role: Role
    let text: String
    var trustLabel: String? = nil
    var actionCard: ActionCard? = nil
}
