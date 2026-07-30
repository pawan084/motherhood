import Foundation

// Backend JSON is snake_case; the shared decoder uses `.convertFromSnakeCase`, so
// every property below is camelCase and maps automatically (context_line ->
// contextLine, week_video -> weekVideo, and so on). The one exception is a JSON
// key that is a Swift keyword (`repeat`), handled with explicit CodingKeys.

struct DeviceRegister: Decodable {
    let userId: String
    let token: String
    let kind: String
}

struct User: Decodable, Identifiable {
    let id: String
    let kind: String
    let email: String?
    let name: String
    let journey: String
    let language: String
    let onboarded: Bool
}
struct MeResponse: Decodable { let user: User }

// MARK: - Today

struct NextAction: Decodable {
    let tool: String
    let title: String
    let detail: String
    let minutes: Int?
}

struct TodayData: Decodable {
    let name: String
    let journey: String
    let contextLine: String
    let weeks: Int?
    let nextAction: NextAction?
    let priorities: [String]
}

// MARK: - Journey

struct JourneySection: Decodable, Identifiable {
    var id: String { title }
    let title: String
    let text: String
}

struct JourneyData: Decodable {
    let journey: String
    let title: String
    let weeks: Int?
    let thisWeek: String
    let body: String
    let sections: [JourneySection]
}

// MARK: - Care

struct CarePlan: Decodable {
    let total: Int
    let onTrack: Int
}

struct CareData: Decodable {
    let appointments: [CareItem]
    let medicinesDue: [CareItem]
    let reminders: [CareItem]
    let documentsCount: Int
    let carePlan: CarePlan
}

/// One care row. Fields vary by `kind`, so all but id/kind are optional.
struct CareItem: Decodable, Identifiable {
    let id: String
    let kind: String
    let name: String?
    let dose: String?
    let schedule: String?
    let time: String?
    let doctor: String?
    let place: String?
    let when: String?
    let title: String?
    let repeatText: String?
    let done: Bool?
    // check-in / symptom
    let feeling: String?
    let sleepHours: Double?
    let note: String?
    let what: String?
    let severity: String?
    let started: String?

    enum CodingKeys: String, CodingKey {
        case id, kind, name, dose, schedule, time, doctor, place, when, title, done
        case feeling, note, what, severity, started
        case sleepHours           // matches "sleep_hours" after snake-case conversion
        case repeatText = "repeat" // "repeat" is unchanged by the converter; a Swift keyword
    }
}
struct CareItemsResponse: Decodable { let items: [CareItem] }

// MARK: - Chat

struct SafetyInfo: Decodable {
    let level: String
    let categories: [String]
    let degraded: Bool
}

struct ActionCard: Decodable {
    let tool: String
    let title: String
    let detail: String
}

struct Contact: Decodable {
    let name: String?
    let phone: String?
}

struct UrgentHelp: Decodable {
    let headline: String
    let message: String
    let careTeam: Contact?
    let emergencyContact: Contact?
    let showEmergencyServices: Bool?
}

struct ChatTurnResponse: Decodable {
    let safety: SafetyInfo
    let urgent: Bool
    let urgentHelp: UrgentHelp?
    let reply: String?
    let trustLabel: String?
    let actionCard: ActionCard?
    let disclaimerNeeded: Bool?
    let degradedLlm: Bool?
}

struct ChatHistoryItem: Decodable, Identifiable {
    var id: String { "\(role)-\(ts)-\(text.hashValue)" }
    let role: String
    let text: String
    let ts: Double
}
struct ChatHistoryResponse: Decodable { let items: [ChatHistoryItem] }

// MARK: - Consent

struct ConsentFeature: Decodable, Identifiable {
    var id: String { key }
    let key: String
    let label: String
    let granted: Bool
    let locked: Bool
    let available: Bool
}
struct ConsentResponse: Decodable { let features: [ConsentFeature] }

// MARK: - Videos (Learn)

struct VideoTiming: Decodable {
    let type: String
    let startWeek: Int?
    let endWeek: Int?
}

struct VideoDuration: Decodable {
    let minSeconds: Int
    let maxSeconds: Int
}

struct VideoTopic: Decodable, Identifiable {
    let id: String
    let slug: String
    let title: String
    let category: String
    let categoryLabel: String
    let journeys: [String]
    let timing: VideoTiming
    let contentFormat: String
    let duration: VideoDuration
    let description: String
    let safetyLevel: String
    let inAppActions: [String]
    let languages: [String]
    let status: String
    let playable: Bool
    let saved: Bool?
}

struct VideoCategory: Decodable, Identifiable {
    var id: String { key }
    let key: String
    let label: String
}

struct VideosResponse: Decodable {
    let items: [VideoTopic]
    let weekVideo: VideoTopic?
    let categories: [VideoCategory]
    let savedIds: [String]
}
