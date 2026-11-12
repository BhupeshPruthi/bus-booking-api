import Foundation
import Combine
import PDFKit

/// The Aarti & Path list. Anyone can read it; admins can also delete items.
@MainActor final class AartiPathListViewModel: ObservableObject {
    @Published private(set) var items: [AartiPathItem]
    @Published private(set) var cachedIDs: Set<String> = []
    @Published private(set) var isLoading = false
    /// Shown full screen only when there is no list to show at all (none saved, none loaded).
    @Published var error: String?
    /// The refresh failed, so the saved list is shown with a small "Offline" notice.
    @Published private(set) var isShowingSavedList = false
    /// A list is available, even an empty one: saved on the phone or loaded this time.
    @Published private(set) var hasList: Bool
    @Published var actionError: String?
    @Published var toast: String?
    @Published private(set) var deletingID: String?

    init() {
        let saved = AartiPathCache.savedList()
        items = saved ?? []
        hasList = saved != nil
        cachedIDs = AartiPathCache.cachedIDs()
    }

    func refreshCachedIDs() { cachedIDs = AartiPathCache.cachedIDs() }

    func load(using appState: AppState) async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let loaded: [AartiPathItem] = try await appState.api.get("aarti-path")
            items = loaded
            error = nil
            hasList = true
            isShowingSavedList = false
            AartiPathCache.saveList(loaded)
            AartiPathCache.prune(keeping: Set(loaded.map(\.id)))
            refreshCachedIDs()
        } catch where error.isAartiPathCancellation {
            // Screen went away mid-request; keep what is shown.
        } catch {
            if hasList {
                // Offline: the saved list still opens every PDF already on the phone.
                isShowingSavedList = true
            } else {
                self.error = error.localizedDescription
            }
        }
    }

    /// An admin's upload finished: show it at the end of the list (the order added).
    func added(_ item: AartiPathItem) {
        if !items.contains(where: { $0.id == item.id }) { items.append(item) }
        AartiPathCache.saveList(items)
        refreshCachedIDs()
        toast = "Added \(item.title)"
    }

    func delete(_ item: AartiPathItem, using appState: AppState) async {
        guard deletingID == nil else { return }
        deletingID = item.id
        defer { deletingID = nil }
        do {
            let _: AartiPathDeleteResult = try await appState.authDelete("admin/aarti-path/\(item.id)")
            removeLocally(item)
        } catch where error.isAartiPathNotFound {
            // Someone else deleted it already.
            removeLocally(item)
        } catch where error.isAartiPathCancellation {
        } catch {
            actionError = error.localizedDescription
        }
    }

    private func removeLocally(_ item: AartiPathItem) {
        items.removeAll { $0.id == item.id }
        AartiPathCache.remove(item.id)
        AartiPathCache.saveList(items)
        refreshCachedIDs()
        toast = "Deleted \(item.title)"
    }
}

/// The admin's "Add Aarti or Path" sheet.
@MainActor final class AddAartiPathViewModel: ObservableObject {
    struct PickedFile: Equatable {
        let url: URL
        let name: String
        let sizeBytes: Int
        let pageCount: Int
        var detail: String { "\(pageCount == 1 ? "1 page" : "\(pageCount) pages") · \(AartiPathFormat.size(sizeBytes))" }
    }

    @Published var title = "" {
        didSet { if title.count > AartiPathFormat.titleLimit { title = String(title.prefix(AartiPathFormat.titleLimit)) } }
    }
    @Published private(set) var file: PickedFile?
    @Published private(set) var fileError: String?
    /// Non-nil while uploading, from 0 to 1.
    @Published private(set) var progress: Double?
    @Published private(set) var uploadError: String?
    /// The title last filled in from a file name; replaced when another file is picked,
    /// unless the admin has typed their own.
    private var autoTitle = ""

    var isUploading: Bool { progress != nil }
    var trimmedTitle: String { title.trimmingCharacters(in: .whitespacesAndNewlines) }
    var canUpload: Bool { file != nil && !trimmedTitle.isEmpty && !isUploading }

