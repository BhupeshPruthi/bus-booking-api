import Foundation
import Combine

/// Effective state of the marathon, with "scheduled" turning into "running" at the start time.
enum MarathonPhase { case none, scheduled, running }

/// Largest number of rounds an admin can add in one go (matches the server).
let chalisaMaxAdminAddition = 5000

/// A short message at the bottom of the Chalisa screen, optionally with Undo.
struct ChalisaMessage: Identifiable, Equatable {
    enum Action: Equatable { case undoRound(String), restoreMarathon(String) }
    let id = UUID()
    let text: String
    var action: Action?
}

/// Hanuman Chalisa screen state (Android `ChalisaViewModel`).
@MainActor final class ChalisaViewModel: ObservableObject {
    static let alreadyCountedNote = "Your last round was already counted. If you've finished another, tap +1 again."

    @Published private(set) var isLoading = true
    @Published private(set) var isRefreshing = false
    @Published private(set) var loadError: String?
    @Published private(set) var board: ChalisaBoard?
    @Published private(set) var me: ChalisaMyStats?
    @Published private(set) var historyMonth = ChalisaMonth.current
    @Published var joinName = "" { didSet { if joinName.count > 40 { joinName = String(joinName.prefix(40)) } } }
    /// Empty means 0; the field shows "0" as its placeholder.
    @Published var startingCountText = "" { didSet { if let digits = Self.digitsIfChanged(startingCountText) { startingCountText = digits } } }
    @Published private(set) var isJoining = false
    /// Non-nil while the starting-count confirmation is shown.
    @Published var pendingStartingCount: Int?
    @Published private(set) var isCounting = false
    @Published private(set) var lastTap: ChalisaReading?
    /// Admin only: rounds typed into "Add rounds" on My path.
    @Published var adminAddText = "" { didSet { if let digits = Self.digitsIfChanged(adminAddText) { adminAddText = digits } } }
    @Published private(set) var isAddingAdmin = false
    @Published private(set) var serverOffset: TimeInterval = 0
    @Published var showEndSheet = false { didSet { if showEndSheet != oldValue { endConfirmText = "" } } }
    @Published var endConfirmText = "" { didSet { if endConfirmText.count > 10 { endConfirmText = String(endConfirmText.prefix(10)) } } }
    @Published private(set) var isEnding = false
    @Published var showCancelDialog = false
    @Published private(set) var lockScreenOn = ChalisaStore.isLockScreenCounterOn
    /// The marathon this super admin just ended, while undo is still possible.
    @Published private(set) var recentlyEnded: ChalisaArchivedMarathon?
    @Published var message: ChalisaMessage?

    private var service: ChalisaService?
    private var lockScreen: ChalisaLockScreen?

    var participant: ChalisaParticipant? { me?.participant }
    var isJoined: Bool { participant != nil }
    var marathon: ChalisaMarathon? { board?.marathon }

    func configure(appState: AppState) {
        guard service == nil else { return }
        service = ChalisaService(appState: appState)
        lockScreen = ChalisaLockScreen.shared
        if joinName.trimmingCharacters(in: .whitespaces).isEmpty { joinName = appState.session.user?.name ?? "" }
    }

    /// Server time, so "starts in" and the switch to +1 don't depend on the phone's clock.
    func now() -> Date { Date().addingTimeInterval(serverOffset) }

    func phase(at now: Date) -> MarathonPhase {
        guard let marathon else { return .none }
        switch marathon.state {
        case "running": return .running
        case "scheduled":
            if let start = AppFormatters.parseISO(marathon.startsAt), start <= now { return .running }
            return .scheduled
        default: return .none
        }
    }

    private func send(_ text: String, action: ChalisaMessage.Action? = nil) {
        message = ChalisaMessage(text: text, action: action)
    }

    /// At most 4 digits, or nil when the text already is (assigning only then stops didSet recursing).
    private static func digitsIfChanged(_ value: String) -> String? {
        let digits = String(value.filter { $0 >= "0" && $0 <= "9" }.prefix(4))
        return digits == value ? nil : digits
    }

    private static func offset(from serverTime: String?) -> TimeInterval? {
        AppFormatters.parseISO(serverTime).map { $0.timeIntervalSinceNow }
    }

    // MARK: - Loading

