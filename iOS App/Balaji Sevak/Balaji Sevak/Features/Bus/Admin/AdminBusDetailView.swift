import Combine
import SwiftUI
import UIKit

/// Android `AdminBusDetailViewModel`.
@MainActor
final class AdminBusDetailViewModel: ObservableObject {
    @Published private(set) var bus: BusDetailData?
    @Published private(set) var bookings: [BookingData] = []
    @Published private(set) var isLoading = false
    @Published private(set) var bookingsLoading = false
    @Published private(set) var bookingsError: String?
    @Published private(set) var error: String?
    @Published private(set) var actionInProgress: String?
    @Published private(set) var isDeletingBus = false
    @Published var actionError: String?
    @Published var deleteError: String?
    @Published var deleteSuccessMessage: String?

    let busID: String
    init(busID: String) { self.busID = busID }

    func loadAll(using appState: AppState) async {
        isLoading = true; error = nil
        do {
            let loaded = try await BusLoader.detailWithReturnTimes(busID, using: appState)
            bus = loaded
            isLoading = false
            await loadBookings(using: appState)
        } catch {
            isLoading = false
            self.error = error.localizedDescription
        }
    }

    func loadBookings(using appState: AppState) async {
        bookingsLoading = true; bookingsError = nil
        defer { bookingsLoading = false }
        let primary: [BookingData]
        do {
            primary = try await appState.authGet(Self.bookingsPath(busID))
        } catch {
            bookingsError = error.localizedDescription
            return
        }
        var combined = primary
        if let bus, bus.isRoundTrip, let returnID = bus.returnBusId, !returnID.isEmpty, returnID != busID {
            do {
                let returnBookings: [BookingData] = try await appState.authGet(Self.bookingsPath(returnID))
                combined += returnBookings
            } catch {
                bookings = Self.sortedForReview(primary)
                bookingsError = error.localizedDescription
                return
            }
        }
        var seen = Set<String>()
        bookings = Self.sortedForReview(combined.filter { seen.insert($0.id).inserted })
        bookingsError = nil
    }

    func updateStatus(_ bookingID: String, action: String, using appState: AppState) async {
        await perform(bookingID, using: appState) {
            let _: BusIgnoredResponse = try await appState.authPut("admin/bookings/\(bookingID)/status", body: UpdateBookingStatusRequest(action: action))
        }
    }

    func cancelBooking(_ bookingID: String, using appState: AppState) async {
        await perform(bookingID, using: appState) {
            let _: BusIgnoredResponse = try await appState.authPost("admin/bookings/\(bookingID)/cancel", body: BusBookingCancellationRequest(reason: nil))
        }
    }

    func deleteBus(using appState: AppState) async {
        isDeletingBus = true; deleteError = nil; deleteSuccessMessage = nil
        do {
            let result: DeleteBusResult = try await appState.authDelete("admin/buses/\(busID)")
            deleteSuccessMessage = result.message
        } catch {
            deleteError = error.localizedDescription
        }
        isDeletingBus = false
    }

    private func perform(_ bookingID: String, using appState: AppState, _ work: () async throws -> Void) async {
        actionInProgress = bookingID
        do {
            try await work()
            actionInProgress = nil
            await loadAll(using: appState)
        } catch {
            actionInProgress = nil
            actionError = error.localizedDescription
        }
    }

    private static func bookingsPath(_ busID: String) -> String {
        "admin/bookings?busId=\(busID.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? busID)&limit=100"
    }

    static func sortedForReview(_ bookings: [BookingData]) -> [BookingData] {
        let rank = ["pending": 0, "payment_uploaded": 1, "cancellation_requested": 2, "confirmed": 3, "rejected": 4, "cancelled": 5, "expired": 6]
        return bookings.sorted { a, b in
            let ra = rank[a.status] ?? 99, rb = rank[b.status] ?? 99
            if ra != rb { return ra < rb }
            let da = AppFormatters.parseISO(a.createdAt) ?? .distantPast
            let db = AppFormatters.parseISO(b.createdAt) ?? .distantPast
            return da > db
        }
    }
}

