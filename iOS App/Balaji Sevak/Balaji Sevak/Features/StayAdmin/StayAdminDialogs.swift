import SwiftUI

// MARK: - Single sheet host

struct StayAdminSheetContent: View {
    @EnvironmentObject private var appState: AppState
    let sheet: StayAdminSheet
    @ObservedObject var viewModel: StayAdminViewModel

    var body: some View {
        switch sheet {
        case .notice(_, let title, let message, let isError):
            Group {
                if isError {
                    AppErrorSheet(title: title, message: message) { viewModel.sheet = nil }
                } else {
                    StandardBottomSheet(icon: "checkmark.circle.fill", iconColor: .green, title: title, message: message) {
                        BottomSheetActionButton(title: "OK") { viewModel.sheet = nil }
                    }
                }
            }
            .presentationDetents([.height(290)])
            .presentationDragIndicator(.visible)
        case .reject(let booking):
            RejectBookingSheet(booking: booking, viewModel: viewModel)
        case .adminCancel(let booking):
            AdminCancelBookingSheet(booking: booking, viewModel: viewModel)
        case .decision(let request):
            CancellationDecisionSheet(request: request, viewModel: viewModel)
        case .createCoupon:
            CreateCouponSheet(viewModel: viewModel)
        case .deactivateCoupon(let coupon):
            StandardBottomSheet(icon: "tag.slash", iconColor: .red, title: "Deactivate \(coupon.code)?", message: "Users will no longer be able to apply this coupon.") {
                VStack(spacing: 10) {
                    BottomSheetActionButton(title: viewModel.isActing ? "Deactivating…" : "Deactivate", style: .destructive) {
                        Task { await viewModel.deactivateCoupon(coupon, using: appState) }
                    }
                    .disabled(viewModel.isActing)
                    BottomSheetActionButton(title: "Back", style: .secondary) { viewModel.sheet = nil }
                }
            }
            .presentationDetents([.height(340)])
            .interactiveDismissDisabled(viewModel.isActing)
        case .share(let url):
            StayShareSheet(items: [url]).presentationDetents([.medium, .large])
        }
    }
}

/// Form-style dialog chrome: title, Back and a primary action.
private struct StayDialog<Content: View>: View {
    let title: String
    let confirmTitle: String
    let canConfirm: Bool
    @ObservedObject var viewModel: StayAdminViewModel
    let onConfirm: () -> Void
    @ViewBuilder var content: Content

    var body: some View {
        NavigationStack {
            Form { content }
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Back") { viewModel.sheet = nil }.disabled(viewModel.isActing) }
                    ToolbarItem(placement: .confirmationAction) {
                        if viewModel.isActing { ProgressView() } else {
                            Button(confirmTitle, action: onConfirm).fontWeight(.semibold).disabled(!canConfirm)
                        }
                    }
                }
        }
        .presentationDetents([.medium, .large])
        .interactiveDismissDisabled(viewModel.isActing)
    }
}

