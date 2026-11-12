import UIKit

/// Builds the Daily Occupancy PDFs (ports of Android `createDailyOccupancyPdf`
/// and `createOccupancyDayGuestsPdf`).
enum StayOccupancyPDF {
    /// A tab's days that have bookings, with only the room types that have bookings.
    static func writeDays(tab: StayAdminOccupancyTab, bookedDays: [StayDailyOccupancyDay], shownDays: [StayDailyOccupancyDay]) throws -> URL {
        let title = "Daily Occupancy - \(tab.title)"
        let range: String = {
            let dates = shownDays.map(\.date).sorted()
            guard let first = dates.first, let last = dates.last else { return "" }
            return "\(StayFormatting.occupancyDate(first)) to \(StayFormatting.occupancyDate(last))"
        }()
        let totalEarnings = bookedDays.reduce(0) { $0 + $1.totalEarnings }

        let data = pdfData(title: title) { page in
            page.draw(title, StayPDFPage.title, extraBottom: 6)
            if !range.isEmpty { page.draw(range, StayPDFPage.body) }
            page.draw("\(counted(bookedDays.count, "day", "days")) with bookings · Total earnings \(StayFormatting.rupees(totalEarnings))", StayPDFPage.body)
            page.draw("Only days and room types with bookings are listed. Generated \(generatedAt()) (India time).", StayPDFPage.muted, extraBottom: 8)
            page.separator()

            for day in bookedDays {
                let units = day.unitTypes.filter { $0.bookedUnits > 0 }
                // Keeps a day's heading and its rooms on the same page.
                page.ensureSpace(40 + CGFloat(units.count) * 16)
                page.drawRow(StayFormatting.occupancyDate(day.date), StayFormatting.rupees(day.totalEarnings), StayPDFPage.heading, extraBottom: 1)
                page.draw(counted(day.bookingCount, "booking", "bookings"), StayPDFPage.muted, extraBottom: 2)
                for unit in units {
                    // A long room name is shortened so it never runs into the numbers on the right.
                    page.drawRow(unit.displayName, "\(unit.bookedUnits) of \(unit.totalUnits) booked · \(unit.availableUnits) available", StayPDFPage.body, indent: 12)
                }
                page.space(4)
                page.separator()
            }
        }
        return try write(data, fileName: "daily_occupancy_\(tab.slug)_\(AppFormatters.apiDay(.now)).pdf")
    }

    /// Every booking occupying accommodation on one day.
    static func writeGuestList(_ details: StayDailyOccupancyBookings) throws -> URL {
        let title = "Guest list - \(StayFormatting.occupancyDate(details.date))"
        let bookings = details.bookings
        let totalGuests = bookings.reduce(0) { $0 + $1.guestCount }
        let checkIns = bookings.filter { $0.checkInDate == details.date }.count

        let data = pdfData(title: title) { page in
            page.draw(title, StayPDFPage.title, extraBottom: 6)
            page.draw(
                "\(counted(bookings.count, "booking", "bookings")) · \(counted(totalGuests, "guest", "guests")) · "
                    + "\(checkIns) checking in today · Estimated earnings \(StayFormatting.rupees(details.totalEarnings))",
                StayPDFPage.body, extraBottom: 2
            )
            for unit in details.unitTypes where unit.bookedUnits > 0 {
                page.draw("\(unit.displayName): \(unit.bookedUnits) of \(unit.totalUnits) booked · \(unit.availableUnits) available", StayPDFPage.muted, extraBottom: 2)
            }
            page.draw("Generated \(generatedAt()) (India time).", StayPDFPage.muted, extraBottom: 8)
            page.separator()

            for (index, booking) in bookings.enumerated() {
                // Keeps a booking's name, contact and rooms together on one page.
                page.ensureSpace(110 + CGFloat(booking.items.count) * 16)
                page.drawRow("\(index + 1). \(booking.contactName)", counted(booking.guestCount, "guest", "guests"), StayPDFPage.heading, extraBottom: 1)

                let indent: CGFloat = 14
                func line(_ text: String, _ attributes: [NSAttributedString.Key: Any] = StayPDFPage.body) {
                    page.draw(text, attributes, indent: indent, extraBottom: 2)
                }
                line("Phone: \(booking.contactPhone)")
                if booking.contactEmail.stayNilIfBlank != nil { line("Email: \(booking.contactEmail)") }
                line("\(booking.reference) · \(StayFormatting.statusLabel(booking.status)) · "
                     + (booking.checkInDate == details.date ? "Checks in today" : "Continuing stay"))
                line("Stay: \(StayFormatting.occupancyDate(booking.checkInDate)) → \(StayFormatting.occupancyDate(booking.checkOutDate)) · "
                     + counted(booking.nightCount, "night", "nights"))
                for item in booking.items { line("\(item.quantity) × \(item.unitTypeName)") }
                let dayAmount = booking.dailyEarningsContribution.map { " · This day: \(StayFormatting.rupees($0))" } ?? ""
                line("Booking total: \(StayFormatting.rupees(booking.totalAmount))\(dayAmount)")
                if booking.bookingSource == "admin" { line("Admin booking", StayPDFPage.muted) }
                if let note = booking.customerNote, note.stayNilIfBlank != nil { line("Note: \(note)", StayPDFPage.muted) }
                page.space(4)
                page.separator()
            }
        }
        return try write(data, fileName: "guest_list_\(details.date).pdf")
    }

