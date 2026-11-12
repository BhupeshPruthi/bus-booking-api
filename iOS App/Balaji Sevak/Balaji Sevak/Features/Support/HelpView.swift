import Combine
import SwiftUI

// MARK: - Models (Android `SupportDto.kt`)

struct CreateFeedbackRequest: Encodable { let message: String }

struct FeedbackSubmission: Decodable { let id: String; let message: String; let createdAt: String }

struct FeedbackSubmitter: Decodable, Equatable {
    let id: String
    let name: String?
    let email: String?
    let phone: String?
}

struct FeedbackItem: Decodable, Identifiable, Equatable {
    let id: String
    let message: String
    let createdAt: String
    let submittedBy: FeedbackSubmitter
}

struct FeedbackPage: Decodable {
    let items: [FeedbackItem]
    let page: Int
    let limit: Int
    let total: Int
}

// MARK: - View model

@MainActor
final class SupportViewModel: ObservableObject {
    static let maxFeedbackLength = 2000

    @Published private(set) var contacts: [HelpContact] = []
    @Published private(set) var isLoadingHelp = false
    @Published private(set) var helpError: String?

    @Published var message = "" {
        didSet {
            if message.count > Self.maxFeedbackLength { message = String(message.prefix(Self.maxFeedbackLength)) }
            if message != oldValue { submissionComplete = false; submissionError = nil }
        }
    }
    @Published private(set) var isSubmitting = false
    @Published var submissionComplete = false
    @Published private(set) var submissionError: String?

    @Published private(set) var feedback: [FeedbackItem] = []
    @Published private(set) var isLoadingFeedback = false
    @Published private(set) var feedbackError: String?
    @Published private(set) var hasLoadedFeedback = false
    private var page = 1
    private var total = 0

    var canLoadMoreFeedback: Bool { feedback.count < total }

    func loadHelpContacts(using appState: AppState) async {
        guard !isLoadingHelp else { return }
        isLoadingHelp = true
        helpError = nil
        defer { isLoadingHelp = false }
        do {
            contacts = try await appState.api.get("help-contacts")
        } catch {
            helpError = error.localizedDescription
        }
    }

    func submitFeedback(using appState: AppState) async {
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { submissionError = "Please enter your feedback."; return }
        isSubmitting = true
        submissionError = nil
        defer { isSubmitting = false }
        do {
            let _: FeedbackSubmission = try await appState.authPost("feedback", body: CreateFeedbackRequest(message: trimmed))
            message = ""
            submissionComplete = true
        } catch {
            submissionError = error.localizedDescription
        }
    }

    func loadFeedback(using appState: AppState, reset: Bool = true) async {
        guard !isLoadingFeedback else { return }
        let requestedPage = reset ? 1 : page + 1
        isLoadingFeedback = true
        feedbackError = nil
        defer { isLoadingFeedback = false }
        do {
            let result: FeedbackPage = try await appState.authGet("admin/feedback?page=\(requestedPage)&limit=20")
            feedback = reset ? result.items : feedback + result.items
            page = result.page
            total = result.total
            hasLoadedFeedback = true
        } catch {
            feedbackError = error.localizedDescription
        }
    }

    /// Clears admin-only data when the signed-in user changes.
    func resetFeedback() {
        feedback = []; page = 1; total = 0; hasLoadedFeedback = false; feedbackError = nil
    }
}

// MARK: - Support tab (Help | Feedback)

enum SupportSection: String, CaseIterable, Identifiable {
    case help = "Help", feedback = "Feedback"
    var id: String { rawValue }
}

/// Tab root for Support, matching Android `SupportTab`.
struct HelpView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var viewModel = SupportViewModel()
    @State private var section: SupportSection = .help
    #if DEBUG
    @State private var isNetworkConsolePresented = false
    #endif

    private var isAdmin: Bool { appState.session.isAnyAdmin }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                SupportTabRow(selection: $section)

                switch section {
                case .help: HelpContentView(viewModel: viewModel)
                case .feedback:
                    if isAdmin { AdminFeedbackView(viewModel: viewModel) }
                    else { UserFeedbackView(viewModel: viewModel) }
                }
            }
            .screenTitle("Support")
            #if DEBUG
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { isNetworkConsolePresented = true } label: { Image(systemName: "network") }
                        .accessibilityLabel("Open network inspector")
                }
            }
            .sheet(isPresented: $isNetworkConsolePresented) { NetworkConsoleView() }
            #endif
            .task { await viewModel.loadHelpContacts(using: appState) }
            .task(id: FeedbackTrigger(section: section, isAdmin: isAdmin, userID: appState.session.user?.id)) {
                if section == .feedback && isAdmin {
                    if !viewModel.hasLoadedFeedback { await viewModel.loadFeedback(using: appState) }
                } else if section != .feedback {
                    viewModel.submissionComplete = false
                }
            }
            .onChange(of: appState.session.user?.id) { _, _ in viewModel.resetFeedback() }
            .onDisappear { viewModel.submissionComplete = false }
        }
    }

    private struct FeedbackTrigger: Equatable { let section: SupportSection; let isAdmin: Bool; let userID: String? }
}

