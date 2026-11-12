import Combine
import SwiftUI

/// Loads a bus and fills in the return leg's times when the server omitted them
/// (Android `BusRepository.getBusDetailWithReturnTimes`).
enum BusLoader {
    static func detailWithReturnTimes(_ busID: String, using appState: AppState) async throws -> BusDetailData {
        var bus: BusDetailData = try await appState.authGet("buses/\(busID)")
        if bus.isRoundTrip, let returnID = bus.returnBusId, !returnID.isEmpty, (bus.returnArrivalTime ?? "").isEmpty {
            if let returnBus: BusDetailData = try? await appState.authGet("buses/\(returnID)") {
                bus.returnDepartureTime = returnBus.departureTime
                bus.returnArrivalTime = returnBus.arrivalTime
            }
        }
        return bus
    }
}

/// Android `BusDetailViewModel`.
@MainActor
final class BusDetailViewModel: ObservableObject {
    @Published private(set) var bus: BusDetailData?
    @Published private(set) var isLoading = false
    @Published private(set) var error: String?

    // Booking form
    @Published var seatCount = 1
    @Published var passengerName = ""
    @Published var passengerPhone = "" {
        didSet {
            let cleaned = String(passengerPhone.filter(\.isNumber).prefix(10))
            if cleaned != passengerPhone { passengerPhone = cleaned }
        }
    }
    @Published var selectedPickupID: String?
    @Published private(set) var isBooking = false
    @Published var bookingError: String?

    func load(_ busID: String, using appState: AppState) async {
        isLoading = true; error = nil
        do {
            let loaded = try await BusLoader.detailWithReturnTimes(busID, using: appState)
            bus = loaded
            selectedPickupID = loaded.stops.first?.id
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    func prepareBookingForm(using appState: AppState) {
        bookingError = nil
        seatCount = 1
        passengerName = appState.session.user?.name ?? ""
        passengerPhone = appState.session.user?.mobile ?? ""
        if selectedPickupID == nil { selectedPickupID = bus?.stops.first?.id }
    }

    var maxSeats: Int { max(1, bus?.seatAvailability.availableSeats ?? 1) }

    func confirmBooking(using appState: AppState) async -> BookingData? {
        guard let bus, let pickupID = selectedPickupID else { return nil }
        if passengerName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            bookingError = "Please enter passenger name"; return nil
        }
        if passengerPhone.count < 10 {
            bookingError = "Please enter a valid 10-digit phone number"; return nil
        }
        isBooking = true; bookingError = nil
        defer { isBooking = false }
        do {
            let name = passengerName.trimmingCharacters(in: .whitespacesAndNewlines)
            let request = CreateBookingRequest(busId: bus.id, pickupPointId: pickupID, seatCount: seatCount, passengerName: name, passengerPhone: passengerPhone.trimmingCharacters(in: .whitespaces))
            let booking: BookingData = try await appState.authPost("bookings", body: request)
            appState.session.updateUserNameIfBlank(name)
            seatCount = 1
            return booking
        } catch {
            bookingError = error.localizedDescription
            return nil
        }
    }
}

/// Consumer bus details (Android `BusDetailScreen`).
struct BusDetailView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var viewModel = BusDetailViewModel()
    let busID: String
    let onBookingSuccess: () -> Void

    @State private var showBookingSheet = false
    @State private var pendingResult: BookingData?
    @State private var bookingResult: BookingData?

    init(busID: String, onBookingSuccess: @escaping () -> Void) {
        self.busID = busID
        self.onBookingSuccess = onBookingSuccess
    }

    var body: some View {
        Group {
            if viewModel.isLoading && viewModel.bus == nil {
                LoadingView()
            } else if let error = viewModel.error, viewModel.bus == nil {
                ErrorStateView(message: error) { Task { await viewModel.load(busID, using: appState) } }
            } else if let bus = viewModel.bus {
                content(bus)
            } else {
                LoadingView()
            }
        }
        .screenTitle("Bus Details")
        .task { await viewModel.load(busID, using: appState) }
        .sheet(isPresented: $showBookingSheet, onDismiss: {
            if let pendingResult { bookingResult = pendingResult; self.pendingResult = nil }
        }) {
            if let bus = viewModel.bus {
                BusBookingSheet(bus: bus, viewModel: viewModel) { booking in
                    pendingResult = booking
                    showBookingSheet = false
                }
            }
        }
        .sheet(item: $bookingResult) { booking in
            BookingRequestedSheet(booking: booking) {
                bookingResult = nil
                Task { await viewModel.load(busID, using: appState) }
                onBookingSuccess()
            }
            .presentationDetents([.medium])
            .interactiveDismissDisabled()
        }
    }