/// Android `AdminBusDetailScreen`.
struct AdminBusDetailView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var viewModel: AdminBusDetailViewModel
    let onClose: () -> Void

    @State private var showDeleteConfirm = false
    @State private var cancelTarget: BookingData?
    @State private var exportFile: BusExportFile?
    @State private var exportFailed = false

    init(busID: String, onClose: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: AdminBusDetailViewModel(busID: busID))
        self.onClose = onClose
    }

    var body: some View {
        Group {
            if viewModel.isLoading && viewModel.bus == nil {
                LoadingView()
            } else if let error = viewModel.error, viewModel.bus == nil {
                ErrorStateView(message: error) { Task { await viewModel.loadAll(using: appState) } }
            } else {
                content
            }
        }
        .navigationTitle("Bus Details")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { Task { await viewModel.loadAll(using: appState) } } label: { Image(systemName: "arrow.clockwise") }
                    .accessibilityLabel("Refresh bookings")
                if !viewModel.bookings.isEmpty {
                    Button { export() } label: { Label("Export", systemImage: "square.and.arrow.up") }
                        .labelStyle(.titleAndIcon)
                }
            }
        }
        .task { await viewModel.loadAll(using: appState) }
        .alert("Delete the bus", isPresented: $showDeleteConfirm) {
            Button("Keep Bus", role: .cancel) {}
            Button("Delete", role: .destructive) { Task { await viewModel.deleteBus(using: appState) } }
        } message: {
            Text("If this future trip has no bookings, it will be deleted. If bookings exist, the bus will be cancelled and bookings will be rejected or cancelled.")
        }
        .alert("Cancel booking", isPresented: Binding(get: { cancelTarget != nil }, set: { if !$0 { cancelTarget = nil } }), presenting: cancelTarget) { booking in
            Button("Keep", role: .cancel) {}
            Button("Cancel Booking", role: .destructive) {
                Task {
                    if booking.status == "cancellation_requested" {
                        await viewModel.updateStatus(booking.id, action: "approve_cancellation", using: appState)
                    } else {
                        await viewModel.cancelBooking(booking.id, using: appState)
                    }
                }
            }
        } message: { _ in
            Text("This will release the assigned seat for new bookings.")
        }
        .alert("Bus deleted", isPresented: Binding(get: { viewModel.deleteSuccessMessage != nil }, set: { _ in })) {
            Button("OK") { viewModel.deleteSuccessMessage = nil; onClose() }
        } message: {
            Text(viewModel.deleteSuccessMessage ?? "")
        }
        .sheet(isPresented: Binding(get: { viewModel.actionError != nil }, set: { if !$0 { viewModel.actionError = nil } })) {
            AppErrorSheet(message: viewModel.actionError ?? "Unable to update the booking.") { viewModel.actionError = nil }
                .presentationDetents([.height(270)])
                .presentationDragIndicator(.visible)
        }
        .sheet(item: $exportFile) { file in
            BusActivityView(items: [file.url]).ignoresSafeArea()
        }
        .alert("Unable to export bookings PDF", isPresented: $exportFailed) { Button("OK") {} }
    }

    private var content: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                if let bus = viewModel.bus { BusSummaryCard(bus: bus, bookings: viewModel.bookings) }

                Text("Bookings (\(viewModel.bookings.count))").font(.headline).padding(.top, 8)

                if let message = viewModel.bookingsError {
                    VStack(alignment: .leading, spacing: 10) {
                        Label("Could not load all booking requests", systemImage: "exclamationmark.circle")
                            .font(.subheadline.weight(.semibold)).foregroundStyle(.red)
                        Text(message).font(.caption).foregroundStyle(.secondary)
                        Button { Task { await viewModel.loadBookings(using: appState) } } label: {
                            Label("Retry", systemImage: "arrow.clockwise")
                        }
                        .buttonStyle(.bordered)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
                }

                if viewModel.bookings.isEmpty && !viewModel.bookingsLoading && viewModel.bookingsError == nil {
                    VStack(spacing: 8) {
                        Image(systemName: "tray").font(.system(size: 36)).foregroundStyle(.tertiary)
                        Text("No booking requests yet").font(.subheadline).foregroundStyle(.secondary)
                    }
                    .padding(32)
                    .frame(maxWidth: .infinity)
                    .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
                }

                ForEach(viewModel.bookings) { booking in
                    AdminBookingCard(
                        booking: booking,
                        isActionInProgress: viewModel.actionInProgress == booking.id,
                        isBusy: viewModel.actionInProgress != nil,
                        onApprove: { Task { await viewModel.updateStatus(booking.id, action: "approve", using: appState) } },
                        onReject: { Task { await viewModel.updateStatus(booking.id, action: "reject", using: appState) } },
                        onKeep: { Task { await viewModel.updateStatus(booking.id, action: "reject_cancellation", using: appState) } },
                        onCancel: { cancelTarget = booking }
                    )
                }

                if viewModel.bus != nil { deleteAction.padding(.top, 8) }
            }
            .padding(16)
        }
        .overlay(alignment: .top) {
            if viewModel.bookingsLoading || viewModel.isDeletingBus { ProgressView().progressViewStyle(.linear).tint(AppTheme.brandOrange) }
        }
        .refreshable { await viewModel.loadAll(using: appState) }
    }

    private var deleteAction: some View {
        VStack(spacing: 8) {
            Button { showDeleteConfirm = true } label: {
                HStack(spacing: 8) {
                    if viewModel.isDeletingBus { ProgressView() } else { Image(systemName: "trash") }
                    Text("Delete the Bus").fontWeight(.semibold)
                }
                .frame(maxWidth: .infinity).frame(height: 52)
            }
            .buttonStyle(.plain)
            .foregroundStyle(.red)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.red, lineWidth: 1))
            .disabled(viewModel.isDeletingBus)
            if let error = viewModel.deleteError {
                Text(error).font(.caption).foregroundStyle(.red).multilineTextAlignment(.center).frame(maxWidth: .infinity)
            }
        }
    }

    private func export() {
        do {
            let url = try BusBookingsPDF.make(bus: viewModel.bus, bookings: viewModel.bookings)
            exportFile = BusExportFile(url: url)
        } catch {
            exportFailed = true
        }
    }
}