/// Accepts up to two decimal places (Android `^\d*\.?\d{0,2}$`).
private func filteredAmount(_ value: String, previous: String) -> String {
    value.range(of: #"^\d*\.?\d{0,2}$"#, options: .regularExpression) != nil ? value : previous
}

// MARK: - Reject

private struct RejectBookingSheet: View {
    @EnvironmentObject private var appState: AppState
    let booking: StayBooking
    @ObservedObject var viewModel: StayAdminViewModel
    @State private var reason = ""

    var body: some View {
        StayDialog(title: "Reject \(booking.reference)", confirmTitle: "Confirm", canConfirm: reason.stayNilIfBlank != nil, viewModel: viewModel) {
            guard let trimmed = reason.stayNilIfBlank else { return }
            Task { await viewModel.reject(booking, reason: trimmed, using: appState) }
        } content: {
            Section {
                TextField("Mandatory reason", text: $reason, axis: .vertical)
                    .lineLimit(3...6)
                    .onChange(of: reason) { _, value in if value.count > 1000 { reason = String(value.prefix(1000)) } }
            } footer: { Text("The guest will see this reason.") }
        }
    }
}

// MARK: - Admin direct cancellation

private struct AdminCancelBookingSheet: View {
    @EnvironmentObject private var appState: AppState
    let booking: StayBooking
    @ObservedObject var viewModel: StayAdminViewModel
    @State private var decision = "full"
    @State private var amount = ""
    @State private var reason = ""

    private var partialAmount: Double? { Double(amount) }
    private var canConfirm: Bool {
        switch decision {
        case "partial": (partialAmount ?? 0) > 0 && reason.stayNilIfBlank != nil
        case "none": reason.stayNilIfBlank != nil
        default: true
        }
    }

    var body: some View {
        StayDialog(title: "Cancel \(booking.reference)", confirmTitle: "Cancel booking", canConfirm: canConfirm, viewModel: viewModel) {
            Task {
                await viewModel.cancelAdminBooking(
                    booking, refundDecision: decision,
                    refundAmount: decision == "partial" ? partialAmount : nil,
                    reason: reason.stayNilIfBlank, using: appState
                )
            }
        } content: {
            Section {
                Text("Choose the refund to record. The server enforces the 48-hour refund policy.").font(.footnote)
                Picker("Refund", selection: $decision) {
                    Text("Full").tag("full"); Text("Partial").tag("partial"); Text("None").tag("none")
                }
                .pickerStyle(.segmented)
                if decision == "partial" {
                    TextField("Refund amount (₹)", text: Binding(get: { amount }, set: { amount = filteredAmount($0, previous: amount) }))
                        .keyboardType(.decimalPad)
                }
                TextField(decision == "full" ? "Note (optional)" : "Reason", text: $reason, axis: .vertical)
                    .lineLimit(2...5)
                    .onChange(of: reason) { _, value in if value.count > 1000 { reason = String(value.prefix(1000)) } }
            } footer: {
                Text("Booking total: \(StayFormatting.rupees(booking.totalAmount))")
            }
        }
    }
}

// MARK: - Cancellation request decision

private struct CancellationDecisionSheet: View {
    @EnvironmentObject private var appState: AppState
    let request: StayCancellation
    @ObservedObject var viewModel: StayAdminViewModel
    @State private var decision: String
    @State private var amount = ""
    @State private var reason = ""

    init(request: StayCancellation, viewModel: StayAdminViewModel) {
        self.request = request
        self.viewModel = viewModel
        _decision = State(initialValue: request.standardFullRefundEligible ? "full" : "none")
    }

    private var canApprove: Bool { decision != "partial" || (Double(amount) ?? 0) > 0 }

    var body: some View {
        StayDialog(title: "Cancellation decision", confirmTitle: "Approve", canConfirm: canApprove, viewModel: viewModel) {
            approve()
        } content: {
            Section {
                Text(request.reference ?? "").font(.headline)
                if let name = request.contactName { Text("\(name) · \(request.contactPhone ?? "")") }
                if let total = request.totalAmount { Text("Amount: \(StayFormatting.rupees(total))") }
                if let reason = request.reason, !reason.isEmpty { Text("Reason: \(reason)") }
            }
            Section {
                if request.standardFullRefundEligible {
                    Text("This request is entitled to a full refund.")
                } else {
                    Picker("Refund", selection: $decision) {
                        Text("None").tag("none"); Text("Partial").tag("partial"); Text("Full").tag("full")
                    }
                    .pickerStyle(.segmented)
                }
                if decision == "partial" {
                    TextField("Refund amount", text: Binding(get: { amount }, set: { amount = filteredAmount($0, previous: amount) }))
                        .keyboardType(.decimalPad)
                }
                TextField("Decision note", text: $reason, axis: .vertical)
                    .lineLimit(2...5)
                    .onChange(of: reason) { _, value in if value.count > 1000 { reason = String(value.prefix(1000)) } }
            } footer: {
                Text("A decision note is required to reject the request.")
            }
            Section {
                Button("Approve cancellation") { approve() }
                    .fontWeight(.semibold)
                    .disabled(!canApprove || viewModel.isActing)
                Button("Reject request", role: .destructive) {
                    guard let note = reason.stayNilIfBlank else { return }
                    Task { await viewModel.decideCancellation(request, action: "reject", refundDecision: nil, refundAmount: nil, reason: note, using: appState) }
                }
                .disabled(reason.stayNilIfBlank == nil || viewModel.isActing)
            }
        }
    }

    private func approve() {
        Task {
            await viewModel.decideCancellation(
                request, action: "approve", refundDecision: decision,
                refundAmount: decision == "partial" ? Double(amount) : nil,
                reason: reason.stayNilIfBlank, using: appState
            )
        }
    }
}

// MARK: - Create coupon

private struct CreateCouponSheet: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: StayAdminViewModel
    @State private var code = ""
    @State private var amount = ""
    @State private var startDate = StayFormatting.today
    @State private var endDate = StayFormatting.addDays(30, to: StayFormatting.today)

    private var parsedAmount: Double? { Double(amount) }
    private var validationMessage: String? {
        if code.count < 3 { return "Coupon code must be at least 3 characters." }
        if (parsedAmount ?? 0) <= 0 { return "Enter a discount amount greater than ₹0." }
        if endDate < startDate { return "End date cannot be before start date." }
        return nil
    }

    var body: some View {
        StayDialog(title: "Create Stay coupon", confirmTitle: "Create", canConfirm: validationMessage == nil, viewModel: viewModel) {
            guard validationMessage == nil, let parsedAmount else { return }
            Task {
                await viewModel.createCoupon(
                    code: code, discountAmount: parsedAmount,
                    startDate: AppFormatters.apiDay(startDate), endDate: AppFormatters.apiDay(endDate), using: appState
                )
            }
        } content: {
            Section {
                TextField("Coupon code", text: Binding(get: { code }, set: { code = StayFormatting.normalizeCoupon($0) }))
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                TextField("Discount amount (₹)", text: Binding(get: { amount }, set: { amount = filteredAmount($0, previous: amount) }))
                    .keyboardType(.decimalPad)
            } footer: { Text("Users will type this code") }
            Section {
                DatePicker("Starts", selection: $startDate, displayedComponents: .date)
                DatePicker("Ends", selection: $endDate, in: startDate..., displayedComponents: .date)
            } footer: {
                if let validationMessage, !(code.isEmpty && amount.isEmpty) {
                    Text(validationMessage).foregroundStyle(.red)
                }
            }
            .environment(\.timeZone, AppFormatters.india)
            .environment(\.calendar, StayFormatting.calendar)
            .onChange(of: startDate) { _, value in if endDate < value { endDate = value } }
        }
    }
}

