import Foundation
import Combine

/// iOS counterpart of Android's forced Play Store update (MainActivity): at launch and
/// whenever the app comes back to the foreground, looks up this app's latest App Store
/// version and requires an update when it is newer than the installed one.
/// A failed lookup, an app that isn't listed yet or no newer version never blocks the app.
@MainActor final class AppStoreUpdateChecker: ObservableObject {
    /// App Store page of the newer version; nil while this version can keep being used.
    @Published private(set) var requiredUpdateURL: URL?

    private var lookup: Task<Void, Never>?

    func check() {
        guard Self.isEnabled, lookup == nil, let bundleID = Bundle.main.bundleIdentifier else { return }
        lookup = Task {
            defer { lookup = nil }
            // On failure keep the last answer, so a network error never lifts or adds the block.
            guard let listing = try? await Self.fetchListing(bundleID: bundleID) else { return }
            requiredUpdateURL = Self.requiresUpdate(to: listing) ? listing.storeURL : nil
        }
    }

    /// Debug builds can skip the check, like AppConfiguration's `-APIBaseURL`:
    /// launch with `-SkipForcedUpdate YES`.
    private static var isEnabled: Bool {
        #if DEBUG
        !UserDefaults.standard.bool(forKey: "SkipForcedUpdate")
        #else
        true
        #endif
    }

    private static func requiresUpdate(to listing: AppStoreListing) -> Bool {
        let installed = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        let os = ProcessInfo.processInfo.operatingSystemVersion
        let osVersion = "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)"
        // A device below the new version's minimum iOS can't install it, so don't lock it out.
        if let minimumOS = listing.minimumOsVersion, isVersion(minimumOS, newerThan: osVersion) { return false }
        return listing.storeURL != nil && isVersion(listing.version, newerThan: installed)
    }

    private static func fetchListing(bundleID: String) async throws -> AppStoreListing? {
        var components = URLComponents(string: "https://itunes.apple.com/lookup")!
        components.queryItems = [URLQueryItem(name: "bundleId", value: bundleID), URLQueryItem(name: "country", value: "in")]
        let request = URLRequest(url: components.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return try JSONDecoder().decode(AppStoreLookupResponse.self, from: data).results.first
    }

    /// Compares dotted versions number by number, so 1.0.10 is newer than 1.0.9 and 1.0 equals 1.0.0.
    nonisolated static func isVersion(_ candidate: String, newerThan current: String) -> Bool {
        func numbers(_ version: String) -> [Int] {
            version.split(separator: ".").map { Int($0.prefix(while: \.isNumber)) ?? 0 }
        }
        let lhs = numbers(candidate), rhs = numbers(current)
        for index in 0..<max(lhs.count, rhs.count) {
            let left = index < lhs.count ? lhs[index] : 0
            let right = index < rhs.count ? rhs[index] : 0
            if left != right { return left > right }
        }
        return false
    }
}

/// iTunes lookup API response (https://itunes.apple.com/lookup?bundleId=…); only the fields used here.
nonisolated private struct AppStoreLookupResponse: Decodable { let results: [AppStoreListing] }

nonisolated private struct AppStoreListing: Decodable {
    let version: String
    let trackViewUrl: URL?
    let trackId: Int?
    let minimumOsVersion: String?

    var storeURL: URL? { trackViewUrl ?? trackId.flatMap { URL(string: "https://apps.apple.com/app/id\($0)") } }
}
