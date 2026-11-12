import Combine
import SwiftUI

// MARK: - View model

@MainActor
final class StayViewModel: ObservableObject {
    @Published private(set) var catalog: StayCatalog?
    @Published private(set) var quote: StayQuote?
    @Published private(set) var isLoading = false
    @Published private(set) var isApplyingCoupon = false
    @Published var error: String?

    @Published private(set) var checkIn: Date = StayFormatting.addDays(1, to: StayFormatting.today)
    @Published private(set) var checkOut: Date = StayFormatting.addDays(2, to: StayFormatting.today)
    @Published private(set) var quantities: [String: Int] = [:]

    @Published private(set) var couponInput = ""
    @Published private(set) var couponError: String?
    @Published private(set) var couponMessage: String?

    @Published var contactName = ""
    @Published var contactPhone = ""
    @Published var guestCount = "1"
    @Published var customerNote = ""
    @Published var policyAccepted = false
    @Published private(set) var guestCountError: String?
    @Published var submittedBooking: StayBooking?

    private var didPrefill = false

    var maxNights: Int { max(1, catalog?.maxNights ?? 7) }

    func loadCatalog(using appState: AppState) async {
        guard catalog == nil, !isLoading else { return }
        isLoading = true; error = nil
        defer { isLoading = false }
        do {
            catalog = try await appState.api.get("stays/catalog")
        } catch {
            show(error, fallback: "Failed to load Stay information")
        }
    }

    func prefillContact(from appState: AppState) {
        guard !didPrefill, let user = appState.session.user else { return }
        didPrefill = true
        if contactName.isEmpty { contactName = user.name ?? "" }
        if contactPhone.isEmpty { contactPhone = user.mobile ?? "" }
    }

    /// Android `setDates`: normalises check-out and clears quote and coupon state.
    func setDates(checkIn newCheckIn: Date, checkOut newCheckOut: Date) {
        let start = StayFormatting.calendar.startOfDay(for: newCheckIn)
        var end = StayFormatting.calendar.startOfDay(for: newCheckOut)
        if end <= start { end = StayFormatting.addDays(1, to: start) }
        let latest = StayFormatting.addDays(maxNights, to: start)
        if end > latest { end = latest }
        checkIn = start; checkOut = end
        quote = nil
        clearCouponState()
        error = nil
    }

    func changeQuantity(_ code: String, delta: Int) {
        let current = quantities[code, default: 0]
        let maxUnits = catalog?.unitTypes.first { $0.code == code }?.totalUnits ?? 20
        quantities[code] = min(max(current + delta, 0), maxUnits)
        quote = nil
        clearCouponState()
        error = nil
    }

    /// Returns true when the selection can be fulfilled (proceed to details).
    func checkAvailability(using appState: AppState) async -> Bool {
        let items = itemRequests
        guard !items.isEmpty else { error = "Select at least one room or hall"; return false }
        guard !isLoading else { return false }
        isLoading = true; error = nil
        defer { isLoading = false }
        do {
            let result: StayQuote = try await appState.api.post("stays/quote", body: StayQuoteRequest(
                checkInDate: AppFormatters.apiDay(checkIn), checkOutDate: AppFormatters.apiDay(checkOut),
                items: items, couponCode: nil))
            quote = result
            clearCouponState()
            return result.canFulfill
        } catch {
            show(error, fallback: "Failed to check availability")
            return false
        }
    }

    func updateCouponInput(_ value: String) {
        let normalized = StayFormatting.normalizeCoupon(value)
        couponInput = normalized
        if let quote, let applied = quote.couponCode, applied != normalized {
            self.quote = quote.withoutCoupon()
        }
        couponError = nil
        couponMessage = nil
    }

