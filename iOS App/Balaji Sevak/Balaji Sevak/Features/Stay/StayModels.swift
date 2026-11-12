import Foundation

// MARK: - Catalog & quote

nonisolated struct StayCatalog: Codable {
    let checkInTime: String
    let checkOutTime: String
    let timezone: String
    let currency: String
    let pricesIncludeTaxes: Bool
    let refundCutoffHours: Int
    let maxNights: Int
    let unitTypes: [StayUnitType]
}

nonisolated struct StayUnitType: Codable, Identifiable, Equatable {
    let id: String
    let code: String
    let displayName: String
    let capacity: Int?
    let nightlyRate: Double
    let totalUnits: Int
    let availableUnits: Int?
    let requestedQuantity: Int?
    let lineTotal: Double?
    let canFulfill: Bool?
    let isActive: Bool?
}

nonisolated struct StayBookingItemRequest: Codable, Equatable { let unitTypeCode: String; let quantity: Int }

nonisolated struct StayQuoteRequest: Codable {
    let checkInDate: String
    let checkOutDate: String
    let items: [StayBookingItemRequest]
    let couponCode: String?
}

nonisolated struct StayQuote: Codable, Equatable {
    let checkInDate: String
    let checkOutDate: String
    let nightCount: Int
    let accommodationAmount: Double
    var subtotalAmount: Double
    var discountAmount: Double
    var couponCode: String?
    var totalAmount: Double
    let totalCapacity: Int
    let currency: String
    let pricesIncludeTaxes: Bool
    let canFulfill: Bool
    let availabilityIsInformational: Bool
    let unitTypes: [StayUnitType]

    private enum CodingKeys: String, CodingKey {
        case checkInDate, checkOutDate, nightCount, accommodationAmount, subtotalAmount, discountAmount, couponCode
        case totalAmount, totalCapacity, currency, pricesIncludeTaxes, canFulfill, availabilityIsInformational, unitTypes
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        checkInDate = try c.decode(String.self, forKey: .checkInDate)
        checkOutDate = try c.decode(String.self, forKey: .checkOutDate)
        nightCount = try c.decode(Int.self, forKey: .nightCount)
        accommodationAmount = try c.decode(Double.self, forKey: .accommodationAmount)
        subtotalAmount = try c.decodeIfPresent(Double.self, forKey: .subtotalAmount) ?? accommodationAmount
        discountAmount = try c.decodeIfPresent(Double.self, forKey: .discountAmount) ?? 0
        couponCode = try c.decodeIfPresent(String.self, forKey: .couponCode)
        totalAmount = try c.decode(Double.self, forKey: .totalAmount)
        totalCapacity = try c.decodeIfPresent(Int.self, forKey: .totalCapacity) ?? 0
        currency = try c.decodeIfPresent(String.self, forKey: .currency) ?? "INR"
        pricesIncludeTaxes = try c.decodeIfPresent(Bool.self, forKey: .pricesIncludeTaxes) ?? true
        canFulfill = try c.decode(Bool.self, forKey: .canFulfill)
        availabilityIsInformational = try c.decodeIfPresent(Bool.self, forKey: .availabilityIsInformational) ?? false
        unitTypes = try c.decodeIfPresent([StayUnitType].self, forKey: .unitTypes) ?? []
    }

    /// Drops a previously applied coupon locally (Android `updateCouponInput`).
    func withoutCoupon() -> StayQuote {
        var copy = self
        copy.discountAmount = 0
        copy.couponCode = nil
        copy.totalAmount = subtotalAmount
        return copy
    }
}

nonisolated struct CreateStayBookingRequest: Codable {
    let checkInDate: String
    let checkOutDate: String
    let items: [StayBookingItemRequest]
    let guestCount: Int
    let contactName: String
    let contactEmail: String
    let contactPhone: String
    let couponCode: String?
    let cancellationPolicyAccepted: Bool
    let customerNote: String?
}

nonisolated struct StayPage<Value: Codable>: Codable { let items: [Value]; let page: Int; let limit: Int; let total: Int }

// MARK: - Bookings

