import SwiftUI
import GoogleSignIn

@main
struct BalajiSevakApp: App {
    @StateObject private var appState = AppState.shared

    var body: some Scene {
        WindowGroup {
            AppRootView()
                .environmentObject(appState)
                .tint(AppTheme.brandOrange)
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
        }
    }
}