// MARK: - Help

private struct HelpContentView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.openURL) private var openURL
    @ObservedObject var viewModel: SupportViewModel

    var body: some View {
        Group {
            if viewModel.isLoadingHelp && viewModel.contacts.isEmpty {
                LoadingView()
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("How can we help?")
                                .font(.title2.weight(.semibold))
                            Text("Choose a service below to call the support contact.")
                                .font(.subheadline)
                                .foregroundStyle(.primary)
                        }

                        ForEach(groupedContacts) { group in
                            HelpContactGroup(group: group, onCall: call)
                        }

                        if let error = viewModel.helpError {
                            ErrorStateView(message: error) {
                                Task { await viewModel.loadHelpContacts(using: appState) }
                            }
                            .frame(minHeight: 180)
                        } else if viewModel.contacts.isEmpty {
                            ContentUnavailableView("No help contacts available", systemImage: "phone.slash", description: Text("Please try again shortly."))
                                .frame(maxWidth: .infinity, minHeight: 260)
                        }
                    }
                    .padding()
                }
                .refreshable { await viewModel.loadHelpContacts(using: appState) }
            }
        }
    }

    private var groupedContacts: [HelpContactGroupModel] {
        var groups: [HelpContactGroupModel] = []
        for contact in viewModel.contacts {
            if let index = groups.firstIndex(where: { $0.code == contact.code }) {
                groups[index].contacts.append(contact)
            } else {
                groups.append(HelpContactGroupModel(code: contact.code, title: contact.title, contacts: [contact]))
            }
        }
        return groups
    }

    private func call(_ contact: HelpContact) {
        if let url = SupportLinks.phoneURL(contact.phone) { openURL(url) }
    }
}

enum SupportLinks {
    static func phoneURL(_ phone: String) -> URL? {
        let allowed = CharacterSet.decimalDigits.union(CharacterSet(charactersIn: "+"))
        let digits = String(phone.unicodeScalars.filter { allowed.contains($0) })
        guard !digits.isEmpty else { return nil }
        return URL(string: "tel:\(digits)")
    }

    static func emailURL(_ email: String) -> URL? {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlUserAllowed) else { return nil }
        return URL(string: "mailto:\(encoded)")
    }
}

private struct HelpContactGroupModel: Identifiable {
    let code: String
    let title: String
    var contacts: [HelpContact]
    var id: String { code }
}

private struct HelpContactGroup: View {
    let group: HelpContactGroupModel
    let onCall: (HelpContact) -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 14) {
                Image(systemName: iconName)
                    .font(.title3)
                    .foregroundStyle(AppTheme.brandOrange)
                    .frame(width: 28)
                Text(group.title)
                    .font(.headline)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)

            // Android draws thin orange separators between rows.
            Rectangle().fill(AppTheme.brandOrange.opacity(0.45)).frame(height: 0.75)

            ForEach(Array(group.contacts.enumerated()), id: \.element.id) { index, contact in
                Button { onCall(contact) } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(contact.contactName)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(.primary)
                            Text(contact.phone)
                                .font(.subheadline)
                                .foregroundStyle(AppTheme.brandOrange)
                        }
                        Spacer()
                        Image(systemName: "phone.fill")
                            .foregroundStyle(.primary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 17)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Call \(contact.contactName), \(contact.phone)")

                if index < group.contacts.count - 1 { Rectangle().fill(AppTheme.brandOrange.opacity(0.45)).frame(height: 0.75).padding(.leading, 16) }
            }
        }
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
    }

    private var iconName: String {
        switch group.code {
        case "bus": return "bus.fill"
        case "pooja": return "ticket.fill"
        case "dharamshala", "stay": return "bed.double.fill"
        case "emergency": return "cross.case.fill"
        default: return "phone.fill"
        }
    }
}

// MARK: - Feedback (users)

