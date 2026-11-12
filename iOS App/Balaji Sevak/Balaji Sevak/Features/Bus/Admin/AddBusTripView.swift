import Combine
import SwiftUI

struct PredefinedStop: Identifiable, Equatable {
    var id: String { name }
    let name: String
    var selected = true
}

/// Android `AddBusViewModel`.
@MainActor
final class AddBusViewModel: ObservableObject {
    static let defaultStops = ["Rani Bagh", "Rajori Garden", "Dhola Kuan", "Rajiv Chowk"].map { PredefinedStop(name: $0) }

    @Published var source = "Delhi"
    @Published var destination = "Mahandipur Balaji"
    /// Instants chosen as India wall-clock date + time.
    @Published var departure: Date?
    @Published var arrival: Date?
    @Published var totalSeats = ""
    /// Seats 1...reserved are not for sale; booking starts at seat reserved + 1.
    @Published var reservedSeatsFromStart = "0"
    @Published var price = ""
    @Published var contactName = ""
    @Published var contactPhone = ""
    @Published var stops = AddBusViewModel.defaultStops
    @Published private(set) var isLoading = false
    @Published var error: String?
    @Published var createdTrip: TripData?

    func reset() {
        source = "Delhi"; destination = "Mahandipur Balaji"
        departure = nil; arrival = nil
        totalSeats = ""; reservedSeatsFromStart = "0"; price = ""
        contactName = ""; contactPhone = ""
        stops = Self.defaultStops
        isLoading = false; error = nil; createdTrip = nil
    }

    func toggleStop(_ index: Int) {
        guard stops.indices.contains(index) else { return }
        stops[index].selected.toggle()
    }

    var seatsForSale: (count: Int, from: Int, to: Int)? {
        guard let total = Int(totalSeats) else { return nil }
        let reserved = Int(reservedSeatsFromStart) ?? 0
        guard reserved >= 0, reserved < total else { return nil }
        return (total - reserved, reserved + 1, total)
    }

    /// nil when valid; otherwise every issue in one message (Android `validateCreateTripForm`).
    func validate() -> String? {
        var issues: [String] = []
        if source.isBlank { issues.append("Source is empty") }
        if destination.isBlank { issues.append("Destination is empty") }
        if departure == nil { issues.append("Departure date and time must both be set") }
        if arrival == nil { issues.append("Arrival date and time must both be set") }
        if let departure, let arrival, !(arrival > departure) { issues.append("Arrival must be after departure") }
        if totalSeats.isBlank { issues.append("Total seats is empty") }
        else if let seats = Int(totalSeats), seats >= 1 {} else { issues.append("Total seats must be a positive number") }
        let total = Int(totalSeats)
        let reserved = Int(reservedSeatsFromStart) ?? 0
        if !reservedSeatsFromStart.isBlank && reserved < 0 { issues.append("Reserved seats cannot be negative") }
        if let total, reserved >= total {
            issues.append("Reserved seats must be less than total seats (so there is at least one seat available for booking)")
        }
        if price.isBlank { issues.append("Price is empty") }
        else if let value = Double(price), value > 0 {} else { issues.append("Price must be a number greater than 0") }
        if contactName.isBlank { issues.append("Contact name is empty") }
        if contactPhone.isBlank { issues.append("Contact phone is empty") }
        guard !issues.isEmpty else { return nil }
        return "Fix the following before creating the trip:\n\n" + issues.map { "• \($0)" }.joined(separator: "\n")
    }

    func createTrip(using appState: AppState) async {
        if let message = validate() { error = message; return }
        guard let departure, let arrival, let seats = Int(totalSeats), let fare = Double(price) else { return }
        let reserved = Int(reservedSeatsFromStart) ?? 0
        // Schedules are India wall-clock times; send the matching UTC instant.
        let departureISO = AppFormatters.isoString(departure)
        let arrivalISO = AppFormatters.isoString(arrival)
        let request = CreateTripRequest(
            source: source.trimmingCharacters(in: .whitespacesAndNewlines),
            destination: destination.trimmingCharacters(in: .whitespacesAndNewlines),
            departureTime: departureISO,
            arrivalTime: arrivalISO,
            totalSeats: seats,
            seatStartNumber: reserved + 1,
            price: fare,
            contactName: contactName.trimmingCharacters(in: .whitespacesAndNewlines),
            contactPhone: contactPhone.trimmingCharacters(in: .whitespacesAndNewlines),
            tripType: "round_trip",
            stops: stops.filter(\.selected).map { StopRequest(name: $0.name.trimmingCharacters(in: .whitespacesAndNewlines), address: nil) },
            returnDepartureTime: nil,
            returnArrivalTime: arrivalISO
        )
        isLoading = true; error = nil
        do {
            createdTrip = try await appState.authPost("admin/trips", body: request)
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }
}

/// Android `AddBusScreen`.
struct AddBusTripView: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: AddBusViewModel
    let onSelectStops: () -> Void
    let onDone: () -> Void
    @FocusState private var focused: Bool