    func pick(_ result: Result<URL, Error>) {
        guard case .success(let source) = result else {
            if case .failure(let error) = result, !error.isAartiPathCancellation { fileError = error.localizedDescription }
            return
        }
        fileError = nil
        uploadError = nil

        // Files from iCloud or other apps are security scoped: copy before reading.
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        let fileManager = FileManager.default
        let folder = fileManager.temporaryDirectory.appendingPathComponent("aarti-path-upload", isDirectory: true)
        let copy = folder.appendingPathComponent("\(UUID().uuidString).pdf")
        do {
            try fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
            try fileManager.copyItem(at: source, to: copy)
        } catch {
            fileError = "Couldn't read this file. Try picking it again."
            return
        }

        let size = (try? fileManager.attributesOfItem(atPath: copy.path)[.size] as? Int) ?? 0
        if size > AartiPathFormat.maxUploadBytes {
            try? fileManager.removeItem(at: copy)
            let megabytes = Double(size) / 1_048_576
            fileError = "This PDF is \(String(format: "%.1f", megabytes)) MB. The limit is 20 MB."
            return
        }
        guard let document = PDFDocument(url: copy), !document.isLocked, document.pageCount > 0 else {
            try? fileManager.removeItem(at: copy)
            fileError = "This file can't be opened as a PDF."
            return
        }

        discardFile()
        let name = source.lastPathComponent
        file = PickedFile(url: copy, name: name, sizeBytes: size, pageCount: document.pageCount)
        let suggested = AartiPathFormat.title(fromFileName: name)
        if trimmedTitle.isEmpty || title == autoTitle {
            title = suggested
            autoTitle = title
        }
    }

    /// Uploads, returning the new item; nil when it failed (the error is shown in the sheet).
    func upload(using appState: AppState) async -> AartiPathItem? {
        guard let file, canUpload else { return nil }
        uploadError = nil
        progress = 0
        defer { progress = nil }
        let upload = MultipartFile(fieldName: "file", fileName: file.name, mimeType: "application/pdf", fileURL: file.url)
        do {
            let fields = ["title": trimmedTitle, "pageCount": String(file.pageCount)]
            let item: AartiPathItem = try await appState.authorized { token in
                guard let token else { throw APIClientError.unauthorized }
                return try await appState.api.uploadMultipart("admin/aarti-path", fields: fields, file: upload, accessToken: token) { value in
                    Task { @MainActor in
                        guard self.progress != nil else { return }
                        // An upload retried after a token refresh starts again from 0.
                        self.progress = value
                    }
                }
            }
            AartiPathCache.store(copyOf: file.url, for: item.id)
            discardFile()
            return item
        } catch where error.isAartiPathCancellation {
            return nil
        } catch {
            uploadError = error.localizedDescription
            return nil
        }
    }

    /// Removes the temporary copy (sheet closed or another file picked).
    func discardFile() {
        if let file { try? FileManager.default.removeItem(at: file.url) }
        file = nil
    }
}

/// Opens one PDF: from the phone if saved, otherwise downloads it first.
@MainActor final class AartiPathReaderViewModel: ObservableObject {
    enum Phase {
        case downloading(Double?)
        case ready(PDFDocument)
        /// Not saved on the phone yet and there's no connection.
        case offline
        case failed(String)
    }

    @Published private(set) var phase: Phase = .downloading(nil)
    @Published private(set) var shareURL: URL?
    @Published var currentPage = 1
    let item: AartiPathItem

    init(item: AartiPathItem) { self.item = item }

    func load(using appState: AppState) async {
        if case .ready = phase { return }
        if AartiPathCache.isCached(item.id), open() { return }

        phase = .downloading(nil)
        do {
            try await appState.api.downloadFile("aarti-path/\(item.id)/pdf", to: AartiPathCache.fileURL(for: item.id)) { value in
                Task { @MainActor in
                    guard case .downloading = self.phase else { return }
                    self.phase = .downloading(value)
                }
            }
            if !open() {
                AartiPathCache.remove(item.id)
                phase = .failed("This PDF couldn't be opened. Please try again later.")
            }
        } catch where error.isAartiPathCancellation {
            // Left the screen; the next visit starts again.
        } catch where error.isAartiPathConnectionProblem {
            phase = .offline
        } catch {
            phase = .failed(error.isAartiPathNotFound
                ? "This item was removed. Go back and refresh the list."
                : error.localizedDescription)
        }
    }

    func retry(using appState: AppState) async {
        phase = .downloading(nil)
        await load(using: appState)
    }

    private func open() -> Bool {
        guard let document = PDFDocument(url: AartiPathCache.fileURL(for: item.id)), document.pageCount > 0 else { return false }
        phase = .ready(document)
        currentPage = 1
        shareURL = AartiPathCache.shareCopy(of: item.id, title: item.title)
        return true
    }
}
