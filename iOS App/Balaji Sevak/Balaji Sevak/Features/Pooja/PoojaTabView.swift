import Combine
import SwiftUI

enum PoojaRoute: Hashable {
    case detail(String)
    case book(String)
    case schedule(editID: String?)
    case myTokens
}

@MainActor
final class PoojaListViewModel: ObservableObject {
    @Published private(set) var poojas: [PoojaListItem] = []
    @Published private(set) var isLoading = false
    @Published private(set) var error: String?
    private var generation = 0

    func load(isAdmin: Bool, using appState: AppState) async {
        generation += 1
        let current = generation
        isLoading = true; error = nil
        do {
            let list: [PoojaListItem] = try await appState.authGet(isAdmin ? "admin/poojas" : "poojas")
            guard current == generation else { return }
            poojas = list
        } catch {
            guard current == generation else { return }
            if error is CancellationError || (error as? URLError)?.code == .cancelled { isLoading = false; return }
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    /// Clears stale rows when the role flips so admin data never shows to a customer.
    func reset() { poojas = []; error = nil }
}

/// Entry point for the Puja tab.
struct PoojaTabView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var viewModel = PoojaListViewModel()
    @State private var path: [PoojaRoute] = []

    private var isAdmin: Bool { appState.session.canManagePooja }

    var body: some View {
        NavigationStack(path: $path) {
            content
                .screenTitle("Puja")
                .toolbar {
                    if isAdmin {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button { path.append(.schedule(editID: nil)) } label: { Image(systemName: "plus") }
                                .accessibilityLabel("Schedule Puja")
                        }
                    }
                }
                .overlay(alignment: .bottomTrailing) {
                    if !isAdmin { myTokensButton }
                }
                .onAppear { reload() }
                .onChange(of: isAdmin) { _, _ in viewModel.reset(); reload() }
                .navigationDestination(for: PoojaRoute.self) { route in
                    switch route {
                    case .detail(let id):
                        PoojaDetailView(poojaID: id, onEdit: { path.append(.schedule(editID: id)) }, onBook: { book(id) })
                    case .book(let id):
                        PoojaBookingView(poojaID: id)
                    case .schedule(let editID):
                        SchedulePoojaView(editingID: editID)
                    case .myTokens:
                        MyBookingsView(typeFilter: "pooja")
                    }
                }
        }
    }

    @ViewBuilder private var content: some View {
        if viewModel.isLoading && viewModel.poojas.isEmpty {
            LoadingView()
        } else if let error = viewModel.error, viewModel.poojas.isEmpty {
            ErrorStateView(message: error) { reload() }
        } else if viewModel.poojas.isEmpty {
            ScrollView {
                ContentUnavailableView("No upcoming Puja found", systemImage: "figure.mind.and.body")
                    .padding(.top, 120)
            }
            .refreshable { await viewModel.load(isAdmin: isAdmin, using: appState) }
        } else {
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(viewModel.poojas) { pooja in
                        if isAdmin {
                            Button { path.append(.detail(pooja.id)) } label: { PoojaCard(pooja: pooja, isAdmin: true, onBook: nil) }
                                .buttonStyle(.plain)
                        } else {
                            PoojaCard(pooja: pooja, isAdmin: false) { book(pooja.id) }
                        }
                    }
                }
                .padding(16)
                .padding(.bottom, isAdmin ? 0 : 80)
            }
            .refreshable { await viewModel.load(isAdmin: isAdmin, using: appState) }
        }
    }

    private var myTokensButton: some View {
        Button {
            if appState.session.isLoggedIn { path.append(.myTokens) } else { appState.loginSheetPresented = true }
        } label: {
            Label("My Tokens", systemImage: "clock.arrow.circlepath")
                .font(.body.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .frame(height: 52)
                .background(AppTheme.brandOrange, in: Capsule())
                .shadow(color: .black.opacity(0.18), radius: 8, y: 3)
        }
        .buttonStyle(.plain)
        .padding(16)
    }

    private func reload() { Task { await viewModel.load(isAdmin: isAdmin, using: appState) } }

    private func book(_ id: String) {
        if appState.session.isLoggedIn { path.append(.book(id)) } else { appState.loginSheetPresented = true }
    }
}

private struct PoojaCard: View {
    let pooja: PoojaListItem
    let isAdmin: Bool
    let onBook: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(pooja.place).font(.headline).lineLimit(1)
            Label(pooja.scheduledAt.compactIndiaDateTime, systemImage: "calendar")
                .font(.subheadline).foregroundStyle(.secondary)
            HStack {
                Label {
                    Text("\(pooja.availableTokens) Available / \(pooja.totalTokens) tokens")
                        .fontWeight(.semibold)
                        .foregroundStyle(pooja.availableTokens > 0 ? Color.primary : Color.red)
                } icon: {
                    Image(systemName: "ticket").foregroundStyle(.secondary)
                }
                .font(.subheadline)
                Spacer()
                if isAdmin { PoojaStatusChip(text: pooja.status.poojaCapitalizedFirst) }
            }
            if !isAdmin {
                let window = pooja.window
                HStack {
                    Spacer()
                    Button(window.cardLabel) { onBook?() }
                        .buttonStyle(.bordered)
                        .tint(AppTheme.brandOrange)
                        .disabled(!window.canBook || onBook == nil)
                }
                .padding(.top, 4)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 16))
        .contentShape(RoundedRectangle(cornerRadius: 16))
    }
}

struct PoojaStatusChip: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(AppTheme.brandOrange)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(AppTheme.brandOrange.opacity(0.12), in: Capsule())
    }
}

/// Label/value row used on Puja detail and success cards.
struct PoojaDetailLine: View {
    let label: String
    let value: String
    var body: some View {
        HStack(alignment: .top) {
            Text(label).foregroundStyle(.secondary)
            Spacer(minLength: 12)
            Text(value).fontWeight(.semibold).multilineTextAlignment(.trailing)
        }
        .font(.subheadline)
    }
}
