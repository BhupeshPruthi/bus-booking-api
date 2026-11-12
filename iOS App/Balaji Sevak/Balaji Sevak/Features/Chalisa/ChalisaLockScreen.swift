import ActivityKit
import Foundation

/// Counting from the Lock Screen (Android `ChalisaLockScreenCounter`): a Live Activity with +1,
/// Undo and Stop. Its buttons run in the background, so the phone stays locked between rounds.
/// The server still applies every rule (30 seconds, Path running).
@MainActor final class ChalisaLockScreen {
    typealias State = ChalisaActivityAttributes.ContentState

    static let shared = ChalisaLockScreen()
    static let alreadyCountedNote = ChalisaViewModel.alreadyCountedNote

    /// What the counter last showed, so a failed tap can add a note without more network calls.
    private var lastContent: State?
    /// The reader's count in the running Path, kept for when a reload fails.
    private var lastMarathonCount = 0

    private var service: ChalisaService { ChalisaService(appState: .shared) }
    private var activity: Activity<ChalisaActivityAttributes>? {
        Activity<ChalisaActivityAttributes>.activities.first { $0.activityState == .active || $0.activityState == .stale }
    }
    private var isCounterOn: Bool { ChalisaStore.isLockScreenCounterOn }

    /// The +1 counter (not the "ended" message) is on the Lock Screen right now.
    var isCounterShowing: Bool { activity?.content.state.showsCounter == true }