    func applyCoupon(using appState: AppState) async {
        let code = couponInput.trimmingCharacters(in: .whitespaces)
        guard let currentQuote = quote, currentQuote.canFulfill else {
            couponError = "Check availability before applying a coupon."
            return
        }
        guard !code.isEmpty else {
            quote = currentQuote.withoutCoupon(); couponError = nil; couponMessage = nil
            return
        }
        guard !isApplyingCoupon else { return }
        isApplyingCoupon = true; couponError = nil; couponMessage = nil
        defer { isApplyingCoupon = false }
        do {
            let result: StayQuote = try await appState.api.post("stays/quote", body: StayQuoteRequest(
                checkInDate: AppFormatters.apiDay(checkIn), checkOutDate: AppFormatters.apiDay(checkOut),
                items: itemRequests, couponCode: code))
            quote = result
            couponInput = result.couponCode ?? ""
            couponMessage = "Coupon \(result.couponCode ?? code) applied"
        } catch {
            guard !StayErrorMapper.isCancellation(error) else { return }
            couponError = StayErrorMapper.message(for: error, fallback: "Unable to apply coupon")
        }
    }

    func updateGuestCount(_ value: String) {
        let digits = String(value.filter(\.isASCII).filter(\.isNumber).prefix(9))
        guestCountError = digits != value ? "Enter the number of guests using digits only." : nil
        guestCount = digits
    }

    func submitBooking(using appState: AppState) async {
        guard let quote, quote.canFulfill else { error = "Check availability before submitting"; return }
        guard contactName.stayNilIfBlank != nil else { error = "Name is required"; return }
        guard contactPhone.stayNilIfBlank != nil else { error = "Phone number is required"; return }
        guard let guests = Int(guestCount), guests >= 1 else { error = "Guest count must be at least 1"; return }
        guard policyAccepted else { error = "Please accept the cancellation policy"; return }
        guard let email = appState.session.user?.email?.stayNilIfBlank else {
            error = "Unable to find your login email. Please sign in again."; return
        }
        guard !isLoading else { return }
        isLoading = true; error = nil
        defer { isLoading = false }
        do {
            let booking: StayBooking = try await appState.authPost("stays/bookings", body: CreateStayBookingRequest(
                checkInDate: AppFormatters.apiDay(checkIn), checkOutDate: AppFormatters.apiDay(checkOut),
                items: itemRequests, guestCount: guests, contactName: contactName.trimmingCharacters(in: .whitespacesAndNewlines),
                contactEmail: email, contactPhone: contactPhone.trimmingCharacters(in: .whitespacesAndNewlines),
                couponCode: quote.couponCode, cancellationPolicyAccepted: true, customerNote: customerNote.stayNilIfBlank))
            submittedBooking = booking
        } catch {
            show(error, fallback: "Failed to submit Stay request")
        }
    }

    /// After a successful request: start a fresh search so the same request isn't sent twice.
    func resetAfterSubmission() {
        submittedBooking = nil
        quote = nil
        quantities = [:]
        customerNote = ""
        policyAccepted = false
        clearCouponState()
    }

    func clearError() { error = nil }

    private var itemRequests: [StayBookingItemRequest] {
        let order = catalog?.unitTypes.map(\.code) ?? []
        return quantities.filter { $0.value > 0 }
            .sorted { (order.firstIndex(of: $0.key) ?? .max, $0.key) < (order.firstIndex(of: $1.key) ?? .max, $1.key) }
            .map { StayBookingItemRequest(unitTypeCode: $0.key, quantity: $0.value) }
    }

    private func clearCouponState() { couponInput = ""; couponError = nil; couponMessage = nil }

    private func show(_ error: Error, fallback: String) {
        guard !StayErrorMapper.isCancellation(error) else { return }
        self.error = StayErrorMapper.message(for: error, fallback: fallback)
    }
}

// MARK: - Root

private enum StayRoute: Hashable { case details, myStays }

