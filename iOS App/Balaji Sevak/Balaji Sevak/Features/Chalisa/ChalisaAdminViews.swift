import Combine
import SwiftUI

// MARK: - Schedule or edit a Path (super admin)

@MainActor final class ChalisaScheduleViewModel: ObservableObject {
    let marathonID: String?
    @Published private(set) var isLoading: Bool
    @Published private(set) var existing: ChalisaMarathon?
    @Published var title = "" { didSet { if title.count > 120 { title = String(title.prefix(120)) }; if title != oldValue { error = nil } } }
    @Published var startNow = false { didSet { error = nil } }
    /// Start date and time; the pickers show it in India time.
    @Published var start = ChalisaScheduleViewModel.tomorrowAtSixIndia() { didSet { error = nil } }
    @Published var goalText = "" {
        didSet {
            let digits = String(goalText.filter { $0 >= "0" && $0 <= "9" }.prefix(9))
            if digits != goalText { goalText = digits } else if goalText != oldValue { error = nil }
        }
    }
    @Published private(set) var isSaving = false
    @Published var error: String?
    @Published private(set) var saved = false

    init(marathonID: String?) {
        self.marathonID = marathonID
        isLoading = marathonID != nil
    }

    var isEditing: Bool { existing != nil }
    /// Once a Path has started, only its name and goal can change.
    var startLocked: Bool { existing?.state == "running" }

