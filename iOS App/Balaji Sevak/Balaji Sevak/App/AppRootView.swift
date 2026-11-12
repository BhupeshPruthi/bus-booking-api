import SwiftUI

struct AppRootView: View {
    @EnvironmentObject private var appState: AppState
    /// Android keeps the splash on top for 900 ms after the first frame (MainActivity).
    @State private var showSplash = true
    /// Android checks for a forced Play Store update at launch and on every resume.
    @StateObject private var updateChecker = AppStoreUpdateChecker()
    @Environment(\.scenePhase) private var scenePhase
    var body: some View {
        tabs
        .sheet(isPresented: $appState.loginSheetPresented) { LoginSheet() }
        .overlay {
            if showSplash {
                SplashView().transition(.opacity)
            }
        }
        .task {
            try? await Task.sleep(for: .milliseconds(900))
            withAnimation(.easeOut(duration: 0.25)) { showSplash = false }
        }
        .onChange(of: scenePhase, initial: true) { _, phase in
            if phase == .active { updateChecker.check() }
        }
        .onChange(of: updateChecker.requiredUpdateURL) { _, storeURL in
            if let storeURL { ForcedUpdateWindow.show(storeURL: storeURL) } else { ForcedUpdateWindow.hide() }
        }
    }
}

extension AppRootView {
    @ViewBuilder private var tabs: some View {
        if #available(iOS 18.0, *) {
            TabView(selection: $appState.selectedTab) {
                Tab("Home", systemImage: "house", value: AppTab.home) { HomeView() }
                Tab("Bus", systemImage: "bus", value: AppTab.bus) { BusView() }
                Tab("Puja", systemImage: "figure.mind.and.body", value: AppTab.pooja) { PoojaTabView() }
                Tab("Stay", systemImage: "bed.double", value: AppTab.stay) { stayTab }
                // Help is shown as its own item, separate from the main tab bar.
                Tab("Help", systemImage: "headset", value: AppTab.help, role: Self.separateTabRole) { HelpView() }
            }
        } else {
            TabView(selection: $appState.selectedTab) {
                HomeView().tabItem { Label("Home", systemImage: "house") }.tag(AppTab.home)
                BusView().tabItem { Label("Bus", systemImage: "bus") }.tag(AppTab.bus)
                PoojaTabView().tabItem { Label("Puja", systemImage: "figure.mind.and.body") }.tag(AppTab.pooja)
                stayTab.tabItem { Label("Stay", systemImage: "bed.double") }.tag(AppTab.stay)
                HelpView().tabItem { Label("Help", systemImage: "headset") }.tag(AppTab.help)
            }
        }
    }

    /// iOS 27 has a dedicated `.prominent` role for a separated non-search tab;
    /// iOS 26 only separates tabs with the `.search` role.
    @available(iOS 18.0, *)
    private static var separateTabRole: TabRole {
        if #available(iOS 27.0, *) { .prominent } else { .search }
    }

    @ViewBuilder private var stayTab: some View {
        if appState.session.canManageStay { StayAdminView() } else { StayView() }
    }
}

struct FeaturePlaceholderView: View {
    let title: String
    let image: String
    var body: some View {
        NavigationStack {
            ContentUnavailableView(title, systemImage: image, description: Text("This section is being brought across from the Android app."))
                .navigationTitle(title)
        }
    }
}

/// Same image and letterbox colour as the launch screen (Info.plist `UILaunchScreen`),
/// shown at its natural size so the hand-off from launch screen is seamless.
struct SplashView: View {
    var body: some View {
        ZStack {
            Color("SplashBackground")
            Image("splash_screen")
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}
