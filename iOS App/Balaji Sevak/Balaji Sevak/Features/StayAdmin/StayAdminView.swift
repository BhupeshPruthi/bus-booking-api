import SwiftUI

/// Entry point for the Stay tab when the user can manage stays.
struct StayAdminView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var viewModel = StayAdminViewModel()

    var body: some View {
        NavigationStack(path: $viewModel.path) {
            StayAdminDashboard(viewModel: viewModel)
                .navigationDestination(for: StayAdminRoute.self) { route in
                    StayAdminPage(route: route, viewModel: viewModel)
                }
        }
        .sheet(item: $viewModel.sheet) { sheet in
            StayAdminSheetContent(sheet: sheet, viewModel: viewModel)
        }
    }
}

// MARK: - Page chrome shared by every admin page

private struct StayAdminChrome: ViewModifier {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: StayAdminViewModel
    let route: StayAdminRoute?

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .top) {
                if viewModel.isLoading { ProgressView().progressViewStyle(.linear).tint(AppTheme.brandOrange) }
            }
            .onAppear { viewModel.setCurrentRoute(route) }
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if route != .createBooking {
                        Button { Task { await viewModel.refreshCurrent(using: appState) } } label: {
                            Image(systemName: "arrow.clockwise")
                        }
                        .accessibilityLabel("Refresh")
                        .disabled(viewModel.isLoading)
                    }
                    Menu {
                        Button { open(.createBooking) } label: { Label("Make Booking", systemImage: "plus.circle") }
                        Divider()
                        Button { open(nil) } label: { Label("Stay Admin", systemImage: "square.grid.2x2") }
                        Button { open(.pending) } label: { Label("Pending Requests", systemImage: "hourglass") }
                        Button { open(.confirmed) } label: { Label("Confirmed Bookings", systemImage: "checkmark.seal") }
                        Button { open(.occupancy) } label: { Label("Daily Occupancy", systemImage: "calendar") }
                        Button { open(.cancellations) } label: { Label("Cancellation Requests", systemImage: "xmark.circle") }
                        Button { open(.coupons) } label: { Label("Coupons", systemImage: "tag") }
                        Button { open(.archive) } label: { Label("Stay Records (Last 30 Days)", systemImage: "archivebox") }
                    } label: { Image(systemName: "ellipsis.circle") }
                    .accessibilityLabel("Sections")
                }
            }
    }

    private func open(_ target: StayAdminRoute?) {
        if let target { viewModel.path = [target] } else { viewModel.path = [] }
    }
}

private extension View {
    func stayAdminChrome(_ viewModel: StayAdminViewModel, route: StayAdminRoute?) -> some View {
        modifier(StayAdminChrome(viewModel: viewModel, route: route))
    }
}

// MARK: - Dashboard

private struct StayAdminDashboard: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: StayAdminViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Button { viewModel.path = [.createBooking] } label: {
                    Label("Add confirmed booking", systemImage: "plus").frame(maxWidth: .infinity).frame(minHeight: 32)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppTheme.brandOrange)

                HStack(spacing: 12) {
                    DashboardCountCard(title: "Pending", count: viewModel.pendingTotal, icon: "hourglass") { viewModel.path = [.pending] }
                    DashboardCountCard(title: "Cancellations", count: viewModel.cancellationTotal, icon: "xmark.circle") { viewModel.path = [.cancellations] }
                }
                DashboardCountCard(title: "Confirmed", count: viewModel.confirmedTotal, icon: "checkmark.seal") { viewModel.path = [.confirmed] }

                DashboardActionRow(title: "Daily Occupancy", subtitle: "See booked and available rooms from 3 months ago through next month", icon: "calendar") {
                    viewModel.path = [.occupancy]
                }
                DashboardActionRow(title: "Coupons", subtitle: "Create and manage Stay discount codes", icon: "tag") { viewModel.path = [.coupons] }
                DashboardActionRow(title: "Stay records", subtitle: "View or export the last 30 days", icon: "archivebox") { viewModel.path = [.archive] }
            }
            .padding()
        }
        .refreshable { await viewModel.refreshDashboard(using: appState) }
        .navigationTitle("Stay Admin")
        .stayAdminChrome(viewModel, route: nil)
        .task { await viewModel.refreshDashboard(using: appState) }
    }
}

