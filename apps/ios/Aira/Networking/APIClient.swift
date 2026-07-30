import Foundation

struct APIError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// Minimal async client for the Aira backend. Anonymous device-token auth
/// (Bearer), stored in UserDefaults — the same anonymous-first model as the web
/// and Android clients. Base URL is `http://127.0.0.1:8000` unless the scheme
/// sets `AIRA_API_BASE` (needed when running on a physical device).
final class APIClient {
    static let shared = APIClient()

    let baseURL: String
    private let tokenKey = "aira_session_token"
    private let session = URLSession.shared
    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()

    init() {
        baseURL = ProcessInfo.processInfo.environment["AIRA_API_BASE"] ?? "http://127.0.0.1:8000"
    }

    // MARK: - Session token

    private var token: String? {
        get { UserDefaults.standard.string(forKey: tokenKey) }
        set {
            if let newValue { UserDefaults.standard.set(newValue, forKey: tokenKey) }
            else { UserDefaults.standard.removeObject(forKey: tokenKey) }
        }
    }

    func signOut() { token = nil }

    @discardableResult
    func ensureToken() async throws -> String {
        if let t = token { return t }
        let data = try await send("POST", "/device/register", body: nil, auth: false)
        let reg = try decoder.decode(DeviceRegister.self, from: data)
        token = reg.token
        return reg.token
    }

    // MARK: - Transport

    @discardableResult
    private func send(_ method: String, _ path: String, body: [String: Any]?, auth: Bool = true) async throws -> Data {
        guard let url = URL(string: baseURL + path) else { throw APIError(message: "bad url") }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if auth {
            let t = try await ensureToken()
            req.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let (data, resp) = try await session.data(for: req)
        guard let http = resp as? HTTPURLResponse else { throw APIError(message: "no response") }
        if http.statusCode == 401 { token = nil }
        guard (200..<300).contains(http.statusCode) else {
            let detail = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            throw APIError(message: (detail?["detail"] as? String) ?? "HTTP \(http.statusCode)")
        }
        return data
    }

    private func get<T: Decodable>(_ path: String) async throws -> T {
        try decoder.decode(T.self, from: await send("GET", path, body: nil))
    }
    private func post<T: Decodable>(_ path: String, _ body: [String: Any]? = nil) async throws -> T {
        try decoder.decode(T.self, from: await send("POST", path, body: body))
    }

    // MARK: - Identity

    func me() async throws -> User {
        let r: MeResponse = try await get("/account/me")
        return r.user
    }

    func updateProfile(name: String? = nil, journey: String? = nil, language: String? = nil) async throws -> User {
        var body: [String: Any] = [:]
        if let name { body["name"] = name }
        if let journey { body["journey"] = journey }
        if let language { body["language"] = language }
        let data = try await send("PATCH", "/account/profile", body: body)
        return try decoder.decode(MeResponse.self, from: data).user
    }

    func onboarding(journey: String, name: String?, language: String,
                    priorities: [String], weeks: Int?) async throws -> TodayData {
        var body: [String: Any] = ["journey": journey, "language": language, "priorities": priorities]
        if let name, !name.isEmpty { body["name"] = name }
        if let weeks { body["weeks"] = weeks }
        return try await post("/v1/onboarding", body)
    }

    func deleteAccount() async throws {
        _ = try await send("POST", "/v1/account/delete", body: ["confirm": "DELETE MY DATA"])
    }

    // MARK: - Journey-aware data

    func today() async throws -> TodayData { try await get("/v1/today") }
    func journey() async throws -> JourneyData { try await get("/v1/journey") }
    func care() async throws -> CareData { try await get("/v1/care") }

    func timeline() async throws -> [CareItem] {
        let r: CareItemsResponse = try await get("/v1/care/timeline")
        return r.items
    }

    // MARK: - Chat

    func chatTurn(message: String, history: [[String: String]]) async throws -> ChatTurnResponse {
        try await post("/v1/chat/turn", ["message": message, "history": history])
    }

    func chatHistory(limit: Int = 50) async throws -> [ChatHistoryItem] {
        let r: ChatHistoryResponse = try await get("/v1/chat/history?limit=\(limit)")
        return r.items
    }

    // MARK: - Care tools

    func addReminder(title: String, time: String?, repeatText: String?) async throws {
        var body: [String: Any] = ["title": title]
        if let time { body["time"] = time }
        if let repeatText { body["repeat"] = repeatText }
        _ = try await send("POST", "/v1/care/reminders", body: body)
    }
    func addMedicine(name: String, dose: String?, schedule: String?, time: String?) async throws {
        var body: [String: Any] = ["name": name]
        if let dose { body["dose"] = dose }
        if let schedule { body["schedule"] = schedule }
        if let time { body["time"] = time }
        _ = try await send("POST", "/v1/care/medicines", body: body)
    }
    func addAppointment(doctor: String, place: String?, when: String?) async throws {
        var body: [String: Any] = ["doctor": doctor]
        if let place { body["place"] = place }
        if let when { body["when"] = when }
        _ = try await send("POST", "/v1/care/appointments", body: body)
    }
    func addCheckin(feeling: String?, sleepHours: Double?, note: String?) async throws {
        var body: [String: Any] = [:]
        if let feeling { body["feeling"] = feeling }
        if let sleepHours { body["sleep_hours"] = sleepHours }
        if let note { body["note"] = note }
        _ = try await send("POST", "/v1/care/checkin", body: body)
    }
    func addSymptom(what: String, severity: String?, started: String?) async throws {
        var body: [String: Any] = ["what": what]
        if let severity { body["severity"] = severity }
        if let started { body["started"] = started }
        _ = try await send("POST", "/v1/care/symptom", body: body)
    }
    func markMedicineTaken(_ id: String) async throws {
        _ = try await send("POST", "/v1/care/medicines/\(id)/taken", body: nil)
    }
    func setReminderDone(_ id: String, done: Bool) async throws {
        _ = try await send("POST", "/v1/care/reminders/\(id)/done", body: ["done": done])
    }

    // MARK: - Videos (Learn)

    func videos() async throws -> VideosResponse { try await get("/v1/videos") }
    func saveVideo(_ id: String) async throws {
        _ = try await send("POST", "/v1/videos/\(id)/save", body: nil)
    }
    func unsaveVideo(_ id: String) async throws {
        _ = try await send("DELETE", "/v1/videos/\(id)/save", body: nil)
    }

    // MARK: - Consent

    func consent() async throws -> [ConsentFeature] {
        let r: ConsentResponse = try await get("/v1/consent")
        return r.features
    }
    func setConsent(_ feature: String, granted: Bool) async throws -> [ConsentFeature] {
        let r: ConsentResponse = try await post("/v1/consent", ["feature": feature, "granted": granted])
        return r.features
    }
}
