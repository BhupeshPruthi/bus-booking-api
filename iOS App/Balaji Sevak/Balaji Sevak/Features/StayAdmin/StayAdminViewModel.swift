import Combine
import Foundation

enum StayAdminRoute: Hashable {
    case pending, confirmed, occupancy, occupancyDay(String), cancellations, coupons, archive, createBooking

    var title: String {
        switch self {
        case .pending: "Pending Requests"
        case .confirmed: "Confirmed Bookings"
        case .occupancy: "Daily Occupancy"
        case .occupancyDay(let date): StayFormatting.occupancyDate(date)
        case .cancellations: "Cancellation Requests"
        case .coupons: "Coupons"
        case .archive: "Stay Records (Last 30 Days)"
        case .createBooking: "Make Booking"
        }
    }
}

/// Which booking list the shared `bookings` state currently holds.
enum StayAdminBookingList: Equatable {
    case pending, confirmed, archive

    var status: String? {
        switch self {
        case .pending: "pending"
        case .confirmed: "confirmed"
        case .archive: nil
        }
    }
}

/// Daily Occupancy tabs (Android `OccupancyTab`).
enum StayAdminOccupancyTab: CaseIterable, Identifiable {
    case upcoming, past

    var id: Self { self }

    var title: String {
        switch self {
        case .upcoming: "Upcoming"
        case .past: "Past 3 months"
        }
    }

    /// File name part of the exported PDF.
    var slug: String {
        switch self {
        case .upcoming: "upcoming"
        case .past: "past_3_months"
        }
    }
}

/// Everything presented modally by Stay admin goes through one sheet item so two
/// sheets never compete; replacing the item swaps a dialog for its result notice.
enum StayAdminSheet: Identifiable {
    case notice(id: UUID = UUID(), title: String, message: String, isError: Bool)
    case reject(StayBooking)
    case adminCancel(StayBooking)
    case decision(StayCancellation)
    case createCoupon
    case deactivateCoupon(StayCoupon)
    case share(URL)

    var id: String {
        switch self {
        case .notice(let id, _, _, _): "notice-\(id)"
        case .reject(let booking): "reject-\(booking.id)"
        case .adminCancel(let booking): "cancel-\(booking.id)"
        case .decision(let request): "decision-\(request.id)"
        case .createCoupon: "create-coupon"
        case .deactivateCoupon(let coupon): "deactivate-\(coupon.id)"
        case .share(let url): "share-\(url.absoluteString)"
        }
    }
}

@MainActor
final class StayAdminViewModel: ObservableObject {
    @Published var path: [StayAdminRoute] = []
    @Published var sheet: StayAdminSheet?

    @Published private(set) var isLoading = false
    /// True while a dialog action (confirm, reject, cancel, coupon…) is running.
    @Published private(set) var isActing = false

    @Published private(set) var pendingTotal = 0
    @Published private(set) var confirmedTotal = 0
    @Published private(set) var cancellationTotal = 0
    @Published private(set) var dashboardLoaded = false

    @Published private(set) var bookingList: StayAdminBookingList?
    @Published private(set) var bookings: [StayBooking] = []
    @Published private(set) var bookingSearch = ""
    @Published private(set) var bookingPage = 1
    @Published private(set) var bookingTotal = 0

    @Published private(set) var cancellations: [StayCancellation] = []
    @Published private(set) var cancellationPage = 1
    @Published private(set) var cancellationListTotal = 0

    @Published private(set) var dailyOccupancy: StayDailyOccupancyReport?
    /// Kept here (not in the page) so the tab survives opening a day or another section, as on Android.
    @Published var occupancyTab: StayAdminOccupancyTab = .upcoming
    @Published private(set) var occupancyDayDetails: StayDailyOccupancyBookings?

    @Published private(set) var coupons: [StayCoupon] = []
    @Published private(set) var couponsLoaded = false

    @Published private(set) var isExporting = false

    /// The page currently on screen; used to reload after actions and on Refresh.
    private(set) var currentRoute: StayAdminRoute?
    private var loadingCount = 0 { didSet { isLoading = loadingCount > 0 } }
    private var bookingRequest = UUID()