private struct DashboardCountCard: View {
    let title: String; let count: Int; let icon: String; let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: icon).foregroundStyle(AppTheme.brandOrange)
                Text("\(count)").font(.largeTitle.weight(.bold))
                Text(title).font(.subheadline).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }
}

private struct DashboardActionRow: View {
    let title: String; let subtitle: String; let icon: String; let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon).font(.title3).foregroundStyle(AppTheme.brandOrange).frame(width: 28)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.headline)
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Page router

private struct StayAdminPage: View {
    let route: StayAdminRoute
    @ObservedObject var viewModel: StayAdminViewModel

    var body: some View {
        Group {
            switch route {
            case .pending: StayAdminBookingsPage(list: .pending, viewModel: viewModel)
            case .confirmed: StayAdminBookingsPage(list: .confirmed, viewModel: viewModel)
            case .archive: StayAdminBookingsPage(list: .archive, viewModel: viewModel)
            case .occupancy: StayAdminOccupancyPage(viewModel: viewModel)
            case .occupancyDay(let date): StayAdminOccupancyDayPage(date: date, viewModel: viewModel)
            case .cancellations: StayAdminCancellationsPage(viewModel: viewModel)
            case .coupons: StayAdminCouponsPage(viewModel: viewModel)
            case .createBooking: StayAdminCreateBookingPage(viewModel: viewModel)
            }
        }
        .navigationTitle(route.title)
        .navigationBarTitleDisplayMode(.inline)
        .stayAdminChrome(viewModel, route: route)
    }
}

// MARK: - Booking lists (pending / confirmed / archive)

private struct StayAdminBookingsPage: View {
    @EnvironmentObject private var appState: AppState
    let list: StayAdminBookingList
    @ObservedObject var viewModel: StayAdminViewModel
    @State private var search = ""

    var body: some View {
        List {
            if list == .archive {
                Section {
                    Button { Task { await viewModel.exportLastMonthRecords(using: appState) } } label: {
                        HStack {
                            if viewModel.isExporting { ProgressView() } else { Image(systemName: "square.and.arrow.up") }
                            Text(viewModel.isExporting ? "Preparing PDF…" : "Export last 30 days as PDF")
                        }
                    }
                    .disabled(viewModel.isExporting)
                }
            }
            let rows = viewModel.bookingList == list ? viewModel.bookings : []
            if rows.isEmpty && !viewModel.isLoading {
                Text(emptyMessage).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .center).padding(.vertical, 40)
                    .listRowBackground(Color.clear)
            }
            ForEach(rows) { booking in
                StayAdminBookingCard(booking: booking, showContactDetails: true) { actions(for: booking) }
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    .listRowBackground(Color.clear)
            }
            if viewModel.bookingList == list && rows.count < viewModel.bookingTotal {
                Button("Load more (\(rows.count)/\(viewModel.bookingTotal))") {
                    Task { await viewModel.loadBookings(list, search: viewModel.bookingSearch, reset: false, using: appState) }
                }
                .frame(maxWidth: .infinity)
                .disabled(viewModel.isLoading)
            }
        }
        .listStyle(.plain)
        .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always), prompt: "Reference, guest, phone, or email")
        .onSubmit(of: .search) { Task { await viewModel.loadBookings(list, search: String(search.prefix(200)), using: appState) } }
        .onChange(of: search) { _, value in
            if value.isEmpty && !viewModel.bookingSearch.isEmpty { Task { await viewModel.loadBookings(list, using: appState) } }
        }
        .refreshable { await viewModel.loadBookings(list, search: viewModel.bookingSearch, using: appState) }
        .task {
            search = ""
            await viewModel.loadBookings(list, using: appState)
        }
    }

    private var emptyMessage: String {
        switch list {
        case .pending: "No pending requests"
        case .confirmed: "No confirmed bookings"
        case .archive: "No Stay records in the last 30 days"
        }
    }

    @ViewBuilder
    private func actions(for booking: StayBooking) -> some View {
        switch list {
        case .pending:
            Button("Confirm") { Task { await viewModel.confirm(booking, using: appState) } }
                .buttonStyle(.borderedProminent).tint(AppTheme.brandOrange)
                .disabled(viewModel.isActing)
            Button("Reject") { viewModel.sheet = .reject(booking) }
                .buttonStyle(.bordered).tint(.red)
                .disabled(viewModel.isActing)
        case .confirmed:
            if booking.bookingSource == "admin" {
                Button("Cancel booking") { viewModel.sheet = .adminCancel(booking) }
                    .buttonStyle(.bordered).tint(.red)
                    .disabled(viewModel.isActing)
            }
        case .archive:
            EmptyView()
        }
    }
}

