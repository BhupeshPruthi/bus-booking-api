import Combine
import SwiftUI

@MainActor
final class PoojaBookingViewModel: ObservableObject {
    let poojaID: String
    @Published private(set) var pooja: PoojaDetailData?
    @Published private(set) var isLoading = false
    @Published private(set) var isBooking = false
    @Published private(set) var bookingSuccess: PoojaBookingData?
    @Published var error: String?
    @Published var name = "" { didSet { sanitize(\.name, oldValue) { String($0.filter { $0.isLetter || $0.isWhitespace }.prefix(50)) } } }
    @Published var phone = "" { didSet { sanitize(\.phone, oldValue) { String($0.filter(\.isPoojaDigit).prefix(15)) } } }
    @Published var memberCount = "1" { didSet { sanitize(\.memberCount, oldValue) { String($0.filter(\.isPoojaDigit).prefix(2)) } } }
    @Published var city = PoojaDefaults.city { didSet { sanitize(\.city, oldValue) { String($0.prefix(100)) } } }

    init(poojaID: String) { self.poojaID = poojaID }

    /// Applies an input filter and clears the inline error, as Android's update* functions do.
    private func sanitize(_ keyPath: ReferenceWritableKeyPath<PoojaBookingViewModel, String>, _ oldValue: String, _ filter: (String) -> String) {
        let value = self[keyPath: keyPath]
        let filtered = filter(value)
        if filtered != value { self[keyPath: keyPath] = filtered; return }
        if value != oldValue { error = nil }
    }

