import Foundation

/// Shared parsing/formatting so every feature shows dates and money the same way
/// as Android. Bus, Puja, Event and Stay times are shown in India time.
nonisolated enum AppFormatters {
    static let india = TimeZone(identifier: "Asia/Kolkata")!

    /// Parses ISO-8601 with or without fractional seconds (Node sends `.000Z`).
    static func parseISO(_ value: String?) -> Date? {
        guard let value, !value.isEmpty else { return nil }
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fractional.date(from: value) { return date }
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        return plain.date(from: value)
    }

    /// UTC ISO-8601 string to send to the API.
    static func isoString(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }

    /// e.g. "15 Nov, 09:00 AM" (Android compact format).
    static func compactDateTime(_ date: Date, timeZone: TimeZone = india) -> String { format(date, "dd MMM, hh:mm a", timeZone) }
    /// e.g. "15 Nov 2026, 09:00 AM" (Android full format).
    static func fullDateTime(_ date: Date, timeZone: TimeZone = india) -> String { format(date, "dd MMM yyyy, hh:mm a", timeZone) }
    /// e.g. "15 Nov 2026".
    static func fullDate(_ date: Date, timeZone: TimeZone = india) -> String { format(date, "dd MMM yyyy", timeZone) }
    /// e.g. "15 Nov".
    static func dayMonth(_ date: Date, timeZone: TimeZone = india) -> String { format(date, "dd MMM", timeZone) }

    /// `yyyy-MM-dd` for a calendar day, in India time (Stay dates).
    static func apiDay(_ date: Date, timeZone: TimeZone = india) -> String { format(date, "yyyy-MM-dd", timeZone, posix: true) }

    /// Combines a picked day and time-of-day as India wall-clock time.
    static func indiaDate(day: Date, hour: Int, minute: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = india
        var parts = Calendar.current.dateComponents([.year, .month, .day], from: day)
        parts.hour = hour; parts.minute = minute; parts.second = 0
        return calendar.date(from: parts) ?? day
    }

    /// "₹1,250" (no decimals, Indian grouping).
    static func rupees(_ amount: Double) -> String {
        "₹" + amount.formatted(.number.precision(.fractionLength(0...2)).locale(Locale(identifier: "en_IN")))
    }

    /// Any custom pattern, in India time.
    static func format(_ date: Date, _ pattern: String) -> String { format(date, pattern, india) }

    private static func format(_ date: Date, _ pattern: String, _ timeZone: TimeZone, posix: Bool = false) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: posix ? "en_US_POSIX" : "en_IN")
        formatter.timeZone = timeZone
        // en_IN renders "am"/"pm"; Android shows "AM"/"PM".
        formatter.amSymbol = "AM"; formatter.pmSymbol = "PM"
        formatter.dateFormat = pattern
        return formatter.string(from: date)
    }
}

extension String {
    /// ISO string → "dd MMM, hh:mm a" IST, or the raw string if unparseable.
    var compactIndiaDateTime: String { AppFormatters.parseISO(self).map { AppFormatters.compactDateTime($0) } ?? self }
    /// ISO string → "dd MMM yyyy, hh:mm a" IST, or the raw string if unparseable.
    var fullIndiaDateTime: String { AppFormatters.parseISO(self).map { AppFormatters.fullDateTime($0) } ?? self }
}
