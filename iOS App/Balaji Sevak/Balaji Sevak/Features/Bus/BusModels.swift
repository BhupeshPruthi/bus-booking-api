import Foundation

// MARK: - Bus list / detail (Android BusDto.kt)

struct BusRouteInfo: Codable, Equatable { let id: String?; let name: String; let source: String; let destination: String }

struct BusListItem: Codable, Identifiable, Equatable {
    let id: String
    let routeId: String?
    let busName: String
    let totalSeats: Int
    /// Android default: 1.
    let seatStartNumber: Int?
    let price: Double
    let departureTime: String
    let arrivalTime: String?
    let contactName: String?
    let contactPhone: String?
    let tripType: String?
    let returnBusId: String?
    let returnDepartureTime: String?
    let returnArrivalTime: String?
    let status: String?
    let route: BusRouteInfo
    var bookedSeats: Int
    var availableSeats: Int
    var bookableSeats: Int?

    var firstSeatNumber: Int { seatStartNumber ?? 1 }
    var isRoundTrip: Bool { tripType == "round_trip" }
    /// Round trip shows the return leg's arrival when known.
    var finalArrivalTime: String? { isRoundTrip ? (returnArrivalTime ?? arrivalTime) : arrivalTime }
}

struct PickupPointInfo: Codable, Identifiable, Equatable, Hashable { let id: String; let name: String; let address: String?; let sequence: Int? }

struct BusDetailData: Codable, Identifiable, Equatable {
    let id: String
    let routeId: String?
    let busName: String
    let totalSeats: Int
    let seatStartNumber: Int?
    let price: Double
    let departureTime: String
    let arrivalTime: String?
    let contactName: String?
    let contactPhone: String?
    let tripType: String?
    let returnBusId: String?
    var returnDepartureTime: String?
    var returnArrivalTime: String?
    let status: String?
    let route: BusRouteInfo
    let pickupPoints: [PickupPointInfo]?
    let bookedSeats: Int
    let availableSeats: Int
    let bookableSeats: Int?

    var firstSeatNumber: Int { seatStartNumber ?? 1 }
    var isRoundTrip: Bool { tripType == "round_trip" }
    var stops: [PickupPointInfo] { pickupPoints ?? [] }
    var finalArrivalTime: String? { isRoundTrip ? (returnArrivalTime ?? arrivalTime) : arrivalTime }
}

// MARK: - Bookings

struct CreateBookingRequest: Encodable { let busId: String; let pickupPointId: String; let seatCount: Int; let passengerName: String; let passengerPhone: String }

/// Also decoded by My Bookings for `POST bookings/{id}/cancellation-request`; keep new fields optional.
struct BookingData: Codable, Identifiable, Equatable {
    let id: String
    let seatCount: Int
    let assignedSeats: String?
    let totalAmount: Double
    let status: String
    var busId: String? = nil
    var passengerName: String? = nil
    var passengerPhone: String? = nil
    var cancellationRequestedAt: String? = nil
    var cancellationReason: String? = nil
    var cancellationRejectionReason: String? = nil
    var cancelledAt: String? = nil
    var holdExpiresAt: String? = nil
    var createdAt: String? = nil
    var bus: BookingBusInfo? = nil
    var route: BookingRouteInfo? = nil
    var pickupPoint: BookingPickupInfo? = nil
    var user: BookingUserInfo? = nil

    var displayName: String? { passengerName ?? user?.name }
    var displayPhone: String? { passengerPhone ?? user?.mobile }
}

struct BookingUserInfo: Codable, Equatable { let mobile: String?; let name: String? }
struct BookingBusInfo: Codable, Equatable { let name: String?; let departureTime: String?; let arrivalTime: String?; let tripType: String?; let returnArrivalTime: String? }
struct BookingRouteInfo: Codable, Equatable { let name: String?; let source: String; let destination: String }
struct BookingPickupInfo: Codable, Equatable { let name: String; let address: String? }

struct UpdateBookingStatusRequest: Encodable { let action: String; var rejectionReason: String? = nil }
struct BusBookingCancellationRequest: Encodable { var reason: String? = nil }

struct DeleteBusResult: Codable {
    let message: String
    let busIds: [String]?
    let mode: String?
    let rejectedBookingCount: Int?
    let cancelledBookingCount: Int?
}

/// Accepts any JSON payload; used where the response body is not needed.
struct BusIgnoredResponse: Decodable {}

// MARK: - Create trip (Android TripRequest.kt / TripResponse.kt)

struct StopRequest: Encodable { let name: String; let address: String? }

struct CreateTripRequest: Encodable {
    let source: String
    let destination: String
    let departureTime: String
    let arrivalTime: String
    let totalSeats: Int
    let seatStartNumber: Int
    let price: Double
    let contactName: String
    let contactPhone: String
    let tripType: String
    let stops: [StopRequest]
    let returnDepartureTime: String?
    let returnArrivalTime: String?

}

