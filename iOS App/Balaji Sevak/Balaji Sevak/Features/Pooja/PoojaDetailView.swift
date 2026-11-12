import Combine
import SwiftUI
import UIKit

@MainActor
final class PoojaDetailViewModel: ObservableObject {
    let poojaID: String
    @Published private(set) var pooja: PoojaDetailData?
    @Published private(set) var isLoading = false
    @Published private(set) var cancelInProgressID: String?
    @Published private(set) var isDeleting = false
    @Published var deletionMessage: String?
    @Published var error: String?
    @Published private(set) var loadFailed = false

    init(poojaID: String) { self.poojaID = poojaID }

    func load(isAdmin: Bool, using appState: AppState) async {
        isLoading = true; loadFailed = false
        do {
            pooja = try await appState.authGet(isAdmin ? "admin/poojas/\(poojaID)" : "poojas/\(poojaID)")
        } catch {
            if pooja == nil { loadFailed = true }
            if !(error is CancellationError) { self.error = error.localizedDescription }
        }
        isLoading = false
    }

    func cancelBooking(_ booking: PoojaBookingData, using appState: AppState) async {
        cancelInProgressID = booking.id
        do {
            let _: PoojaBookingData = try await appState.authPost("admin/poojas/\(poojaID)/bookings/\(booking.id)/cancel", body: PoojaEmptyBody())
            cancelInProgressID = nil
            await load(isAdmin: true, using: appState)
        } catch {
            cancelInProgressID = nil
            self.error = error.localizedDescription
        }
    }

    func delete(using appState: AppState) async {
        guard !isDeleting else { return }
        isDeleting = true
        do {
            let result: DeletePoojaResult = try await appState.authDelete("admin/poojas/\(poojaID)")
            deletionMessage = result.deletionType == "hard_delete"
                ? "Puja schedule deleted"
                : "Puja schedule and \(result.cancelledBookings ?? 0) confirmed token(s) cancelled"
        } catch {
            self.error = error.localizedDescription
        }
        isDeleting = false
    }
}