    /// Loads the board and personal stats together.
    func refresh(showSpinner: Bool = false) async {
        guard let service else { return }
        isRefreshing = showSpinner
        let month = historyMonth.apiValue
        let boardTask = Task { try await service.board() }
        let meTask = Task { try await service.myStats(month: month) }
        let loadedBoard = await boardTask.result
        var loadedMe = await meTask.result
        if case .failure(let error as ChalisaError) = loadedMe, error.code == ChalisaErrorCode.invalidGuestToken {
            // The pass was dropped (ChalisaService clears it): reload as a newcomer so Join shows now.
            loadedMe = await Task { try await service.myStats(month: month) }.result
        }
        isLoading = false
        isRefreshing = false
        // Stop on the Lock Screen turns the counter off while this screen may be open.
        lockScreenOn = ChalisaStore.isLockScreenCounterOn
        if case .success(let value) = loadedBoard {
            board = value
            serverOffset = Self.offset(from: value.serverTime) ?? serverOffset
        }
        if case .success(let value) = loadedMe { me = value }
        if case .failure(let error) = loadedBoard, board == nil, !Self.isCancellation(error) {
            loadError = error.localizedDescription
        } else {
            loadError = nil
        }
    }

    /// Quiet refresh of the board while the screen is open.
    func refreshBoard() async {
        guard let value = try? await service?.board() else { return }
        board = value
        serverOffset = Self.offset(from: value.serverTime) ?? serverOffset
    }

    func showMonth(_ month: ChalisaMonth) async {
        guard !month.isAfterCurrent, let service else { return }
        historyMonth = month
        if let stats = try? await service.myStats(month: month.apiValue) { me = stats }
    }

    // MARK: - Joining

    /// Join button: a starting count above 0 is confirmed first.
    func join() async {
        let name = joinName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard name.count >= 2 else { return send("Enter your name, at least 2 letters.") }
        var count = 0
        if phase(at: now()) == .running {
            guard let typed = startingCountOrNil() else { return }
            count = typed
        }
        if count > 0 { pendingStartingCount = count } else { await saveJoin(name: name, startingCount: 0) }
    }

    private func startingCountOrNil() -> Int? {
        let count = Int(startingCountText) ?? 0
        let max = me?.maxStartingCount ?? 5000
        guard count <= max else {
            send("The starting count can be at most \(ChalisaFormat.count(max)).")
            return nil
        }
        return count
    }

    /// "Add starting count" for someone who has joined before but not read in this marathon.
    func addStartingCount() {
        guard let count = startingCountOrNil() else { return }
        guard count > 0 else { return send("Enter how many times you've already read.") }
        pendingStartingCount = count
    }

    func confirmStartingCount() async {
        guard let count = pendingStartingCount else { return }
        pendingStartingCount = nil
        if isJoined {
            isJoining = true
            await saveStartingCount(count)
            isJoining = false
            await refresh()
        } else {
            await saveJoin(name: joinName.trimmingCharacters(in: .whitespacesAndNewlines), startingCount: count)
        }
    }

    private func saveJoin(name: String, startingCount: Int) async {
        guard let service else { return }
        isJoining = true
        do {
            _ = try await service.join(displayName: name)
            if startingCount > 0 { await saveStartingCount(startingCount) } else { send("You're in as \(name).") }
        } catch {
            await handleFailure(error)
        }
        isJoining = false
        await refresh()
    }

    private func saveStartingCount(_ count: Int) async {
        guard let service else { return }
        do {
            let result = try await service.addStartingCount(count)
            startingCountText = ""
            lastTap = result.reading
            lockScreen?.onCountsChanged(result)
            if let reading = result.reading {
                send("Added \(ChalisaFormat.count(count)) readings.", action: .undoRound(reading.clientRequestId))
            }
        } catch {
            await handleFailure(error)
        }
    }

    // MARK: - Counting

