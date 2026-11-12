import Combine
import SwiftUI

@MainActor
final class SchedulePoojaViewModel: ObservableObject {
    let editingID: String?
    var isEditMode: Bool { editingID != nil }
    @Published var date: Date = SchedulePoojaViewModel.defaultDate()
    @Published var place = PoojaDefaults.place
    @Published var totalTokens = PoojaDefaults.tokens { didSet { let f = String(totalTokens.filter(\.isPoojaDigit).prefix(5)); if f != totalTokens { totalTokens = f } } }
    @Published private(set) var minimumTokenCount = 1
    @Published private(set) var isLoading = false
    @Published private(set) var originalDate: Date?
    @Published var error: String?
    @Published var savedPooja: PoojaDetailData?

    init(editingID: String?) { self.editingID = editingID }

    /// Tomorrow at 9:00 AM India time (Android's time picker also starts at 9:00).
    private static func defaultDate() -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppFormatters.india
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: .now) ?? .now
        return calendar.date(bySettingHour: 9, minute: 0, second: 0, of: tomorrow) ?? tomorrow
    }

    func loadIfNeeded(using appState: AppState) async {
        guard let editingID, originalDate == nil, !isLoading else { return }
        isLoading = true; error = nil
        do {
            let pooja: PoojaDetailData = try await appState.authGet("admin/poojas/\(editingID)")
            if let scheduled = AppFormatters.parseISO(pooja.scheduledAt) { date = scheduled; originalDate = scheduled }
            place = pooja.place
            totalTokens = String(pooja.totalTokens)
            minimumTokenCount = (pooja.bookings ?? [])
                .filter { $0.status == "confirmed" }
                .compactMap(\.tokenNumber)
                .max() ?? 1
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    func save(using appState: AppState) async {
        guard let tokens = Int(totalTokens), tokens >= minimumTokenCount else {
            error = minimumTokenCount > 1
                ? "Tokens cannot be less than confirmed token #\(minimumTokenCount)"
                : "Please enter a valid token count"
            return
        }
        let place = self.place.trimmingCharacters(in: .whitespacesAndNewlines)
        if place.isEmpty { error = "Please enter a place"; return }

        let request = PoojaScheduleRequest(scheduledAt: AppFormatters.isoString(date), place: place, totalTokens: tokens)
        isLoading = true; error = nil
        do {
            if let editingID {
                savedPooja = try await appState.authPut("admin/poojas/\(editingID)", body: request)
            } else {
                savedPooja = try await appState.authPost("admin/poojas", body: request)
            }
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }
}

struct SchedulePoojaView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: SchedulePoojaViewModel

    init(editingID: String?) { _viewModel = StateObject(wrappedValue: SchedulePoojaViewModel(editingID: editingID)) }

    /// Past dates are not selectable; when editing a puja that has already started,
    /// keep its current time selectable so the picker is not forced forward.
    private var minimumDate: Date { min(viewModel.originalDate ?? .now, .now) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if viewModel.isLoading { ProgressView().progressViewStyle(.linear).tint(AppTheme.brandOrange) }

                Text(viewModel.isEditMode
                     ? "Update this puja schedule. Existing bookings will remain valid."
                     : "Create a new puja schedule for users to book tokens.")
                    .foregroundStyle(.secondary)

                Text("Date & Time").font(.subheadline.weight(.semibold))
                DatePicker("Date & Time", selection: $viewModel.date, in: minimumDate..., displayedComponents: [.date, .hourAndMinute])
                    .datePickerStyle(.compact)
                    .environment(\.timeZone, AppFormatters.india)
                    .tint(AppTheme.brandOrange)
                    .padding(.horizontal, 14).frame(minHeight: 50)
                    .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 12))

                Text("Details").font(.subheadline.weight(.semibold))
                field("Place") { TextField("Place", text: $viewModel.place).textInputAutocapitalization(.words) }
                field("Tokens") { TextField("Tokens", text: $viewModel.totalTokens).keyboardType(.numberPad) }

                if viewModel.isEditMode && viewModel.minimumTokenCount > 1 {
                    Text("Minimum \(viewModel.minimumTokenCount) tokens because confirmed token numbers must remain valid.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Button {
                    Task { await viewModel.save(using: appState) }
                } label: {
                    Group {
                        if viewModel.isLoading { ProgressView().tint(.white) }
                        else { Text(viewModel.isEditMode ? "Save Changes" : "Schedule Puja") }
                    }
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity).frame(height: 50)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppTheme.brandOrange)
            }
            .disabled(viewModel.isLoading)
            .padding(16)
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle(viewModel.isEditMode ? "Edit Puja" : "Schedule Puja")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.loadIfNeeded(using: appState) }
        .alert("Error", isPresented: Binding(get: { viewModel.error != nil }, set: { if !$0 { viewModel.error = nil } })) {
            Button("OK") { viewModel.error = nil }
        } message: { Text(viewModel.error ?? "") }
        .alert(viewModel.isEditMode ? "Puja Updated" : "Puja Scheduled",
               isPresented: Binding(get: { viewModel.savedPooja != nil }, set: { if !$0 { viewModel.savedPooja = nil } }),
               presenting: viewModel.savedPooja) { _ in
            Button("Done") { dismiss() }
        } message: { pooja in
            Text("Place: \(pooja.place)\nDate & Time: \(pooja.scheduledAt.fullIndiaDateTime)\nTokens: \(pooja.totalTokens)")
        }
    }

    private func field<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            content()
                .padding(.horizontal, 14)
                .frame(height: 50)
                .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 12))
        }
    }
}