struct StayView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var viewModel = StayViewModel()
    @State private var path: [StayRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            StaySearchPage(viewModel: viewModel) { path.append(.details) }
                .screenTitle("Rooms & halls")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            if appState.session.isLoggedIn { path.append(.myStays) } else { appState.loginSheetPresented = true }
                        } label: { Image(systemName: "clock.arrow.circlepath").foregroundStyle(.primary) }
                        .accessibilityLabel("My Stays")
                    }
                }
                .navigationDestination(for: StayRoute.self) { route in
                    switch route {
                    case .details: StayBookingDetailsPage(viewModel: viewModel) { path.removeAll() }
                    case .myStays: MyBookingsView(typeFilter: "stay").navigationTitle("My Stays")
                    }
                }
        }
        .task { await viewModel.loadCatalog(using: appState) }
        .onAppear { viewModel.prefillContact(from: appState) }
        .onChange(of: appState.session.user) { _, _ in viewModel.prefillContact(from: appState) }
        // The only sheet on this view: date picking uses its own child view's sheet.
        .sheet(isPresented: Binding(get: { viewModel.error != nil }, set: { if !$0 { viewModel.clearError() } })) {
            AppErrorSheet(message: viewModel.error ?? "Unable to continue. Please try again.") { viewModel.clearError() }
                .presentationDetents([.height(290)])
                .presentationDragIndicator(.visible)
        }
        .alert(
            "Request submitted",
            isPresented: Binding(get: { viewModel.submittedBooking != nil }, set: { _ in }),
            presenting: viewModel.submittedBooking
        ) { _ in
            Button("View My Stays") {
                viewModel.resetAfterSubmission()
                path = [.myStays]
            }
        } message: { booking in
            Text(Self.submissionMessage(booking))
        }
    }

    private static func submissionMessage(_ booking: StayBooking) -> String {
        var lines = ["Reference: \(booking.reference)", "", "Please contact the Admin to confirm your booking and complete the payment."]
        if booking.discountAmount > 0 {
            lines.append("")
            lines.append("Subtotal: \(StayFormatting.rupees(booking.subtotalAmount ?? booking.totalAmount))")
            lines.append("Coupon \(booking.couponCode ?? ""): −\(StayFormatting.rupees(booking.discountAmount))")
            lines.append("Remaining amount: \(StayFormatting.rupees(booking.totalAmount))")
        } else {
            lines.append("")
            lines.append("Total: \(StayFormatting.rupees(booking.totalAmount))")
        }
        return lines.joined(separator: "\n")
    }
}

// MARK: - Search page

private struct StaySearchPage: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: StayViewModel
    let onAvailable: () -> Void
    @State private var isDatePickerPresented = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                dateSelectors
                if let catalog = viewModel.catalog {
                    ForEach(catalog.unitTypes) { type in
                        StayUnitSelector(
                            type: type,
                            quantity: viewModel.quantities[type.code, default: 0],
                            onMinus: { viewModel.changeQuantity(type.code, delta: -1) },
                            onPlus: { viewModel.changeQuantity(type.code, delta: 1) }
                        )
                    }
                    Button {
                        Task { if await viewModel.checkAvailability(using: appState) { onAvailable() } }
                    } label: {
                        HStack { Spacer(); if viewModel.isLoading { ProgressView().tint(.white) } else { Text("Check availability") }; Spacer() }
                            .frame(minHeight: 30)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppTheme.brandOrange)
                    .disabled(viewModel.isLoading)

                    if let quote = viewModel.quote, !quote.canFulfill { NotAvailableCard(quote: quote) }
                } else if viewModel.isLoading {
                    LoadingView().frame(height: 260)
                } else {
                    ErrorStateView(message: "Stay information could not be loaded.") {
                        Task { await viewModel.loadCatalog(using: appState) }
                    }
                }
            }
            .padding()
        }
        .refreshable { await viewModel.loadCatalog(using: appState) }
        .sheet(isPresented: $isDatePickerPresented) {
            StayDateRangePicker(
                checkIn: viewModel.checkIn,
                checkOut: viewModel.checkOut,
                maxNights: viewModel.maxNights,
                checkInTime: checkInTime,
                checkOutTime: checkOutTime
            ) { start, end in viewModel.setDates(checkIn: start, checkOut: end) }
            .presentationDetents([.large])
        }
    }

    private var checkInTime: String { StayFormatting.clockTime(viewModel.catalog?.checkInTime, fallback: "12:00 noon") }
    private var checkOutTime: String { StayFormatting.clockTime(viewModel.catalog?.checkOutTime, fallback: "11:00 AM") }

    /// Android `StayDateButton`s: two outlined orange pills, each opening the range picker.
    private var dateSelectors: some View {
        HStack(spacing: 12) {
            StayDatePill(title: "Check-in", date: viewModel.checkIn, time: checkInTime) { isDatePickerPresented = true }
            StayDatePill(title: "Check-out", date: viewModel.checkOut, time: checkOutTime) { isDatePickerPresented = true }
        }
    }
}

