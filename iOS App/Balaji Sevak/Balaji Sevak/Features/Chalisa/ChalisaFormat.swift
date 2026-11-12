import Foundation

/// Marathon times and reading days follow India time, whatever the phone's own time zone
/// (Android `ChalisaFormat.kt`).
nonisolated enum ChalisaFormat {
    private static var indiaCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppFormatters.india
        return calendar
    }

    /// 124580 → "1,24,580"
    static func count(_ value: Int) -> String {
        value.formatted(.number.grouping(.automatic).locale(Locale(identifier: "en_IN")))
    }

    /// "Wed, 1 Oct, 6:00 AM"
    static func start(_ date: Date) -> String { AppFormatters.format(date, "EEE, d MMM, h:mm a") }

    /// "6:38 PM", or "Fri, 10 Oct, 6:38 PM" when it isn't today.
    static func lastRound(_ date: Date) -> String {
        indiaCalendar.isDate(date, inSameDayAs: Date()) ? time(date) : start(date)
    }

    static func time(_ date: Date) -> String { AppFormatters.format(date, "h:mm a") }
    /// "12 Oct 2026"
    static func date(_ date: Date) -> String { AppFormatters.format(date, "d MMM yyyy") }
    /// "12 Oct"
    static func shortDate(_ date: Date) -> String { AppFormatters.format(date, "d MMM") }

    /// "Today, 11 Oct" / "Yesterday, 10 Oct" / "Thu, 9 Oct" for a `yyyy-MM-dd` reading day.
    static func historyDay(_ day: String) -> String {
        guard let date = dayDate(day) else { return day }
        let calendar = indiaCalendar
        if calendar.isDateInToday(date) { return "Today, \(shortDate(date))" }
        if calendar.isDateInYesterday(date) { return "Yesterday, \(shortDate(date))" }
        return AppFormatters.format(date, "EEE, d MMM")
    }

    /// Noon India time on a `yyyy-MM-dd` day, so the day never shifts when formatted.
    static func dayDate(_ day: String) -> Date? {
        let parts = day.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return indiaCalendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2], hour: 12))
    }

    /// "in 2 days 14 h", "in 3 h 20 min", "in 5 min", "in under a minute"
    static func startsIn(_ start: Date, now: Date) -> String {
        let remaining = Int(start.timeIntervalSince(now))
        if remaining <= 0 { return "now" }
        let days = remaining / 86_400
        let hours = remaining / 3_600 % 24
        let minutes = remaining / 60 % 60
        let dayWord = days == 1 ? "day" : "days"
        if days > 0 { return hours > 0 ? "in \(days) \(dayWord) \(hours) h" : "in \(days) \(dayWord)" }
        if hours > 0 { return minutes > 0 ? "in \(hours) h \(minutes) min" : "in \(hours) h" }
        if minutes > 0 { return "in \(minutes) min" }
        return "in under a minute"
    }

    static func rounds(_ count: Int) -> String { "\(self.count(count)) \(count == 1 ? "round" : "rounds")" }
}

/// A calendar month for the reading history, in India time.
nonisolated struct ChalisaMonth: Equatable, Hashable {
    let year: Int
    let month: Int

    static var current: ChalisaMonth {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppFormatters.india
        let parts = calendar.dateComponents([.year, .month], from: Date())
        return ChalisaMonth(year: parts.year ?? 2026, month: parts.month ?? 1)
    }

    /// "2026-10", as the API expects.
    var apiValue: String { String(format: "%04d-%02d", year, month) }
    /// "October 2026"
    var title: String {
        ChalisaFormat.dayDate(String(format: "%04d-%02d-01", year, month)).map { AppFormatters.format($0, "MMMM yyyy") } ?? apiValue
    }
    var previous: ChalisaMonth { month == 1 ? ChalisaMonth(year: year - 1, month: 12) : ChalisaMonth(year: year, month: month - 1) }
    var next: ChalisaMonth { month == 12 ? ChalisaMonth(year: year + 1, month: 1) : ChalisaMonth(year: year, month: month + 1) }
    var isAfterCurrent: Bool { (year, month) > (Self.current.year, Self.current.month) }
}
