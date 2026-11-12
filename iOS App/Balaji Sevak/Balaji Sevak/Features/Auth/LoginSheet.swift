import AuthenticationServices
import GoogleSignIn
import SwiftUI
import UIKit

struct LoginSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var isSigningIn = false
    @State private var error: String?

    /// Android `AppModalBottomSheet` background (#12161F).
    private static let sheetBackground = Color(red: 0x12 / 255, green: 0x16 / 255, blue: 0x1F / 255)

    var body: some View {
        VStack(spacing: 14) {
            ZStack(alignment: .topTrailing) {
                VStack(spacing: 10) {
                    Text("Log in to\nBalajiSevak")
                        .font(.system(.title, design: .serif).weight(.bold))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white)
                    Text("Jai Shri Ram\nJai Balaji Maharaj")
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white.opacity(0.65))
                }
                .frame(maxWidth: .infinity)
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.title3.weight(.medium)).foregroundStyle(.white).padding(6)
                }
                .accessibilityLabel("Close")
            }
            .padding(.top, 28)

            Rectangle().fill(.white.opacity(0.15)).frame(height: 0.75).padding(.vertical, 4)

            VStack(spacing: 12) {
                Button(action: signInWithGoogle) {
                    HStack(spacing: 10) {
                        Image(systemName: "person.crop.circle.fill")
                        Text("Continue with Google").fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .foregroundStyle(.white)
                    .background(AppTheme.brandOrange, in: Capsule())
                }
                .buttonStyle(.plain)

                // Required by App Store guidelines whenever Google sign-in is offered.
                SignInWithAppleButton(.signIn) { request in
                    request.requestedScopes = [.fullName, .email]
                } onCompletion: { result in
                    handleAppleResult(result)
                }
                .signInWithAppleButtonStyle(.white)
                .frame(height: 50)
                .clipShape(Capsule())
            }
            .disabled(isSigningIn)

            if isSigningIn { ProgressView("Signing in…").tint(.white).foregroundStyle(.white) }
            if let error {
                Text(error).font(.footnote).foregroundStyle(Color(red: 1, green: 0.55, blue: 0.5)).multilineTextAlignment(.center)
            }

            Spacer(minLength: 0)
            legalText
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Self.sheetBackground)
        .presentationBackground(Self.sheetBackground)
        .presentationDetents([.height(420)])
        .presentationDragIndicator(.visible)
        .preferredColorScheme(.dark)
    }

    /// "By proceeding, you agree to our Terms and Privacy Policy" (Android `strings.xml`).
    private var legalText: some View {
        Text(Self.legalAttributed)
            .font(.footnote)
            .foregroundStyle(.white.opacity(0.6))
            .tint(.white)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// Links underlined, like Android's legal line.
    private static let legalAttributed: AttributedString = {
        var text = (try? AttributedString(markdown: "By proceeding, you agree to our\n[Terms](https://policies.google.com/terms) and [Privacy Policy](https://policies.google.com/privacy)", options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)))
            ?? AttributedString("By proceeding, you agree to our Terms and Privacy Policy")
        for run in text.runs where run.link != nil { text[run.range].underlineStyle = .single }
        return text
    }()

    private func signInWithGoogle() {
        guard !AppConfiguration.googleClientID.isEmpty else {
            error = "Google Sign-In is not configured for this iOS app. Add the iOS OAuth client ID as GIDClientID in the target build settings."
            return
        }
        guard let presenter = activeViewController else { error = "Unable to start Google Sign-In. Please try again."; return }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: AppConfiguration.googleClientID)
        isSigningIn = true
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, signInError in
            Task { @MainActor in
                guard let result else {
                    isSigningIn = false
                    if let signInError { error = signInError.localizedDescription }
                    return
                }
                guard let idToken = result.user.idToken?.tokenString else {
                    isSigningIn = false; error = "Google did not return an identity token."
                    return
                }
                await completeLogin(path: "auth/google", request: GoogleSignInRequest(idToken: idToken))
            }
        }
    }

    private func handleAppleResult(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .failure(let error):
            if (error as? ASAuthorizationError)?.code != .canceled { self.error = error.localizedDescription }
        case .success(let authorization):
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let tokenData = credential.identityToken,
                  let token = String(data: tokenData, encoding: .utf8) else {
                error = "Apple did not return an identity token."
                return
            }
            isSigningIn = true
            Task { await completeLogin(path: "auth/apple", request: AppleSignInRequest(identityToken: token)) }
        }
    }

    private func completeLogin<Body: Encodable>(path: String, request: Body) async {
        defer { isSigningIn = false }
        do {
            let login: LoginData = try await appState.api.post(path, body: request)
            try appState.session.save(login)
            dismiss()
        } catch {
            self.error = error.localizedDescription
            appState.handleUnauthorized(error)
        }
    }

    private var activeViewController: UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
    }
}