    // MARK: Dashboard

    func refreshDashboard(using appState: AppState, showErrors: Bool = true) async {
        begin(); defer { end() }
        do {
            async let pending: StayPage<StayBooking> = appState.authGet("admin/stay/bookings?status=pending&page=1&limit=1")
            async let confirmed: StayPage<StayBooking> = appState.authGet("admin/stay/bookings?status=confirmed&page=1&limit=1")
            async let cancellations: StayPage<StayCancellation> = appState.authGet("admin/stay/cancellation-requests?status=pending&page=1&limit=1")
            let (p, c, x) = try await (pending, confirmed, cancellations)
            pendingTotal = p.total
            confirmedTotal = c.total
            cancellationTotal = x.total
            dashboardLoaded = true
        } catch {
            if showErrors { showError(error, fallback: "Failed to load Stay administration") }
        }
    }

    func setCurrentRoute(_ route: StayAdminRoute?) { currentRoute = route }

    /// Toolbar Refresh: reloads whatever is on screen.
    func refreshCurrent(using appState: AppState) async {
        switch currentRoute {
        case nil: await refreshDashboard(using: appState)
        case .pending: await loadBookings(.pending, search: bookingSearch, using: appState)
        case .confirmed: await loadBookings(.confirmed, search: bookingSearch, using: appState)
        case .archive: await loadBookings(.archive, search: bookingSearch, using: appState)
        case .cancellations: await loadCancellations(using: appState)
        case .occupancy: await loadDailyOccupancy(using: appState)
        case .occupancyDay(let date): await loadDailyOccupancyBookings(date, using: appState)
        case .coupons: await loadCoupons(using: appState)
        case .createBooking: break
        }
    }

    // MARK: Booking lists

    func loadBookings(_ list: StayAdminBookingList, search: String = "", reset: Bool = true, using appState: AppState) async {
        if bookingList != list {
            // Never show another list's rows while this one loads.
            bookings = []; bookingTotal = 0; bookingPage = 1
        }
        bookingList = list
        let trimmedSearch = search.trimmingCharacters(in: .whitespacesAndNewlines)
        let nextPage = reset ? 1 : bookingPage + 1
        var query = "page=\(nextPage)&limit=50"
        if let status = list.status { query += "&status=\(status)" }
        if !trimmedSearch.isEmpty { query += "&search=\(StayFormatting.queryValue(String(trimmedSearch.prefix(200))))" }
        if list == .archive {
            let range = Self.archiveRange()
            query += "&fromDate=\(range.from)&toDate=\(range.to)"
        }
        let request = UUID(); bookingRequest = request
        begin(); defer { end() }
        do {
            let page: StayPage<StayBooking> = try await appState.authGet("admin/stay/bookings?\(query)")
            guard bookingRequest == request, bookingList == list else { return }
            bookings = reset ? page.items : bookings + page.items.filter { item in !bookings.contains { $0.id == item.id } }
            bookingSearch = search
            bookingPage = page.page
            bookingTotal = page.total
        } catch {
            guard bookingRequest == request else { return }
            showError(error, fallback: "Failed to load Stay bookings")
        }
    }

    static func archiveRange() -> (from: String, to: String) {
        let today = StayFormatting.today
        let from = StayFormatting.calendar.date(byAdding: .month, value: -1, to: today) ?? today
        return (AppFormatters.apiDay(from), AppFormatters.apiDay(StayFormatting.addDays(1, to: today)))
    }

    // MARK: Cancellations, occupancy, coupons

    func loadCancellations(reset: Bool = true, using appState: AppState) async {
        let nextPage = reset ? 1 : cancellationPage + 1
        begin(); defer { end() }
        do {
            let page: StayPage<StayCancellation> = try await appState.authGet("admin/stay/cancellation-requests?status=pending&page=\(nextPage)&limit=50")
            cancellations = reset ? page.items : cancellations + page.items.filter { item in !cancellations.contains { $0.id == item.id } }
            cancellationPage = page.page
            cancellationListTotal = page.total
            cancellationTotal = page.total
        } catch {
            showError(error, fallback: "Failed to load cancellation requests")
        }
    }