private struct StayDatePill: View {
    let title: String; let date: Date; let time: String; let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(spacing: 1) {
                Text(title).font(.caption)
                Text(AppFormatters.format(date, "dd MMM yy")).font(.headline)
                Text(time).font(.caption.weight(.semibold))
            }
            .foregroundStyle(AppTheme.brandOrange)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .overlay(Capsule().stroke(AppTheme.brandOrange, lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(title) \(AppFormatters.fullDate(date)), \(time)")
    }
}

private struct NotAvailableCard: View {
    let quote: StayQuote
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Not available").font(.headline).foregroundStyle(.red)
            Text("\(quote.nightCount) night(s)")
            ForEach(quote.unitTypes.filter { ($0.requestedQuantity ?? 0) > 0 }) { type in
                HStack {
                    Text("\(type.displayName) × \(type.requestedQuantity ?? 0): \(StayFormatting.rupees(type.lineTotal ?? 0))")
                    if type.canFulfill == false {
                        Text("· \(type.availableUnits ?? 0) available").foregroundStyle(.red)
                    }
                }
                .font(.subheadline)
            }
            Divider()
            Text("Total \(StayFormatting.rupees(quote.totalAmount))").fontWeight(.bold)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
    }
}

private struct StayUnitSelector: View {
    let type: StayUnitType
    let quantity: Int
    let onMinus: () -> Void
    let onPlus: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(type.displayName).font(.headline)
                Text("\(capacityPrefix)\(StayFormatting.rupees(type.nightlyRate))/night").font(.subheadline)
                Text("\(type.totalUnits) available \(type.totalUnits == 1 ? "unit" : "units")").font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button(action: onMinus) { Image(systemName: "minus.circle.fill").font(.title2) }
                .disabled(quantity == 0)
                .accessibilityLabel("Remove one \(type.displayName)")
            Text("\(quantity)").font(.headline).frame(minWidth: 24)
            Button(action: onPlus) { Image(systemName: "plus.circle.fill").font(.title2) }
                .disabled(quantity >= type.totalUnits)
                .accessibilityLabel("Add one \(type.displayName)")
        }
        .buttonStyle(.borderless)
        .tint(AppTheme.brandOrange)
        .padding(16)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
    }

    private var capacityPrefix: String {
        guard type.code == "hall", let capacity = type.capacity else { return "" }
        return "\(capacity) guests · "
    }
}

// MARK: - Date range picker (India calendar days)

private struct StayDateRangePicker: View {
    @Environment(\.dismiss) private var dismiss
    let maxNights: Int
    let checkInTime: String
    let checkOutTime: String
    let onDone: (Date, Date) -> Void
    @State private var displayedMonth: Date
    @State private var start: Date?
    @State private var end: Date?
    private let calendar = StayFormatting.calendar

