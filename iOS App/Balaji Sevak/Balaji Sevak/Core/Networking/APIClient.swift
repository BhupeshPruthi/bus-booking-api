import Foundation
import Pulse

actor APIClient {
    private let baseURL: URL
    private let session: any URLSessionProtocol
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    init(baseURL: URL = AppConfiguration.apiBaseURL, session: (any URLSessionProtocol)? = nil) {
        self.baseURL = baseURL
        #if DEBUG
        self.session = session ?? URLSessionProxy(configuration: .default)
        #else
        self.session = session ?? URLSession.shared
        #endif
        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
        encoder.outputFormatting = [.withoutEscapingSlashes]
    }

    func get<Value: Decodable>(_ path: String, accessToken: String? = nil, headers: [String: String] = [:]) async throws -> Value {
        try await send(path: path, method: "GET", body: Optional<Data>.none, accessToken: accessToken, headers: headers)
    }

    func post<Body: Encodable, Value: Decodable>(_ path: String, body: Body, accessToken: String? = nil, headers: [String: String] = [:]) async throws -> Value {
        try await send(path: path, method: "POST", body: encoder.encode(body), accessToken: accessToken, headers: headers)
    }

    func put<Body: Encodable, Value: Decodable>(_ path: String, body: Body, accessToken: String? = nil, headers: [String: String] = [:]) async throws -> Value {
        try await send(path: path, method: "PUT", body: encoder.encode(body), accessToken: accessToken, headers: headers)
    }

    func patch<Body: Encodable, Value: Decodable>(_ path: String, body: Body, accessToken: String? = nil, headers: [String: String] = [:]) async throws -> Value {
        try await send(path: path, method: "PATCH", body: encoder.encode(body), accessToken: accessToken, headers: headers)
    }

    func delete<Value: Decodable>(_ path: String, accessToken: String? = nil, headers: [String: String] = [:]) async throws -> Value {
        try await send(path: path, method: "DELETE", body: Optional<Data>.none, accessToken: accessToken, headers: headers)
    }

    func postMultipart<Value: Decodable>(_ path: String, fields: [String: String], image: Data?, accessToken: String) async throws -> Value {
        let boundary = "Boundary-\(UUID().uuidString)"
        var body = Data()
        for (name, value) in fields {
            body.append("--\(boundary)\r\n")
            body.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n")
            body.append("\(value)\r\n")
        }
        if let image {
            body.append("--\(boundary)\r\n")
            body.append("Content-Disposition: form-data; name=\"image\"; filename=\"event.jpg\"\r\n")
            body.append("Content-Type: image/jpeg\r\n\r\n")
            body.append(image)
            body.append("\r\n")
        }
        body.append("--\(boundary)--\r\n")
        return try await send(path: path, method: "POST", body: body, accessToken: accessToken, contentType: "multipart/form-data; boundary=\(boundary)")
    }

    private func send<Value: Decodable>(path: String, method: String, body: Data?, accessToken: String?, headers: [String: String] = [:], contentType: String = "application/json") async throws -> Value {
        guard let url = URL(string: path, relativeTo: baseURL) else { throw APIClientError.invalidURL }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        if let accessToken { request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization") }
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        request.httpBody = body

        let data: Data, response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch where EncryptedDNSFallback.isNameResolutionFailure(error) {
            // e.g. Jio refuses the Railway host name: retry once over DNS-over-HTTPS.
            EncryptedDNSFallback.enable()
            (data, response) = try await session.data(for: request)
        }
        return try decodeEnvelope(data, response)
    }

    /// Downloads a file (e.g. an Aarti & Path PDF) and moves it to `destination`, reporting
    /// progress from 0 to 1. Uses a plain URLSession: the debug network logger drops task
    /// delegates, which would hide progress.
    func downloadFile(_ path: String, to destination: URL, progress: @escaping @Sendable (Double) -> Void) async throws {
        guard let url = URL(string: path, relativeTo: baseURL) else { throw APIClientError.invalidURL }
        var request = URLRequest(url: url)
        request.setValue("application/pdf, application/json", forHTTPHeaderField: "Accept")

        let delegate = TransferProgressDelegate(onProgress: progress)
        let tempURL: URL, response: URLResponse
        do {
            (tempURL, response) = try await URLSession.shared.download(for: request, delegate: delegate)
        } catch where EncryptedDNSFallback.isNameResolutionFailure(error) {
            EncryptedDNSFallback.enable()
            (tempURL, response) = try await URLSession.shared.download(for: request, delegate: delegate)
        }
        defer { try? FileManager.default.removeItem(at: tempURL) }
        guard let http = response as? HTTPURLResponse else { throw APIClientError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else {
            // Errors come back as the usual JSON envelope.
            let data = (try? Data(contentsOf: tempURL)) ?? Data()
            if var error = (try? decoder.decode(APIEnvelope<EmptyValue>.self, from: data))?.error {
                error.statusCode = http.statusCode
                throw error
            }
            throw APIClientError.httpStatus(http.statusCode)
        }
        let fileManager = FileManager.default
        try fileManager.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
        if fileManager.fileExists(atPath: destination.path) { try fileManager.removeItem(at: destination) }
        try fileManager.moveItem(at: tempURL, to: destination)
        progress(1)
    }

    /// Multipart upload of text fields plus one file, reporting upload progress from 0 to 1.
    func uploadMultipart<Value: Decodable>(_ path: String, fields: [String: String], file: MultipartFile, accessToken: String, progress: @escaping @Sendable (Double) -> Void) async throws -> Value {
        guard let url = URL(string: path, relativeTo: baseURL) else { throw APIClientError.invalidURL }
        let boundary = "Boundary-\(UUID().uuidString)"
        var body = Data()
        for (name, value) in fields {
            body.append("--\(boundary)\r\n")
            body.append("Content-Disposition: form-data; name=\"\(name)\"\r\n")
            body.append("Content-Type: text/plain; charset=utf-8\r\n\r\n")
            body.append("\(value)\r\n")
        }
        let fileName = file.fileName.replacingOccurrences(of: "\"", with: "").replacingOccurrences(of: "\r", with: "").replacingOccurrences(of: "\n", with: "")
        body.append("--\(boundary)\r\n")
        body.append("Content-Disposition: form-data; name=\"\(file.fieldName)\"; filename=\"\(fileName)\"\r\n")
        body.append("Content-Type: \(file.mimeType)\r\n\r\n")
        body.append(try Data(contentsOf: file.fileURL))
        body.append("\r\n")
        body.append("--\(boundary)--\r\n")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        let delegate = TransferProgressDelegate(onProgress: progress)
        let data: Data, response: URLResponse
        do {
            (data, response) = try await URLSession.shared.upload(for: request, from: body, delegate: delegate)
        } catch where EncryptedDNSFallback.isNameResolutionFailure(error) {
            EncryptedDNSFallback.enable()
            (data, response) = try await URLSession.shared.upload(for: request, from: body, delegate: delegate)
        }
        return try decodeEnvelope(data, response)
    }

    private func decodeEnvelope<Value: Decodable>(_ data: Data, _ response: URLResponse) throws -> Value {
        guard let http = response as? HTTPURLResponse else { throw APIClientError.invalidResponse }
        let envelope = try? decoder.decode(APIEnvelope<Value>.self, from: data)
        if (200..<300).contains(http.statusCode), envelope?.success == true, let value = envelope?.data { return value }
        // Authentication endpoints can legitimately return 401 with a useful API error
        // (for example, an unaccepted Google token audience). Preserve that message.
        if var error = envelope?.error {
            error.statusCode = http.statusCode
            throw error
        }
        if http.statusCode == 401 { throw APIClientError.unauthorized }
        throw APIClientError.httpStatus(http.statusCode)
    }
}

nonisolated enum APIClientError: LocalizedError {
    case invalidURL, invalidResponse, unauthorized, httpStatus(Int)
    var errorDescription: String? {
        switch self {
        case .invalidURL: return "The API address is invalid."
        case .invalidResponse: return "The server returned an invalid response."
        case .unauthorized: return "Your session has expired. Please sign in again."
        case .httpStatus(let status): return "Request failed (error \(status)). Please try again."
        }
    }
}

nonisolated private extension Data {
    mutating func append(_ string: String) { append(Data(string.utf8)) }
}

/// A file sent in a multipart request; the client reads it from disk off the main actor.
nonisolated struct MultipartFile: Sendable {
    let fieldName: String
    let fileName: String
    let mimeType: String
    let fileURL: URL
}

/// Placeholder for decoding only the `error` of an envelope.
nonisolated private struct EmptyValue: Decodable {}

/// Reports upload (bytes sent) or download (task progress) for one URLSession task.
nonisolated private final class TransferProgressDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    private let onProgress: @Sendable (Double) -> Void
    private let lock = NSLock()
    private var observation: NSKeyValueObservation?

    init(onProgress: @escaping @Sendable (Double) -> Void) { self.onProgress = onProgress }

    func urlSession(_ session: URLSession, didCreateTask task: URLSessionTask) {
        // Downloads: follow the task's own progress (needs a Content-Length to move).
        guard task is URLSessionDownloadTask else { return }
        let onProgress = onProgress
        let observation = task.progress.observe(\.fractionCompleted, options: [.new]) { progress, _ in
            onProgress(min(max(progress.fractionCompleted, 0), 1))
        }
        lock.lock(); self.observation = observation; lock.unlock()
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        guard totalBytesExpectedToSend > 0 else { return }
        onProgress(min(Double(totalBytesSent) / Double(totalBytesExpectedToSend), 1))
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: (any Error)?) {
        lock.lock(); observation?.invalidate(); observation = nil; lock.unlock()
    }
}