    var body: some View {
        Form {
            Section {
                TextField("Source", text: $viewModel.source, prompt: Text("e.g. Delhi")).focused($focused)
                TextField("Destination", text: $viewModel.destination, prompt: Text("e.g. Mumbai")).focused($focused)
            } header: { Text("Route") }

            Section("Departure") {
                BusDateTimeField(value: $viewModel.departure)
            }
            Section("Arrival Back To Origin") {
                BusDateTimeField(value: $viewModel.arrival)
            }

            Section {
                LabeledContent("Total seats") {
                    TextField("0", text: filtered($viewModel.totalSeats) { $0.allSatisfy(\.isASCIIDigit) && $0.count <= 3 })
                        .keyboardType(.numberPad).multilineTextAlignment(.trailing).focused($focused)
                }
                LabeledContent("Reserved") {
                    TextField("e.g. 10", text: filtered($viewModel.reservedSeatsFromStart) { $0.allSatisfy(\.isASCIIDigit) && $0.count <= 4 })
                        .keyboardType(.numberPad).multilineTextAlignment(.trailing).focused($focused)
                }
                LabeledContent("Price (₹)") {
                    TextField("0", text: filtered($viewModel.price) { $0.range(of: #"^\d*\.?\d{0,2}$"#, options: .regularExpression) != nil })
                        .keyboardType(.decimalPad).multilineTextAlignment(.trailing).focused($focused)
                }
            } header: { Text("Seat") } footer: {
                if let sale = viewModel.seatsForSale {
                    Text("Up to \(sale.count) seat(s) can be booked (sales from seat #\(sale.from) to #\(sale.to)).")
                }
            }

            Section("Point of Contact") {
                TextField("Contact Name", text: $viewModel.contactName).textContentType(.name).focused($focused)
                TextField("Contact Phone", text: filtered($viewModel.contactPhone) { $0.count <= 15 && $0.allSatisfy { $0.isASCIIDigit || $0 == "+" } })
                    .keyboardType(.phonePad).focused($focused)
            }

            Section("Stops (In Between)") {
                let selected = viewModel.stops.filter(\.selected)
                Button(action: onSelectStops) {
                    HStack {
                        Text("\(selected.count) of \(viewModel.stops.count) stops selected").foregroundStyle(.primary)
                        Spacer()
                        Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                    }
                }
                if !selected.isEmpty {
                    BusChipFlow(items: selected.map(\.name))
                }
            }

            Section {
                Button {
                    focused = false
                    Task { await viewModel.createTrip(using: appState) }
                } label: {
                    HStack {
                        Spacer()
                        if viewModel.isLoading { ProgressView().tint(.white) } else { Text("Create Trip").fontWeight(.semibold) }
                        Spacer()
                    }
                    .frame(height: 50)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.white)
                .listRowInsets(EdgeInsets())
                .listRowBackground(AppTheme.brandOrange)
                .disabled(viewModel.isLoading)
            }
        }
        .navigationTitle("Add Bus Trip")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { focused = false }
            }
        }
        .alert(errorTitle, isPresented: Binding(get: { viewModel.error != nil }, set: { if !$0 { viewModel.error = nil } })) {
            Button("OK") { viewModel.error = nil }
        } message: {
            Text(viewModel.error ?? "")
        }
        .alert("Trip Created", isPresented: Binding(get: { viewModel.createdTrip != nil }, set: { _ in })) {
            Button("Done") {
                viewModel.reset()
                onDone()
            }
        } message: {
            Text(createdMessage)
        }
    }

    private var errorTitle: String { (viewModel.error ?? "").hasPrefix("Fix the following") ? "Create trip blocked" : "Error" }

    private var createdMessage: String {
        guard let trip = viewModel.createdTrip else { return "" }
        let outbound = trip.outbound
        var lines: [String] = []
        if let route = outbound.route { lines.append("\(route.source) to \(route.destination)") }
        lines.append("Bus: \(outbound.busName ?? "-")")
        lines.append("Seats: \(outbound.totalSeats.map(String.init) ?? "-") | Fare: \(outbound.price.map(AppFormatters.rupees) ?? "-")")
        lines.append("Status: \(outbound.status ?? "-")")
        if let returnTrip = trip.returnTrip { lines.append("\nReturn: \(returnTrip.busName ?? "-")") }
        return lines.joined(separator: "\n")
    }

    /// Rejects edits that don't match `isAllowed`, like Android's onValueChange guards.
    private func filtered(_ binding: Binding<String>, _ isAllowed: @escaping (String) -> Bool) -> Binding<String> {
        Binding(get: { binding.wrappedValue }, set: { if isAllowed($0) { binding.wrappedValue = $0 } })
    }
}

/// Android Date + Time picker pair; the wall-clock is always India time.
private struct BusDateTimeField: View {
    @Binding var value: Date?
    @State private var showPicker = false
    @State private var draft = Date()