    init(checkIn: Date, checkOut: Date, maxNights: Int, checkInTime: String, checkOutTime: String, onDone: @escaping (Date, Date) -> Void) {
        self.maxNights = maxNights
        self.checkInTime = checkInTime
        self.checkOutTime = checkOutTime
        self.onDone = onDone
        let calendar = StayFormatting.calendar
        let month = calendar.date(from: calendar.dateComponents([.year, .month], from: checkIn)) ?? checkIn
        _displayedMonth = State(initialValue: month)
        // Start with a blank range each time; the saved dates change only on Done.
        _start = State(initialValue: nil)
        _end = State(initialValue: nil)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                HStack(spacing: 12) {
                    StayRangeDateSummary(title: "Check-in", date: start, time: checkInTime)
                    Divider().frame(height: 42)
                    StayRangeDateSummary(title: "Check-out", date: end, time: checkOutTime)
                }
                .padding(16).background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))

                HStack {
                    Button { moveMonth(-1) } label: { Image(systemName: "chevron.left") }
                        .disabled(displayedMonth <= firstMonth)
                    Spacer()
                    Text(monthTitle).font(.headline)
                    Spacer()
                    Button { moveMonth(1) } label: { Image(systemName: "chevron.right") }
                }

                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 10) {
                    ForEach(Array(calendar.veryShortWeekdaySymbols.enumerated()), id: \.offset) { _, symbol in
                        Text(symbol).font(.caption.weight(.semibold)).foregroundStyle(.secondary).frame(maxWidth: .infinity)
                    }
                    ForEach(Array(monthDays.enumerated()), id: \.offset) { _, date in
                        if let date {
                            Button { select(date) } label: {
                                Text("\(calendar.component(.day, from: date))")
                                    .font(.subheadline.weight(isBoundary(date) ? .bold : .regular))
                                    .frame(width: 38, height: 38)
                                    .foregroundStyle(isUnavailable(date) ? Color.secondary.opacity(0.3) : (isBoundary(date) ? Color.white : Color.primary))
                                    .background(
                                        isBoundary(date) ? AppTheme.brandOrange : (isInRange(date) ? AppTheme.brandOrange.opacity(0.22) : .clear),
                                        in: Circle()
                                    )
                            }
                            .buttonStyle(.plain)
                            .disabled(isUnavailable(date))
                        } else { Color.clear.frame(height: 38) }
                    }
                }

                Text("Select check-in, then check-out. A stay can be up to \(maxNights) \(maxNights == 1 ? "night" : "nights").")
                    .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                Spacer()
            }
            .padding()
            .navigationTitle("Select dates")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: dismiss.callAsFunction) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        if let start, let end { onDone(start, end); dismiss() }
                    }
                    .disabled(start == nil || end == nil)
                }
            }
        }
    }

    private var monthTitle: String {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = AppFormatters.india
        formatter.locale = Locale(identifier: "en_IN")
        formatter.dateFormat = "MMMM yyyy"
        return formatter.string(from: displayedMonth)
    }
    private var firstMonth: Date { calendar.date(from: calendar.dateComponents([.year, .month], from: .now)) ?? .now }
    private var monthDays: [Date?] {
        guard let days = calendar.range(of: .day, in: .month, for: displayedMonth),
              let first = calendar.date(from: calendar.dateComponents([.year, .month], from: displayedMonth)) else { return [] }
        let offset = (calendar.component(.weekday, from: first) - calendar.firstWeekday + 7) % 7
        return Array(repeating: nil, count: offset) + days.compactMap { calendar.date(byAdding: .day, value: $0 - 1, to: first) }
    }
    private func isUnavailable(_ date: Date) -> Bool {
        if date < StayFormatting.today { return true }
        // While choosing check-out, dates past the maximum stay length can't be picked.
        if let start, end == nil, date > StayFormatting.addDays(maxNights, to: start) { return true }
        return false
    }
    private func isBoundary(_ date: Date) -> Bool {
        (start.map { calendar.isDate(date, inSameDayAs: $0) } ?? false) || (end.map { calendar.isDate(date, inSameDayAs: $0) } ?? false)
    }
    private func isInRange(_ date: Date) -> Bool {
        guard let start, let end else { return false }
        return date >= start && date <= end
    }
    private func select(_ date: Date) {
        if start == nil || end != nil {
            start = date; end = nil
        } else if let start, date > start {
            end = min(date, StayFormatting.addDays(maxNights, to: start))
        } else {
            start = date; end = nil
        }
    }
    private func moveMonth(_ offset: Int) { displayedMonth = calendar.date(byAdding: .month, value: offset, to: displayedMonth) ?? displayedMonth }
}

private struct StayRangeDateSummary: View {
    let title: String; let date: Date?; let time: String
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(date.map { AppFormatters.dayMonth($0) } ?? "Select date")
                .font(.headline)
                .foregroundStyle(date == nil ? .secondary : .primary)
            Text(time).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Booking details page

private struct StayBookingDetailsPage: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: StayViewModel
    let onBack: () -> Void
    @FocusState private var focusedField: Field?

    private enum Field: Hashable { case coupon, name, phone, guests, note }

