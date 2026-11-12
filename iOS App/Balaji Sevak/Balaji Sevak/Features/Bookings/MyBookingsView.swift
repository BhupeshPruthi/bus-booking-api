import Combine
import SwiftUI

enum BookingBucket: String, CaseIterable, Identifiable {
    case upcoming, past, failed
    var id: String { rawValue }
    var title: String {
        switch self {
        case .upcoming: "Upcoming"
        case .past: "Past"
        case .failed: "Failed"
        }
    }
}

@MainActor
final class MyBookingsViewModel: ObservableObject {
    @Published private(set) var items: [BookingBucket: [UnifiedBookingItem]] = [:]
    @Published private(set) var cursors: [BookingBucket: String] = [:]
    @Published private(set) var loaded: Set<BookingBucket> = []
    @Published private(set) var loading: Set<BookingBucket> = []
    @Published private(set) var counts = UnifiedBookingCounts()
    @Published private(set) var errors: [BookingBucket: String] = [:]

    /// Optional `types` filter for the feed: "bus", "stay" or "pooja" (Android `initialType`).
    var typeFilter: String?

    func bookings(for bucket: BookingBucket) -> [UnifiedBookingItem] { items[bucket] ?? [] }
    func error(for bucket: BookingBucket) -> String? { errors[bucket] }
    func count(for bucket: BookingBucket) -> Int {
        switch bucket { case .upcoming: counts.upcoming; case .past: counts.past; case .failed: counts.failed }
    }
    func canLoadMore(_ bucket: BookingBucket) -> Bool { cursors[bucket] != nil }

    func load(_ bucket: BookingBucket, using appState: AppState, reset: Bool = true) async {
        guard !loading.contains(bucket), reset || canLoadMore(bucket) else { return }
        guard appState.session.isLoggedIn else { return }
        loading.insert(bucket); errors[bucket] = nil
        defer { loading.remove(bucket) }
        let cursor = reset ? nil : cursors[bucket]
        do {
            var path = "me/bookings?bucket=\(bucket.rawValue)&limit=20"
            if let typeFilter { path += "&types=\(typeFilter)" }
            if let cursor, let encoded = cursor.addingPercentEncoding(withAllowedCharacters: .alphanumerics) {
                path += "&cursor=\(encoded)"
            }
            let page: UnifiedBookingPage = try await appState.authGet(path)
            let existing = reset ? [] : bookings(for: bucket)
            items[bucket] = (existing + page.items).uniqueByID
            cursors[bucket] = page.nextCursor
            counts = page.counts
            loaded.insert(bucket)
        } catch is CancellationError {
            // Screen left mid-request.
        } catch {
            errors[bucket] = error.localizedDescription
        }
    }

    func loadIfNeeded(_ bucket: BookingBucket, using appState: AppState) async {
        guard !loaded.contains(bucket) else { return }
        await load(bucket, using: appState)
    }

    /// Refresh every tab after an action changes a booking's status, so the
    /// counts and cards remain consistent even before the user opens a tab.
    func reloadAll(using appState: AppState) async {
        for bucket in BookingBucket.allCases {
            await load(bucket, using: appState, reset: true)
        }
    }

    /// Drops everything loaded for the previous account.
    func reset() {
        items = [:]; cursors = [:]; loaded = []; errors = [:]; counts = UnifiedBookingCounts()
    }
}