nonisolated struct StayBooking: Codable, Identifiable, Equatable {
    let id: String
    let reference: String
    let userId: String?
    let bookingSource: String
    let status: String
    let checkInDate: String
    let checkOutDate: String
    let nightCount: Int
    let guestCount: Int
    let contactName: String
    let contactEmail: String
    let contactPhone: String
    let subtotalAmount: Double?
    let discountAmount: Double
    let couponCode: String?
    let totalAmount: Double
    let customerNote: String?
    let rejectionReason: String?
    let confirmedAt: String?
    let completedAt: String?
    let createdAt: String?
    let totalAccommodationUnits: Int?
    let dailyEarningsContribution: Double?
    let items: [StayBookingItem]
    let cancellation: StayCancellation?

    private enum CodingKeys: String, CodingKey {
        case id, reference, userId, bookingSource, status, checkInDate, checkOutDate, nightCount, guestCount
        case contactName, contactEmail, contactPhone, subtotalAmount, discountAmount, couponCode, totalAmount
        case customerNote, rejectionReason, confirmedAt, completedAt, createdAt, totalAccommodationUnits
        case dailyEarningsContribution, items, cancellation
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        reference = try c.decode(String.self, forKey: .reference)
        userId = try c.decodeIfPresent(String.self, forKey: .userId)
        bookingSource = try c.decodeIfPresent(String.self, forKey: .bookingSource) ?? "customer"
        status = try c.decode(String.self, forKey: .status)
        checkInDate = try c.decode(String.self, forKey: .checkInDate)
        checkOutDate = try c.decode(String.self, forKey: .checkOutDate)
        nightCount = try c.decodeIfPresent(Int.self, forKey: .nightCount) ?? 0
        guestCount = try c.decodeIfPresent(Int.self, forKey: .guestCount) ?? 0
        contactName = try c.decodeIfPresent(String.self, forKey: .contactName) ?? ""
        contactEmail = try c.decodeIfPresent(String.self, forKey: .contactEmail) ?? ""
        contactPhone = try c.decodeIfPresent(String.self, forKey: .contactPhone) ?? ""
        subtotalAmount = try c.decodeIfPresent(Double.self, forKey: .subtotalAmount)
        discountAmount = try c.decodeIfPresent(Double.self, forKey: .discountAmount) ?? 0
        couponCode = try c.decodeIfPresent(String.self, forKey: .couponCode)
        totalAmount = try c.decode(Double.self, forKey: .totalAmount)
        customerNote = try c.decodeIfPresent(String.self, forKey: .customerNote)
        rejectionReason = try c.decodeIfPresent(String.self, forKey: .rejectionReason)
        confirmedAt = try c.decodeIfPresent(String.self, forKey: .confirmedAt)
        completedAt = try c.decodeIfPresent(String.self, forKey: .completedAt)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        totalAccommodationUnits = try c.decodeIfPresent(Int.self, forKey: .totalAccommodationUnits)
        dailyEarningsContribution = try c.decodeIfPresent(Double.self, forKey: .dailyEarningsContribution)
        items = try c.decodeIfPresent([StayBookingItem].self, forKey: .items) ?? []
        cancellation = try c.decodeIfPresent(StayCancellation.self, forKey: .cancellation)
    }
}

nonisolated struct StayBookingItem: Codable, Identifiable, Equatable {
    let id: String
    let unitTypeCode: String
    let unitTypeName: String
    let quantity: Int
    let nightlyRate: Double
    let nightCount: Int
    let lineTotal: Double
}

nonisolated struct StayCancellation: Codable, Identifiable, Equatable {
    let id: String
    let bookingId: String
    let reference: String?
    let status: String
    let previousBookingStatus: String
    let reason: String?
    let requestedAt: String
    let standardFullRefundEligible: Bool
    let hoursBeforeCheckIn: Double
    let refundDecision: String?
    let refundAmount: Double?
    let decisionReason: String?
    let contactName: String?
    let contactPhone: String?
    let contactEmail: String?
    let checkInDate: String?
    let checkOutDate: String?
    let totalAmount: Double?
}

/// Customer cancellation request body (`POST stays/bookings/{id}/cancellation-requests`).
nonisolated struct StayCancellationRequest: Codable { let reason: String? }
/// The cancellation endpoint returns the created cancellation request, not the booking itself.
nonisolated struct StayCancellationResponse: Codable { let id: String }

