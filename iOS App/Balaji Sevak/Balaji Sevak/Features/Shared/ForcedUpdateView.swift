import SwiftUI
import UIKit

/// Full-screen "Update required" screen, the iOS stand-in for Android's IMMEDIATE
/// Play Store update. It has no way to close; the only action opens the App Store.
struct ForcedUpdateView: View {
    let storeURL: URL
    @Environment(\.openURL) private var openURL

    var body: some View {
        StandardBottomSheet(
            icon: "arrow.down.app.fill",
            title: "Update required",
            message: "A new version of Balaji Sevak is available. Please update from the App Store to keep using the app."
        ) {
            BottomSheetActionButton(title: "Update") { openURL(storeURL) }
        }
        .frame(maxWidth: 480)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemBackground))
    }
}

/// Shows ForcedUpdateView in its own window above the app, so it also covers any sheet
/// that is open when the app comes back to the foreground.
@MainActor enum ForcedUpdateWindow {
    private static var window: UIWindow?

    static func show(storeURL: URL) {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        guard let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first else { return }
        scene.windows.forEach { $0.endEditing(true) }
        let controller = UIHostingController(rootView: ForcedUpdateView(storeURL: storeURL))
        controller.view.accessibilityViewIsModal = true
        let window = Self.window ?? UIWindow(windowScene: scene)
        window.windowLevel = .alert + 1
        window.rootViewController = controller
        window.makeKeyAndVisible()
        Self.window = window
    }

    static func hide() {
        window?.isHidden = true
        window = nil
    }
}