    func load(using appState: AppState) async {
        if let user = appState.session.user {
            if name.trimmingCharacters(in: .whitespaces).isEmpty { name = user.name ?? "" }
            if phone.trimmingCharacters(in: .whitespaces).isEmpty { phone = user.mobile ?? "" }
        }
        isLoading = true; error = nil
        do {
            pooja = try await appState.authGet("poojas/\(poojaID)")
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    func book(using appState: AppState) async {
        let name = self.name.trimmingCharacters(in: .whitespaces)
        let phone = self.phone.trimmingCharacters(in: .whitespaces)
        let members = Int(memberCount)
        let trimmedCity = city.trimmingCharacters(in: .whitespacesAndNewlines)
        let city = trimmedCity.isEmpty ? PoojaDefaults.city : trimmedCity

        if name.isEmpty { error = "Please enter your name"; return }
        if !name.allSatisfy({ $0.isLetter || $0 == " " }) { error = "Name should contain only letters"; return }
        if !(10...15).contains(phone.count) || !phone.allSatisfy(\.isPoojaDigit) { error = "Please enter a valid phone number"; return }
        guard let members, (1...PoojaDefaults.maxMembers).contains(members) else {
            error = "Members must be between 1 and \(PoojaDefaults.maxMembers)"; return
        }
        if city.isEmpty { error = "Please enter your city"; return }
        guard let pooja else { error = "Puja details are still loading"; return }
        let window = pooja.window
        if !window.canBook { error = window.closedMessage; return }
        if pooja.availableTokens <= 0 { error = "No tokens available for this pooja"; return }

        isBooking = true; error = nil
        do {
            let booking: PoojaBookingData = try await appState.authPost(
                "poojas/\(poojaID)/bookings",
                body: BookPoojaRequest(name: name, phone: phone, memberCount: members, city: city)
            )
            appState.session.updateUserNameIfBlank(name)
            bookingSuccess = booking
        } catch {
            self.error = error.localizedDescription
        }
        isBooking = false
    }
}

struct PoojaBookingView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: PoojaBookingViewModel
    @FocusState private var focused: Field?
    @State private var cityPrefillCleared = false

    private enum Field { case name, phone, members, city }

    init(poojaID: String) { _viewModel = StateObject(wrappedValue: PoojaBookingViewModel(poojaID: poojaID)) }

    var body: some View {
        Group {
            if let booking = viewModel.bookingSuccess {
                successPage(booking)
            } else if viewModel.isLoading && viewModel.pooja == nil {
                LoadingView()
            } else if let pooja = viewModel.pooja {
                form(pooja)
            } else {
                ErrorStateView(message: viewModel.error ?? "Failed to load pooja") {
                    Task { await viewModel.load(using: appState) }
                }
            }
        }
        .navigationTitle("Book Puja")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(viewModel.isBooking || viewModel.bookingSuccess != nil)
        .task { if viewModel.pooja == nil { await viewModel.load(using: appState) } }
        .onChange(of: focused) { _, newValue in
            if newValue == .city, !cityPrefillCleared, viewModel.city == PoojaDefaults.city {
                cityPrefillCleared = true
                viewModel.city = ""
            }
        }
    }

    private func form(_ pooja: PoojaDetailData) -> some View {
        let window = pooja.window
        return ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                PoojaSummaryCard(pooja: pooja, tokensText: "\(pooja.availableTokens) available / \(pooja.totalTokens) total tokens", showStatus: false)

                Text("Booking Details").font(.headline).padding(.top, 4)

                field("Name") {
                    TextField("Name", text: $viewModel.name)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled()
                        .textContentType(.name)
                        .focused($focused, equals: .name)
                }
                field("Phone") {
                    TextField("Phone", text: $viewModel.phone)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                        .focused($focused, equals: .phone)
                }
                field("Members", helper: "1 token will be issued for this booking request") {
                    TextField("Members", text: $viewModel.memberCount)
                        .keyboardType(.numberPad)
                        .focused($focused, equals: .members)
                }
                field("City") {
                    TextField("City", text: $viewModel.city)
                        .textInputAutocapitalization(.words)
                        .focused($focused, equals: .city)
                }

                if let error = viewModel.error {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(error).font(.subheadline).foregroundStyle(.red)
                        Button("Dismiss") { viewModel.error = nil }.font(.subheadline.weight(.semibold))
                    }
                }

                Button {
                    focused = nil
                    Task { await viewModel.book(using: appState) }
                } label: {
                    Group {
                        if viewModel.isBooking { ProgressView().tint(.white) }
                        else { Text(window.buttonLabel(opensAt: pooja.bookingOpensAt ?? pooja.scheduledAt)).lineLimit(1) }
                    }
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppTheme.brandOrange)
                .disabled(viewModel.isBooking || !window.canBook)
            }
            .disabled(viewModel.isBooking)
            .padding(16)
            .padding(.bottom, 40)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func field<Content: View>(_ title: String, helper: String? = nil, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            content()
                .padding(.horizontal, 14)
                .frame(height: 50)
                .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 12))
            if let helper { Text(helper).font(.caption).foregroundStyle(.secondary) }
        }
    }

    private func successPage(_ booking: PoojaBookingData) -> some View {
        ScrollView {
            VStack(spacing: 16) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(AppTheme.brandOrange)
                Text("Token Booked").font(.title2.weight(.bold))
                Text(booking.tokenNumber.map { "Token #\($0)" } ?? "Token number pending")
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(AppTheme.brandOrange)
                    .multilineTextAlignment(.center)
                VStack(spacing: 10) {
                    if let pooja = viewModel.pooja {
                        PoojaDetailLine(label: "Puja", value: pooja.place)
                        PoojaDetailLine(label: "Date", value: pooja.scheduledAt.fullIndiaDateTime)
                    }
                    PoojaDetailLine(label: "Name", value: booking.name)
                    PoojaDetailLine(label: "Phone", value: booking.phone)
                    PoojaDetailLine(label: "Members", value: "\(booking.memberCount)")
                    PoojaDetailLine(label: "City", value: booking.city)
                    PoojaDetailLine(label: "Status", value: booking.status.poojaCapitalizedFirst)
                }
                .padding(16)
                .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
                BottomSheetActionButton(title: "Done") { dismiss() }
            }
            .padding(20)
        }
    }
}

/// Header card used on the booking and details screens.
struct PoojaSummaryCard: View {
    let pooja: PoojaDetailData
    let tokensText: String
    var showStatus = true

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(pooja.place).font(.title2.weight(.bold)).lineLimit(2)
            Label(pooja.scheduledAt.fullIndiaDateTime, systemImage: "calendar")
                .font(.subheadline).foregroundStyle(.secondary)
            Label {
                Text(tokensText)
                    .fontWeight(.semibold)
                    .foregroundStyle(pooja.availableTokens > 0 ? Color.primary : Color.red)
            } icon: {
                Image(systemName: "ticket").foregroundStyle(.secondary)
            }
            .font(.subheadline)
            if showStatus {
                Text("Status: \(pooja.status.poojaCapitalizedFirst)").font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
    }
}

extension Character {
    var isPoojaDigit: Bool { ("0"..."9").contains(self) }
}