struct TripData: Decodable {
    let outbound: TripBusDetail
    let returnTrip: TripBusDetail?
    enum CodingKeys: String, CodingKey { case outbound; case returnTrip = "return" }
}

struct TripBusDetail: Decodable {
    let id: String
    let busName: String?
    let totalSeats: Int?
    let price: Double?
    let status: String?
    let route: BusRouteInfo?
}

// MARK: - Seat availability (Android SeatAvailability.kt)

struct SeatAvailability: Equatable {
    let availableSeats: Int
    let reservedSeats: Int
    let bookableSeats: Int
    let totalSeats: Int

    /// "Bus Fully Booked" once every bookable seat is taken, otherwise "X available / Y seats".
    var summary: String {
        availableSeats == 0 && bookableSeats > 0
            ? "Bus Fully Booked"
            : "\(availableSeats) available / \(bookableSeats) seats"
    }

    private static let releasedStatuses: Set<String> = ["cancelled", "rejected", "expired"]

    static func make(totalSeats: Int, seatStartNumber: Int, serverBookableSeats: Int?, reservedSeats: Int, availableSeats: Int? = nil) -> SeatAvailability {
        let bookable = serverBookableSeats ?? max(0, totalSeats - seatStartNumber + 1)
        let reserved = max(0, reservedSeats)
        let available = availableSeats ?? max(0, bookable - reserved)
        return SeatAvailability(availableSeats: min(max(available, 0), max(bookable, 0)), reservedSeats: reserved, bookableSeats: bookable, totalSeats: totalSeats)
    }

    static func reservesSeat(_ booking: BookingData) -> Bool { !releasedStatuses.contains(booking.status) }
}

extension BusListItem {
    var seatAvailability: SeatAvailability {
        .make(totalSeats: totalSeats, seatStartNumber: firstSeatNumber, serverBookableSeats: bookableSeats, reservedSeats: bookedSeats, availableSeats: availableSeats)
    }

    func seatAvailability(pairedWith pair: BusListItem?) -> SeatAvailability {
        let own = seatAvailability
        guard isRoundTrip, let pair, pair.id != id else { return own }
        if bookableSeats != nil && pair.bookableSeats != nil { return own }
        let reserved = own.reservedSeats + pair.seatAvailability.reservedSeats
        return SeatAvailability(
            availableSeats: min(max(own.bookableSeats - reserved, 0), max(own.bookableSeats, 0)),
            reservedSeats: max(0, reserved),
            bookableSeats: own.bookableSeats,
            totalSeats: own.totalSeats
        )
    }
}

extension BusDetailData {
    var seatAvailability: SeatAvailability {
        .make(totalSeats: totalSeats, seatStartNumber: firstSeatNumber, serverBookableSeats: bookableSeats, reservedSeats: bookedSeats, availableSeats: availableSeats)
    }

    func seatAvailability(fromBookings bookings: [BookingData]) -> SeatAvailability {
        var reservingIDs: Set<String> = [id]
        if isRoundTrip, let returnBusId { reservingIDs.insert(returnBusId) }
        let reserved = bookings
            .filter { $0.busId == nil || reservingIDs.contains($0.busId!) }
            .filter(SeatAvailability.reservesSeat)
            .reduce(0) { $0 + $1.seatCount }
        return .make(totalSeats: totalSeats, seatStartNumber: firstSeatNumber, serverBookableSeats: bookableSeats, reservedSeats: reserved)
    }
}

// MARK: - Display helpers

enum BusFormat {
    /// Android `formatRouteTitle`: ↔ for round trips, → otherwise.
    static func routeTitle(_ source: String, _ destination: String, tripType: String?) -> String {
        "\(source)  \(tripType == "round_trip" ? "↔" : "→")  \(destination)"
    }

    /// "departure → final arrival" in compact IST.
    static func tripDates(departure: String, finalArrival: String?) -> String {
        let outbound = departure.compactIndiaDateTime
        guard let finalArrival else { return outbound }
        return "\(outbound)  →  \(finalArrival.compactIndiaDateTime)"
    }

    /// Whole rupees like Android's `price.toLong()`.
    static func rupees(_ amount: Double) -> String { "₹\(Int64(amount))" }

    static func tripTypeLabel(_ tripType: String?) -> String { tripType == "round_trip" ? "Round Way" : "One Way" }

    static func titleLabel(_ value: String) -> String {
        value.split(whereSeparator: { $0 == "_" || $0 == " " }).map { $0.prefix(1).uppercased() + $0.dropFirst() }.joined(separator: " ")
    }
}