private struct BusSummaryCard: View {
    let bus: BusDetailData
    let bookings: [BookingData]

    var body: some View {
        let availability = bus.seatAvailability(fromBookings: bookings)
        let confirmedAmount = bookings.filter { $0.status == "confirmed" }.reduce(0) { $0 + $1.totalAmount }
        BusCard {
            Text(BusFormat.routeTitle(bus.route.source, bus.route.destination, tripType: bus.tripType)).font(.title2.weight(.bold))
            VStack(spacing: 0) {
                BusInfoRow(icon: "airplane.departure", label: "Departure", value: bus.departureTime.fullIndiaDateTime, labelWidth: 96)
                if let arrival = bus.finalArrivalTime {
                    BusInfoRow(icon: "airplane.arrival", label: "Arrival", value: arrival.fullIndiaDateTime, labelWidth: 96)
                }
                BusInfoRow(icon: "carseat.right.fill", label: "Seats", value: availability.summary, labelWidth: 96)
                BusInfoRow(icon: "ticket.fill", label: "Requested", value: "\(availability.reservedSeats) requested / \(availability.totalSeats) total", labelWidth: 96)
                BusInfoRow(icon: "indianrupeesign.circle", label: "Price", value: "\(BusFormat.rupees(bus.price)) per seat", labelWidth: 96)
                BusInfoRow(icon: "indianrupeesign.circle.fill", label: "Total amount", value: BusFormat.rupees(confirmedAmount), labelWidth: 96)
                if let name = bus.contactName {
                    BusInfoRow(icon: "person.fill", label: "Contact", value: name + (bus.contactPhone.map { " (\($0))" } ?? ""), labelWidth: 96)
                }
                BusInfoRow(icon: "arrow.left.arrow.right", label: "Type", value: BusFormat.tripTypeLabel(bus.tripType), labelWidth: 96)
            }
        }
    }
}

private struct AdminBookingCard: View {
    let booking: BookingData
    let isActionInProgress: Bool
    let isBusy: Bool
    let onApprove: () -> Void
    let onReject: () -> Void
    let onKeep: () -> Void
    let onCancel: () -> Void