    // MARK: Helpers

    private static func counted(_ count: Int, _ one: String, _ many: String) -> String { "\(count) \(count == 1 ? one : many)" }

    /// e.g. "24 Oct 2026, 9:45 AM" in India time.
    private static func generatedAt() -> String { AppFormatters.format(.now, "dd MMM yyyy, h:mm a") }

    private static func pdfData(title: String, _ draw: (StayPDFPage) -> Void) -> Data {
        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = [kCGPDFContextTitle as String: title]
        let renderer = UIGraphicsPDFRenderer(bounds: StayPDFPage.pageRect, format: format)
        return renderer.pdfData { context in draw(StayPDFPage(context: context)) }
    }

    private static func write(_ data: Data, fileName: String) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try data.write(to: url, options: .atomic)
        return url
    }
}

/// Top-to-bottom drawing on A4 pages, starting a new page when the next block does not fit.
private final class StayPDFPage {
    static let pageRect = CGRect(x: 0, y: 0, width: 595, height: 842)
    static let title: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 20), .foregroundColor: UIColor.black]
    static let heading: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 13), .foregroundColor: UIColor.black]
    static let body: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 11), .foregroundColor: UIColor(white: 0.16, alpha: 1)]
    static let muted: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 10), .foregroundColor: UIColor(white: 0.37, alpha: 1)]

    private let context: UIGraphicsPDFRendererContext
    private let margin: CGFloat = 40
    private var y: CGFloat = 0
    private var contentWidth: CGFloat { Self.pageRect.width - margin * 2 }
    private var right: CGFloat { Self.pageRect.width - margin }

    init(context: UIGraphicsPDFRendererContext) {
        self.context = context
        newPage()
    }

    func ensureSpace(_ required: CGFloat) {
        if y + required > Self.pageRect.height - margin { newPage() }
    }

    func space(_ amount: CGFloat) { y += amount }

    /// Text wrapped to the content width.
    func draw(_ text: String, _ attributes: [NSAttributedString.Key: Any], indent: CGFloat = 0, extraBottom: CGFloat = 4) {
        let width = contentWidth - indent
        let string = NSAttributedString(string: text, attributes: attributes)
        let height = ceil(string.boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude),
                                              options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil).height)
        ensureSpace(height)
        string.draw(with: CGRect(x: margin + indent, y: y, width: width, height: height),
                    options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        y += height + extraBottom
    }

    /// One line: `leading` on the left, cut short with "…" so it stays clear of the right-aligned `trailing`.
    func drawRow(_ leading: String, _ trailing: String, _ attributes: [NSAttributedString.Key: Any], indent: CGFloat = 0, extraBottom: CGFloat = 0) {
        let trailingString = NSAttributedString(string: trailing, attributes: attributes)
        let trailingSize = trailingString.size()
        let lineHeight = ceil(max((attributes[.font] as? UIFont)?.lineHeight ?? 0, trailingSize.height))
        ensureSpace(lineHeight)

        let truncating = NSMutableParagraphStyle()
        truncating.lineBreakMode = .byTruncatingTail
        var leadingAttributes = attributes
        leadingAttributes[.paragraphStyle] = truncating
        let leadingWidth = max(0, contentWidth - indent - ceil(trailingSize.width) - 16)
        NSAttributedString(string: leading, attributes: leadingAttributes)
            .draw(with: CGRect(x: margin + indent, y: y, width: leadingWidth, height: lineHeight),
                  options: [.usesLineFragmentOrigin, .usesFontLeading, .truncatesLastVisibleLine], context: nil)
        trailingString.draw(at: CGPoint(x: right - trailingSize.width, y: y))
        y += lineHeight + extraBottom
    }

    func separator() {
        ensureSpace(12)
        let path = UIBezierPath()
        path.move(to: CGPoint(x: margin, y: y + 4))
        path.addLine(to: CGPoint(x: right, y: y + 4))
        UIColor(white: 0.82, alpha: 1).setStroke()
        path.lineWidth = 1
        path.stroke()
        y += 12
    }

    private func newPage() {
        context.beginPage()
        y = margin
    }
}