    var body: some View {
        Button {
            draft = value ?? AppFormatters.indiaDate(day: .now, hour: 9, minute: 0)
            showPicker = true
        } label: {
            HStack {
                Text(label).foregroundStyle(value == nil ? .secondary : .primary)
                Spacer()
                Image(systemName: "calendar").foregroundStyle(AppTheme.brandOrange)
            }
        }
        .sheet(isPresented: $showPicker) {
            NavigationStack {
                VStack {
                    DatePicker("Date", selection: $draft, displayedComponents: .date)
                        .datePickerStyle(.graphical)
                    DatePicker("Time", selection: $draft, displayedComponents: .hourAndMinute)
                    Text("Times are India time (IST).").font(.footnote).foregroundStyle(.secondary)
                    Spacer()
                }
                .padding()
                .environment(\.timeZone, AppFormatters.india)
                .navigationTitle("Select date & time")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { showPicker = false } }
                    ToolbarItem(placement: .confirmationAction) { Button("Done") { value = draft; showPicker = false } }
                }
            }
            .presentationDetents([.large])
        }
    }

    private var label: String {
        guard let value else { return "Select date & time" }
        return "\(AppFormatters.fullDate(value)) · \(Self.time(value))"
    }

    private static func time(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = AppFormatters.india
        formatter.dateFormat = "hh:mm a"
        return formatter.string(from: date)
    }
}

/// Simple wrapping row of chips (Android `FlowRow` of `AssistChip`).
private struct BusChipFlow: View {
    let items: [String]
    var body: some View {
        BusFlowLayout(spacing: 8) {
            ForEach(items, id: \.self) { item in
                Text(item).font(.caption)
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .overlay(Capsule().stroke(Color.secondary.opacity(0.4)))
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct BusFlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        let height = rows.reduce(0) { $0 + $1.height } + spacing * CGFloat(max(rows.count - 1, 0))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX + (bounds.width - row.width) / 2
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: .unspecified)
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row { var indices: [Int] = []; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let extra = rows[rows.count - 1].indices.isEmpty ? size.width : size.width + spacing
            if rows[rows.count - 1].width + extra > width, !rows[rows.count - 1].indices.isEmpty {
                rows.append(Row(indices: [index], width: size.width, height: size.height))
            } else {
                rows[rows.count - 1].indices.append(index)
                rows[rows.count - 1].width += extra
                rows[rows.count - 1].height = max(rows[rows.count - 1].height, size.height)
            }
        }
        return rows
    }
}

/// Android `SelectStopsScreen`.
struct SelectStopsView: View {
    @ObservedObject var viewModel: AddBusViewModel
    let onDone: () -> Void

    var body: some View {
        ScrollView {
            BusStopsTimeline(
                stops: viewModel.stops.map { BusStopTimelineItem(id: $0.name, title: $0.name) },
                onTap: { viewModel.toggleStop($0) }
            ) { index in
                Image(systemName: viewModel.stops[index].selected ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundStyle(viewModel.stops[index].selected ? AppTheme.brandOrange : .secondary)
                    .accessibilityLabel(viewModel.stops[index].selected ? "Selected" : "Not selected")
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
        }
        .navigationTitle("Select Stops")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            HStack {
                Text("\(viewModel.stops.filter(\.selected).count) of \(viewModel.stops.count) stops selected")
                    .font(.subheadline).foregroundStyle(.secondary)
                Spacer()
                Button("Done", action: onDone).buttonStyle(.borderedProminent).tint(AppTheme.brandOrange)
            }
            .padding(16)
            .background(.bar)
        }
    }
}

private extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}

private extension Character {
    var isASCIIDigit: Bool { ("0"..."9").contains(self) }
}