// MARK: - Admin

nonisolated struct StayRejectionRequest: Codable { let reason: String }

nonisolated struct StayCancellationDecisionRequest: Codable {
    let action: String
    let refundDecision: String?
    let refundAmount: Double?
    let reason: String?
}

nonisolated struct AdminStayCancellationRequest: Codable {
    let refundDecision: String
    let refundAmount: Double?
    let reason: String?
}

nonisolated struct StayDailyOccupancyReport: Codable, Equatable {
    let fromDate: String
    let toDate: String
    let days: [StayDailyOccupancyDay]
}

nonisolated struct StayDailyOccupancyDay: Codable, Equatable, Identifiable {
    let date: String
    let bookingCount: Int
    let totalEarnings: Double
    let unitTypes: [StayDailyOccupancyUnit]
    var id: String { date }

    private enum CodingKeys: String, CodingKey { case date, bookingCount, totalEarnings, unitTypes }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        date = try c.decode(String.self, forKey: .date)
        bookingCount = try c.decodeIfPresent(Int.self, forKey: .bookingCount) ?? 0
        totalEarnings = try c.decodeIfPresent(Double.self, forKey: .totalEarnings) ?? 0
        unitTypes = try c.decodeIfPresent([StayDailyOccupancyUnit].self, forKey: .unitTypes) ?? []
    }
}

nonisolated struct StayDailyOccupancyBookings: Codable, Equatable {
    let date: String
    let bookingCount: Int
    let totalEarnings: Double
    let unitTypes: [StayDailyOccupancyUnit]
    let bookings: [StayBooking]

    private enum CodingKeys: String, CodingKey { case date, bookingCount, totalEarnings, unitTypes, bookings }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        date = try c.decode(String.self, forKey: .date)
        bookingCount = try c.decodeIfPresent(Int.self, forKey: .bookingCount) ?? 0
        totalEarnings = try c.decodeIfPresent(Double.self, forKey: .totalEarnings) ?? 0
        unitTypes = try c.decodeIfPresent([StayDailyOccupancyUnit].self, forKey: .unitTypes) ?? []
        bookings = try c.decodeIfPresent([StayBooking].self, forKey: .bookings) ?? []
    }
}

nonisolated struct StayDailyOccupancyUnit: Codable, Equatable {
    let code: String
    let displayName: String
    let bookedUnits: Int
    let totalUnits: Int
    let availableUnits: Int
}

nonisolated struct StayCoupon: Codable, Identifiable, Equatable {
    let id: String
    let code: String
    let discountAmount: Double
    let startDate: String
    let endDate: String
    let isActive: Bool
    let status: String
    let createdAt: String?
}

nonisolated struct CreateStayCouponRequest: Codable {
    let code: String
    let discountAmount: Double
    let startDate: String
    let endDate: String
}

// MARK: - Helpers shared by customer and admin Stay screens