/// Unified bookings list. Has no NavigationStack of its own; push it from one.
struct MyBookingsView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var viewModel = MyBookingsViewModel()
    /// "bus", "stay" or "pooja" to show only that type; nil for every booking.
    var typeFilter: String? = nil
    @State private var bucket: BookingBucket = .upcoming

    var body: some View {
        Group {
            if !appState.session.isLoggedIn {
                ContentUnavailableView {
                    Label("Log in to view your bookings", systemImage: "person.crop.circle")
                } description: {
                    Text("Log in to view all your bookings.")
                } actions: {
                    Button("Log in") { appState.loginSheetPresented = true }.buttonStyle(.borderedProminent).tint(AppTheme.brandOrange)
                }
            } else {
                VStack(spacing: 0) {
                    Picker("Booking status", selection: $bucket) {
                        ForEach(BookingBucket.allCases) { item in
                            let count = viewModel.count(for: item)
                            Text(count > 0 ? "\(item.title) (\(count))" : item.title).tag(item)
                        }
                    }
                    .pickerStyle(.segmented)
                    .padding()

                    bookingContent
                }
            }
        }
        .navigationTitle("My Bookings")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let typeFilter {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 0) {
                        Text("My Bookings").font(.headline)
                        Text(typeFilter.bookingTypeLabel).font(.caption).foregroundStyle(.secondary)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
        .task {
            viewModel.typeFilter = typeFilter
            await viewModel.load(bucket, using: appState)
        }
        .onChange(of: bucket) { _, newValue in
            Task { await viewModel.loadIfNeeded(newValue, using: appState) }
        }
        .onChange(of: appState.session.user?.id) { _, userID in
            viewModel.reset()
            if userID != nil { Task { await viewModel.load(bucket, using: appState) } }
        }
    }

    @ViewBuilder private var bookingContent: some View {
        let bookings = viewModel.bookings(for: bucket)
        let isLoading = viewModel.loading.contains(bucket)
        if isLoading && bookings.isEmpty {
            LoadingView()
        } else if bookings.isEmpty {
            // Wrapped in a ScrollView so pull-to-refresh works on empty/error states too.
            ScrollView {
                Group {
                    if let error = viewModel.error(for: bucket) {
                        ErrorStateView(message: error) { Task { await viewModel.load(bucket, using: appState) } }
                    } else {
                        ContentUnavailableView(emptyTitle, systemImage: emptyIcon, description: Text(emptyMessage))
                    }
                }
                .containerRelativeFrame([.horizontal, .vertical])
            }
            .refreshable { await viewModel.load(bucket, using: appState) }
        } else {
            List {
                ForEach(bookings) { booking in
                    NavigationLink {
                        UnifiedBookingDetailView(bookingType: booking.bookingType, bookingID: booking.bookingId) {
                            await viewModel.reloadAll(using: appState)
                        }
                    } label: {
                        UnifiedBookingCard(booking: booking)
                    }
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                }
                if let error = viewModel.error(for: bucket) {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .listRowSeparator(.hidden)
                }
                if viewModel.canLoadMore(bucket) {
                    Button {
                        Task { await viewModel.load(bucket, using: appState, reset: false) }
                    } label: {
                        if isLoading { ProgressView().frame(maxWidth: .infinity) }
                        else { Text("Load more").frame(maxWidth: .infinity) }
                    }
                    .disabled(isLoading)
                    .listRowSeparator(.hidden)
                }
            }
            .listStyle(.plain)
            .refreshable { await viewModel.load(bucket, using: appState) }
        }
    }

    private var emptyTitle: String { bucket == .upcoming ? "No upcoming bookings" : bucket == .past ? "No past bookings" : "No failed bookings" }
    private var emptyIcon: String { bucket == .upcoming ? "suitcase" : bucket == .past ? "clock.arrow.circlepath" : "exclamationmark.triangle" }
    private var emptyMessage: String { bucket == .upcoming ? "No upcoming Bus, Stay, or Puja bookings." : bucket == .past ? "No past bookings yet." : "No cancelled, rejected, or expired bookings." }
}

