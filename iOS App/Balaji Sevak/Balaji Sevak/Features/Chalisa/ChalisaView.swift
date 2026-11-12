import SwiftUI

/// Tab order: My path first, Live Path second (Android `CHALISA_TAB_*`).
enum ChalisaTab: Hashable { case myPath, group }

/// Hanuman Chalisa counter: personal counting and history, and the live group board.
struct ChalisaView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var model = ChalisaViewModel()
    @State private var tab: ChalisaTab
    @State private var now = Date()
    @State private var roundTaps = 0
    @FocusState private var focusedField: ChalisaField?
    private let onOpenArchive: () -> Void
    private let onSchedule: (String?) -> Void

    init(initialTab: ChalisaTab = .myPath, onOpenArchive: @escaping () -> Void, onSchedule: @escaping (String?) -> Void) {
        _tab = State(initialValue: initialTab)
        self.onOpenArchive = onOpenArchive
        self.onSchedule = onSchedule
    }

    private var canManage: Bool { appState.session.canManageChalisa }

    var body: some View {
        let phase = model.phase(at: now)
        let countingOpen = phase == .running && model.isJoined
        VStack(spacing: 0) {
            Picker("Section", selection: $tab) {
                Text("My path").tag(ChalisaTab.myPath)
                Text("Live Path").tag(ChalisaTab.group)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            content(phase: phase)
        }
        .screenTitle("Hanuman Chalisa")
        .toolbar {
            if canManage { ToolbarItem(placement: .topBarTrailing) { adminMenu(phase: phase) } }
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { focusedField = nil }
            }
        }
        .overlay(alignment: .bottom) {
            if let message = model.message {
                ChalisaSnackbar(message: message, onAction: { perform(message.action) }, onDismiss: { model.message = nil })
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: model.message)
        // Each time the screen appears (also after scheduling or editing from it), like Android ON_RESUME.
        .task {
            model.configure(appState: appState)
            await model.refresh()
        }
        // Board refresh every 30 seconds, only while the screen is visible.
        .task(id: model.board?.refreshAfterSeconds ?? 30) {
            let seconds = max(model.board?.refreshAfterSeconds ?? 30, 15)
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(seconds))
                if Task.isCancelled { break }
                await model.refreshBoard()
            }
        }
        // Clock for "starts in" and for switching to +1 at the start time.
        .task(id: model.serverOffset) {
            while !Task.isCancelled {
                now = model.now()
                try? await Task.sleep(for: .seconds(15))
            }
        }
        .task(id: model.message?.id) {
            guard let message = model.message else { return }
            try? await Task.sleep(for: .seconds(message.action == nil ? 4 : 10))
            if !Task.isCancelled, model.message?.id == message.id { model.message = nil }
        }
        // The lock-screen counter is on by default: once the reader has joined a running Path it appears.
        .task(id: countingOpen && model.lockScreenOn) {
            if countingOpen && model.lockScreenOn { await model.showLockScreenCounterIfOn() }
        }
        .onChange(of: phase) { _, newPhase in
            if model.marathon?.state == "scheduled" && newPhase == .running { Task { await model.refresh() } }
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active { Task { await model.refresh() } }
        }
        .sensoryFeedback(.impact(weight: .medium), trigger: roundTaps)
        .sheet(isPresented: startingCountPresented) {
            if let count = model.pendingStartingCount {
                StartingCountConfirmSheet(count: count, marathonTitle: model.marathon?.title, onChange: { model.pendingStartingCount = nil }) {
                    Task { await model.confirmStartingCount() }
                }
                .presentationDetents([.height(360)])
                .presentationDragIndicator(.visible)
            }
        }
        .sheet(isPresented: $model.showEndSheet) {
            EndMarathonSheet(model: model)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $model.showCancelDialog) {
            StandardBottomSheet(
                icon: "calendar.badge.minus",
                iconColor: .red,
                title: "Cancel the scheduled Path?",
                message: "It will disappear from everyone's Home page. Nobody has read in it yet, so nothing is lost."
            ) {
                HStack(spacing: 12) {
                    BottomSheetActionButton(title: "Cancel Path", style: .destructive) { Task { await model.cancelScheduledMarathon() } }
                    BottomSheetActionButton(title: "Keep it", style: .secondary) { model.showCancelDialog = false }
                }
            }
            .presentationDetents([.height(320)])
            .presentationDragIndicator(.visible)
        }
    }

    private var startingCountPresented: Binding<Bool> {
        Binding(get: { model.pendingStartingCount != nil }, set: { if !$0 { model.pendingStartingCount = nil } })
    }

    private func perform(_ action: ChalisaMessage.Action?) {
        model.message = nil
        switch action {
        case .undoRound(let id): Task { await model.undo(id) }
        case .restoreMarathon(let id): Task { await model.restoreMarathon(id) }
        case nil: break
        }
    }

    private func adminMenu(phase: MarathonPhase) -> some View {
        Menu {
            let marathonID = model.marathon?.id
            switch phase {
            case .none:
                Button("Schedule a Path") { onSchedule(nil) }
            case .scheduled:
                Button("Edit schedule") { onSchedule(marathonID) }
                Button("Cancel Path", role: .destructive) { model.showCancelDialog = true }
            case .running:
                Button("Edit name and goal") { onSchedule(marathonID) }
                Button("End Path", role: .destructive) { model.showEndSheet = true }
            }
            Button("History", action: onOpenArchive)
        } label: {
            Image(systemName: "ellipsis.circle")
        }
        .accessibilityLabel("Path options")
    }

    @ViewBuilder private func content(phase: MarathonPhase) -> some View {
        if model.isLoading && model.board == nil {
            LoadingView()
        } else if let error = model.loadError, model.board == nil {
            ErrorStateView(message: error) { Task { await model.refresh(showSpinner: true) } }
        } else {
            ScrollView {
                LazyVStack(spacing: 12) {
                    switch tab {
                    case .group: groupTab(phase: phase)
                    case .myPath: myPathTab(phase: phase)
                    }
                }
                .padding(16)
                // Keeps the last rows clear of the message bar.
                .padding(.bottom, model.message == nil ? 0 : 64)
            }
            .scrollDismissesKeyboard(.interactively)
            .refreshable { await model.refresh(showSpinner: true) }
        }
    }

    // MARK: - Live Path tab

    @ViewBuilder private func groupTab(phase: MarathonPhase) -> some View {
        if phase == .none || model.board?.marathon == nil {
            NoMarathonCard(model: model, canManage: canManage, now: now, onSchedule: { onSchedule(nil) })
        } else if let board = model.board, let marathon = board.marathon {
            MarathonHeaderCard(marathon: marathon, board: board, phase: phase, now: now)
            if !model.isJoined {
                JoinCard(model: model, phase: phase, focusedField: $focusedField)
            } else {
                MyRankRow(rank: board.me?.rank, count: board.me?.count ?? 0)
            }
            if board.leaderboard.isEmpty {
                ChalisaMutedText(phase == .running ? "Nobody has counted yet. Be the first." : "The board fills up once the Path starts.")
                    .padding(.vertical, 12)
            } else {
                ForEach(board.leaderboard) { entry in
                    BoardRow(entry: entry, isMe: entry.participantId == model.participant?.id)
                }
            }
        }
        VStack(spacing: 4) {
            Button("History", action: onOpenArchive).font(.body.weight(.semibold))
            if phase != .none {
                Text("Updates every 30 seconds").font(.caption).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
    }

    // MARK: - My path tab

    @ViewBuilder private func myPathTab(phase: MarathonPhase) -> some View {
        if !model.isJoined {
            if phase != .none {
                JoinCard(model: model, phase: phase, focusedField: $focusedField)
            } else {
                ChalisaMutedText("Counting opens when the next Path starts.").padding(.vertical, 24)
            }
        } else {
            todaySummary(phase: phase)
            switch phase {
            case .running: countControls
            case .scheduled: scheduledBox
            case .none: ChalisaMutedText("Counting opens when the next Path starts.")
            }
            if phase == .running {
                LockScreenToggleCard(isOn: model.lockScreenOn) { on in Task { await model.setLockScreenCounter(on) } }
            }
            if phase == .running && model.me?.canSetStartingCount == true {
                ChalisaOutlinedCard {
                    Text("Already read before opening the app?").font(.subheadline.weight(.semibold))
                    StartingCountField(text: $model.startingCountText, focusedField: $focusedField)
                    Button("Add starting count") { focusedField = nil; model.addStartingCount() }
                        .buttonStyle(.bordered)
                        .tint(AppTheme.brandOrange)
                        .disabled(model.isJoining)
                }
            }
            if phase == .running && canManage {
                AdminAddRoundsCard(model: model, focusedField: $focusedField)
            }
        }
        if let me = model.me, model.isJoined || me.lifetime > 0 {
            historyHeader
            if me.days.isEmpty {
                Text("No readings in \(model.historyMonth.title).")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                ForEach(me.days) { day in
                    HStack {
                        Text(ChalisaFormat.historyDay(day.date))
                        Spacer()
                        Text(ChalisaFormat.count(day.count)).fontWeight(.semibold)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }

    private func todaySummary(phase: MarathonPhase) -> some View {
        let me = model.me
        var parts = ["Lifetime \(ChalisaFormat.count(me?.lifetime ?? 0))"]
        if phase != .none { parts.append("Path \(ChalisaFormat.count(model.board?.me?.count ?? 0))") }
        if let streak = me?.streakDays, streak > 0 { parts.append("\(streak)-day streak") }
        return VStack(spacing: 10) {
            ZStack {
                Circle().stroke(AppTheme.brandOrange.opacity(0.22), lineWidth: 8)
                VStack(spacing: 0) {
                    Text(ChalisaFormat.count(me?.today ?? 0)).font(.system(size: 38, weight: .bold)).monospacedDigit()
                    Text("today").font(.caption)
                }
            }
            .frame(width: 140, height: 140)
            .accessibilityElement(children: .combine)
            Text(parts.joined(separator: " · "))
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var countControls: some View {
        let me = model.me
        let lastRead = AppFormatters.parseISO(me?.lastReadAt)
        let tap = model.lastTap
        let canUndo = tap.flatMap { AppFormatters.parseISO($0.undoUntil) }.map { $0 > Date() } ?? false
        let lastAction: String? = {
            if let tap, tap.kind == "admin", canUndo { return "Added \(ChalisaFormat.rounds(tap.count))" }
            guard let lastRead else { return nil }
            if tap?.kind == "opening" { return "Starting count added at \(ChalisaFormat.lastRound(lastRead))" }
            return "Last round at \(ChalisaFormat.lastRound(lastRead))"
        }()
        return VStack(spacing: 4) {
            Button {
                roundTaps += 1
                Task { await model.countRound() }
            } label: {
                Text("+1 Round complete")
                    .font(.system(size: 18, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 64)
                    .foregroundStyle(.white)
                    .background(AppTheme.brandOrange.opacity(model.isCounting ? 0.6 : 1), in: RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            .disabled(model.isCounting)
            HStack(spacing: 8) {
                if let lastAction { Text(lastAction).font(.caption).foregroundStyle(.secondary) }
                if let tap, canUndo {
                    Button("Undo") { Task { await model.undo(tap.clientRequestId) } }.font(.subheadline.weight(.semibold))
                }
            }
            .frame(minHeight: 32)
        }
    }

    private var scheduledBox: some View {
        let start = AppFormatters.parseISO(model.marathon?.startsAt)
        return Text(start.map { "Counting opens \(ChalisaFormat.start($0))" } ?? "Counting opens soon")
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity)
            .frame(height: 64)
            .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
    }

    private var historyHeader: some View {
        let month = model.historyMonth
        return HStack {
            Text("Your history · \(month.title)").font(.subheadline.weight(.semibold))
            Spacer()
            Button { Task { await model.showMonth(month.previous) } } label: { Image(systemName: "chevron.left").frame(width: 36, height: 36) }
                .accessibilityLabel("Previous month")
            Button { Task { await model.showMonth(month.next) } } label: { Image(systemName: "chevron.right").frame(width: 36, height: 36) }
                .accessibilityLabel("Next month")
                .disabled(month == .current)
        }
        .padding(.top, 8)
    }
}
