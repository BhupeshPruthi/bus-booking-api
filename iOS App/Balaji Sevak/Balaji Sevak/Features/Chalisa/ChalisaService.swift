import Foundation

/// A failed Chalisa call, with the server's error code so screens can react to it.
nonisolated struct ChalisaError: LocalizedError {
    let code: String?
    let message: String
    var retryAfterSeconds: Int?
    var httpStatus: Int?
    var errorDescription: String? { message }

    /// The server (or a proxy in front of it) may have saved the request even though it failed:
    /// no connection, a timeout, a 5xx, or an answer that isn't the API's own error.
    var outcomeUnknown: Bool {
        code == ChalisaErrorCode.network || code == nil || httpStatus == 408 || (httpStatus ?? 0) >= 500
    }
}

nonisolated enum ChalisaErrorCode {
    static let roundTooSoon = "ROUND_TOO_SOON"
    static let nameRequired = "NAME_REQUIRED"
    static let invalidGuestToken = "INVALID_GUEST_TOKEN"
    static let startingCountNotAllowed = "STARTING_COUNT_NOT_ALLOWED"
    static let marathonNotStarted = "MARATHON_NOT_STARTED"
    static let marathonNotRunning = "MARATHON_NOT_RUNNING"
    static let marathonAlreadyActive = "MARATHON_ALREADY_ACTIVE"
    static let undoExpired = "UNDO_EXPIRED"
    static let restoreExpired = "RESTORE_EXPIRED"
    static let validation = "VALIDATION_ERROR"
    static let network = "NETWORK"
}

/// Hanuman Chalisa API calls (Android `ChalisaRepository`). Signed-in users are counted under
/// their account; everyone else sends the guest pass stored on this phone.
@MainActor final class ChalisaService {
    private let appState: AppState
    private static let guestHeader = "X-Chalisa-Guest"

    init(appState: AppState) { self.appState = appState }

    // MARK: - Reading

    func summary() async throws -> ChalisaSummary {
        try await request("Couldn't load the Hanuman Chalisa Path") { api, token, headers in
            try await api.get("chalisa/summary", accessToken: token, headers: headers)
        }
    }

    func board() async throws -> ChalisaBoard {
        try await request("Couldn't load the Path board") { api, token, headers in
            try await api.get("chalisa/marathon?limit=100&offset=0", accessToken: token, headers: headers)
        }
    }

    func myStats(month: String? = nil) async throws -> ChalisaMyStats {
        let path = month.map { "chalisa/me?month=\($0)" } ?? "chalisa/me"
        return try await request("Couldn't load your readings") { api, token, headers in
            try await api.get(path, accessToken: token, headers: headers)
        }
    }

    func archive() async throws -> [ChalisaArchivedMarathon] {
        try await request("Couldn't load past Paths") { api, token, headers in
            try await api.get("chalisa/marathons/archive?limit=20", accessToken: token, headers: headers)
        }
    }

    // MARK: - Counting

    /// Saves the name on the board. A new guest's pass is stored on this phone.
    func join(displayName: String) async throws -> ChalisaParticipant {
        let body = ChalisaParticipantRequest(displayName: displayName.trimmingCharacters(in: .whitespacesAndNewlines))
        let result: ChalisaParticipantResult = try await request("Couldn't save your name") { api, token, headers in
            try await api.put("chalisa/participant", body: body, accessToken: token, headers: headers)
        }
        if let guestToken = result.guestToken { ChalisaStore.saveGuestToken(guestToken) }
        return result.participant
    }

    /// Counts one round. Each tap gets a new id, except after a lost answer: then the server may
    /// already have counted it, so the id is reused until a definite answer arrives (the server
    /// treats a repeated id as the same round).
    func addRound() async throws -> ChalisaCounts {
        let pending = ChalisaStore.pendingRoundID
        do {
            return try await sendRound(pending ?? UUID().uuidString.lowercased())
        } catch let error as ChalisaError where pending != nil && error.code == ChalisaErrorCode.validation && error.httpStatus == 400 {
            // The pending id belongs to someone else (another account signed in on this phone
            // since): it can't be this person's round, so send it as a new one.
            ChalisaStore.setPendingRoundID(nil)
            return try await sendRound(UUID().uuidString.lowercased())
        }
    }

    private func sendRound(_ clientRequestId: String) async throws -> ChalisaCounts {
        ChalisaStore.setPendingRoundID(clientRequestId)
        do {
            let counts: ChalisaCounts = try await request("Couldn't count this round") { api, token, headers in
                try await api.post("chalisa/readings", body: ChalisaReadingRequest(clientRequestId: clientRequestId), accessToken: token, headers: headers)
            }
            ChalisaStore.setPendingRoundID(nil)
            return counts
        } catch let error as ChalisaError {
            if !error.outcomeUnknown { ChalisaStore.setPendingRoundID(nil) }
            throw error
        }
    }

    /// Admin only: adds `count` rounds to the admin's own entry in the running Path.
    func addAdminRounds(_ count: Int) async throws -> ChalisaCounts {
        let body = ChalisaAdminReadingRequest(clientRequestId: UUID().uuidString.lowercased(), count: count)
        return try await request("Couldn't add the rounds") { api, token, headers in
            try await api.post("admin/chalisa/readings", body: body, accessToken: token, headers: headers)
        }
    }

