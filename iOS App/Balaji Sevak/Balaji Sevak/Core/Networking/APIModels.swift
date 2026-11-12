import Foundation

nonisolated struct APIEnvelope<Value: Decodable>: Decodable { let success: Bool; let data: Value?; let error: APIError? }

nonisolated struct APIError: Decodable, Error, LocalizedError {
    let code: String?
    let message: String
    let details: [APIErrorDetail]?
    /// Some errors (Hanuman Chalisa) carry an object instead of a list, e.g. `{ "retryAfterSeconds": 12 }`.
    let retryAfterSeconds: Int?
    /// HTTP status of the response that carried this error; set by `APIClient`.
    var statusCode: Int?
    var errorDescription: String? { details?.first?.message ?? message }

    private enum CodingKeys: String, CodingKey { case code, message, details }
    private struct ObjectDetails: Decodable { let retryAfterSeconds: Int? }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        code = try container.decodeIfPresent(String.self, forKey: .code)
        message = try container.decode(String.self, forKey: .message)
        details = try? container.decodeIfPresent([APIErrorDetail].self, forKey: .details)
        retryAfterSeconds = (try? container.decodeIfPresent(ObjectDetails.self, forKey: .details))?.retryAfterSeconds
    }
}

nonisolated struct APIErrorDetail: Decodable { let field: String; let message: String }

nonisolated struct UserInfo: Codable, Equatable {
    let id: String; let mobile: String?; let name: String?; let email: String?; let role: String
    let isSuperUser: Bool; let adminType: String?; let capabilities: [String]; let isNewUser: Bool
}

nonisolated struct LoginData: Decodable { let accessToken: String; let refreshToken: String; let user: UserInfo }
nonisolated struct TokenData: Decodable { let accessToken: String; let refreshToken: String }
nonisolated struct LogoutData: Decodable { let message: String }
nonisolated struct DeleteAccountData: Decodable { let message: String }
nonisolated struct RefreshTokenRequest: Encodable { let refreshToken: String }
nonisolated struct GoogleSignInRequest: Encodable { let idToken: String }
nonisolated struct AppleSignInRequest: Encodable { let identityToken: String }

nonisolated struct EventListItem: Codable, Identifiable, Equatable {
    let id: String; let header: String; let subHeader: String; let eventDate: String; let status: String?; let imageURL: URL?; let createdAt: String?
    enum CodingKeys: String, CodingKey { case id, header, subHeader, eventDate, status, createdAt; case imageURL = "imageUrl" }
}

nonisolated struct CreateEventRequest: Encodable { let header: String; let subHeader: String; let eventDate: String }

nonisolated struct HelpContact: Codable, Identifiable, Equatable {
    let id: String
    let code: String
    let title: String
    let contactName: String
    let phone: String
}
