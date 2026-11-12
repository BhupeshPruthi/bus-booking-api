import Foundation
import Network

/// Uses the phone's normal DNS first. If the network's DNS can't find a name (Jio blocks
/// `*.up.railway.app`), the app switches to DNS over HTTPS with Cloudflare, reached by IP
/// address so it doesn't depend on the network's DNS (Android `FallbackDns`).
nonisolated enum EncryptedDNSFallback {
    private static let lock = NSLock()
    nonisolated(unsafe) private static var isOn = false

    /// The request never left the phone, so it's safe to send again once DNS is switched.
    static func isNameResolutionFailure(_ error: Error) -> Bool {
        guard let error = error as? URLError else { return false }
        return error.code == .cannotFindHost || error.code == .dnsLookupFailed
    }

    /// Resolves every later name over HTTPS for the rest of this launch.
    static func enable() {
        lock.lock()
        defer { lock.unlock() }
        guard !isOn else { return }
        isOn = true
        let context = NWParameters.PrivacyContext.default
        context.requireEncryptedNameResolution(
            true,
            fallbackResolver: .https(
                URL(string: "https://cloudflare-dns.com/dns-query")!,
                serverAddresses: [.hostPort(host: "1.1.1.1", port: 443), .hostPort(host: "1.0.0.1", port: 443)]
            )
        )
        // Drops the failed lookup so the retry asks Cloudflare.
        context.flushCache()
    }
}