    func addStartingCount(_ count: Int) async throws -> ChalisaCounts {
        let body = ChalisaReadingRequest(clientRequestId: UUID().uuidString.lowercased(), startingCount: count)
        return try await request("Couldn't add your starting count") { api, token, headers in
            try await api.post("chalisa/readings", body: body, accessToken: token, headers: headers)
        }
    }

    func undo(_ clientRequestId: String) async throws -> ChalisaCounts {
        try await request("Couldn't undo this round") { api, token, headers in
            try await api.delete("chalisa/readings/\(clientRequestId)", accessToken: token, headers: headers)
        }
    }

    // MARK: - Super admin

    func schedule(title: String, startsAt: Date?, goal: Int?) async throws -> ChalisaMarathon {
        let body = ChalisaScheduleRequest(title: title.trimmingCharacters(in: .whitespacesAndNewlines), startsAt: startsAt.map(AppFormatters.isoString), goal: goal)
        let result: ChalisaMarathonResult = try await request("Couldn't schedule the Path") { api, token, headers in
            try await api.post("admin/chalisa/marathons", body: body, accessToken: token, headers: headers)
        }
        return result.marathon
    }

    func update(marathonID: String, title: String?, startsAt: Date?, goal: Int?) async throws -> ChalisaMarathon {
        let body = ChalisaUpdateRequest(title: title?.trimmingCharacters(in: .whitespacesAndNewlines), startsAt: startsAt.map(AppFormatters.isoString), goal: goal)
        let result: ChalisaMarathonResult = try await request("Couldn't save the changes") { api, token, headers in
            try await api.patch("admin/chalisa/marathons/\(marathonID)", body: body, accessToken: token, headers: headers)
        }
        return result.marathon
    }

    func cancel(marathonID: String) async throws {
        let _: ChalisaCancelResult = try await request("Couldn't cancel the Path") { api, token, headers in
            try await api.delete("admin/chalisa/marathons/\(marathonID)", accessToken: token, headers: headers)
        }
    }

    func end(marathonID: String, confirmText: String) async throws -> ChalisaArchivedMarathon {
        let body = ChalisaEndRequest(confirmText: confirmText.trimmingCharacters(in: .whitespacesAndNewlines))
        let result: ChalisaEndResult = try await request("Couldn't end the Path") { api, token, headers in
            try await api.post("admin/chalisa/marathons/\(marathonID)/end", body: body, accessToken: token, headers: headers)
        }
        return result.archived
    }

    func restore(marathonID: String) async throws -> ChalisaMarathon {
        let result: ChalisaMarathonResult = try await request("Couldn't undo ending the Path") { api, token, headers in
            try await api.post("admin/chalisa/marathons/\(marathonID)/restore", body: [String: String](), accessToken: token, headers: headers)
        }
        return result.marathon
    }

    // MARK: - Plumbing

    /// Runs a call with the sign-in token (refreshed once if it expired) and the guest pass,
    /// and turns every failure into a `ChalisaError` with a message ready to show.
    private func request<Value>(
        _ fallback: String,
        _ call: @escaping (APIClient, String?, [String: String]) async throws -> Value
    ) async throws -> Value {
        let api = appState.api
        do {
            return try await appState.authorized { token in
                // Read at call time: joining stores a new pass, and a refresh retries this.
                var headers: [String: String] = [:]
                if let guest = ChalisaStore.guestToken, !guest.isEmpty { headers[Self.guestHeader] = guest }
                return try await call(api, token, headers)
            }
        } catch {
            throw Self.chalisaError(from: error, fallback: fallback)
        }
    }

    private static func chalisaError(from error: Error, fallback: String) -> Error {
        if error is CancellationError || (error as? URLError)?.code == .cancelled { return error }
        if let apiError = error as? APIError {
            if apiError.code == ChalisaErrorCode.invalidGuestToken { ChalisaStore.clearGuestToken() }
            let message = apiError.message.trimmingCharacters(in: .whitespacesAndNewlines)
            let usable = !message.isEmpty && message.caseInsensitiveCompare("Internal server error") != .orderedSame
            return ChalisaError(
                code: apiError.code,
                message: usable ? message : "\(fallback). Please try again.",
                retryAfterSeconds: apiError.retryAfterSeconds,
                httpStatus: apiError.statusCode
            )
        }
        if let urlError = error as? URLError {
            let message = urlError.code == .timedOut
                ? "The server is taking too long to respond. Please try again."
                : "Can't reach the server. Check your internet connection, or try Wi-Fi, and tap Retry."
            return ChalisaError(code: ChalisaErrorCode.network, message: message)
        }
        if let clientError = error as? APIClientError {
            switch clientError {
            case .httpStatus(let status): return ChalisaError(code: nil, message: "\(fallback). Please try again.", httpStatus: status)
            case .unauthorized: return ChalisaError(code: nil, message: clientError.localizedDescription, httpStatus: 401)
            case .invalidURL, .invalidResponse: break
            }
        }
        return ChalisaError(code: nil, message: "\(fallback). Please try again.")
    }
}
