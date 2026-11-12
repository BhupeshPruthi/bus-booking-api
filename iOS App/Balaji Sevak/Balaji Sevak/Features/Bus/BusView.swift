import Combine
import SwiftUI

/// Navigation destinations inside the Bus tab.
enum BusRoute: Hashable {
    case busDetail(String)
    case adminBusDetail(String)
    case addBus
    case selectStops
    case myBookings
}

/// Android `HomeViewModel`: loads the role-specific bus list.
@MainActor
final class BusListViewModel: ObservableObject {
    @Published private(set) var buses: [BusListItem] = []
    @Published private(set) var isLoading = false
    @Published private(set) var isAdmin = false
    @Published private(set) var error: String?
    private var generation = 0

    func load(using appState: AppState) async {
        generation += 1
        let current = generation
        let admin = appState.session.effectiveIsAdmin
        isLoading = true; error = nil; isAdmin = admin
        do {
            let fetched: [BusListItem] = admin
                ? try await appState.authGet("admin/buses?status=scheduled")
                : try await appState.authGet("buses")
            guard current == generation else { return }
            buses = Self.prepare(fetched, isAdmin: admin)
        } catch {
            guard current == generation else { return }
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    /// Admin cutoff filter, paired round-trip availability, then de-duplication
    /// keeping the earlier-departing leg (Android `HomeViewModel.loadBuses`).
    static func prepare(_ buses: [BusListItem], isAdmin: Bool, now: Date = .now) -> [BusListItem] {
        let filtered: [BusListItem]
        if isAdmin {
            let cutoff = now.addingTimeInterval(-24 * 60 * 60)
            filtered = buses.filter { bus in
                guard let arrival = bus.arrivalTime else { return true }
                guard let date = AppFormatters.parseISO(arrival) else { return true }
                return date > cutoff
            }
        } else {
            filtered = buses
        }

        let byID = Dictionary(filtered.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let paired: [BusListItem] = filtered.map { bus in
            let pair = bus.returnBusId.flatMap { byID[$0] }
            let availability = bus.seatAvailability(pairedWith: pair)
            var copy = bus
            copy.bookedSeats = availability.reservedSeats
            copy.availableSeats = availability.availableSeats
            copy.bookableSeats = availability.bookableSeats
            return copy
        }

        let pairedByID = Dictionary(paired.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        var removed = Set<String>()
        for bus in paired where bus.isRoundTrip && !removed.contains(bus.id) {
            guard let returnID = bus.returnBusId, let returnBus = pairedByID[returnID] else { continue }
            let depA = AppFormatters.parseISO(bus.departureTime) ?? .distantFuture
            let depB = AppFormatters.parseISO(returnBus.departureTime) ?? .distantFuture
            if depA <= depB { removed.insert(returnBus.id) } else { removed.insert(bus.id) }
        }
        return paired.filter { !removed.contains($0.id) }
    }
}

struct BusView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var viewModel = BusListViewModel()
    @StateObject private var addBusViewModel = AddBusViewModel()
    @State private var path: [BusRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            content
                .screenTitle("Active Buses")
                .toolbar {
                    if viewModel.isAdmin {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button {
                                addBusViewModel.reset()
                                path.append(.addBus)
                            } label: { Image(systemName: "plus") }
                            .accessibilityLabel("Add Bus")
                        }
                    }
                }
                .onAppear { Task { await viewModel.load(using: appState) } }
                .onChange(of: appState.session.effectiveIsAdmin) { _, _ in
                    path = []
                    Task { await viewModel.load(using: appState) }
                }
                .onChange(of: appState.session.isLoggedIn) { _, _ in
                    Task { await viewModel.load(using: appState) }
                }
                .navigationDestination(for: BusRoute.self) { route in
                    switch route {
                    case .busDetail(let id):
                        BusDetailView(busID: id) { path = [.myBookings] }
                    case .adminBusDetail(let id):
                        AdminBusDetailView(busID: id) { if !path.isEmpty { path.removeLast() } }
                    case .addBus:
                        AddBusTripView(viewModel: addBusViewModel,
                                       onSelectStops: { path.append(.selectStops) },
                                       onDone: { if !path.isEmpty { path.removeLast() } })
                    case .selectStops:
                        SelectStopsView(viewModel: addBusViewModel) { if !path.isEmpty { path.removeLast() } }
                    case .myBookings:
                        MyBookingsView(typeFilter: "bus")
                    }
                }
        }
    }

    @ViewBuilder private var content: some View {
        if viewModel.isLoading && viewModel.buses.isEmpty {
            LoadingView()
        } else if let error = viewModel.error, viewModel.buses.isEmpty {
            ErrorStateView(message: error) { Task { await viewModel.load(using: appState) } }
        } else if viewModel.buses.isEmpty {
            ScrollView {
                VStack(spacing: 20) {
                    BusWelcomeHeader()
                    Text("No active buses found").font(.headline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                .padding(.horizontal, 24).padding(.vertical, 16)
                .frame(maxWidth: .infinity)
            }
            .refreshable { await viewModel.load(using: appState) }
        } else {
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(viewModel.buses) { bus in
                        Button {
                            path.append(viewModel.isAdmin ? .adminBusDetail(bus.id) : .busDetail(bus.id))
                        } label: {
                            BusListCard(bus: bus, isAdmin: viewModel.isAdmin)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
            .refreshable { await viewModel.load(using: appState) }
        }
    }
}

/// Android `AdminBusCard` / `ConsumerBusCard`.
private struct BusListCard: View {
    let bus: BusListItem
    let isAdmin: Bool

    var body: some View {
        let availability = bus.seatAvailability
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                if !isAdmin { Image(systemName: "bus.fill").font(.title3) }
                Text(BusFormat.routeTitle(bus.route.source, bus.route.destination, tripType: bus.tripType))
                    .font(.headline).lineLimit(1).truncationMode(.tail)
                Spacer(minLength: 0)
            }
            Text(BusFormat.tripDates(departure: bus.departureTime, finalArrival: bus.finalArrivalTime))
                .font(isAdmin ? .subheadline : .caption)
                .foregroundStyle(.primary)
            Label(availability.summary, systemImage: "carseat.right.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
            if !isAdmin {
                Text("\(BusFormat.rupees(bus.price)) / seat").font(.subheadline.weight(.semibold))
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
        .contentShape(RoundedRectangle(cornerRadius: 16))
    }
}