struct StayStatusChip: View {
    let status: String
    var body: some View {
        Text(status.replacingOccurrences(of: "_", with: " ").uppercased())
            .font(.caption.weight(.bold))
            .lineLimit(1)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .foregroundStyle(color)
            .background(color.opacity(0.14), in: RoundedRectangle(cornerRadius: 8))
    }
    private var color: Color {
        switch status {
        case "confirmed", "completed", "active": .green
        case "pending", "cancellation_requested": AppTheme.brandOrange
        case "rejected", "cancelled", "expired", "inactive": .red
        default: .secondary
        }
    }
}

/// Port of Android `StayBookingCard` (admin flavour shows contact details).
private struct StayAdminBookingCard<Actions: View>: View {
    let booking: StayBooking
    var showContactDetails = false
    @ViewBuilder var actions: Actions

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(booking.reference).font(.headline)
            StayStatusChip(status: booking.status)
            if showContactDetails {
                Text("Name: \(booking.contactName)").fontWeight(.medium)
                Text("Phone: \(booking.contactPhone)").textSelection(.enabled)
                if !booking.contactEmail.isEmpty { Text("Email: \(booking.contactEmail)") }
            }
            Text("\(StayFormatting.dateRange(booking.checkInDate, booking.checkOutDate)) · \(booking.nightCount) night(s)")
            if let createdAt = booking.createdAt {
                Text("Booked: \(StayFormatting.timestamp(createdAt))").font(.caption).foregroundStyle(.secondary)
            }
            Text(booking.discountAmount > 0
                 ? "\(booking.guestCount) guests · Remaining \(StayFormatting.rupees(booking.totalAmount))"
                 : "\(booking.guestCount) guests · \(StayFormatting.rupees(booking.totalAmount))")
            if booking.discountAmount > 0 {
                Text("\(booking.couponCode ?? ""): \(StayFormatting.rupees(booking.subtotalAmount ?? booking.totalAmount)) − \(StayFormatting.rupees(booking.discountAmount))")
                    .font(.caption).foregroundStyle(AppTheme.brandOrange)
            }
            if !booking.items.isEmpty {
                Text(booking.items.map { "\($0.unitTypeName) × \($0.quantity)" }.joined(separator: ", "))
            }
            if let reason = booking.rejectionReason, !reason.isEmpty { Text("Reason: \(reason)").foregroundStyle(.red) }
            if let note = booking.customerNote, !note.isEmpty { Text("Note: \(note)").font(.callout).foregroundStyle(.secondary) }
            HStack(spacing: 8) { actions }.padding(.top, 2)
        }
        .font(.subheadline)
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
    }
}

// MARK: - Daily occupancy

