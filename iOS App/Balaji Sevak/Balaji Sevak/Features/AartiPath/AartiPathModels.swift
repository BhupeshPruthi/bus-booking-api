import Foundation

/// One Aarti & Path PDF (`GET /api/aarti-path`). Items never change: an admin fixes a
/// title or file by deleting it and adding it again, so a cached PDF never goes stale.
nonisolated struct AartiPathItem: Codable, Identifiable, Hashable {
    let id: String
    let title: String
    let pageCount: Int?
    let sizeBytes: Int?
    let pdfUrl: String?
    let createdAt: String?

    /// "2 pages", or nil when the uploader's phone couldn't count them.
    var pagesText: String? {
        guard let pageCount, pageCount > 0 else { return nil }
        return pageCount == 1 ? "1 page" : "\(pageCount) pages"
    }

    var sizeText: String? {
        guard let sizeBytes, sizeBytes > 0 else { return nil }
        return AartiPathFormat.size(sizeBytes)
    }
}

nonisolated struct AartiPathDeleteResult: Decodable { let id: String? }

nonisolated enum AartiPathFormat {
    static let titleLimit = 80
    /// Matches the server's limit (20 × 1024 × 1024 bytes).
    static let maxUploadBytes = 20 * 1024 * 1024

    /// "93 KB", "1.3 MB", "12 MB" in 1024-based units, matching Android and the 20 MB limit.
    static func size(_ bytes: Int) -> String {
        let kb = Double(bytes) / 1024
        if kb < 1024 { return "\(Int(max(kb, 1))) KB" }
        let mb = kb / 1024
        return mb >= 10 ? "\(Int(mb)) MB" : String(format: "%.1f MB", locale: Locale(identifier: "en_US_POSIX"), mb)
    }

    /// "hanuman_Chalisa-in  English.pdf" → "Hanuman Chalisa in English".
    static func title(fromFileName name: String) -> String {
        var base = name
        if base.lowercased().hasSuffix(".pdf") { base = String(base.dropLast(4)) }
        base = base.replacingOccurrences(of: "_", with: " ").replacingOccurrences(of: "-", with: " ")
        base = base.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
        guard let first = base.first else { return "" }
        return String(String(first).uppercased() + base.dropFirst()).prefix(titleLimit).description
    }

    /// A file name that is safe to share, e.g. "Hanuman Chalisa.pdf".
    static func shareFileName(for title: String) -> String {
        let cleaned = title
            .components(separatedBy: CharacterSet(charactersIn: "/\\:?%*|\"<>\n\r\t"))
            .joined(separator: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return (cleaned.isEmpty ? "Aarti" : cleaned) + ".pdf"
    }
}

/// A PDF opened once stays on the phone for good: Application Support/aarti-path/<id>.pdf
/// (not Caches, which iOS may empty), excluded from iCloud backup. The last list is saved
/// beside the PDFs so the screen still opens saved PDFs with no network.
nonisolated enum AartiPathCache {
    /// Created once per launch. Downloads land in a temp file and are moved here only when
    /// complete, so a half-finished download never counts as saved.
    private static let directory: URL = {
        let fileManager = FileManager.default
        var folder = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("aarti-path", isDirectory: true)
        try? fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? folder.setResourceValues(values)
        // Early test builds kept PDFs in Caches; that folder is no longer used.
        let oldFolder = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("aarti-path", isDirectory: true)
        try? fileManager.removeItem(at: oldFolder)
        return folder
    }()
    private static var listURL: URL { directory.appendingPathComponent("list.json") }

    static func fileURL(for id: String) -> URL { directory.appendingPathComponent("\(id).pdf") }

    static func isCached(_ id: String) -> Bool { FileManager.default.fileExists(atPath: fileURL(for: id).path) }

    static func cachedIDs() -> Set<String> {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        return Set(names.filter { $0.hasSuffix(".pdf") }.map { String($0.dropLast(4)) })
    }

    static func remove(_ id: String) { try? FileManager.default.removeItem(at: fileURL(for: id)) }

    /// Deletes saved PDFs whose item is no longer in the list.
    static func prune(keeping ids: Set<String>) {
        for id in cachedIDs() where !ids.contains(id) { remove(id) }
    }

    /// Saves a copy of a file the admin just uploaded, so it opens without downloading.
    static func store(copyOf source: URL, for id: String) {
        let fileManager = FileManager.default
        try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let destination = fileURL(for: id)
        try? fileManager.removeItem(at: destination)
        try? fileManager.copyItem(at: source, to: destination)
    }

    static func savedList() -> [AartiPathItem]? {
        guard let data = try? Data(contentsOf: listURL) else { return nil }
        return try? JSONDecoder().decode([AartiPathItem].self, from: data)
    }

    static func saveList(_ items: [AartiPathItem]) {
        guard let data = try? JSONEncoder().encode(items) else { return }
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try? data.write(to: listURL, options: .atomic)
    }

    /// A copy named after the title, so a shared PDF isn't called "<uuid>.pdf".
    static func shareCopy(of id: String, title: String) -> URL? {
        let fileManager = FileManager.default
        let folder = fileManager.temporaryDirectory.appendingPathComponent("aarti-path-share/\(id)", isDirectory: true)
        let destination = folder.appendingPathComponent(AartiPathFormat.shareFileName(for: title))
        do {
            try fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
            if fileManager.fileExists(atPath: destination.path) { try fileManager.removeItem(at: destination) }
            try fileManager.copyItem(at: fileURL(for: id), to: destination)
            return destination
        } catch {
            return nil
        }
    }
}

extension Error {
    /// The item was already deleted (404), which counts as deleted.
    var isAartiPathNotFound: Bool {
        if let error = self as? APIError { return error.statusCode == 404 }
        if case .httpStatus(404)? = self as? APIClientError { return true }
        return false
    }

    /// No connection (or it dropped), as opposed to an error from the server.
    var isAartiPathConnectionProblem: Bool {
        guard let error = self as? URLError else { return false }
        let codes: Set<URLError.Code> = [
            .notConnectedToInternet, .networkConnectionLost, .cannotFindHost, .cannotConnectToHost,
            .dnsLookupFailed, .timedOut, .dataNotAllowed, .internationalRoamingOff, .secureConnectionFailed,
        ]
        return codes.contains(error.code)
    }

    /// The request was cancelled because the screen went away; not worth showing.
    var isAartiPathCancellation: Bool {
        if self is CancellationError { return true }
        return (self as? URLError)?.code == .cancelled
    }
}