struct PoojaDetailView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: PoojaDetailViewModel
    let onEdit: () -> Void
    let onBook: () -> Void
    @State private var bookingToCancel: PoojaBookingData?
    @State private var showDeleteConfirmation = false
    @State private var exportFile: PoojaExportFile?

    init(poojaID: String, onEdit: @escaping () -> Void, onBook: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: PoojaDetailViewModel(poojaID: poojaID))
        self.onEdit = onEdit
        self.onBook = onBook
    }

    private var isAdmin: Bool { appState.session.canManagePooja }

    var body: some View {
        content
            .navigationTitle("Puja Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbarContent }
            .onAppear { reload() }
            .onChange(of: isAdmin) { _, _ in reload() }
            .alert("Cancel Token", isPresented: Binding(get: { bookingToCancel != nil }, set: { if !$0 { bookingToCancel = nil } }), presenting: bookingToCancel) { booking in
                Button("Cancel Token", role: .destructive) {
                    Task { await viewModel.cancelBooking(booking, using: appState) }
                }
                Button("Keep", role: .cancel) {}
            } message: { booking in
                Text("Cancel token \(booking.tokenNumber.map { "#\($0)" } ?? booking.name)? This will release this pooja token for another booking request.")
            }
            .alert("Delete Puja Schedule", isPresented: $showDeleteConfirmation) {
                Button(bookingCount > 0 ? "Cancel Schedule" : "Delete", role: .destructive) {
                    Task { await viewModel.delete(using: appState) }
                }
                Button("Keep", role: .cancel) {}
            } message: {
                Text(bookingCount > 0
                     ? "This schedule has booking history. The schedule and all confirmed tokens will be cancelled, while the history is preserved."
                     : "This schedule has no bookings and will be permanently deleted. This cannot be undone.")
            }
            .alert("Puja Schedule", isPresented: Binding(get: { viewModel.deletionMessage != nil }, set: { if !$0 { viewModel.deletionMessage = nil } }), presenting: viewModel.deletionMessage) { _ in
                Button("OK") { dismiss() }
            } message: { Text($0) }
            .sheet(isPresented: Binding(get: { viewModel.error != nil }, set: { if !$0 { viewModel.error = nil } })) {
                AppErrorSheet(title: "Error", message: viewModel.error ?? "Something went wrong") { viewModel.error = nil }
                    .presentationDetents([.height(270)])
                    .presentationDragIndicator(.visible)
            }
            .sheet(item: $exportFile) { file in
                PoojaActivityView(items: [file.url]).ignoresSafeArea()
            }
    }

    private var bookingCount: Int { viewModel.pooja?.bookings?.count ?? 0 }

    @ToolbarContentBuilder private var toolbarContent: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            if let pooja = viewModel.pooja {
                if isAdmin && pooja.status == "scheduled" && !pooja.window.isExpired {
                    Button(action: onEdit) { Image(systemName: "pencil") }
                        .accessibilityLabel("Edit puja schedule")
                        .disabled(viewModel.isDeleting)
                    Button { showDeleteConfirmation = true } label: { Image(systemName: "trash").foregroundStyle(.red) }
                        .accessibilityLabel("Delete puja schedule")
                        .disabled(viewModel.isDeleting)
                }
                if isAdmin, let bookings = pooja.bookings, !bookings.isEmpty {
                    Button { export(pooja, bookings) } label: { Label("Export", systemImage: "square.and.arrow.up").labelStyle(.titleAndIcon) }
                }
            }
        }
    }

    @ViewBuilder private var content: some View {
        if let pooja = viewModel.pooja {
            loaded(pooja)
        } else if viewModel.isLoading || !viewModel.loadFailed {
            LoadingView()
        } else {
            ErrorStateView(message: "Failed to load pooja") { reload() }
        }
    }

    private func loaded(_ pooja: PoojaDetailData) -> some View {
        let window = pooja.window
        let canCancelTokens = !window.isExpired
        let bookings = pooja.bookings ?? []
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                PoojaSummaryCard(pooja: pooja, tokensText: "Tokens: \(pooja.availableTokens) available / \(pooja.totalTokens) total")
                if !isAdmin {
                    Button(action: onBook) {
                        Text(window.buttonLabel(opensAt: pooja.bookingOpensAt ?? pooja.scheduledAt))
                            .font(.body.weight(.semibold)).lineLimit(1)
                            .frame(maxWidth: .infinity).frame(height: 48)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppTheme.brandOrange)
                    .disabled(!window.canBook)
                } else {
                    Text("Enrolled (\(bookings.count))").font(.headline).padding(.top, 4)
                    if bookings.isEmpty {
                        Text("No one has enrolled yet.").font(.subheadline).foregroundStyle(.secondary)
                    } else {
                        ForEach(bookings) { booking in
                            PoojaBookingRow(
                                booking: booking,
                                canCancel: canCancelTokens && booking.status == "confirmed",
                                isCancelling: viewModel.cancelInProgressID == booking.id,
                                onCancel: { bookingToCancel = booking }
                            )
                        }
                    }
                }
            }
            .padding(16)
        }
        .refreshable { await viewModel.load(isAdmin: isAdmin, using: appState) }
    }

    private func reload() { Task { await viewModel.load(isAdmin: isAdmin, using: appState) } }

    private func export(_ pooja: PoojaDetailData, _ bookings: [PoojaBookingData]) {
        do {
            exportFile = PoojaExportFile(url: try PoojaBookingsPDF.write(pooja: pooja, bookings: bookings))
        } catch {
            viewModel.error = "Unable to export pooja tokens PDF"
        }
    }
}

