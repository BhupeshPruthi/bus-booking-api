import SwiftUI
import WebKit

/// In-app browser for the live events page (Android `InAppWebViewScreen`).
struct LiveEventsWebView: View {
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var reloadToken = 0

    var body: some View {
        ZStack {
            WebView(url: AppConfiguration.liveEventsURL, reloadToken: reloadToken, isLoading: $isLoading, errorMessage: $errorMessage)
                .opacity(errorMessage == nil ? 1 : 0)
            if let errorMessage {
                ErrorStateView(message: errorMessage) {
                    self.errorMessage = nil
                    isLoading = true
                    reloadToken += 1
                }
            } else if isLoading {
                ProgressView().controlSize(.large)
            }
        }
        .navigationTitle("Live events")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct WebView: UIViewRepresentable {
    let url: URL
    let reloadToken: Int
    @Binding var isLoading: Bool
    @Binding var errorMessage: String?

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let view = WKWebView()
        view.navigationDelegate = context.coordinator
        view.allowsBackForwardNavigationGestures = true
        context.coordinator.lastReloadToken = reloadToken
        view.load(URLRequest(url: url))
        return view
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.parent = self
        guard context.coordinator.lastReloadToken != reloadToken else { return }
        context.coordinator.lastReloadToken = reloadToken
        if webView.url == nil { webView.load(URLRequest(url: url)) } else { webView.reload() }
    }

    static func dismantleUIView(_ webView: WKWebView, coordinator: Coordinator) {
        webView.stopLoading()
        webView.navigationDelegate = nil
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var parent: WebView
        var lastReloadToken = 0
        init(_ parent: WebView) { self.parent = parent }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            parent.isLoading = true
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.isLoading = false
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            handle(error)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            handle(error)
        }

        private func handle(_ error: Error) {
            parent.isLoading = false
            let nsError = error as NSError
            // A cancelled load (e.g. a new navigation replacing it) is not a failure.
            guard !(nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled) else { return }
            // "Frame load interrupted" (e.g. a link that downloads a file) is not a page failure.
            guard !(nsError.domain == "WebKitErrorDomain" && nsError.code == 102) else { return }
            parent.errorMessage = nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorNotConnectedToInternet
                ? "Check your internet connection and try again."
                : "Unable to load live events. Please try again."
        }
    }
}
