import Foundation
import Combine

@MainActor final class SessionStore: ObservableObject {
    private enum Keys { static let access = "access-token"; static let refresh = "refresh-token"; static let user = "user"; static let installed = "has-launched-before" }
    @Published private(set) var accessToken: String?
    @Published private(set) var refreshToken: String?
    @Published private(set) var user: UserInfo?

    init() { restore() }
    var isLoggedIn: Bool { !(accessToken?.isEmpty ?? true) }
    var effectiveIsAdmin: Bool { guard let user else { return false }; return user.isSuperUser || user.adminType == "super_admin" || user.adminType == "bus_admin" || (user.role == "admin" && user.adminType == nil) }
    var isAnyAdmin: Bool { guard let user else { return false }; return user.isSuperUser || user.role == "admin" || user.role == "superuser" || ["bus_admin", "stay_admin", "super_admin"].contains(user.adminType) }
    var canManageStay: Bool { user?.isSuperUser == true || user?.adminType == "super_admin" || user?.adminType == "stay_admin" || user?.capabilities.contains("stay.manage") == true }
    var canManageEvents: Bool { user?.isSuperUser == true || user?.adminType == "super_admin" || user?.adminType == "bus_admin" || user?.capabilities.contains("event.manage") == true }
    /// Schedules, edits and ends Hanuman Chalisa Paths, and adds rounds to their own entry.
    var canManageChalisa: Bool { user?.isSuperUser == true || user?.adminType == "super_admin" || user?.capabilities.contains("chalisa.manage") == true }
    /// Adds and deletes Aarti & Path PDFs (same admins as Events).
    var canManageAartiPath: Bool { user?.isSuperUser == true || user?.adminType == "super_admin" || user?.adminType == "bus_admin" || user?.capabilities.contains("aarti_path.manage") == true }

    func save(_ login: LoginData) throws {
        try KeychainStore.write(login.accessToken, for: Keys.access); try KeychainStore.write(login.refreshToken, for: Keys.refresh)
        UserDefaults.standard.set(try JSONEncoder().encode(login.user), forKey: Keys.user)
        accessToken = login.accessToken; refreshToken = login.refreshToken; user = login.user
    }
    func updateTokens(_ tokens: TokenData) throws {
        try KeychainStore.write(tokens.accessToken, for: Keys.access); try KeychainStore.write(tokens.refreshToken, for: Keys.refresh)
        accessToken = tokens.accessToken; refreshToken = tokens.refreshToken
    }
    /// Mirrors Android: after a booking, save the entered name if the profile had none.
    func updateUserNameIfBlank(_ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let user, (user.name ?? "").trimmingCharacters(in: .whitespaces).isEmpty, !trimmed.isEmpty else { return }
        let updated = UserInfo(id: user.id, mobile: user.mobile, name: trimmed, email: user.email, role: user.role, isSuperUser: user.isSuperUser, adminType: user.adminType, capabilities: user.capabilities, isNewUser: user.isNewUser)
        UserDefaults.standard.set(try? JSONEncoder().encode(updated), forKey: Keys.user)
        self.user = updated
    }
    func clear() {
        KeychainStore.delete(Keys.access); KeychainStore.delete(Keys.refresh); UserDefaults.standard.removeObject(forKey: Keys.user)
        accessToken = nil; refreshToken = nil; user = nil
    }
    private func restore() {
        // Keychain items survive app deletion but UserDefaults does not. On the
        // first launch after a (re)install, drop stale tokens so the app is not
        // left logged in without a user record.
        if !UserDefaults.standard.bool(forKey: Keys.installed) {
            if UserDefaults.standard.data(forKey: Keys.user) == nil { KeychainStore.delete(Keys.access); KeychainStore.delete(Keys.refresh) }
            UserDefaults.standard.set(true, forKey: Keys.installed)
        }
        accessToken = KeychainStore.read(Keys.access); refreshToken = KeychainStore.read(Keys.refresh)
        if let data = UserDefaults.standard.data(forKey: Keys.user) { user = try? JSONDecoder().decode(UserInfo.self, from: data) }
    }
}