    func loadDailyOccupancy(using appState: AppState) async {
        begin(); defer { end() }
        do {
            dailyOccupancy = try await appState.authGet("admin/stay/daily-occupancy")
        } catch {
            showError(error, fallback: "Failed to load daily occupancy")
        }
    }

    /// The server sends days oldest first and counts "today" in India time; past days are shown newest first.
    /// Worked out on each call (not stored), so the split moves on after midnight.
    func occupancyDays(for tab: StayAdminOccupancyTab) -> [StayDailyOccupancyDay] {
        let days = dailyOccupancy?.days ?? []
        let today = AppFormatters.apiDay(.now)
        switch tab {
        case .upcoming: return days.filter { $0.date >= today }
        case .past: return days.filter { $0.date < today }.reversed()
        }
    }

    func loadDailyOccupancyBookings(_ date: String, using appState: AppState) async {
        if occupancyDayDetails?.date != date { occupancyDayDetails = nil }
        begin(); defer { end() }
        do {
            occupancyDayDetails = try await appState.authGet("admin/stay/daily-occupancy/\(date)/bookings")
        } catch {
            showError(error, fallback: "Failed to load bookings for this day")
        }
    }

    func loadCoupons(using appState: AppState) async {
        begin(); defer { end() }
        do {
            coupons = try await appState.authGet("admin/stay/coupons")
            couponsLoaded = true
        } catch {
            showError(error, fallback: "Failed to load coupons")
        }
    }

    // MARK: Actions

    func confirm(_ booking: StayBooking, using appState: AppState) async {
        await act(success: "Booking confirmed", fallback: "Failed to confirm booking", using: appState) {
            let _: StayBooking = try await appState.authPost("admin/stay/bookings/\(booking.id)/confirm", body: EmptyStayBody())
        }
    }

    func reject(_ booking: StayBooking, reason: String, using appState: AppState) async {
        await act(success: "Booking rejected", fallback: "Failed to reject booking", using: appState) {
            let _: StayBooking = try await appState.authPost("admin/stay/bookings/\(booking.id)/reject", body: StayRejectionRequest(reason: reason))
        }
    }

    func cancelAdminBooking(_ booking: StayBooking, refundDecision: String, refundAmount: Double?, reason: String?, using appState: AppState) async {
        await act(success: "Stay booking cancelled", fallback: "Failed to cancel Stay booking", using: appState) {
            let _: StayBooking = try await appState.authPost(
                "admin/stay/bookings/\(booking.id)/cancel",
                body: AdminStayCancellationRequest(refundDecision: refundDecision, refundAmount: refundAmount, reason: reason)
            )
        }
    }

    func decideCancellation(_ request: StayCancellation, action: String, refundDecision: String?, refundAmount: Double?, reason: String?, using appState: AppState) async {
        await act(success: "Cancellation processed", fallback: "Failed to process cancellation", using: appState) {
            let _: StayBooking = try await appState.authPost(
                "admin/stay/cancellation-requests/\(request.id)/decision",
                body: StayCancellationDecisionRequest(action: action, refundDecision: refundDecision, refundAmount: refundAmount, reason: reason)
            )
        }
    }

    func createCoupon(code: String, discountAmount: Double, startDate: String, endDate: String, using appState: AppState) async {
        await act(success: "Coupon created", fallback: "Failed to create coupon", using: appState) {
            let _: StayCoupon = try await appState.authPost(
                "admin/stay/coupons",
                body: CreateStayCouponRequest(code: code, discountAmount: discountAmount, startDate: startDate, endDate: endDate)
            )
        }
    }

    func deactivateCoupon(_ coupon: StayCoupon, using appState: AppState) async {
        await act(success: "Coupon deactivated", fallback: "Failed to deactivate coupon", using: appState) {
            let _: StayCoupon = try await appState.authPost("admin/stay/coupons/\(coupon.id)/deactivate", body: EmptyStayBody())
        }
    }

