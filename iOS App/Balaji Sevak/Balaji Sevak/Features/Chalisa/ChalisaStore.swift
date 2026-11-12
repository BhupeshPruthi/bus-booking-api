import Foundation

/// Hanuman Chalisa settings kept on this phone (Android `ChalisaStore`). The guest pass lets
/// someone who isn't signed in keep counting under the same name; it isn't cleared on logout
/// because it belongs to the phone.
nonisolated enum ChalisaStore {
    private enum Keys {
        static let guestToken = "chalisa-guest-token"
        static let lockScreenCounter = "chalisa.lockScreenCounterOn"
        static let lastTapID = "chalisa.lastTapID"
        static let lastTapUndoUntil = "chalisa.lastTapUndoUntil"
        static let pendingRoundID = "chalisa.pendingRoundID"
        static let pendingRoundSince = "chalisa.pendingRoundSince"
    }

    /// A lost answer is retried within minutes; an older pending id is dropped, not reused.
    private static let pendingRoundMaxAge: TimeInterval = 10 * 60
    private static var defaults: UserDefaults { .standard }

    static var guestToken: String? { KeychainStore.read(Keys.guestToken) }
    static func saveGuestToken(_ token: String) { try? KeychainStore.write(token, for: Keys.guestToken) }
    static func clearGuestToken() { KeychainStore.delete(Keys.guestToken) }

    /// On by default; off only once the reader turns it off.
    static var isLockScreenCounterOn: Bool {
        get { defaults.object(forKey: Keys.lockScreenCounter) as? Bool ?? true }
        set { defaults.set(newValue, forKey: Keys.lockScreenCounter) }
    }

    /// Id of a +1 whose answer never arrived (timeout, lost connection). The server may have
    /// counted it, so the next +1 reuses this id and can't be counted twice.
    static var pendingRoundID: String? {
        guard let id = defaults.string(forKey: Keys.pendingRoundID),
              let since = defaults.object(forKey: Keys.pendingRoundSince) as? Date else { return nil }
        let age = Date().timeIntervalSince(since)
        return age >= 0 && age <= pendingRoundMaxAge ? id : nil
    }

    static func setPendingRoundID(_ id: String?) {
        guard let id else {
            defaults.removeObject(forKey: Keys.pendingRoundID)
            defaults.removeObject(forKey: Keys.pendingRoundSince)
            return
        }
        guard defaults.string(forKey: Keys.pendingRoundID) != id else { return }
        defaults.set(id, forKey: Keys.pendingRoundID)
        defaults.set(Date(), forKey: Keys.pendingRoundSince)
    }

    /// The latest tap, so the lock-screen Undo knows what to remove.
    static func saveLastTap(_ reading: ChalisaReading) {
        defaults.set(reading.clientRequestId, forKey: Keys.lastTapID)
        defaults.set(reading.undoUntil, forKey: Keys.lastTapUndoUntil)
    }

    static var lastTap: (id: String, undoUntil: String)? {
        guard let id = defaults.string(forKey: Keys.lastTapID),
              let until = defaults.string(forKey: Keys.lastTapUndoUntil) else { return nil }
        return (id, until)
    }

    static func clearLastTap() {
        defaults.removeObject(forKey: Keys.lastTapID)
        defaults.removeObject(forKey: Keys.lastTapUndoUntil)
    }

    /// The latest tap can still be undone.
    static var canUndoLastTap: Bool {
        guard let until = lastTap.flatMap({ AppFormatters.parseISO($0.undoUntil) }) else { return false }
        return until > Date()
    }
}