private struct UserFeedbackView: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: SupportViewModel
    @FocusState private var isEditorFocused: Bool

    private var canSubmit: Bool {
        !viewModel.isSubmitting && !viewModel.message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Share your feedback")
                        .font(.title2.weight(.semibold))
                    Text("Tell us what we can improve. Admin may contact you using your account details.")
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                }

                VStack(alignment: .leading, spacing: 12) {
                    if appState.session.isLoggedIn {
                        Text("Your feedback").font(.subheadline.weight(.medium))
                        ZStack(alignment: .topLeading) {
                            TextEditor(text: $viewModel.message)
                                .focused($isEditorFocused)
                                .scrollContentBackground(.hidden)
                                .frame(minHeight: 130, maxHeight: 240)
                                .padding(8)
                                .disabled(viewModel.isSubmitting)
                            if viewModel.message.isEmpty {
                                Text("Write your feedback here")
                                    .foregroundStyle(.tertiary)
                                    .padding(.horizontal, 13)
                                    .padding(.vertical, 16)
                                    .allowsHitTesting(false)
                            }
                        }
                        .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.secondary.opacity(0.25)))

                        Text("\(viewModel.message.count)/\(SupportViewModel.maxFeedbackLength)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .trailing)

                        Button {
                            isEditorFocused = false
                            Task { await viewModel.submitFeedback(using: appState) }
                        } label: {
                            Group {
                                if viewModel.isSubmitting { ProgressView().tint(.white) }
                                else { Text("Send feedback").font(.body.weight(.semibold)) }
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(.white)
                        .background(AppTheme.brandOrange.opacity(canSubmit || viewModel.isSubmitting ? 1 : 0.4), in: RoundedRectangle(cornerRadius: 14))
                        .disabled(!canSubmit)
                    } else {
                        Text("Please sign in so your feedback can include your contact details.")
                            .font(.subheadline)
                        BottomSheetActionButton(title: "Sign in to continue") { appState.loginSheetPresented = true }
                    }
                }
                .padding(16)
                .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))

                if viewModel.submissionComplete {
                    Label("Thank you. Your feedback has been sent to Admin.", systemImage: "checkmark.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.brandOrange)
                }
                if let error = viewModel.submissionError {
                    Text(error).font(.subheadline).foregroundStyle(.red)
                }
            }
            .padding()
        }
        .scrollDismissesKeyboard(.interactively)
    }
}

// MARK: - Feedback (admins)

private struct AdminFeedbackView: View {
    @EnvironmentObject private var appState: AppState
    @ObservedObject var viewModel: SupportViewModel

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("User feedback")
                        .font(.title2.weight(.semibold))
                    Text("Contact the user directly by phone or email if a follow-up is needed.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                if !viewModel.feedback.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(Array(viewModel.feedback.enumerated()), id: \.element.id) { index, item in
                            FeedbackRow(feedback: item)
                            if index < viewModel.feedback.count - 1 { Divider() }
                        }
                    }
                    .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
                } else if !viewModel.isLoadingFeedback && viewModel.feedbackError == nil {
                    ContentUnavailableView("No feedback has been submitted yet.", systemImage: "text.bubble")
                        .frame(maxWidth: .infinity, minHeight: 220)
                }

                if let error = viewModel.feedbackError {
                    ErrorStateView(message: error) { Task { await viewModel.loadFeedback(using: appState) } }
                        .frame(minHeight: 180)
                }

                if viewModel.isLoadingFeedback {
                    ProgressView().frame(maxWidth: .infinity).padding()
                } else if viewModel.canLoadMoreFeedback {
                    BottomSheetActionButton(title: "Load more", style: .secondary) {
                        Task { await viewModel.loadFeedback(using: appState, reset: false) }
                    }
                }
            }
            .padding()
        }
        .refreshable { await viewModel.loadFeedback(using: appState) }
    }
}

private struct FeedbackRow: View {
    @Environment(\.openURL) private var openURL
    let feedback: FeedbackItem

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(feedback.message).font(.body)
            Text(formattedTime)
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(.top, 8)
            Text(submitterName)
                .font(.subheadline.weight(.semibold))
                .padding(.top, 12)
            if let phone = feedback.submittedBy.phone?.nonBlank, let url = SupportLinks.phoneURL(phone) {
                contactLine(phone, icon: "phone.fill") { openURL(url) }
            }
            if let email = feedback.submittedBy.email?.nonBlank, let url = SupportLinks.emailURL(email) {
                contactLine(email, icon: "envelope.fill") { openURL(url) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
    }

    private var submitterName: String { feedback.submittedBy.name?.nonBlank ?? "User" }

    /// "dd MMM yy, h:mm a" in device time, as Android.
    private var formattedTime: String {
        guard let date = AppFormatters.parseISO(feedback.createdAt) else { return feedback.createdAt }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_IN")
        formatter.dateFormat = "dd MMM yy, h:mm a"
        return formatter.string(from: date)
    }

    private func contactLine(_ text: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.footnote)
                Text(text).font(.subheadline)
            }
            .foregroundStyle(AppTheme.brandOrange)
            .padding(.top, 7)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private extension String {
    var nonBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

/// Android Material `TabRow`: orange labels with an underline under the selected tab.
private struct SupportTabRow: View {
    @Binding var selection: SupportSection
    @Namespace private var indicator

    var body: some View {
        HStack(spacing: 0) {
            ForEach(SupportSection.allCases) { tab in
                Button { withAnimation(.easeInOut(duration: 0.2)) { selection = tab } } label: {
                    VStack(spacing: 10) {
                        Text(tab.rawValue).font(.subheadline.weight(.semibold)).foregroundStyle(AppTheme.brandOrange)
                        ZStack {
                            Color.clear.frame(height: 3)
                            if selection == tab {
                                Rectangle().fill(AppTheme.brandOrange).frame(height: 3)
                                    .matchedGeometryEffect(id: "indicator", in: indicator)
                            }
                        }
                    }
                    .padding(.top, 10)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selection == tab ? .isSelected : [])
            }
        }
        .overlay(alignment: .bottom) { Rectangle().fill(AppTheme.brandOrange.opacity(0.35)).frame(height: 0.75) }
    }
}