    /// Turns the counter on and shows the latest numbers. False when Live Activities are off for the app.
    func start() async -> Bool {
        ChalisaStore.isLockScreenCounterOn = true
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return false }
        await refresh()
        return true
    }

    /// Removes the counter and remembers that the reader turned it off.
    func stop() async {
        ChalisaStore.isLockScreenCounterOn = false
        for activity in Activity<ChalisaActivityAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }

    /// The account was deleted: remove its counts from the Lock Screen and forget its last tap.
    /// The on/off choice and the guest pass belong to the phone, so they're kept.
    func removeAfterAccountDeleted() async {
        ChalisaStore.clearLastTap()
        ChalisaStore.setPendingRoundID(nil)
        lastContent = nil
        lastMarathonCount = 0
        for activity in Activity<ChalisaActivityAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }

    /// Puts the counter on the Lock Screen while a Path runs, unless the reader turned it off.
    /// Called when the Chalisa screen shows a running Path the reader has joined.
    func showIfOn() async {
        guard isCounterOn, ActivityAuthorizationInfo().areActivitiesEnabled, !isCounterShowing else { return }
        await refresh()
    }

    /// Re-reads the numbers from the server and redraws the counter, if it's on.
    func refresh(note: String? = nil) async {
        guard isCounterOn else { return }
        let summary = try? await service.summary()
        if let summary, summary.state != "running" {
            await showClosed(summary.state == "scheduled" ? "Counting opens when the Path starts." : nil)
            return
        }
        let me = try? await service.myStats()
        let marathonID = summary?.marathon?.id
        await show(
            today: me?.today ?? summary?.myToday ?? 0,
            marathonCount: me?.marathons.first { $0.id == marathonID }?.count ?? lastMarathonCount,
            groupTotal: summary?.total ?? 0,
            lastReadAt: me?.lastReadAt,
            note: note
        )
    }

    /// Called after the app itself counts or undoes a round, to keep the counter in step.
    func onCountsChanged(_ counts: ChalisaCounts) {
        if let reading = counts.reading { ChalisaStore.saveLastTap(reading) }
        guard isCounterOn else { return }
        Task { await show(counts) }
    }

    /// The +1 button (Lock Screen or Control Center). Returns what happened, in a short line.
    func countRound() async -> String {
        do {
            let counts = try await service.addRound()
            if counts.duplicate {
                // The id of an earlier tap whose answer was lost: that round was counted then.
                if isCounterOn { await showNote(Self.alreadyCountedNote) }
                return Self.alreadyCountedNote
            }
            if let reading = counts.reading { ChalisaStore.saveLastTap(reading) }
            if isCounterOn { await show(counts, note: "Round counted") }
            return "Round counted"
        } catch {
            return await handleFailure(error)
        }
    }

    /// The Undo button: removes the latest tap if it's still within the undo time.
    func undoLastTap() async {
        guard let tap = ChalisaStore.lastTap else { return await showNote("Nothing to undo") }
        do {
            let counts = try await service.undo(tap.id)
            ChalisaStore.clearLastTap()
            await show(counts, note: "Removed")
        } catch let error as ChalisaError where error.code == ChalisaErrorCode.network {
            // Keep the tap so Undo can be tried again.
            await showNote("No connection, so nothing was removed. Tap Undo again.")
        } catch {
            ChalisaStore.clearLastTap()
            _ = await handleFailure(error)
        }
    }

    /// Home found no running Path: clear a counter that's still showing.
    func onNoMarathonRunning() async {
        if isCounterShowing { await showClosed(nil) }
    }

    // MARK: - Drawing

    /// Shows what went wrong and returns it as a short line.
    private func handleFailure(_ error: Error) async -> String {
        let note: String
        switch (error as? ChalisaError)?.code {
        case ChalisaErrorCode.marathonNotRunning:
            if isCounterOn { await showClosed(nil) }
            return "No Path"
        case ChalisaErrorCode.marathonNotStarted:
            if isCounterOn { await showClosed("Counting opens when the Path starts.") }
            return "Not started"
        case ChalisaErrorCode.nameRequired, ChalisaErrorCode.invalidGuestToken:
            note = "Open the app and enter your name to count"
        case ChalisaErrorCode.network:
            // The server may have counted it; the next +1 reuses the same id, so it can't count twice.
            note = "No connection. This round may not have been counted. Tap +1 again; it won't count twice."
        default:
            note = error.localizedDescription
        }
        if isCounterOn { await showNote(note) }
        return note
    }

    /// Adds a note to what's showing, using the last numbers instead of reloading them.
    private func showNote(_ note: String) async {
        var state = lastContent ?? State(headline: "Hanuman Chalisa", detail: "", today: 0, showsCounter: true, undoUntil: nil)
        state.detail = note
        state.undoUntil = undoUntil
        await present(state)
    }

    private func show(_ counts: ChalisaCounts, note: String? = nil) async {
        await show(
            today: counts.today,
            marathonCount: counts.marathon?.myCount ?? 0,
            groupTotal: counts.marathon?.total ?? 0,
            lastReadAt: counts.lastReadAt,
            note: note
        )
    }

    private func show(today: Int, marathonCount: Int, groupTotal: Int, lastReadAt: String?, note: String?) async {
        var detail = AppFormatters.parseISO(lastReadAt).map { "Last round at \(ChalisaFormat.lastRound($0)) · " } ?? ""
        detail += "Group \(ChalisaFormat.count(groupTotal))"
        let state = State(
            headline: "Today \(ChalisaFormat.count(today)) · Path \(ChalisaFormat.count(marathonCount))",
            detail: note.map { "\($0)\n\(detail)" } ?? detail,
            today: today,
            showsCounter: true,
            undoUntil: undoUntil
        )
        lastContent = State(headline: state.headline, detail: detail, today: today, showsCounter: true, undoUntil: nil)
        lastMarathonCount = marathonCount
        await present(state)
    }

    /// The Path isn't running: say so. With no note (it ended) the counter ends and can be
    /// dismissed; the reader's on/off choice is kept, so it comes back for the next Path.
    private func showClosed(_ note: String?) async {
        ChalisaStore.clearLastTap()
        if let note {
            await present(State(headline: "Hanuman Chalisa", detail: note, today: 0, showsCounter: false, undoUntil: nil))
            return
        }
        let ended = State(
            headline: "The Path has ended",
            detail: "Thank you for taking part. Your readings are in your Profile.",
            today: 0,
            showsCounter: false,
            undoUntil: nil
        )
        for activity in Activity<ChalisaActivityAttributes>.activities {
            await activity.end(ActivityContent(state: ended, staleDate: nil), dismissalPolicy: .default)
        }
    }

    private func present(_ state: State) async {
        let content = ActivityContent(state: state, staleDate: nil)
        if let activity {
            await activity.update(content)
            return
        }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        _ = try? Activity.request(attributes: ChalisaActivityAttributes(), content: content, pushType: nil)
    }

    private var undoUntil: Date? {
        guard ChalisaStore.canUndoLastTap else { return nil }
        return ChalisaStore.lastTap.flatMap { AppFormatters.parseISO($0.undoUntil) }
    }

}