private struct UnifiedBookingCard: View {
    let booking: UnifiedBookingItem
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                Image(systemName: booking.bookingType.bookingTypeIcon).foregroundStyle(AppTheme.brandOrange).frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(booking.title).font(.headline).lineLimit(2)
                    Text(booking.bookingType.bookingTypeLabel).font(.caption.weight(.semibold)).foregroundStyle(AppTheme.brandOrange)
                }
                Spacer()
                BookingStatusChip(status: booking.rawStatus)
            }
            Text(booking.subtitle).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
            Text("\(booking.startsAt.bookingDateTime) → \(booking.endsAt.bookingDateTime)").font(.caption).foregroundStyle(.secondary)
            HStack {
                Text(booking.reference).font(.caption.weight(.medium)).foregroundStyle(.secondary)
                Spacer()
                if booking.bookingType != "pooja", let amount = booking.totalAmount { Text(AppFormatters.rupees(amount)).font(.subheadline.weight(.bold)) }
            }
        }
        .padding(15)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
        .padding(.vertical, 4)
    }
}

private struct BookingStatusChip: View {
    let status: String
    var body: some View {
        Text(status.lowercased().replacingOccurrences(of: "_", with: " ").capitalizedFirst)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(color)
            .lineLimit(1)
            .padding(.horizontal, 8).padding(.vertical, 5)
            .background(color.opacity(0.12), in: Capsule())
    }
    private var color: Color {
        switch status.lowercased() {
        case "confirmed", "completed": .green
        case "rejected", "cancelled": .red
        case "expired": .secondary
        default: AppTheme.eventPurple
        }
    }
}

private extension Array where Element: Identifiable, Element.ID: Hashable {
    var uniqueByID: [Element] {
        var seen = Set<Element.ID>()
        return filter { seen.insert($0.id).inserted }
    }
}

private extension String {
    var bookingTypeLabel: String { self == "bus" ? "Bus" : self == "stay" ? "Stay" : self == "pooja" ? "Puja" : capitalizedFirst }
    var bookingTypeIcon: String { self == "bus" ? "bus" : self == "stay" ? "bed.double" : self == "pooja" ? "figure.mind.and.body" : "suitcase" }
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
    /// "dd MMM yyyy, hh:mm a" in device time, as Android's unified bookings.
    var bookingDateTime: String {
        AppFormatters.parseISO(self).map { AppFormatters.fullDateTime($0, timeZone: .current) } ?? self
    }
}

// MARK: - Detail

private struct UnifiedBookingDetailView: View {
    @EnvironmentObject private var appState: AppState
    let bookingType: String
    let bookingID: String
    let onBookingChanged: () async -> Void
    @State private var booking: UnifiedBookingItem?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var actionError: String?
    @State private var cancellationShown = false
    @State private var cancellationReason = ""
    @State private var isCancelling = false
    @State private var confirmation: String?

    var body: some View {
        Group {
            if isLoading && booking == nil { LoadingView() }
            else if let booking { details(booking) }
            else if let loadError { ErrorStateView(message: loadError) { Task { await load() } } }
        }
        .navigationTitle("Booking details")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
        .sheet(isPresented: $cancellationShown) { cancellationSheet }
        .sheet(isPresented: Binding(get: { actionError != nil }, set: { if !$0 { actionError = nil } })) {
            AppErrorSheet(message: actionError ?? "") { actionError = nil }
                .presentationDetents([.height(280)])
                .presentationDragIndicator(.visible)
        }
        .alert("Request received", isPresented: Binding(get: { confirmation != nil }, set: { if !$0 { confirmation = nil } })) {
            Button("OK") { confirmation = nil }
        } message: { Text(confirmation ?? "") }
    }