    /// Records a paid, immediately confirmed booking. Returns true on success.
    func createAdminBooking(_ draft: CreateStayBookingRequest, using appState: AppState) async -> Bool {
        guard let email = appState.session.user?.email?.stayNilIfBlank else {
            sheet = .notice(title: "Unable to continue", message: "Unable to find your login email. Please sign in again.", isError: true)
            return false
        }
        guard !isActing else { return false }
        isActing = true; defer { isActing = false }
        do {
            let request = CreateStayBookingRequest(
                checkInDate: draft.checkInDate, checkOutDate: draft.checkOutDate, items: draft.items, guestCount: draft.guestCount,
                contactName: draft.contactName, contactEmail: email, contactPhone: draft.contactPhone, couponCode: draft.couponCode,
                cancellationPolicyAccepted: true, customerNote: draft.customerNote
            )
            let _: StayBooking = try await appState.authPost("admin/stay/bookings", body: request)
            path = []
            sheet = .notice(title: "Done", message: "Stay booking confirmed", isError: false)
            await refreshDashboard(using: appState, showErrors: false)
            return true
        } catch {
            showError(error, fallback: "Failed to create Stay booking")
            return false
        }
    }

    private func act(success: String, fallback: String, using appState: AppState, _ operation: () async throws -> Void) async {
        guard !isActing else { return }
        isActing = true
        do {
            try await operation()
            isActing = false
            sheet = .notice(title: "Done", message: success, isError: false)
            await refreshDashboard(using: appState, showErrors: false)
            await refreshCurrent(using: appState)
        } catch {
            isActing = false
            showError(error, fallback: fallback)
        }
    }

    // MARK: Export

    func exportLastMonthRecords(using appState: AppState) async {
        guard !isExporting else { return }
        isExporting = true; defer { isExporting = false }
        let range = Self.archiveRange()
        var records: [StayBooking] = []
        var page = 1
        do {
            while true {
                let response: StayPage<StayBooking> = try await appState.authGet(
                    "admin/stay/bookings?fromDate=\(range.from)&toDate=\(range.to)&page=\(page)&limit=100"
                )
                records += response.items
                if response.items.isEmpty || records.count >= response.total { break }
                page += 1
            }
        } catch {
            showError(error, fallback: "Failed to load Stay records for export")
            return
        }
        do {
            let url = try StayRecordsPDF.write(records)
            sheet = .share(url)
        } catch {
            sheet = .notice(title: "Unable to continue", message: "Unable to export Stay records PDF", isError: true)
        }
    }

    /// Shares a PDF of the tab's days that have bookings (days with no bookings are left out).
    func exportDailyOccupancy(_ tab: StayAdminOccupancyTab, shownDays: [StayDailyOccupancyDay]) {
        let bookedDays = shownDays.filter { $0.bookingCount > 0 }
        guard !bookedDays.isEmpty else { return }
        do {
            sheet = .share(try StayOccupancyPDF.writeDays(tab: tab, bookedDays: bookedDays, shownDays: shownDays))
        } catch {
            sheet = .notice(title: "Unable to continue", message: "Unable to export daily occupancy PDF", isError: true)
        }
    }

    /// Shares a PDF guest list of every booking occupying accommodation on one day.
    func exportOccupancyDayGuests(_ details: StayDailyOccupancyBookings) {
        do {
            sheet = .share(try StayOccupancyPDF.writeGuestList(details))
        } catch {
            sheet = .notice(title: "Unable to continue", message: "Unable to export guest list PDF", isError: true)
        }
    }

    // MARK: Helpers

    private func begin() { loadingCount += 1 }
    private func end() { loadingCount = max(0, loadingCount - 1) }

    private func showError(_ error: Error, fallback: String) {
        guard !StayErrorMapper.isCancellation(error) else { return }
        sheet = .notice(title: "Unable to continue", message: StayErrorMapper.message(for: error, fallback: fallback), isError: true)
    }
}

nonisolated private struct EmptyStayBody: Encodable {}