private struct StayAdminOccupancyPage: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: StayAdminViewModel

    var body: some View {
        Group {
            if viewModel.dailyOccupancy == nil && !viewModel.isLoading {
                ScrollView {
                    Text("No occupancy information available").foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity).padding(.vertical, 60)
                }
            } else {
                content
            }
        }
        .refreshable { await viewModel.loadDailyOccupancy(using: appState) }
        .task { await viewModel.loadDailyOccupancy(using: appState) }
    }

    private var content: some View {
        let tab = viewModel.occupancyTab
        let shownDays = viewModel.occupancyDays(for: tab)
        let hasBookedDays = shownDays.contains { $0.bookingCount > 0 }
        return VStack(spacing: 0) {
            Picker("Range", selection: $viewModel.occupancyTab) {
                ForEach(StayAdminOccupancyTab.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            if viewModel.dailyOccupancy != nil && hasBookedDays {
                Button { viewModel.exportDailyOccupancy(tab, shownDays: shownDays) } label: {
                    Label("Export \(tab.title.lowercased()) as PDF", systemImage: "square.and.arrow.up")
                        .frame(maxWidth: .infinity).frame(minHeight: 32)
                }
                .buttonStyle(.bordered).tint(AppTheme.brandOrange)
                .padding(.horizontal, 16)
                .padding(.top, 4)
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    if viewModel.dailyOccupancy != nil {
                        Text(rangeLabel(shownDays)).font(.subheadline).foregroundStyle(.secondary)
                        ForEach(shownDays) { day in
                            Button { viewModel.path.append(.occupancyDay(day.date)) } label: { OccupancyDayCard(day: day) }
                                .buttonStyle(.plain)
                                .disabled(day.bookingCount == 0)
                        }
                    }
                }
                .padding()
            }
            // Each tab starts at the top.
            .id(tab)
        }
    }

    private func rangeLabel(_ days: [StayDailyOccupancyDay]) -> String {
        guard let first = days.first, let last = days.last else { return "No days in this range" }
        return "\(StayFormatting.occupancyDate(first.date)) to \(StayFormatting.occupancyDate(last.date))"
    }
}

private func bookingsLabel(_ count: Int) -> String { "\(count) \(count == 1 ? "booking" : "bookings")" }

private struct OccupancyDayCard: View {
    let day: StayDailyOccupancyDay
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(StayFormatting.occupancyDate(day.date)).font(.headline)
                Spacer()
                Text(bookingsLabel(day.bookingCount)).font(.caption).foregroundStyle(.secondary)
            }
            Text("Total earnings: \(StayFormatting.rupees(day.totalEarnings))")
                .font(.subheadline.weight(.medium)).foregroundStyle(AppTheme.brandOrange)
            Divider()
            ForEach(day.unitTypes, id: \.code) { unit in
                HStack {
                    Text(unit.displayName).font(.subheadline)
                    Spacer()
                    Text("\(unit.bookedUnits) booked · \(unit.availableUnits) available").font(.caption).foregroundStyle(.secondary)
                }
            }
            if day.bookingCount > 0 {
                HStack {
                    Spacer()
                    Text("View all \(bookingsLabel(day.bookingCount))").font(.subheadline.weight(.semibold)).foregroundStyle(AppTheme.brandOrange)
                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(AppTheme.brandOrange)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
        .opacity(day.bookingCount == 0 ? 0.75 : 1)
    }
}

