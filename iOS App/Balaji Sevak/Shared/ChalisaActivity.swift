import ActivityKit
import AppIntents
import Foundation

// Shared by the app and the ChalisaWidget extension. The extension only draws the Lock Screen
// counter; its buttons are LiveActivityIntents, which the system runs in the app's process,
// where the sign-in and guest pass live.

/// Hanuman Chalisa counter on the Lock Screen (Android's lock-screen notification).
nonisolated struct ChalisaActivityAttributes: ActivityAttributes {
    nonisolated struct ContentState: Codable, Hashable {
        /// "Today 12 · Path 340", or "The Path has ended".
        var headline: String
        /// A note about the last tap, or "Last round at 6:38 PM · Group 1,24,580".
        var detail: String
        var today: Int
        /// +1 and Undo are shown only while the Path is running.
        var showsCounter: Bool
        /// The latest tap can be undone until then.
        var undoUntil: Date?
    }
}

/// "+1 Round" on the Lock Screen counter and in Control Center.
nonisolated struct ChalisaCountRoundIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Count a Hanuman Chalisa round"
    static let description = IntentDescription("Adds one completed round to your count in the running Path.")

    init() {}

    func perform() async throws -> some IntentResult {
        #if !WIDGET_EXTENSION
        _ = await ChalisaLockScreen.shared.countRound()
        #endif
        return .result()
    }
}

/// "Undo" on the Lock Screen counter: removes the latest tap while it can still be undone.
nonisolated struct ChalisaUndoRoundIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Undo the last Hanuman Chalisa round"
    static let isDiscoverable = false

    init() {}

    func perform() async throws -> some IntentResult {
        #if !WIDGET_EXTENSION
        await ChalisaLockScreen.shared.undoLastTap()
        #endif
        return .result()
    }
}

/// "Stop" on the Lock Screen counter: removes it and remembers that the reader turned it off.
nonisolated struct ChalisaStopCounterIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Stop the Lock Screen counter"
    static let isDiscoverable = false

    init() {}

    func perform() async throws -> some IntentResult {
        #if !WIDGET_EXTENSION
        await ChalisaLockScreen.shared.stop()
        #endif
        return .result()
    }
}