    private static let actionable: Set<String> = ["pending", "payment_uploaded", "confirmed", "cancellation_requested"]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(booking.displayName ?? "Unknown").font(.subheadline.weight(.bold)).lineLimit(1)
                    let phone = booking.displayPhone ?? ""
                    if !phone.isEmpty {
                        if let url = URL(string: "tel:\(phone.filter { $0.isNumber || $0 == "+" })") {
                            Link(phone, destination: url).font(.caption)
                        } else {
                            Text(phone).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    if let route = booking.route {
                        Text("\(route.source) to \(route.destination)").font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                Spacer()
                BusBookingStatusChip(status: booking.status)
            }

            HStack {
                Label(booking.assignedSeats.map { "Seats: \($0)" } ?? "\(booking.seatCount) seat(s)", systemImage: "carseat.right.fill")
                    .font(.subheadline)
                Spacer()
                Text(BusFormat.rupees(booking.totalAmount)).font(.subheadline.weight(.semibold))
            }

            if let pickup = booking.pickupPoint {
                Label(pickup.name, systemImage: "mappin.and.ellipse").font(.caption).foregroundStyle(.secondary)
            }
            if let createdAt = booking.createdAt {
                Text("Requested: \(createdAt.fullIndiaDateTime)").font(.caption2).foregroundStyle(.tertiary)
            }

            if Self.actionable.contains(booking.status) {
                HStack(spacing: 8) {
                    Spacer()
                    if isActionInProgress {
                        ProgressView()
                    } else if booking.status == "cancellation_requested" {
                        Button(action: onKeep) { Label("Keep Booking", systemImage: "arrow.uturn.backward") }
                            .buttonStyle(.bordered)
                        Button(action: onCancel) { Label("Cancel", systemImage: "xmark.circle") }
                            .buttonStyle(.bordered).tint(.red)
                    } else if booking.status == "confirmed" {
                        Button(action: onCancel) { Label("Cancel Booking", systemImage: "xmark.circle") }
                            .buttonStyle(.bordered).tint(.red)
                    } else {
                        Button(action: onReject) { Label("Reject", systemImage: "xmark") }
                            .buttonStyle(.bordered).tint(.red)
                        Button(action: onApprove) { Label("Approve", systemImage: "checkmark") }
                            .buttonStyle(.borderedProminent).tint(AppTheme.brandOrange)
                    }
                }
                .font(.subheadline.weight(.semibold))
                .disabled(isBusy)
                .padding(.top, 4)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
    }
}

// MARK: - PDF export

struct BusExportFile: Identifiable { let id = UUID(); let url: URL }

struct BusActivityView: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// Android `createBookingsPdf`: A4 passenger list for the trip.
enum BusBookingsPDF {
    static func make(bus: BusDetailData?, bookings: [BookingData]) throws -> URL {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("exports", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let source = safeFilePart(bus?.route.source ?? "bookings")
        let destination = safeFilePart(bus?.route.destination ?? "trip")
        let url = dir.appendingPathComponent("bookings_\(source)_to_\(destination).pdf")

        let pageRect = CGRect(x: 0, y: 0, width: 595, height: 842)
        let margin: CGFloat = 40
        let contentWidth = pageRect.width - margin * 2

        let title: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 20), .foregroundColor: UIColor.black]
        let section: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 14), .foregroundColor: UIColor.black]
        let body: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 11), .foregroundColor: UIColor(white: 40 / 255, alpha: 1)]
        let muted: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 10), .foregroundColor: UIColor(white: 95 / 255, alpha: 1)]

        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = [kCGPDFContextTitle as String: "Booking Requests"]
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect, format: format)

        try renderer.writePDF(to: url) { context in
            var y = margin
            context.beginPage()

            func ensureSpace(_ required: CGFloat) {
                if y + required > pageRect.height - margin { context.beginPage(); y = margin }
            }
            func draw(_ text: String, _ attributes: [NSAttributedString.Key: Any], extraBottom: CGFloat = 4) {
                let string = NSAttributedString(string: text, attributes: attributes)
                let height = ceil(string.boundingRect(with: CGSize(width: contentWidth, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil).height)
                ensureSpace(height + 4)
                string.draw(with: CGRect(x: margin, y: y, width: contentWidth, height: height), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
                y += height + 2 + extraBottom
            }
            func separator() {
                ensureSpace(12)
                let cg = context.cgContext
                cg.setStrokeColor(UIColor(white: 210 / 255, alpha: 1).cgColor)
                cg.setLineWidth(1)
                cg.move(to: CGPoint(x: margin, y: y)); cg.addLine(to: CGPoint(x: pageRect.width - margin, y: y)); cg.strokePath()
                y += 12
            }

            draw("Booking Requests", title, extraBottom: 8)
            if let bus {
                draw("\(bus.route.source) to \(bus.route.destination)", section)
                draw("Departure: \(bus.departureTime.fullIndiaDateTime)", body)
                if let arrival = bus.finalArrivalTime { draw("Arrival: \(arrival.fullIndiaDateTime)", body) }
                let availability = bus.seatAvailability(fromBookings: bookings)
                draw(availability.summary, body)
                draw("\(availability.reservedSeats) requested / \(availability.totalSeats) total", body)
                draw("Fare: Rs \(Int64(bus.price)) per seat", body, extraBottom: 8)
            }
            separator()
            draw("Passenger List (\(bookings.count) requests)", section, extraBottom: 8)
            for (index, booking) in bookings.enumerated() {
                ensureSpace(86)
                draw("\(index + 1). \(booking.displayName ?? "N/A")", section)
                draw("Phone: \(booking.displayPhone ?? "N/A")", body)
                draw("Seats: \(booking.assignedSeats ?? String(booking.seatCount))", body)
                if let pickup = booking.pickupPoint { draw("Pickup: \(pickup.name)", body) }
                draw("Amount: Rs \(Int64(booking.totalAmount))", body)
                draw("Status: \(BusFormat.titleLabel(booking.status))", muted, extraBottom: 6)
                separator()
            }
        }
        return url
    }

    private static func safeFilePart(_ value: String) -> String {
        let replaced = value.replacingOccurrences(of: "[^A-Za-z0-9_-]+", with: "_", options: .regularExpression)
        let trimmed = replaced.trimmingCharacters(in: CharacterSet(charactersIn: "_"))
        return String((trimmed.isEmpty ? "trip" : trimmed).prefix(40))
    }
}
