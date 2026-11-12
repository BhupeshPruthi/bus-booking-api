import SwiftUI
import UIKit

/// Builds the "Stay Records – last 30 days" PDF (port of Android `createStayRecordsPdf`).
enum StayRecordsPDF {
    static func write(_ bookings: [StayBooking]) throws -> URL {
        let pageRect = CGRect(x: 0, y: 0, width: 595, height: 842)
        let margin: CGFloat = 40
        let contentWidth = pageRect.width - margin * 2

        let title: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 20), .foregroundColor: UIColor.black]
        let section: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 14), .foregroundColor: UIColor.black]
        let body: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 11), .foregroundColor: UIColor(white: 0.16, alpha: 1)]
        let muted: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 10), .foregroundColor: UIColor(white: 0.37, alpha: 1)]

        let sorted = bookings.sorted {
            let lhs = $0.createdAt ?? "", rhs = $1.createdAt ?? ""
            return lhs != rhs ? lhs > rhs : $0.reference < $1.reference
        }
        let confirmedCount = bookings.filter { $0.status == "confirmed" }.count
        let cancelledCount = bookings.filter { $0.status == "cancelled" }.count

        let renderer = UIGraphicsPDFRenderer(bounds: pageRect, format: {
            let format = UIGraphicsPDFRendererFormat()
            format.documentInfo = [kCGPDFContextTitle as String: "Stay Records - Last 30 Days"]
            return format
        }())

        let data = renderer.pdfData { context in
            var y = margin
            func newPage() { context.beginPage(); y = margin }
            func ensureSpace(_ required: CGFloat) { if y + required > pageRect.height - margin { newPage() } }
            func draw(_ text: String, _ attributes: [NSAttributedString.Key: Any], extraBottom: CGFloat = 4) {
                let string = NSAttributedString(string: text, attributes: attributes)
                let height = ceil(string.boundingRect(with: CGSize(width: contentWidth, height: .greatestFiniteMagnitude),
                                                      options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil).height)
                ensureSpace(height)
                string.draw(with: CGRect(x: margin, y: y, width: contentWidth, height: height),
                            options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
                y += height + extraBottom
            }
            func separator() {
                ensureSpace(12)
                let path = UIBezierPath()
                path.move(to: CGPoint(x: margin, y: y + 4))
                path.addLine(to: CGPoint(x: pageRect.width - margin, y: y + 4))
                UIColor(white: 0.82, alpha: 1).setStroke()
                path.lineWidth = 1
                path.stroke()
                y += 12
            }

            newPage()
            draw("Stay Records", title, extraBottom: 8)
            draw("Rolling 30-day report", section)
            draw("\(bookings.count) records · \(confirmedCount) confirmed · \(cancelledCount) cancelled", body, extraBottom: 8)
            separator()

            for (index, booking) in sorted.enumerated() {
                ensureSpace(120)
                draw("\(index + 1). \(booking.reference) — \(booking.contactName)", section)
                draw("Phone: \(booking.contactPhone)  |  Email: \(booking.contactEmail)", body)
                draw("Stay: \(booking.checkInDate) to \(booking.checkOutDate) (\(booking.nightCount) nights) · \(booking.guestCount) guests", body)
                let rooms = booking.items.map { "\($0.quantity) × \($0.unitTypeName)" }.joined(separator: ", ")
                draw("Accommodation: \(rooms.isEmpty ? "Not available" : rooms)", body)
                draw("Amount: \(StayFormatting.rupees(booking.totalAmount))", body)
                draw("Status: \(StayFormatting.statusLabel(booking.status))", muted)
                if let createdAt = booking.createdAt {
                    draw("Booked: \(StayFormatting.timestamp(createdAt))", muted, extraBottom: 6)
                }
                separator()
            }
        }

        let url = FileManager.default.temporaryDirectory.appendingPathComponent("stay_records_last_30_days.pdf")
        try data.write(to: url, options: .atomic)
        return url
    }
}

/// UIKit share sheet for the exported PDF.
struct StayShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
