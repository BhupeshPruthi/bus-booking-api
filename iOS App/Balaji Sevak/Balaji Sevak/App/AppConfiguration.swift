import Foundation

nonisolated enum AppConfiguration {
    static let defaultAPIBaseURL = URL(string: "https://bus-booking-api-production-94dc.up.railway.app/api/")!
    /// Debug builds can point at a local server, like Android's local-dev override:
    /// launch with `-APIBaseURL http://127.0.0.1:8080/api/`.
    static let apiBaseURL: URL = {
        #if DEBUG
        if let override = UserDefaults.standard.string(forKey: "APIBaseURL"), let url = URL(string: override) {
            return url.absoluteString.hasSuffix("/") ? url : url.appendingPathComponent("")
        }
        #endif
        return defaultAPIBaseURL
    }()
    static let liveEventsURL = URL(string: "http://bolguru.com/home/live-events/")!
    /// Configure this as the iOS OAuth client ID in the target's build settings.
    static let googleClientID = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String ?? ""
    static let appVersion: String = {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(version) (\(build))"
    }()
}
