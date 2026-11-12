import GoogleSignIn
import SwiftUI

struct ProfileView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var showLogoutConfirmation = false
    @State private var showDeleteConfirmation = false
    @State private var isDeletingAccount = false
    @State private var deleteError: String?
    @StateObject private var chalisa = ChalisaProfileViewModel()
    var onOpenChalisa: () -> Void = {}
    var body: some View {
        List {
            Section("Account") {
                if let user = appState.session.user { LabeledContent("Name", value: user.name ?? "User"); if let email = user.email { LabeledContent("Email", value: email) }; if let mobile = user.mobile { LabeledContent("Phone", value: mobile) } }
                else { Button("Log in") { appState.loginSheetPresented = true } }
            }
            if let stats = chalisa.stats, stats.participant != nil || stats.lifetime > 0 {
                Section {
                    Button(action: onOpenChalisa) { ChalisaProfileSection(stats: stats) }
                        .buttonStyle(.plain)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }
            }
            Section("App") { LabeledContent("Version", value: AppConfiguration.appVersion) }
            if appState.session.isLoggedIn {
                Section {
                    Button("Log out", role: .destructive) { showLogoutConfirmation = true }
                }
                Section {
                    Button("Delete account", role: .destructive) { showDeleteConfirmation = true }
                } footer: {
                    Text("Permanently deletes your account and your Hanuman Chalisa counts.")
                }
            }
        }
        .navigationTitle("Profile")
        .task { await chalisa.refresh(using: appState) }
        .sheet(isPresented: $showLogoutConfirmation) {
            StandardBottomSheet(
                icon: "rectangle.portrait.and.arrow.right",
                iconColor: .red,
                title: "Log out?",
                message: "You will need to sign in again to view or make bookings."
            ) {
                HStack(spacing: 12) {
                    BottomSheetActionButton(title: "Yes, log out", style: .destructive) {
                        // Pop back first; AppState.logout also switches to the Home tab.
                        showLogoutConfirmation = false
                        dismiss()
                        GIDSignIn.sharedInstance.signOut()
                        Task { await appState.logout() }
                    }
                    BottomSheetActionButton(title: "No, wait") { showLogoutConfirmation = false }
                }
            }
            .presentationDetents([.height(285)])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showDeleteConfirmation) {
            StandardBottomSheet(
                icon: "person.crop.circle.badge.xmark",
                iconColor: .red,
                title: "Delete account?",
                message: "This permanently deletes your account and your Hanuman Chalisa counts. It can't be undone. Records of past bookings may be kept."
            ) {
                HStack(spacing: 12) {
                    if isDeletingAccount {
                        ProgressView().frame(maxWidth: .infinity).frame(height: 50)
                    } else {
                        BottomSheetActionButton(title: "Yes, delete", style: .destructive) {
                            Task { await deleteAccount() }
                        }
                    }
                    BottomSheetActionButton(title: "No, keep it") { showDeleteConfirmation = false }
                        .disabled(isDeletingAccount)
                }
            }
            .presentationDetents([.height(330)])
            .presentationDragIndicator(.visible)
            .interactiveDismissDisabled(isDeletingAccount)
        }
        .sheet(isPresented: Binding(get: { deleteError != nil }, set: { if !$0 { deleteError = nil } })) {
            AppErrorSheet(title: "Account not deleted", message: deleteError ?? "") { deleteError = nil }
                .presentationDetents([.height(280)])
                .presentationDragIndicator(.visible)
        }
    }

    private func deleteAccount() async {
        guard !isDeletingAccount else { return }
        isDeletingAccount = true
        defer { isDeletingAccount = false }
        do {
            // Signs out and switches to the Home tab; Home pops this screen once signed out.
            try await appState.deleteAccount()
            showDeleteConfirmation = false
            GIDSignIn.sharedInstance.signOut()
        } catch {
            showDeleteConfirmation = false
            // An expired sign-in has already signed out and shown Login.
            guard appState.session.isLoggedIn else { return }
            // Let the confirmation sheet finish dismissing before showing the error sheet.
            try? await Task.sleep(for: .milliseconds(400))
            deleteError = error.localizedDescription
        }
    }
}