    private func content(_ bus: BusDetailData) -> some View {
        let availability = bus.seatAvailability
        return ScrollView {
            VStack(spacing: 16) {
                BusCard {
                    Text(BusFormat.routeTitle(bus.route.source, bus.route.destination, tripType: bus.tripType))
                        .font(.title2.weight(.bold))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }

                BusCard(title: "Journey Details") {
                    VStack(spacing: 2) {
                        BusInfoRow(icon: "airplane.departure", label: "Departure", value: bus.departureTime.fullIndiaDateTime)
                        if let arrival = bus.finalArrivalTime {
                            BusInfoRow(icon: "airplane.arrival", label: "Arrival", value: arrival.fullIndiaDateTime)
                        }
                        BusInfoRow(icon: "arrow.left.arrow.right", label: "Trip Type", value: BusFormat.tripTypeLabel(bus.tripType))
                        BusInfoRow(icon: "carseat.right.fill", label: "Seats", value: availability.summary)
                    }
                }

                if !bus.stops.isEmpty {
                    BusCard(title: "Stops") {
                        BusStopsTimeline(stops: bus.stops.map { stop in
                            let address = stop.address?.trimmingCharacters(in: .whitespacesAndNewlines)
                            return BusStopTimelineItem(id: stop.id, title: stop.name, subtitle: (address?.isEmpty == false && address != stop.name) ? address : nil)
                        })
                    }
                }

                if bus.contactName != nil || bus.contactPhone != nil {
                    BusCard(title: "Contact") {
                        VStack(spacing: 2) {
                            if let name = bus.contactName { BusInfoRow(icon: "person.fill", label: "Name", value: name) }
                            if let phone = bus.contactPhone {
                                if let url = URL(string: "tel:\(phone.filter { $0.isNumber || $0 == "+" })") {
                                    Link(destination: url) {
                                        BusInfoRow(icon: "phone.fill", label: "Phone", value: phone)
                                    }
                                    .foregroundStyle(.primary)
                                } else {
                                    BusInfoRow(icon: "phone.fill", label: "Phone", value: phone)
                                }
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .refreshable { await viewModel.load(busID, using: appState) }
        .safeAreaInset(edge: .bottom) {
            if availability.availableSeats > 0 {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(BusFormat.rupees(bus.price)) / seat").font(.headline)
                        Text("\(availability.availableSeats) \(availability.availableSeats == 1 ? "seat" : "seats") left").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button {
                        if appState.session.isLoggedIn {
                            viewModel.prepareBookingForm(using: appState)
                            showBookingSheet = true
                        } else {
                            appState.loginSheetPresented = true
                        }
                    } label: {
                        Label("Book Ticket", systemImage: "ticket.fill")
                            .font(.body.weight(.semibold))
                            .padding(.horizontal, 18).frame(height: 46)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.white)
                    .background(AppTheme.brandOrange, in: RoundedRectangle(cornerRadius: 14))
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
                .background(AppTheme.brandOrange.opacity(0.08)).background(.bar)
            }
        }
    }
}

/// Android `BookingDialog`.
private struct BusBookingSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    let bus: BusDetailData
    @ObservedObject var viewModel: BusDetailViewModel
    let onBooked: (BookingData) -> Void
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(BusFormat.routeTitle(bus.route.source, bus.route.destination, tripType: bus.tripType)).font(.headline)
                }
                Section("Passenger") {
                    TextField("Passenger Name", text: $viewModel.passengerName)
                        .textContentType(.name).focused($focused)
                    TextField("Phone Number", text: $viewModel.passengerPhone)
                        .keyboardType(.phonePad).textContentType(.telephoneNumber).focused($focused)
                }
                Section("Seats") {
                    Stepper(value: $viewModel.seatCount, in: 1...viewModel.maxSeats) {
                        HStack {
                            Text("Seats")
                            Spacer()
                            Text("\(viewModel.seatCount)").font(.headline).monospacedDigit()
                        }
                    }
                }
                if !bus.stops.isEmpty {
                    Section {
                        Picker("Pickup Point", selection: $viewModel.selectedPickupID) {
                            ForEach(bus.stops) { stop in Text(stop.name).tag(Optional(stop.id)) }
                        }
                    }
                }
                Section {
                    HStack {
                        Text("Total").font(.headline)
                        Spacer()
                        Text(BusFormat.rupees(bus.price * Double(viewModel.seatCount))).font(.title3.weight(.bold))
                    }
                    if let error = viewModel.bookingError {
                        Text(error).font(.footnote).foregroundStyle(.red)
                    }
                }
                Section {
                    Button {
                        focused = false
                        Task { if let booking = await viewModel.confirmBooking(using: appState) { onBooked(booking) } }
                    } label: {
                        HStack {
                            Spacer()
                            if viewModel.isBooking { ProgressView().padding(.trailing, 6) }
                            Text("Confirm").fontWeight(.semibold)
                            Spacer()
                        }
                    }
                    .disabled(viewModel.isBooking || viewModel.selectedPickupID == nil)
                }
            }
            .navigationTitle("Book Ticket")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(viewModel.isBooking)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") { focused = false }
                }
            }
        }
        .interactiveDismissDisabled(viewModel.isBooking)
    }
}

/// Android "Booking Requested!" dialog.
private struct BookingRequestedSheet: View {
    let booking: BookingData
    let onOK: () -> Void

    var body: some View {
        StandardBottomSheet(icon: "checkmark.circle.fill", iconColor: .green, title: "Booking Requested!", message: message) {
            BottomSheetActionButton(title: "OK", action: onOK)
        }
    }

    private var message: String {
        var lines = ["\(booking.seatCount) seat(s) requested successfully."]
        if let seats = booking.assignedSeats, !seats.isEmpty { lines.append("Seat Numbers: \(seats)") }
        lines.append("")
        lines.append("Total: \(BusFormat.rupees(booking.totalAmount))")
        lines.append("Status: \(booking.status.prefix(1).uppercased() + booking.status.dropFirst())")
        lines.append("")
        lines.append("Your request will be reviewed by the admin.")
        return lines.joined(separator: "\n")
    }
}
