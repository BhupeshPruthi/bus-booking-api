import Foundation
import Combine

@MainActor final class EventsViewModel: ObservableObject {
    @Published private(set) var events: [EventListItem] = []
    @Published private(set) var isLoading = false
    @Published var error: String?

    func load(using appState: AppState) async {
        guard !isLoading else { return }
        isLoading = true; error = nil
        defer { isLoading = false }
        do {
            let loaded: [EventListItem] = try await appState.api.get("events")
            // Soonest first; unparseable dates go last (Android `sortedBy`).
            events = loaded.sorted {
                (AppFormatters.parseISO($0.eventDate) ?? .distantFuture) < (AppFormatters.parseISO($1.eventDate) ?? .distantFuture)
            }
        } catch is CancellationError {
            // View went away mid-request; keep the previous list.
        } catch {
            self.error = error.localizedDescription
        }
    }
}

@MainActor final class AddEventViewModel: ObservableObject {
    static let headerLimit = 200
    static let subHeaderLimit = 500

    @Published var header = "" { didSet { if header.count > Self.headerLimit { header = String(header.prefix(Self.headerLimit)) } } }
    @Published var subHeader = "" { didSet { if subHeader.count > Self.subHeaderLimit { subHeader = String(subHeader.prefix(Self.subHeaderLimit)) } } }
    /// No default: the admin must pick a date explicitly, as on Android.
    @Published var eventDate: Date?
    @Published var imageData: Data?
    @Published var isSubmitting = false
    @Published var error: String?
    @Published var createdEvent: EventListItem?

    func submit(using appState: AppState) async {
        let title = header.trimmingCharacters(in: .whitespacesAndNewlines)
        let subtitle = subHeader.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { error = "Please enter a header"; return }
        guard !subtitle.isEmpty else { error = "Please enter a sub header"; return }
        guard let eventDate else { error = "Please select an event date"; return }
        guard appState.session.isLoggedIn else { appState.loginSheetPresented = true; return }
        isSubmitting = true; error = nil
        defer { isSubmitting = false }
        // The picked calendar day at 23:59 on the organisation's India calendar,
        // serialised as UTC (Android `eventEndInstant`).
        let date = AppFormatters.isoString(AppFormatters.indiaDate(day: eventDate, hour: 23, minute: 59))
        do {
            if let imageData {
                createdEvent = try await appState.authMultipart(
                    "admin/events",
                    fields: ["header": title, "subHeader": subtitle, "eventDate": date],
                    image: imageData
                )
            } else {
                createdEvent = try await appState.authPost(
                    "admin/events",
                    body: CreateEventRequest(header: title, subHeader: subtitle, eventDate: date)
                )
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
}