    private func details(_ booking: UnifiedBookingItem) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack { Image(systemName: booking.bookingType.bookingTypeIcon); Text(booking.title).font(.title3.weight(.bold)); Spacer() }
                    Text(booking.bookingType.bookingTypeLabel).foregroundStyle(.secondary)
                    BookingStatusChip(status: booking.rawStatus)
                    Text(booking.subtitle)
                    DetailRow("Reference", booking.reference)
                    DetailRow("Starts", booking.startsAt.bookingDateTime)
                    DetailRow("Ends", booking.endsAt.bookingDateTime)
                    if booking.bookingType != "pooja", let amount = booking.totalAmount {
                        DetailRow(isDiscountedStay(booking) ? "Remaining amount" : "Total", AppFormatters.rupees(amount))
                    }
                }
                .padding(16).background(AppTheme.brandOrange.opacity(0.12), in: RoundedRectangle(cornerRadius: 18))

                typeDetails(booking)
                if canCancel(booking) {
                    Button("Request cancellation", role: .destructive) {
                        cancellationReason = ""
                        cancellationShown = true
                    }
                    .frame(maxWidth: .infinity).buttonStyle(.bordered)
                    .disabled(isCancelling)
                }
            }
            .padding()
        }
    }

    private func isDiscountedStay(_ booking: UnifiedBookingItem) -> Bool {
        booking.bookingType == "stay" && (booking.details?.discountAmount ?? 0) > 0
    }

    /// Only Bus and Stay have a cancellation endpoint.
    private func canCancel(_ booking: UnifiedBookingItem) -> Bool {
        booking.availableActions.contains("request_cancellation") && ["bus", "stay"].contains(booking.bookingType)
    }

    @ViewBuilder private func typeDetails(_ booking: UnifiedBookingItem) -> some View {
        let details = booking.details
        VStack(alignment: .leading, spacing: 0) {
            Text("\(booking.bookingType.bookingTypeLabel) details").font(.headline).padding(.bottom, 10)
            switch booking.bookingType {
            case "bus":
                if let value = details?.busName { DetailRow("Bus", value) }
                if let source = details?.source, let destination = details?.destination { DetailRow("Route", "\(source) → \(destination)") }
                if let value = details?.pickupPoint { DetailRow("Pickup point", value) }
                if let value = details?.assignedSeats { DetailRow("Seats", value) } else if let count = details?.seatCount { DetailRow("Seats", "\(count) seat(s)") }
                if let value = details?.passengerName { DetailRow("Passenger", value) }
                if let value = details?.passengerPhone { DetailRow("Phone", value) }
            case "stay":
                if let checkIn = details?.checkInDate, let checkOut = details?.checkOutDate { DetailRow("Stay", StayFormatting.dateRange(checkIn, checkOut)) }
                if let guests = details?.guestCount { DetailRow("Guests", "\(guests)") }
                ForEach(details?.items ?? []) { item in DetailRow(item.unitTypeName, "\(item.quantity) × \(item.nightCount) night(s) · \(AppFormatters.rupees(item.lineTotal))") }
                if let subtotal = details?.subtotalAmount, let discount = details?.discountAmount, discount > 0 {
                    DetailRow("Subtotal", AppFormatters.rupees(subtotal))
                    DetailRow("Coupon \(details?.couponCode ?? "")", "−\(AppFormatters.rupees(discount))")
                    if let total = booking.totalAmount { DetailRow("Remaining amount", AppFormatters.rupees(total)) }
                }
                if let value = details?.contactName { DetailRow("Contact", value) }
                if let value = details?.contactEmail { DetailRow("Email", value) }
                if let value = details?.contactPhone { DetailRow("Phone", value) }
                if let value = details?.rejectionReason { DetailRow("Reason", value) }
            case "pooja":
                if let value = details?.place { DetailRow("Place", value) }
                if let value = details?.tokenNumber { DetailRow("Token", "#\(value)") }
                if let value = details?.memberCount { DetailRow("Members", "\(value)") }
                if let value = details?.city { DetailRow("City", value) }
                if let value = details?.name { DetailRow("Name", value) }
                if let value = details?.phone { DetailRow("Phone", value) }
            default: EmptyView()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16).background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 18))
    }

    /// Bus cancellation requests take no reason (the API ignores it).
    private var showsReasonField: Bool { bookingType == "stay" }

    private var cancellationSheet: some View {
        VStack(spacing: 14) {
            Image(systemName: "xmark.circle.fill")
                .font(.system(size: 34, weight: .medium))
                .foregroundStyle(.red)
                .frame(width: 64, height: 64)
                .background(Color.red.opacity(0.12), in: Circle())
            Text(cancelsImmediately ? "Cancel booking" : "Request cancellation")
                .font(.title3.weight(.bold))
            Text(cancelsImmediately
                 ? "This pending booking will be cancelled immediately."
                 : "This request will be sent to the relevant admin for review.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            if showsReasonField {
                Text("Reason (optional)")
                    .font(.subheadline.weight(.medium))
                    .frame(maxWidth: .infinity, alignment: .leading)
                TextField("Tell us why you wish to cancel", text: $cancellationReason, axis: .vertical)
                    .lineLimit(3...5)
                    .padding(12)
                    .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 12))
                    .onChange(of: cancellationReason) { _, value in
                        if value.count > 1000 { cancellationReason = String(value.prefix(1000)) }
                    }
                    .disabled(isCancelling)
            }
            HStack(spacing: 12) {
                BottomSheetActionButton(title: "Back", style: .secondary) { cancellationShown = false }
                    .disabled(isCancelling)
                if isCancelling {
                    ProgressView().frame(maxWidth: .infinity).frame(height: 50)
                } else {
                    BottomSheetActionButton(title: cancelsImmediately ? "Cancel booking" : "Submit request", style: .destructive) {
                        Task { await requestCancellation() }
                    }
                }
            }
            .padding(.top, 4)
        }
        .padding(24)
        .presentationDetents([.height(showsReasonField ? 440 : 320)])
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(isCancelling)
    }

    private var cancelsImmediately: Bool { bookingType == "stay" && booking?.rawStatus == "pending" }

    private func load() async {
        isLoading = true; loadError = nil
        defer { isLoading = false }
        do { booking = try await appState.authGet("me/bookings/\(bookingType)/\(bookingID)") }
        catch is CancellationError {}
        catch {
            // Keep showing the loaded booking; surface refresh failures in a sheet.
            if booking == nil { loadError = error.localizedDescription } else { actionError = error.localizedDescription }
        }
    }

    private func requestCancellation() async {
        guard let booking, !isCancelling else { return }
        // Capture before the reload changes the status (pending → cancelled).
        let wasCancelledImmediately = cancelsImmediately
        isCancelling = true
        defer { isCancelling = false }
        do {
            switch booking.bookingType {
            case "bus":
                let _: BookingData = try await appState.authPost("bookings/\(booking.bookingId)/cancellation-request", body: StayCancellationRequest(reason: nil))
            case "stay":
                let reason = cancellationReason.trimmingCharacters(in: .whitespacesAndNewlines)
                let _: StayCancellationResponse = try await appState.authPost("stays/bookings/\(booking.bookingId)/cancellation-requests", body: StayCancellationRequest(reason: reason.isEmpty ? nil : reason))
            default:
                throw CancellationUnavailableError()
            }
            cancellationShown = false
            // Let the sheet finish dismissing before presenting the alert.
            async let sheetDismissed: Void? = try? Task.sleep(for: .milliseconds(400))
            await load()
            _ = await sheetDismissed
            confirmation = wasCancelledImmediately ? "Your booking has been cancelled" : "Cancellation request submitted"
            await onBookingChanged()
        } catch {
            cancellationShown = false
            // Let the cancellation sheet finish dismissing before showing the error sheet.
            try? await Task.sleep(for: .milliseconds(400))
            actionError = error.localizedDescription
        }
    }
}

private struct CancellationUnavailableError: LocalizedError {
    var errorDescription: String? { "Cancellation is not available for this booking" }
}

private struct DetailRow: View {
    let title: String
    let value: String
    init(_ title: String, _ value: String) { self.title = title; self.value = value }
    var body: some View {
        HStack(alignment: .top) {
            Text(title).foregroundStyle(.secondary)
            Spacer(minLength: 18)
            Text(value).fontWeight(.semibold).multilineTextAlignment(.trailing)
        }
        .font(.subheadline).padding(.vertical, 6)
    }
}
