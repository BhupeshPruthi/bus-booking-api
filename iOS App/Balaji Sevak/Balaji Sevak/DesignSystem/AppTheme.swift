import SwiftUI

enum AppTheme {
    static let brandOrange = Color(red: 252 / 255, green: 121 / 255, blue: 13 / 255)
    static let eventPurple = Color(red: 107 / 255, green: 23 / 255, blue: 189 / 255)
    static let softSurface = Color(uiColor: .secondarySystemBackground)
}

struct SectionTitle: View {
    let title: String
    var body: some View { Text(title).font(.title3.weight(.bold)).frame(maxWidth: .infinity, alignment: .leading) }
}

struct LoadingView: View {
    var body: some View { ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity) }
}

struct ErrorStateView: View {
    let message: String
    let retry: () -> Void
    var body: some View {
        ContentUnavailableView {
            Label("Unable to continue", systemImage: "exclamationmark.triangle")
        } description: { Text(message) } actions: {
            Button("Try Again", action: retry).buttonStyle(.borderedProminent)
        }
    }
}

extension View {
    /// Android-style top bar: small, left-aligned title instead of the iOS large
    /// title. `navigationTitle` is still set so back buttons and accessibility
    /// keep the screen name.
    func screenTitle(_ title: String) -> some View {
        navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .modifier(LeadingTitleToolbar(title: title))
    }

    /// Screen without a visible title (Android Home has none).
    func hiddenScreenTitle(_ title: String) -> some View {
        navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .principal) { Color.clear.frame(width: 1, height: 1).accessibilityHidden(true) } }
    }
}

private struct LeadingTitleToolbar: ViewModifier {
    let title: String

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            // Keep the title as plain text, not inside a Liquid Glass capsule.
            content.toolbar {
                ToolbarItem(placement: .principal) { hiddenPrincipal }
                ToolbarItem(placement: .topBarLeading) { label }.sharedBackgroundVisibility(.hidden)
            }
        } else {
            content.toolbar {
                ToolbarItem(placement: .principal) { hiddenPrincipal }
                ToolbarItem(placement: .topBarLeading) { label }
            }
        }
    }

    private var label: some View { Text(title).font(.title3.weight(.semibold)).lineLimit(1).fixedSize() }
    private var hiddenPrincipal: some View { Color.clear.frame(width: 1, height: 1).accessibilityHidden(true) }
}
