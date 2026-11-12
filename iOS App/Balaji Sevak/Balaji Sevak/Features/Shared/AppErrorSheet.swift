import SwiftUI

enum BottomSheetButtonStyle { case primary, secondary, destructive }

/// Shared action button used in all compact confirmation and status sheets.
struct BottomSheetActionButton: View {
    let title: String
    var style: BottomSheetButtonStyle = .primary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 50)
        }
        .buttonStyle(.plain)
        .foregroundStyle(foreground)
        .background(background, in: RoundedRectangle(cornerRadius: 14))
    }

    private var foreground: Color { style == .primary ? .white : (style == .destructive ? .red : AppTheme.brandOrange) }
    private var background: Color {
        switch style {
        case .primary: AppTheme.brandOrange
        case .secondary: AppTheme.brandOrange.opacity(0.10)
        case .destructive: Color.red.opacity(0.10)
        }
    }
}

/// Standard compact bottom-sheet composition for confirmations and messages.
struct StandardBottomSheet<Actions: View>: View {
    let icon: String
    let iconColor: Color
    let title: String
    let message: String
    @ViewBuilder let actions: Actions

    init(icon: String, iconColor: Color = AppTheme.brandOrange, title: String, message: String, @ViewBuilder actions: () -> Actions) {
        self.icon = icon
        self.iconColor = iconColor
        self.title = title
        self.message = message
        self.actions = actions()
    }

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 34, weight: .medium))
                .foregroundStyle(iconColor)
                .frame(width: 64, height: 64)
                .background(iconColor.opacity(0.12), in: Circle())
            Text(title).font(.title3.weight(.bold)).multilineTextAlignment(.center)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            actions.padding(.top, 6)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
    }
}

/// Reusable bottom sheet for API and validation errors throughout the app.
struct AppErrorSheet: View {
    let title: String
    let message: String
    let actionTitle: String
    let dismiss: () -> Void

    init(
        title: String = "Unable to continue",
        message: String,
        actionTitle: String = "OK",
        dismiss: @escaping () -> Void
    ) {
        self.title = title
        self.message = message
        self.actionTitle = actionTitle
        self.dismiss = dismiss
    }

    var body: some View {
        StandardBottomSheet(icon: "exclamationmark.triangle.fill", iconColor: .red, title: title, message: message) {
            BottomSheetActionButton(title: actionTitle, action: dismiss)
        }
    }
}
