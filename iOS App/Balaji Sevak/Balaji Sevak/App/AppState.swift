import Foundation
import Combine

@MainActor final class AppState: ObservableObject {
    /// The app's one session. Lock Screen counter buttons run in the background with no
    /// screen open, so they reach it here instead of through the SwiftUI environment.
    static let shared = AppState()

    let session = SessionStore()
    let api = APIClient()
    @Published var loginSheetPresented = false
    @Published var selectedTab = AppTab.home

    /// The single in-flight refresh. Refresh tokens rotate, so concurrent 401s
    /// must share one refresh instead of each spending the same token.
    private var refreshTask: Task<Void, Error>?
    private var cancellables = Set<AnyCancellable>()

    init() {
        // Views observe AppState only, so re-publish session changes (login,
        // logout, role) to keep role-gated UI in sync.
        session.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
        Task { await refreshSessionIfNeeded() }
    }

    // MARK: - Authenticated requests

    /// Runs an authenticated call. On an expired/invalid token it refreshes once
    /// and retries; if that fails the session is cleared and Login is shown.
    /// Use these for every endpoint that needs (or benefits from) the user's token.
    func authorized<Value>(_ call: (String?) async throws -> Value) async throws -> Value {
        do {
            return try await call(session.accessToken)
        } catch where error.isRefreshableAuthenticationError && session.refreshToken != nil {
            do {
                try await refreshSession()
            } catch {
                handleUnauthorized(error)
                throw error
            }
            do {
                return try await call(session.accessToken)
            } catch {
                handleUnauthorized(error)
                throw error
            }
        } catch {
            handleUnauthorized(error)
            throw error
        }
    }

    func authGet<Value: Decodable>(_ path: String) async throws -> Value {
        try await authorized { try await self.api.get(path, accessToken: $0) }
    }

    func authPost<Body: Encodable, Value: Decodable>(_ path: String, body: Body) async throws -> Value {
        try await authorized { try await self.api.post(path, body: body, accessToken: $0) }
    }

    func authPut<Body: Encodable, Value: Decodable>(_ path: String, body: Body) async throws -> Value {
        try await authorized { try await self.api.put(path, body: body, accessToken: $0) }
    }

    func authDelete<Value: Decodable>(_ path: String) async throws -> Value {
        try await authorized { try await self.api.delete(path, accessToken: $0) }
    }

    func authMultipart<Value: Decodable>(_ path: String, fields: [String: String], image: Data?) async throws -> Value {
        try await authorized { token in
            guard let token else { throw APIClientError.unauthorized }
            return try await self.api.postMultipart(path, fields: fields, image: image, accessToken: token)
        }
    }

    // MARK: - Session

    func handleUnauthorized(_ error: Error) {
        guard error.isRefreshableAuthenticationError, session.isLoggedIn else { return }
        session.clear(); loginSheetPresented = true
    }

    /// Exchanges the rotating refresh token for a fresh access token. Concurrent
    /// callers await the same request.
    func refreshSession() async throws {
        if let refreshTask { return try await refreshTask.value }
        let task = Task { try await performRefresh() }
        refreshTask = task
        defer { refreshTask = nil }
        try await task.value
    }

    private func performRefresh() async throws {
        guard let refreshToken = session.refreshToken, !refreshToken.isEmpty else {
            throw APIClientError.unauthorized
        }
        let tokens: TokenData = try await api.post(
            "auth/refresh-token",
            body: RefreshTokenRequest(refreshToken: refreshToken)
        )
        // Do not restore a session after the user explicitly logged out while
        // a background refresh request was still in flight.
        guard session.refreshToken == refreshToken else { throw APIClientError.unauthorized }
        try session.updateTokens(tokens)
    }

    /// Local sign-out is immediate; server invalidation is best-effort, matching
    /// Android's behaviour when the device is offline.
    func logout() async {
        let refreshToken = session.refreshToken
        session.clear()
        selectedTab = .home
        guard let refreshToken, !refreshToken.isEmpty else { return }
        _ = try? await api.post("auth/logout", body: RefreshTokenRequest(refreshToken: refreshToken)) as LogoutData
    }

    /// Permanently deletes the account on the server, then signs out here. The server has
    /// already removed every refresh token, so there is no auth/logout call.
    func deleteAccount() async throws {
        let _: DeleteAccountData = try await authDelete("auth/account")
        await ChalisaLockScreen.shared.removeAfterAccountDeleted()
        session.clear()
        selectedTab = .home
    }

    /// Refresh on launch only when the restored access token is already expired
    /// or will expire in the next minute. This avoids unnecessary token rotation.
    private func refreshSessionIfNeeded() async {
        guard session.isLoggedIn, accessTokenExpiresSoon else { return }
        do {
            try await refreshSession()
        } catch {
            // A connectivity failure should not sign the user out; an invalid
            // refresh token should return them to Login.
            handleUnauthorized(error)
        }
    }

    private var accessTokenExpiresSoon: Bool {
        guard let token = session.accessToken,
              let payloadSegment = token.split(separator: ".").dropFirst().first else { return true }
        var base64 = String(payloadSegment)
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        base64 += String(repeating: "=", count: (4 - base64.count % 4) % 4)
        guard let data = Data(base64Encoded: base64),
              let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let expiry = payload["exp"] as? TimeInterval else { return true }
        return Date(timeIntervalSince1970: expiry) <= Date().addingTimeInterval(60)
    }

    static var preview: AppState {
        let state = AppState()
        return state
    }
}

enum AppTab: Hashable { case home, bus, pooja, stay, help }

extension Error {
    /// True for errors that mean the access token is missing, expired or invalid.
    var isRefreshableAuthenticationError: Bool {
        if let clientError = self as? APIClientError, case .unauthorized = clientError { return true }
        if let error = self as? APIError { return ["TOKEN_EXPIRED", "INVALID_TOKEN", "UNAUTHORIZED"].contains(error.code) }
        return false
    }
}