    private static func tomorrowAtSixIndia() -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = AppFormatters.india
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: Date()) ?? Date()
        return calendar.date(bySettingHour: 6, minute: 0, second: 0, of: tomorrow) ?? tomorrow
    }

    func load(using appState: AppState) async {
        guard let marathonID, existing == nil else { return }
        do {
            let summary = try await ChalisaService(appState: appState).summary()
            guard let marathon = summary.marathon, marathon.id == marathonID else {
                error = "This Path is no longer scheduled or running."
                isLoading = false
                return
            }
            existing = marathon
            title = marathon.title
            if let date = AppFormatters.parseISO(marathon.startsAt) { start = date }
            goalText = marathon.goal.map(String.init) ?? ""
            error = nil
        } catch is CancellationError {
            return
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    func save(using appState: AppState) async {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanTitle.count >= 2 else { error = "Enter a name for the Path."; return }
        let goal = Int(goalText).flatMap { $0 > 0 ? $0 : nil }
        // Whole minutes, as the pickers show them.
        let pickedStart = Date(timeIntervalSince1970: (start.timeIntervalSince1970 / 60).rounded(.down) * 60)
        let startsAt: Date? = startLocked || (!isEditing && startNow) ? nil : pickedStart
        if let startsAt, startsAt < Date().addingTimeInterval(-60) {
            error = "The start time is in the past. Choose a later time, or start right away."
            return
        }
        isSaving = true
        error = nil
        let service = ChalisaService(appState: appState)
        do {
            if let existing {
                _ = try await service.update(marathonID: existing.id, title: cleanTitle, startsAt: startsAt, goal: goal)
            } else {
                _ = try await service.schedule(title: cleanTitle, startsAt: startsAt, goal: goal)
            }
            saved = true
        } catch {
            self.error = error.localizedDescription
        }
        isSaving = false
    }
}

/// Android `ChalisaScheduleScreen`.
struct ChalisaScheduleView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var model: ChalisaScheduleViewModel
    @FocusState private var focused: Bool
    private let onSaved: () -> Void

    init(marathonID: String?, onSaved: @escaping () -> Void) {
        _model = StateObject(wrappedValue: ChalisaScheduleViewModel(marathonID: marathonID))
        self.onSaved = onSaved
    }

    var body: some View {
        Group {
            if model.isLoading {
                LoadingView()
            } else {
                form
            }
        }
        .screenTitle(model.isEditing ? "Edit Path" : "Schedule a Path")
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { focused = false }
            }
        }
        .task { await model.load(using: appState) }
        .onChange(of: model.saved) { _, saved in if saved { onSaved() } }
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ChalisaInput(label: "Path name") {
                    TextField("Hanuman Jayanti Path", text: $model.title)
                        .textInputAutocapitalization(.sentences)
                        .focused($focused)
                }
                if model.startLocked {
                    Text("This Path has started, so its start time can't change. You can still edit the name and goal.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } else {
                    if !model.isEditing {
                        Toggle(isOn: $model.startNow) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Start right away").fontWeight(.semibold)
                                Text("Counting opens as soon as you save.").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .tint(AppTheme.brandOrange)
                    }
                    if model.isEditing || !model.startNow {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                DatePicker("Date", selection: $model.start, in: Calendar.current.startOfDay(for: Date())..., displayedComponents: .date)
                                    .labelsHidden()
                                Spacer()
                                DatePicker("Time", selection: $model.start, displayedComponents: .hourAndMinute)
                                    .labelsHidden()
                            }
                            .environment(\.timeZone, AppFormatters.india)
                            Text("Start date and time, India time").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                ChalisaInput(label: "Goal (optional)", helper: Int(model.goalText).map { "\(ChalisaFormat.count($0)) readings" }) {
                    TextField("200000", text: $model.goalText)
                        .keyboardType(.numberPad)
                        .focused($focused)
                }
                Text(model.isEditing
                     ? "Changes show on everyone's screen the next time they open it."
                     : "Everyone sees the Path on their Home page as soon as you save. It runs until you end it.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                if let error = model.error {
                    Text(error).font(.subheadline).foregroundStyle(.red)
                }
                Button {
                    focused = false
                    Task { await model.save(using: appState) }
                } label: {
                    Group {
                        if model.isSaving { ProgressView().tint(.white) } else { Text(model.isEditing ? "Save changes" : "Schedule Path") }
                    }
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .foregroundStyle(.white)
                    .background(AppTheme.brandOrange, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(model.isSaving || (model.marathonID != nil && !model.isEditing))
            }
            .padding(16)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}

// MARK: - History of past Paths

@MainActor final class ChalisaArchiveViewModel: ObservableObject {
    @Published private(set) var isLoading = true
    @Published private(set) var marathons: [ChalisaArchivedMarathon] = []
    @Published private(set) var error: String?

    func load(using appState: AppState) async {
        isLoading = true
        error = nil
        do {
            marathons = try await ChalisaService(appState: appState).archive()
        } catch is CancellationError {
            return
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }
}

/// Finished Paths with their final totals (Android `ChalisaArchiveScreen`).
struct ChalisaArchiveView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var model = ChalisaArchiveViewModel()

    var body: some View {
        Group {
            if model.isLoading && model.marathons.isEmpty {
                LoadingView()
            } else if let error = model.error {
                ErrorStateView(message: error) { Task { await model.load(using: appState) } }
            } else if model.marathons.isEmpty {
                ContentUnavailableView("Finished Paths will appear here.", systemImage: "clock.arrow.circlepath")
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(model.marathons) { ArchivedMarathonCard(marathon: $0) }
                    }
                    .padding(16)
                }
            }
        }
        .screenTitle("History")
        .task { await model.load(using: appState) }
        .refreshable { await model.load(using: appState) }
    }
}

private struct ArchivedMarathonCard: View {
    let marathon: ChalisaArchivedMarathon
    var body: some View {
        let start = AppFormatters.parseISO(marathon.startsAt)
        let end = AppFormatters.parseISO(marathon.endedAt)
        let dates = [start.map(ChalisaFormat.date), end.map { "– \(ChalisaFormat.date($0))" }, marathon.endedBy.map { "· ended by \($0)" }]
            .compactMap { $0 }
            .joined(separator: " ")
        ChalisaOutlinedCard {
            Text(marathon.title).font(.headline)
            if !dates.isEmpty { Text(dates).font(.caption).foregroundStyle(.secondary) }
            HStack {
                Text("\(ChalisaFormat.count(marathon.finalTotal)) readings")
                Spacer()
                Text("\(ChalisaFormat.count(marathon.finalParticipantCount)) devotees").fontWeight(.semibold)
            }
            .padding(.top, 4)
        }
    }
}
