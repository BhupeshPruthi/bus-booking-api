import Foundation

// MARK: - API models (mirror Android `PoojaDto.kt`)

/// Item from `GET poojas` / `GET admin/poojas`.
struct PoojaListItem: Codable, Identifiable, Equatable {
    let id: String
    let scheduledAt: String
    let place: String
    let totalTokens: Int
    let bookedTokens: Int
    let availableTokens: Int
    let status: String
    var bookingStatus: String? = nil
    var bookingOpensAt: String? = nil
    var bookingClosesAt: String? = nil
    var canBook: Bool? = nil
    var createdAt: String? = nil

    var window: PoojaBookingWindow { PoojaBookingWindow(scheduledAt: scheduledAt, availableTokens: availableTokens, serverStatus: bookingStatus, serverCanBook: canBook) }
}

struct PoojaBookingUserInfo: Codable, Equatable {
    let mobile: String
    var name: String? = nil
}

/// A token request, as returned by booking and admin detail endpoints.
struct PoojaBookingData: Codable, Identifiable, Equatable {
    let id: String
    let poojaId: String
    var userId: String?
    let name: String
    let phone: String
    var memberCount: Int
    var city: String
    var tokenNumber: Int?
    let status: String
    var cancelledAt: String?
    var createdAt: String?
    var user: PoojaBookingUserInfo?

    enum CodingKeys: String, CodingKey { case id, poojaId, userId, name, phone, memberCount, city, tokenNumber, status, cancelledAt, createdAt, user }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        poojaId = try c.decodeIfPresent(String.self, forKey: .poojaId) ?? ""
        userId = try c.decodeIfPresent(String.self, forKey: .userId)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        phone = try c.decodeIfPresent(String.self, forKey: .phone) ?? ""
        memberCount = try c.decodeIfPresent(Int.self, forKey: .memberCount) ?? 1
        city = try c.decodeIfPresent(String.self, forKey: .city) ?? PoojaDefaults.city
        tokenNumber = try c.decodeIfPresent(Int.self, forKey: .tokenNumber)
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? ""
        cancelledAt = try c.decodeIfPresent(String.self, forKey: .cancelledAt)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        user = try? c.decodeIfPresent(PoojaBookingUserInfo.self, forKey: .user)
    }
}

/// `GET poojas/{id}` / `GET admin/poojas/{id}` (admin includes `bookings`).
struct PoojaDetailData: Codable, Identifiable, Equatable {
    let id: String
    let scheduledAt: String
    let place: String
    let totalTokens: Int
    let bookedTokens: Int
    let availableTokens: Int
    let status: String
    var bookingStatus: String? = nil
    var bookingOpensAt: String? = nil
    var bookingClosesAt: String? = nil
    var canBook: Bool? = nil
    var createdAt: String? = nil
    var bookings: [PoojaBookingData]? = nil

    var window: PoojaBookingWindow { PoojaBookingWindow(scheduledAt: scheduledAt, availableTokens: availableTokens, serverStatus: bookingStatus, serverCanBook: canBook) }
}

struct DeletePoojaResult: Codable {
    let id: String
    let status: String
    let deletionType: String
    var cancelledBookings: Int?
}

struct PoojaScheduleRequest: Encodable { let scheduledAt: String; let place: String; let totalTokens: Int }
struct BookPoojaRequest: Encodable { let name: String; let phone: String; let memberCount: Int; let city: String }
struct PoojaEmptyBody: Encodable {}

enum PoojaDefaults {
    static let city = "Delhi - NCR"
    static let place = "Mahandipur Balaji"
    static let tokens = "50"
    static let maxMembers = 10
}

// MARK: - Booking window (mirror Android `PoojaActiveWindow.kt`)

enum PoojaBookingState: String {
    case notStarted = "not_started", open, full, expired
}

/// Server fields win; otherwise booking opens at `scheduledAt` and closes 8 hours later.
struct PoojaBookingWindow {
    let status: String
    let canBook: Bool

    init(scheduledAt: String, availableTokens: Int, serverStatus: String?, serverCanBook: Bool?, now: Date = .now) {
        let resolved: String
        if let serverStatus, !serverStatus.trimmingCharacters(in: .whitespaces).isEmpty {
            resolved = serverStatus
        } else if let opensAt = AppFormatters.parseISO(scheduledAt) {
            let closesAt = opensAt.addingTimeInterval(8 * 60 * 60)
            if now < opensAt { resolved = PoojaBookingState.notStarted.rawValue }
            else if now >= closesAt { resolved = PoojaBookingState.expired.rawValue }
            else if availableTokens <= 0 { resolved = PoojaBookingState.full.rawValue }
            else { resolved = PoojaBookingState.open.rawValue }
        } else {
            resolved = PoojaBookingState.expired.rawValue
        }
        status = resolved
        canBook = serverCanBook ?? (resolved == PoojaBookingState.open.rawValue)
    }

    var state: PoojaBookingState { PoojaBookingState(rawValue: status) ?? .expired }
    var isExpired: Bool { state == .expired }

    /// Short label used on the Puja tab card button.
    var cardLabel: String {
        switch state {
        case .open: "Book"
        case .notStarted: "Not Started"
        case .full: "Full"
        case .expired: "Expired"
        }
    }

    /// Full-width button label used on the booking and details screens.
    func buttonLabel(opensAt: String) -> String {
        switch state {
        case .notStarted: "Booking Opens at \(opensAt.fullIndiaDateTime)"
        case .open: "Book Token"
        case .full: "No Tokens Available"
        case .expired: "Puja Expired"
        }
    }

    /// Message shown when a booking is attempted while closed.
    var closedMessage: String {
        switch state {
        case .notStarted: "Puja booking has not started yet"
        case .full: "No tokens available for this pooja"
        default: "This pooja has expired"
        }
    }
}

// MARK: - Helpers

extension SessionStore {
    /// Puja admin: super user, super/bus admin, or explicit `pooja.manage` capability.
    /// A stay_admin sees the customer UI.
    var canManagePooja: Bool {
        guard let user else { return false }
        return effectiveIsAdmin
            || user.isSuperUser
            || user.adminType == "super_admin"
            || user.adminType == "bus_admin"
            || user.capabilities.contains("pooja.manage")
    }
}

extension String {
    /// "not_started" → "Not Started" (Android `toTitleLabel`).
    var poojaTitleLabel: String {
        split(whereSeparator: { $0 == "_" || $0 == " " })
            .map { $0.prefix(1).uppercased() + $0.dropFirst() }
            .joined(separator: " ")
    }

    /// First letter uppercased (Android `replaceFirstChar { it.uppercase() }`).
    var poojaCapitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