// MARK: - Make booking (admin)

private struct AdminUnitField: Identifiable { let code: String; let label: String; var id: String { code } }

struct StayAdminCreateBookingPage: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: StayAdminViewModel
    @State private var checkIn = StayFormatting.addDays(1, to: StayFormatting.today)
    @State private var checkOut = StayFormatting.addDays(2, to: StayFormatting.today)
    @State private var quantities: [String: String] = [:]
    @State private var guestCount = ""
    @State private var name = ""
    @State private var phone = ""
    @State private var coupon = ""
    @State private var note = ""
    @FocusState private var focused: Bool

    private let maxNights = 7
    private let units = [
        AdminUnitField(code: "three_bed_room", label: "3 Bed rooms"),
        AdminUnitField(code: "four_bed_room", label: "4 Bed rooms"),
        AdminUnitField(code: "five_bed_room", label: "5 Bed rooms"),
        AdminUnitField(code: "hall", label: "Halls"),
    ]

    private var selectedItems: [StayBookingItemRequest] {
        units.compactMap { unit in
            guard let quantity = Int(quantities[unit.code] ?? ""), quantity > 0 else { return nil }
            return StayBookingItemRequest(unitTypeCode: unit.code, quantity: quantity)
        }
    }

    private var canConfirm: Bool {
        checkOut >= StayFormatting.addDays(1, to: checkIn) && !selectedItems.isEmpty && (Int(guestCount) ?? 0) > 0
            && name.stayNilIfBlank != nil && phone.stayNilIfBlank != nil
    }

    var body: some View {
        Form {
            Section {
                Text("This records payment as received and confirms the booking immediately.").font(.subheadline)
            }
            Section("Stay dates and accommodation") {
                Group {
                    DatePicker("Check-in", selection: $checkIn, displayedComponents: .date)
                    DatePicker("Check-out", selection: $checkOut,
                               in: StayFormatting.addDays(1, to: checkIn)...StayFormatting.addDays(maxNights, to: checkIn),
                               displayedComponents: .date)
                }
                .environment(\.timeZone, AppFormatters.india)
                .environment(\.calendar, StayFormatting.calendar)
                ForEach(units) { unit in
                    LabeledContent(unit.label) {
                        TextField("0", text: Binding(
                            get: { quantities[unit.code] ?? "" },
                            set: { quantities[unit.code] = String($0.filter(\.isASCII).filter(\.isNumber).prefix(2)) }
                        ))
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 80)
                        .focused($focused)
                    }
                }
            }
            Section("Guest details") {
                TextField("Total guests", text: Binding(get: { guestCount }, set: { guestCount = String($0.filter(\.isASCII).filter(\.isNumber).prefix(9)) }))
                    .keyboardType(.numberPad).focused($focused)
                TextField("Guest name", text: Binding(get: { name }, set: { name = String($0.prefix(200)) }))
                    .textContentType(.name).focused($focused)
                TextField("Phone", text: Binding(get: { phone }, set: { phone = String($0.prefix(20)) }))
                    .keyboardType(.phonePad).focused($focused)
            }
            Section {
                TextField("Coupon code (optional)", text: Binding(get: { coupon }, set: { coupon = StayFormatting.normalizeCoupon($0) }))
                    .textInputAutocapitalization(.characters).autocorrectionDisabled().focused($focused)
                TextField("Note (optional)", text: Binding(get: { note }, set: { note = String($0.prefix(1000)) }), axis: .vertical)
                    .lineLimit(1...4).focused($focused)
            }
            Section {
                Button {
                    focused = false
                    Task { await submit() }
                } label: {
                    HStack { Spacer(); if viewModel.isActing { ProgressView() } else { Text("Confirm booking").fontWeight(.semibold) }; Spacer() }
                }
                .disabled(!canConfirm || viewModel.isActing)
            }
        }
        .onChange(of: checkIn) { _, value in
            if checkOut <= value { checkOut = StayFormatting.addDays(1, to: value) }
            let latest = StayFormatting.addDays(maxNights, to: value)
            if checkOut > latest { checkOut = latest }
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("Done") { focused = false } }
        }
    }

    private func submit() async {
        guard canConfirm, let guests = Int(guestCount) else { return }
        _ = await viewModel.createAdminBooking(CreateStayBookingRequest(
            checkInDate: AppFormatters.apiDay(checkIn),
            checkOutDate: AppFormatters.apiDay(checkOut),
            items: selectedItems,
            guestCount: guests,
            contactName: name.trimmingCharacters(in: .whitespacesAndNewlines),
            // The view model supplies the signed-in admin's email, as Android does.
            contactEmail: "",
            contactPhone: phone.trimmingCharacters(in: .whitespacesAndNewlines),
            couponCode: coupon.stayNilIfBlank,
            cancellationPolicyAccepted: true,
            customerNote: note.stayNilIfBlank
        ), using: appState)
    }
}
