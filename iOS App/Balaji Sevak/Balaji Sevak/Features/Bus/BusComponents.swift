import SwiftUI

/// Android `WelcomeHeader` shown above the empty Bus list.
struct BusWelcomeHeader: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "bus.fill").font(.system(size: 56)).foregroundStyle(.primary).padding(.bottom, 4)
            Text("Welcome to BalajiSevak").font(.title2.weight(.bold)).multilineTextAlignment(.center)
            Text("Book bus seats, manage trips and more — all in one place.")
                .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct BusStopTimelineItem: Identifiable {
    let id: String
    let title: String
    var subtitle: String? = nil
}

/// Android `StopListTimeline`: dashed vertical line running through each bus icon.
struct BusStopsTimeline<Trailing: View>: View {
    let stops: [BusStopTimelineItem]
    var onTap: ((Int) -> Void)? = nil
    @ViewBuilder var trailing: (Int) -> Trailing

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(stops.enumerated()), id: \.element.id) { index, stop in
                HStack(spacing: 4) {
                    ZStack {
                        DashedVerticalLine()
                            .stroke(Color.secondary.opacity(0.45), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [3, 3]))
                            .frame(width: 2)
                        Image(systemName: "bus.fill")
                            .font(.system(size: 20))
                            .foregroundStyle(AppTheme.brandOrange)
                            .padding(.vertical, 2)
                    }
                    .frame(width: 48)
                    .frame(maxHeight: .infinity)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(stop.title).font(.subheadline.weight(.medium))
                        if let subtitle = stop.subtitle { Text(subtitle).font(.caption).foregroundStyle(.secondary) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 8)
                    trailing(index)
                }
                .fixedSize(horizontal: false, vertical: true)
                .contentShape(Rectangle())
                .onTapGesture { onTap?(index) }
            }
        }
    }
}

extension BusStopsTimeline where Trailing == EmptyView {
    init(stops: [BusStopTimelineItem]) { self.init(stops: stops, onTap: nil) { _ in EmptyView() } }
}

private struct DashedVerticalLine: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        return path
    }
}

/// Icon + fixed-width label + value row used in Journey/Contact/Summary cards.
struct BusInfoRow: View {
    let icon: String
    let label: String
    let value: String
    var labelWidth: CGFloat = 84

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Image(systemName: icon).font(.subheadline).foregroundStyle(.primary).frame(width: 20)
            Text(label).font(.subheadline).foregroundStyle(.primary).frame(width: labelWidth, alignment: .leading)
            Text(value).font(.subheadline.weight(.medium)).frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 3)
    }
}

/// Rounded soft card used throughout the Bus screens.
struct BusCard<Content: View>: View {
    var title: String? = nil
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let title { Text(title).font(.headline) }
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
    }
}

/// Small status pill for bus bookings (Android `StatusChip`).
struct BusBookingStatusChip: View {
    let status: String

    var body: some View {
        Text(label)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 8).padding(.vertical, 5)
            .background(color.opacity(0.12), in: Capsule())
    }

    private var label: String {
        switch status {
        case "pending": "Pending"
        case "confirmed": "Confirmed"
        case "cancellation_requested": "Cancellation Requested"
        case "payment_uploaded": "Payment Sent"
        case "rejected": "Rejected"
        case "cancelled": "Cancelled"
        case "expired": "Expired"
        default: status.prefix(1).uppercased() + status.dropFirst()
        }
    }

    private var color: Color {
        switch status {
        case "pending": AppTheme.brandOrange
        case "confirmed": .green
        case "cancellation_requested", "payment_uploaded": AppTheme.eventPurple
        case "rejected", "cancelled": .red
        default: .secondary
        }
    }
}