private struct StayAdminOccupancyDayPage: View {
    @EnvironmentObject private var appState: AppState
    let date: String
    @ObservedObject var viewModel: StayAdminViewModel

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                if let details = viewModel.occupancyDayDetails, details.date == date {
                    if !details.bookings.isEmpty {
                        Button { viewModel.exportOccupancyDayGuests(details) } label: {
                            Label("Export guest list as PDF", systemImage: "square.and.arrow.up")
                                .frame(maxWidth: .infinity).frame(minHeight: 32)
                        }
                        .buttonStyle(.bordered).tint(AppTheme.brandOrange)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text(StayFormatting.occupancyDate(details.date)).font(.headline)
                        Text("\(bookingsLabel(details.bookingCount)) occupying accommodation")
                        Text("Estimated earnings for this day: \(StayFormatting.rupees(details.totalEarnings))").fontWeight(.medium)
                        Divider()
                        ForEach(details.unitTypes, id: \.code) { unit in
                            HStack {
                                Text(unit.displayName)
                                Spacer()
                                Text("\(unit.bookedUnits) booked · \(unit.availableUnits) available").font(.caption)
                            }
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(AppTheme.brandOrange.opacity(0.12), in: RoundedRectangle(cornerRadius: 16))

                    if details.bookings.isEmpty {
                        Text("No confirmed bookings occupy accommodation on this date.").foregroundStyle(.secondary)
                    } else {
                        ForEach(details.bookings) { booking in OccupancyBookingCard(booking: booking, occupancyDate: details.date) }
                    }
                } else {
                    Text(viewModel.isLoading ? "Loading bookings…" : "No booking details available")
                        .foregroundStyle(.secondary).frame(maxWidth: .infinity).padding(.vertical, 60)
                }
            }
            .padding()
        }
        .refreshable { await viewModel.loadDailyOccupancyBookings(date, using: appState) }
        .task { await viewModel.loadDailyOccupancyBookings(date, using: appState) }
    }
}

private struct OccupancyBookingCard: View {
    let booking: StayBooking
    let occupancyDate: String

    var body: some View {
        let units = booking.totalAccommodationUnits ?? booking.items.reduce(0) { $0 + $1.quantity }
        VStack(alignment: .leading, spacing: 7) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(booking.reference).font(.headline)
                    Text(booking.checkInDate == occupancyDate ? "Checks in today" : "Continuing stay").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                StayStatusChip(status: booking.status)
            }
            Divider()
            Text("Booker: \(booking.contactName)").fontWeight(.medium)
            Text("Phone: \(booking.contactPhone)")
            if !booking.contactEmail.isEmpty { Text("Email: \(booking.contactEmail)") }
            Text(booking.bookingSource == "admin" ? "Source: Admin booking" : "Source: Customer booking").font(.caption).foregroundStyle(.secondary)
            Divider()
            Text("Stay: \(StayFormatting.dateRange(booking.checkInDate, booking.checkOutDate))")
            Text("\(booking.nightCount) night(s) · \(booking.guestCount) guest(s)")
            Text("\(units) total accommodation \(units == 1 ? "unit" : "units")").fontWeight(.medium)
            ForEach(booking.items) { item in
                Text("\(item.quantity) × \(item.unitTypeName) · \(StayFormatting.rupees(item.nightlyRate))/night · \(StayFormatting.rupees(item.lineTotal))")
            }
            Divider()
            if booking.discountAmount > 0 {
                Text("Subtotal: \(StayFormatting.rupees(booking.subtotalAmount ?? booking.totalAmount))")
                Text("Coupon \(booking.couponCode ?? ""): −\(StayFormatting.rupees(booking.discountAmount))").foregroundStyle(AppTheme.brandOrange)
            }
            Text("Booking total: \(StayFormatting.rupees(booking.totalAmount))").fontWeight(.bold)
            if let amount = booking.dailyEarningsContribution {
                Text("Amount allocated to this day: \(StayFormatting.rupees(amount))")
            } else {
                Text("Amount for this day is excluded while cancellation is under review.").font(.caption).foregroundStyle(.secondary)
            }
            if let note = booking.customerNote, !note.trimmingCharacters(in: .whitespaces).isEmpty {
                Divider()
                Text("Customer note").fontWeight(.medium)
                Text(note)
            }
            if let cancellation = booking.cancellation {
                Divider()
                Text("Cancellation request").fontWeight(.medium)
                Text("Requested: \(StayFormatting.timestamp(cancellation.requestedAt))")
                if let reason = cancellation.reason, !reason.isEmpty { Text("Reason: \(reason)") }
                Text(cancellation.standardFullRefundEligible ? "Eligible for a standard full refund" : "Refund is at admin discretion").font(.caption)
            }
            Divider()
            if let createdAt = booking.createdAt {
                Text("Booked: \(StayFormatting.timestamp(createdAt))").font(.caption).foregroundStyle(.secondary)
            }
            if let confirmedAt = booking.confirmedAt {
                Text("Confirmed: \(StayFormatting.timestamp(confirmedAt))").font(.caption).foregroundStyle(.secondary)
            }
        }
        .font(.subheadline)
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
    }
}