    var body: some View {
        Group {
            if let quote = viewModel.quote, quote.canFulfill {
                form(quote)
            } else {
                ContentUnavailableView {
                    Label("Check availability first", systemImage: "calendar.badge.exclamationmark")
                } description: {
                    Text("Availability must be checked before entering booking details.")
                } actions: {
                    Button("Back to Stay", action: onBack).buttonStyle(.borderedProminent).tint(AppTheme.brandOrange)
                }
            }
        }
        .navigationTitle("Booking details")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { focusedField = nil }
            }
        }
    }

    private func form(_ quote: StayQuote) -> some View {
        Form {
            Section("Your stay") {
                Text("\(StayFormatting.dateRange(quote.checkInDate, quote.checkOutDate)) · \(quote.nightCount) night(s)")
                ForEach(quote.unitTypes.filter { ($0.requestedQuantity ?? 0) > 0 }) { type in
                    Text("\(type.displayName) × \(type.requestedQuantity ?? 0): \(StayFormatting.rupees(type.lineTotal ?? 0))")
                }
                if quote.discountAmount > 0 {
                    Text("Subtotal \(StayFormatting.rupees(quote.subtotalAmount))")
                    Text("Coupon \(quote.couponCode ?? ""): −\(StayFormatting.rupees(quote.discountAmount))")
                        .foregroundStyle(.green)
                    Text("Remaining amount \(StayFormatting.rupees(quote.totalAmount))").fontWeight(.bold)
                } else {
                    Text("Total \(StayFormatting.rupees(quote.totalAmount))").fontWeight(.bold)
                }
                Text("Your reservation will be confirmed by the Admin after your request and payment are reviewed.")
                    .font(.footnote).foregroundStyle(.secondary)
            }

            Section {
                HStack(spacing: 10) {
                    TextField("Coupon code", text: Binding(get: { viewModel.couponInput }, set: { viewModel.updateCouponInput($0) }))
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .focused($focusedField, equals: .coupon)
                        .disabled(viewModel.isApplyingCoupon)
                    let couponApplied = quote.couponCode != nil
                    Button {
                        focusedField = nil
                        if couponApplied { viewModel.updateCouponInput("") } else { Task { await viewModel.applyCoupon(using: appState) } }
                    } label: {
                        if viewModel.isApplyingCoupon { ProgressView() } else { Text(couponApplied ? "Remove" : "Apply") }
                    }
                    .buttonStyle(.bordered)
                    .tint(AppTheme.brandOrange)
                    .disabled(viewModel.isApplyingCoupon || viewModel.isLoading || (!couponApplied && viewModel.couponInput.isEmpty))
                }
                if let message = viewModel.couponMessage {
                    Text(message).font(.footnote).foregroundStyle(.green)
                }
                if let couponError = viewModel.couponError {
                    Text(couponError).font(.footnote).foregroundStyle(.red)
                }
            } header: { Text("Coupon") }

            Section("Primary contact") {
                TextField("Name", text: $viewModel.contactName)
                    .textContentType(.name)
                    .focused($focusedField, equals: .name)
                TextField("Phone", text: $viewModel.contactPhone)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                    .focused($focusedField, equals: .phone)
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Total guests", text: Binding(get: { viewModel.guestCount }, set: { viewModel.updateGuestCount($0) }))
                        .keyboardType(.numberPad)
                        .focused($focusedField, equals: .guests)
                    if let guestError = viewModel.guestCountError {
                        Text(guestError).font(.caption).foregroundStyle(.red)
                    }
                }
                TextField("Note (optional)", text: $viewModel.customerNote, axis: .vertical)
                    .lineLimit(1...4)
                    .focused($focusedField, equals: .note)
            }

            Section("Cancellation policy") {
                Text("Pending requests can be cancelled immediately. After confirmation, cancellation may be requested before 12:00 noon check-in. A full refund applies when requested at least 48 hours before check-in; later refunds are at admin discretion.")
                    .font(.footnote)
                Button { viewModel.policyAccepted.toggle() } label: {
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: viewModel.policyAccepted ? "checkmark.square.fill" : "square")
                            .font(.title3)
                            .foregroundStyle(viewModel.policyAccepted ? AppTheme.brandOrange : .secondary)
                        Text("I have read and accept this policy.").foregroundStyle(.primary)
                        Spacer()
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("I have read and accept this policy.")
                .accessibilityValue(viewModel.policyAccepted ? "Selected" : "Not selected")
            }

            Section {
                Button {
                    focusedField = nil
                    if appState.session.isLoggedIn {
                        Task { await viewModel.submitBooking(using: appState) }
                    } else {
                        appState.loginSheetPresented = true
                    }
                } label: {
                    HStack { Spacer(); if viewModel.isLoading { ProgressView() } else { Text("Submit booking request").fontWeight(.semibold) }; Spacer() }
                }
                .disabled(viewModel.isLoading)
            }
        }
    }
}