    /// "+1 Round complete"
    func countRound() async {
        guard !isCounting, let service else { return }
        isCounting = true
        defer { isCounting = false }
        do {
            let result = try await service.addRound()
            if result.duplicate {
                // An earlier tap whose answer was lost had already been counted.
                send(Self.alreadyCountedNote)
                await refresh()
                return
            }
            lastTap = result.reading
            if var stats = me {
                stats.today = result.today
                stats.lifetime = result.lifetime
                stats.lastReadAt = result.lastReadAt
                stats.canSetStartingCount = result.canSetStartingCount
                me = stats
            }
            if var current = board {
                current.total = result.marathon?.total ?? current.total
                current.participantCount = result.marathon?.participantCount ?? current.participantCount
                if let myCount = result.marathon?.myCount { current.me?.count = myCount }
                board = current
            }
            if let reading = result.reading { send("Round counted.", action: .undoRound(reading.clientRequestId)) }
            lockScreen?.onCountsChanged(result)
            await refreshBoard()
            // Keeps the day-by-day history in step with the new round.
            if let stats = try? await service.myStats(month: historyMonth.apiValue) { me = stats }
        } catch {
            await handleFailure(error)
        }
    }

    func undo(_ clientRequestId: String) async {
        guard let service else { return }
        do {
            let result = try await service.undo(clientRequestId)
            lastTap = nil
            ChalisaStore.clearLastTap()
            lockScreen?.onCountsChanged(result)
            send("Removed.")
        } catch {
            await handleFailure(error)
        }
        await refresh()
    }

    /// Admin only: adds the typed number of rounds to the admin's own entry.
    func addAdminRounds() async {
        guard !isAddingAdmin, let service else { return }
        let count = Int(adminAddText) ?? 0
        guard (1...chalisaMaxAdminAddition).contains(count) else {
            return send("Enter between 1 and \(ChalisaFormat.count(chalisaMaxAdminAddition)) rounds.")
        }
        isAddingAdmin = true
        do {
            let result = try await service.addAdminRounds(count)
            adminAddText = ""
            lastTap = result.reading
            lockScreen?.onCountsChanged(result)
            if let reading = result.reading {
                send("Added \(ChalisaFormat.rounds(count)).", action: .undoRound(reading.clientRequestId))
            }
        } catch {
            await handleFailure(error)
        }
        isAddingAdmin = false
        await refresh()
    }

    // MARK: - Lock screen

    /// "Count from lock screen" switch.
    func setLockScreenCounter(_ on: Bool) async {
        lockScreenOn = on
        guard let lockScreen else { return }
        if on {
            if await lockScreen.start() {
                send("The counter is on your Lock Screen. Tap +1 there after each round.")
            } else {
                lockScreenOn = false
                send("Turn on Live Activities for Balaji Sevak in Settings to count from the Lock Screen.")
            }
        } else {
            await lockScreen.stop()
        }
    }

    /// Shows the lock-screen counter if it's on (the default) and not already showing.
    func showLockScreenCounterIfOn() async {
        await lockScreen?.showIfOn()
        lockScreenOn = ChalisaStore.isLockScreenCounterOn
    }

    // MARK: - Super admin

    func endMarathon() async {
        guard let service, let marathonID = marathon?.id,
              endConfirmText.trimmingCharacters(in: .whitespaces) == "END" else { return }
        isEnding = true
        do {
            let archived = try await service.end(marathonID: marathonID, confirmText: endConfirmText)
            showEndSheet = false
            recentlyEnded = archived
            send("Path ended and archived.", action: .restoreMarathon(archived.id))
        } catch {
            await handleFailure(error)
        }
        isEnding = false
        await refresh()
    }

    func restoreMarathon(_ marathonID: String) async {
        guard let service else { return }
        do {
            _ = try await service.restore(marathonID: marathonID)
            recentlyEnded = nil
            send("The Path is running again.")
        } catch {
            recentlyEnded = nil
            await handleFailure(error)
        }
        await refresh()
    }

    func cancelScheduledMarathon() async {
        guard let service, let marathonID = marathon?.id else { return }
        showCancelDialog = false
        do {
            try await service.cancel(marathonID: marathonID)
            send("The scheduled Path was cancelled.")
        } catch {
            await handleFailure(error)
        }
        await refresh()
    }

    // MARK: - Errors

    private func handleFailure(_ error: Error) async {
        if Self.isCancellation(error) { return }
        send(error.localizedDescription)
        switch (error as? ChalisaError)?.code {
        case ChalisaErrorCode.marathonNotRunning, ChalisaErrorCode.marathonNotStarted, ChalisaErrorCode.nameRequired,
             ChalisaErrorCode.invalidGuestToken, ChalisaErrorCode.startingCountNotAllowed:
            await refresh()
        default: break
        }
    }

    private static func isCancellation(_ error: Error) -> Bool {
        error is CancellationError || (error as? URLError)?.code == .cancelled
    }
}