private struct PoojaBookingRow: View {
    let booking: PoojaBookingData
    let canCancel: Bool
    let isCancelling: Bool
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(booking.name).font(.subheadline.weight(.bold)).lineLimit(1)
                    if let token = booking.tokenNumber {
                        Text("Token #\(token)").font(.caption.weight(.semibold)).foregroundStyle(AppTheme.brandOrange)
                    }
                }
                Spacer()
                PoojaStatusChip(text: booking.status.poojaCapitalizedFirst)
            }
            Label(booking.phone, systemImage: "phone.fill").font(.subheadline).foregroundStyle(.secondary)
            Text("Members: \(booking.memberCount)").font(.subheadline).foregroundStyle(.secondary)
            Text("City: \(booking.city)").font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
            if let createdAt = booking.createdAt {
                Text("Booked: \(createdAt.fullIndiaDateTime)").font(.caption2).foregroundStyle(.tertiary)
            }
            if let cancelledAt = booking.cancelledAt {
                Text("Cancelled: \(cancelledAt.fullIndiaDateTime)").font(.caption2).foregroundStyle(.tertiary)
            }
            if canCancel {
                Button(role: .destructive, action: onCancel) {
                    Group {
                        if isCancelling { ProgressView() } else { Text("Cancel Token") }
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(.red)
                .disabled(isCancelling)
                .padding(.top, 4)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - PDF export (mirrors Android `createPoojaBookingsPdf`)

struct PoojaExportFile: Identifiable { let url: URL; var id: URL { url } }

enum PoojaBookingsPDF {
    static func write(pooja: PoojaDetailData, bookings: [PoojaBookingData]) throws -> URL {
        let pageWidth: CGFloat = 595, pageHeight: CGFloat = 842, margin: CGFloat = 40
        let contentWidth = pageWidth - margin * 2
        let pageRect = CGRect(x: 0, y: 0, width: pageWidth, height: pageHeight)

        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("exports", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let url = dir.appendingPathComponent("pooja_tokens_\(safeFilePart(pooja.place)).pdf")

        let title: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 20), .foregroundColor: UIColor.black]
        let section: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 14), .foregroundColor: UIColor.black]
        let body: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 11), .foregroundColor: UIColor(white: 40 / 255, alpha: 1)]
        let muted: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 10), .foregroundColor: UIColor(white: 95 / 255, alpha: 1)]

        let sorted = bookings.sorted { a, b in
            let ta = a.tokenNumber ?? Int.max, tb = b.tokenNumber ?? Int.max
            if ta != tb { return ta < tb }
            let ca = a.createdAt ?? "", cb = b.createdAt ?? ""
            if ca != cb { return ca < cb }
            return a.name < b.name
        }
        let confirmed = bookings.filter { $0.status == "confirmed" }.count
        let cancelled = bookings.filter { $0.status == "cancelled" }.count

        let renderer = UIGraphicsPDFRenderer(bounds: pageRect, format: {
            let format = UIGraphicsPDFRendererFormat()
            format.documentInfo = [kCGPDFContextTitle as String: "Puja Token Bookings - \(pooja.place)"]
            return format
        }())

        try renderer.writePDF(to: url) { context in
            var y = margin
            context.beginPage()

            func ensureSpace(_ required: CGFloat) {
                if y + required > pageHeight - margin { context.beginPage(); y = margin }
            }
            func draw(_ text: String, _ attributes: [NSAttributedString.Key: Any], extraBottom: CGFloat = 4) {
                let string = NSAttributedString(string: text, attributes: attributes)
                let height = ceil(string.boundingRect(with: CGSize(width: contentWidth, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil).height)
                ensureSpace(height + 4)
                string.draw(with: CGRect(x: margin, y: y, width: contentWidth, height: height), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
                y += height + 3 + extraBottom
            }
            func separator() {
                ensureSpace(12)
                let path = UIBezierPath()
                path.move(to: CGPoint(x: margin, y: y)); path.addLine(to: CGPoint(x: pageWidth - margin, y: y))
                path.lineWidth = 1
                UIColor(white: 210 / 255, alpha: 1).setStroke()
                path.stroke()
                y += 12
            }

            draw("Puja Token Bookings", title, extraBottom: 8)
            draw(pooja.place, section)
            draw("Scheduled: \(pooja.scheduledAt.fullIndiaDateTime)", body)
            draw("Status: \(pooja.status.poojaTitleLabel)", body)
            draw("Tokens: \(pooja.bookedTokens) booked / \(pooja.totalTokens) total (\(pooja.availableTokens) available)", body)
            draw("Requests: \(bookings.count) total, \(confirmed) confirmed, \(cancelled) cancelled", body, extraBottom: 8)
            separator()
            draw("Token List (\(bookings.count) requests)", section, extraBottom: 8)

            for (index, booking) in sorted.enumerated() {
                ensureSpace(112)
                draw("\(index + 1). \(booking.tokenNumber.map { "Token #\($0)" } ?? "Token N/A") - \(booking.name)", section)
                draw("Phone: \(booking.phone)", body)
                draw("Members: \(booking.memberCount)", body)
                draw("City: \(booking.city)", body)
                if let name = booking.user?.name, !name.trimmingCharacters(in: .whitespaces).isEmpty, name != booking.name {
                    draw("User: \(name)", body)
                }
                if let mobile = booking.user?.mobile, !mobile.trimmingCharacters(in: .whitespaces).isEmpty, mobile != booking.phone {
                    draw("User mobile: \(mobile)", body)
                }
                if let createdAt = booking.createdAt { draw("Booked: \(createdAt.fullIndiaDateTime)", muted) }
                if let cancelledAt = booking.cancelledAt { draw("Cancelled: \(cancelledAt.fullIndiaDateTime)", muted) }
                draw("Status: \(booking.status.poojaTitleLabel)", muted, extraBottom: 6)
                separator()
            }
        }
        return url
    }

    private static func safeFilePart(_ value: String) -> String {
        let replaced = value.replacingOccurrences(of: "[^A-Za-z0-9_-]+", with: "_", options: .regularExpression)
        let trimmed = replaced.trimmingCharacters(in: CharacterSet(charactersIn: "_"))
        return String((trimmed.isEmpty ? "pooja" : trimmed).prefix(40))
    }
}

/// Share sheet for the exported PDF.
struct PoojaActivityView: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
