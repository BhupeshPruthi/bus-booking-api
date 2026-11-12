import Foundation

// MARK: - Unified "My Bookings" feed (Android `UnifiedBookingDto.kt`)
//
// Decoding is lenient where Android has Kotlin defaults, so a missing
// `counts`, count field or `availableActions` does not fail the whole page.

struct UnifiedBookingPage: Decodable {
    let items: [UnifiedBookingItem]
    let counts: UnifiedBookingCounts
    let nextCursor: String?
    let serverTime: String?

    enum CodingKeys: String, CodingKey { case items, counts, nextCursor, serverTime }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        items = try container.decodeIfPresent([UnifiedBookingItem].self, forKey: .items) ?? []
        counts = try container.decodeIfPresent(UnifiedBookingCounts.self, forKey: .counts) ?? UnifiedBookingCounts()
        nextCursor = try container.decodeIfPresent(String.self, forKey: .nextCursor)
        serverTime = try container.decodeIfPresent(String.self, forKey: .serverTime)
    }
}

struct UnifiedBookingCounts: Decodable, Equatable {
    var upcoming = 0
    var past = 0
    var failed = 0

    init(upcoming: Int = 0, past: Int = 0, failed: Int = 0) {
        self.upcoming = upcoming; self.past = past; self.failed = failed
    }

    enum CodingKeys: String, CodingKey { case upcoming, past, failed }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        upcoming = try container.decodeIfPresent(Int.self, forKey: .upcoming) ?? 0
        past = try container.decodeIfPresent(Int.self, forKey: .past) ?? 0
        failed = try container.decodeIfPresent(Int.self, forKey: .failed) ?? 0
    }
}

struct UnifiedBookingItem: Decodable, Identifiable, Equatable {
    let id: String
    let bookingType: String
    let bookingId: String
    let reference: String
    let rawStatus: String
    let normalizedStatus: String
    let startsAt: String
    let endsAt: String
    let createdAt: String?
    let updatedAt: String?
    let totalAmount: Double?
    let currency: String?
    let title: String
    let subtitle: String
    let details: UnifiedBookingDetails?
    let availableActions: [String]

    enum CodingKeys: String, CodingKey {
        case id, bookingType, bookingId, reference, rawStatus, normalizedStatus, startsAt, endsAt
        case createdAt, updatedAt, totalAmount, currency, title, subtitle, details, availableActions
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        bookingType = try c.decode(String.self, forKey: .bookingType)
        bookingId = try c.decode(String.self, forKey: .bookingId)
        reference = try c.decode(String.self, forKey: .reference)
        rawStatus = try c.decode(String.self, forKey: .rawStatus)
        normalizedStatus = try c.decode(String.self, forKey: .normalizedStatus)
        startsAt = try c.decode(String.self, forKey: .startsAt)
        endsAt = try c.decode(String.self, forKey: .endsAt)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        updatedAt = try c.decodeIfPresent(String.self, forKey: .updatedAt)
        totalAmount = try c.decodeIfPresent(Double.self, forKey: .totalAmount)
        currency = try c.decodeIfPresent(String.self, forKey: .currency)
        title = try c.decode(String.self, forKey: .title)
        subtitle = try c.decode(String.self, forKey: .subtitle)
        details = try c.decodeIfPresent(UnifiedBookingDetails.self, forKey: .details)
        availableActions = try c.decodeIfPresent([String].self, forKey: .availableActions) ?? []
    }
}

struct UnifiedBookingDetails: Decodable, Equatable {
    // Bus
    let busName: String?
    let source: String?
    let destination: String?
    let pickupPoint: String?
    let seatCount: Int?
    let assignedSeats: String?
    let passengerName: String?
    let passengerPhone: String?
    let tripType: String?
    // Stay
    let checkInDate: String?
    let checkOutDate: String?
    let nightCount: Int?
    let guestCount: Int?
    let contactName: String?
    let contactEmail: String?
    let contactPhone: String?
    let subtotalAmount: Double?
    let discountAmount: Double?
    let couponCode: String?
    let items: [UnifiedStayItem]?
    let rejectionReason: String?
    // Puja
    let poojaId: String?
    let place: String?
    let tokenNumber: Int?
    let memberCount: Int?
    let city: String?
    let name: String?
    let phone: String?
}

struct UnifiedStayItem: Decodable, Equatable, Identifiable {
    let unitTypeCode: String
    let unitTypeName: String
    let quantity: Int
    let nightlyRate: Double
    let nightCount: Int
    let lineTotal: Double
    var id: String { unitTypeCode }
}