enum StayFormatting {
    /// Calendar for Stay days: Stay dates are India calendar days.
    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppFormatters.india
        return calendar
    }

    /// Start of today in India time.
    static var today: Date { calendar.startOfDay(for: .now) }

    static func addDays(_ days: Int, to date: Date) -> Date { calendar.date(byAdding: .day, value: days, to: date) ?? date }

    /// Uppercase A–Z, 0–9, `_` and `-`, max 50 characters (server coupon pattern).
    static func normalizeCoupon(_ value: String) -> String {
        String(value.uppercased().filter { ($0.isASCII && ($0.isLetter || $0.isNumber)) || $0 == "_" || $0 == "-" }.prefix(50))
    }

    /// "12:00" → "12:00 noon", "11:00" → "11:00 AM".
    static func clockTime(_ value: String?, fallback: String) -> String {
        guard let value, !value.isEmpty else { return fallback }
        let parts = value.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2, (0..<24).contains(parts[0]), (0..<60).contains(parts[1]) else { return value }
        let (hour, minute) = (parts[0], parts[1])
        if hour == 12 && minute == 0 { return "12:00 noon" }
        let displayHour = hour % 12 == 0 ? 12 : hour % 12
        return String(format: "%d:%02d %@", displayHour, minute, hour < 12 ? "AM" : "PM")
    }

    /// "2026-10-02" → "Fri, 02 Oct 2026".
    static func occupancyDate(_ value: String) -> String {
        let parser = DateFormatter()
        parser.calendar = Calendar(identifier: .gregorian)
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.timeZone = AppFormatters.india
        parser.dateFormat = "yyyy-MM-dd"
        guard let date = parser.date(from: value) else { return value }
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_IN")
        formatter.timeZone = AppFormatters.india
        formatter.dateFormat = "EEE, dd MMM yyyy"
        return formatter.string(from: date)
    }

    /// "2026-10-25", "2026-10-26" → "25 Oct → 26 Oct".
    static func dateRange(_ checkIn: String, _ checkOut: String) -> String {
        let parser = DateFormatter()
        parser.calendar = Calendar(identifier: .gregorian)
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.timeZone = AppFormatters.india
        parser.dateFormat = "yyyy-MM-dd"
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_IN")
        formatter.timeZone = AppFormatters.india
        formatter.dateFormat = "d MMM"
        func format(_ value: String) -> String { parser.date(from: value).map(formatter.string(from:)) ?? value }
        return "\(format(checkIn)) → \(format(checkOut))"
    }

    /// ISO timestamp → "dd MMM yyyy, hh:mm a" IST.
    static func timestamp(_ value: String) -> String { value.fullIndiaDateTime }

    /// "cancellation_requested" → "Cancellation Requested".
    static func statusLabel(_ status: String) -> String {
        status.split(whereSeparator: { $0 == "_" || $0 == " " }).map { $0.prefix(1).uppercased() + $0.dropFirst() }.joined(separator: " ")
    }

    static func rupees(_ amount: Double) -> String { AppFormatters.rupees(amount) }

    /// Percent-encodes a query string value (also escaping `&`, `+`, `=`).
    static func queryValue(_ value: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&+=?/#")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}

/// Maps errors to user-facing messages the same way Android's `StayRepository` does.
enum StayErrorMapper {
    static func message(for error: Error, fallback: String) -> String {
        if let apiError = error as? APIError {
            let detail = apiError.details?.first?.message.trimmingCharacters(in: .whitespacesAndNewlines)
            let serverMessage = detail.flatMap { $0.isEmpty ? nil : friendlyValidationMessage($0) }
                ?? apiError.message.trimmingCharacters(in: .whitespacesAndNewlines)
            if !serverMessage.isEmpty && serverMessage.caseInsensitiveCompare("Internal server error") != .orderedSame {
                return serverMessage
            }
            return "\(fallback) because the Stay service had a problem. Please try again."
        }
        if let clientError = error as? APIClientError {
            switch clientError {
            case .unauthorized: return "Your session has expired. Please sign in again."
            case .httpStatus(let code): return statusMessage(code, fallback: fallback)
            case .invalidResponse, .invalidURL: return "\(fallback). The server response could not be read. Please try again."
            }
        }
        if error is URLError { return "\(fallback). Check your internet connection and try again." }
        return "\(fallback). The server response could not be read. Please try again."
    }

    static func isCancellation(_ error: Error) -> Bool {
        error is CancellationError || (error as? URLError)?.code == .cancelled
    }

    private static func statusMessage(_ code: Int, fallback: String) -> String {
        switch code {
        case 200..<300: return "\(fallback). The server response could not be read. Please try again."
        case 401: return "Your session has expired. Please sign in again."
        case 403: return "You do not have permission to perform this Stay action."
        case 404: return "\(fallback). The requested Stay information was not found."
        case 500..<600: return "\(fallback) because the Stay service had a problem (server error \(code)). Please try again."
        default: return "\(fallback) (error \(code)). Please try again."
        }
    }

    private static func friendlyValidationMessage(_ message: String) -> String {
        if message.contains("less than or equal to 13") { return "Each room or hall category can have at most 13 units." }
        return message.replacingOccurrences(of: #"^"[^"]+"\s*"#, with: "", options: .regularExpression)
    }
}

extension String {
    var stayNilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