// MARK: - Cancellation requests

private struct StayAdminCancellationsPage: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: StayAdminViewModel

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                if viewModel.cancellations.isEmpty && !viewModel.isLoading {
                    Text("No pending cancellation requests").foregroundStyle(.secondary).frame(maxWidth: .infinity).padding(.vertical, 60)
                }
                ForEach(viewModel.cancellations) { request in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(request.reference ?? "").font(.headline)
                        Text("\(request.contactName ?? "") · \(request.contactPhone ?? "")")
                        if let checkIn = request.checkInDate, let checkOut = request.checkOutDate {
                            Text("\(checkIn) → \(checkOut)")
                        }
                        if let total = request.totalAmount { Text("Amount: \(StayFormatting.rupees(total))") }
                        Text("Original state: \(request.previousBookingStatus.replacingOccurrences(of: "_", with: " "))")
                        Text("Requested: \(StayFormatting.timestamp(request.requestedAt))").font(.caption).foregroundStyle(.secondary)
                        Text(request.standardFullRefundEligible ? "Full refund required" : "Refund at admin discretion")
                            .fontWeight(.medium)
                            .foregroundStyle(request.standardFullRefundEligible ? .green : AppTheme.brandOrange)
                        if let reason = request.reason, !reason.isEmpty { Text("Reason: \(reason)") }
                        Button("Review") { viewModel.sheet = .decision(request) }
                            .buttonStyle(.borderedProminent).tint(AppTheme.brandOrange)
                            .disabled(viewModel.isActing)
                    }
                    .font(.subheadline)
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
                }
                if viewModel.cancellations.count < viewModel.cancellationListTotal {
                    Button("Load more") { Task { await viewModel.loadCancellations(reset: false, using: appState) } }
                        .buttonStyle(.bordered).frame(maxWidth: .infinity)
                        .disabled(viewModel.isLoading)
                }
            }
            .padding()
        }
        .refreshable { await viewModel.loadCancellations(using: appState) }
        .task { await viewModel.loadCancellations(using: appState) }
    }
}

// MARK: - Coupons

private struct StayAdminCouponsPage: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: StayAdminViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button { viewModel.sheet = .createCoupon } label: {
                    Label("Create coupon", systemImage: "plus").frame(maxWidth: .infinity).frame(minHeight: 32)
                }
                .buttonStyle(.borderedProminent).tint(AppTheme.brandOrange)

                if viewModel.coupons.isEmpty {
                    if viewModel.couponsLoaded && !viewModel.isLoading {
                        Text("No coupons created yet.").foregroundStyle(.secondary).padding(.top, 8)
                    }
                } else {
                    VStack(spacing: 0) {
                        ForEach(Array(viewModel.coupons.enumerated()), id: \.element.id) { index, coupon in
                            couponRow(coupon)
                            if index != viewModel.coupons.count - 1 { Divider() }
                        }
                    }
                    .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
                }
            }
            .padding()
        }
        .refreshable { await viewModel.loadCoupons(using: appState) }
        .task { await viewModel.loadCoupons(using: appState) }
    }

    private func couponRow(_ coupon: StayCoupon) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(coupon.code).font(.headline)
                Spacer()
                StayStatusChip(status: coupon.status)
            }
            Text("\(StayFormatting.rupees(coupon.discountAmount)) discount")
            Text("\(coupon.startDate) to \(coupon.endDate)").font(.caption).foregroundStyle(.secondary)
            if coupon.isActive && coupon.status != "expired" {
                Button("Deactivate") { viewModel.sheet = .deactivateCoupon(coupon) }
                    .buttonStyle(.bordered).tint(.red)
                    .disabled(viewModel.isActing)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
