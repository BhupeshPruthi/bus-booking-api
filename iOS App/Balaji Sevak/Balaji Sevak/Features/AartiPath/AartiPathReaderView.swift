import Combine
import PDFKit
import SwiftUI

/// Reads one Aarti & Path PDF: pages scroll top to bottom, A−/A+ (or pinch) zoom in steps
/// for people who find small letters hard to read, and Share sends the PDF to another app.
struct AartiPathReaderView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var model: AartiPathReaderViewModel
    @StateObject private var zoom = AartiPathZoomController()

    init(item: AartiPathItem) { _model = StateObject(wrappedValue: AartiPathReaderViewModel(item: item)) }

    var body: some View {
        content
            // Not `screenTitle`: its fixed-size label would push Share off screen for long titles.
            .navigationTitle(model.item.title)
            .navigationBarTitleDisplayMode(.inline)
            // The tab bar would cover the bottom of the page and the zoom buttons.
            .toolbar(.hidden, for: .tabBar)
            .toolbar {
                if let shareURL = model.shareURL {
                    ToolbarItem(placement: .topBarTrailing) {
                        ShareLink(item: shareURL, preview: SharePreview(model.item.title)) {
                            Image(systemName: "square.and.arrow.up")
                        }
                        .accessibilityLabel("Share PDF")
                    }
                }
            }
            .task { await model.load(using: appState) }
    }

    @ViewBuilder private var content: some View {
        switch model.phase {
        case .downloading(let progress):
            VStack(spacing: 14) {
                if let progress {
                    ProgressView(value: progress).tint(AppTheme.brandOrange).frame(maxWidth: 220)
                    Text("Downloading… \(Int((progress * 100).rounded()))%").monospacedDigit()
                } else {
                    ProgressView()
                    Text("Downloading…")
                }
                if let size = model.item.sizeText {
                    Text(size).font(.caption).foregroundStyle(.secondary)
                }
            }
            .font(.subheadline)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .offline:
            ContentUnavailableView {
                Label("You're offline", systemImage: "wifi.slash")
            } description: {
                Text("Connect to the internet to open this the first time.")
            } actions: {
                Button("Retry") { Task { await model.retry(using: appState) } }
                    .buttonStyle(.borderedProminent)
                    .tint(AppTheme.brandOrange)
            }
        case .failed(let message):
            ErrorStateView(message: message) { Task { await model.retry(using: appState) } }
        case .ready(let document):
            ZStack(alignment: .bottom) {
                PDFKitView(document: document, currentPage: $model.currentPage, zoom: zoom)
                    .ignoresSafeArea(edges: .bottom)
                // Chip on the left, buttons on the right, so they never overlap.
                HStack(alignment: .bottom) {
                    pageChip(pageCount: document.pageCount)
                    Spacer(minLength: 12)
                    zoomButtons
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 12)
            }
        }
    }

    private func pageChip(pageCount: Int) -> some View {
        let text = zoom.flash ?? "Page \(model.currentPage) of \(pageCount)"
        return Text(text)
            .font(.footnote.weight(.medium))
            .monospacedDigit()
            .foregroundStyle(.white)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(Color.black.opacity(0.7), in: Capsule())
            .animation(.easeOut(duration: 0.15), value: text)
            .accessibilityLabel(text)
    }

    private var zoomButtons: some View {
        HStack(spacing: 0) {
            Button { zoom.zoomOut() } label: {
                Text("A−")
                    .font(.system(size: 17, weight: .semibold))
                    .frame(width: 52, height: 48)
                    .contentShape(Rectangle())
            }
            .disabled(!zoom.canZoomOut)
            .accessibilityLabel("Smaller text")
            .accessibilityValue("\(zoom.percent) percent")

            Rectangle().fill(Color.white.opacity(0.3)).frame(width: 1, height: 24)

            Button { zoom.zoomIn() } label: {
                Text("A+")
                    .font(.system(size: 22, weight: .semibold))
                    .frame(width: 52, height: 48)
                    .contentShape(Rectangle())
            }
            .disabled(!zoom.canZoomIn)
            .accessibilityLabel("Larger text")
            .accessibilityValue("\(zoom.percent) percent")
        }
        .buttonStyle(ZoomButtonStyle())
        .background(Color.black.opacity(0.7), in: Capsule())
        .shadow(color: .black.opacity(0.2), radius: 6, y: 2)
    }
}

private struct ZoomButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(.white.opacity(isEnabled ? (configuration.isPressed ? 0.6 : 1) : 0.35))
    }
}

/// Zoom in steps relative to fit-width. The level is remembered for every PDF.
final class AartiPathZoomController: NSObject, ObservableObject {
    static let steps: [CGFloat] = [1, 1.25, 1.5, 1.75, 2, 2.5, 3]
    private static let defaultsKey = "aartiPathReaderZoom"
    private static let tolerance: CGFloat = 0.01

    /// Current zoom relative to fit-width (1 = 100%).
    @Published private(set) var level: CGFloat
    /// The level shown briefly in the page chip after a change, e.g. "150%".
    @Published private(set) var flash: String?

    private weak var view: ZoomablePDFView?
    /// Fit-width scale for the current view width.
    private var fitScale: CGFloat = 0
    /// True while the app sets the scale itself, so only the reader's pinches count as a choice.
    private var isApplying = false
    private var flashTask: Task<Void, Never>?

    override init() {
        let saved = CGFloat(UserDefaults.standard.double(forKey: Self.defaultsKey))
        level = Self.clamp(saved == 0 ? 1 : saved)
        super.init()
    }

    var percent: Int { Int((level * 100).rounded()) }
    var canZoomOut: Bool { level > Self.steps[0] + Self.tolerance }
    var canZoomIn: Bool { level < Self.steps[Self.steps.count - 1] - Self.tolerance }

    func zoomIn() {
        guard let next = Self.steps.first(where: { $0 > level + Self.tolerance }) else { return }
        choose(next)
    }

    func zoomOut() {
        guard let next = Self.steps.last(where: { $0 < level - Self.tolerance }) else { return }
        choose(next)
    }

    func attach(_ view: ZoomablePDFView) {
        self.view = view
        view.zoomController = self
        NotificationCenter.default.addObserver(self, selector: #selector(scaleChanged(_:)), name: .PDFViewScaleChanged, object: view)
    }

    func detach() {
        NotificationCenter.default.removeObserver(self)
        flashTask?.cancel()
    }

    /// The point of the page at the centre of what's on screen, to keep it in place.
    func anchor(in view: PDFView, center: CGPoint) -> (page: PDFPage, point: CGPoint)? {
        guard let page = view.page(for: center, nearest: true) else { return nil }
        return (page, view.convert(center, to: page))
    }

    /// Width changed: the first layout (fit-width is known now) or a rotation.
    func widthChanged(isFirstLayout: Bool, anchor: (page: PDFPage, point: CGPoint)?) {
        // Until fit-width is recomputed, scale changes come from the resize, not the reader.
        isApplying = true
        // After this layout pass, so PDFKit has laid out the pages for the new width.
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            defer { self.isApplying = false }
            guard let view = self.view, view.bounds.width > 0 else { return }
            if isFirstLayout { view.autoScales = true }
            let fit = view.scaleFactorForSizeToFit
            guard fit > 0 else { return }
            self.fitScale = fit
            view.autoScales = false
            view.minScaleFactor = fit * Self.steps[0]
            view.maxScaleFactor = fit * Self.steps[Self.steps.count - 1]
            view.scaleFactor = fit * self.level
            view.layoutIfNeeded()
            if isFirstLayout {
                self.showTopOfFirstPage(in: view)
            } else if let anchor {
                self.keep(anchor, atCenterOf: view)
            }
        }
    }

    private func choose(_ newLevel: CGFloat) {
        guard let view, fitScale > 0 else { return }
        let center = CGPoint(x: view.bounds.midX, y: view.bounds.midY)
        let anchor = anchor(in: view, center: center)
        isApplying = true
        view.scaleFactor = fitScale * newLevel
        view.layoutIfNeeded()
        if let anchor { keep(anchor, atCenterOf: view) }
        isApplying = false
        level = newLevel
        save()
        showFlash()
    }

    @objc private func scaleChanged(_ notification: Notification) {
        // A pinch: take whatever level it settled on, so A−/A+ step from there.
        guard !isApplying, fitScale > 0, let view else { return }
        let newLevel = Self.clamp(view.scaleFactor / fitScale)
        guard abs(newLevel - level) > 0.001 else { return }
        level = newLevel
        save()
        showFlash()
    }

    /// Scrolls so the anchored page point is back at the centre of the screen.
    private func keep(_ anchor: (page: PDFPage, point: CGPoint), atCenterOf view: PDFView) {
        guard let scrollView = Self.scrollView(in: view) else {
            view.go(to: PDFDestination(page: anchor.page, at: anchor.point))
            return
        }
        let center = CGPoint(x: view.bounds.midX, y: view.bounds.midY)
        let now = view.convert(anchor.point, from: anchor.page)
        var offset = scrollView.contentOffset
        offset.x += now.x - center.x
        offset.y += now.y - center.y
        scrollView.setContentOffset(Self.clamp(offset, in: scrollView), animated: false)
    }

    /// Opening a PDF: start at the top of page 1, centred across.
    private func showTopOfFirstPage(in view: PDFView) {
        guard let scrollView = Self.scrollView(in: view) else {
            if let first = view.document?.page(at: 0) { view.go(to: first) }
            return
        }
        let inset = scrollView.adjustedContentInset
        let x = (scrollView.contentSize.width - scrollView.bounds.width) / 2
        let offset = CGPoint(x: x, y: -inset.top)
        scrollView.setContentOffset(Self.clamp(offset, in: scrollView), animated: false)
    }

    private func save() { UserDefaults.standard.set(Double(level), forKey: Self.defaultsKey) }

    private func showFlash() {
        flash = "\(percent)%"
        flashTask?.cancel()
        flashTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(1.5))
            guard !Task.isCancelled else { return }
            self?.flash = nil
        }
    }

    private static func clamp(_ level: CGFloat) -> CGFloat { min(max(level, steps[0]), steps[steps.count - 1]) }

    private static func clamp(_ offset: CGPoint, in scrollView: UIScrollView) -> CGPoint {
        let inset = scrollView.adjustedContentInset
        let maxX = max(scrollView.contentSize.width - scrollView.bounds.width + inset.right, -inset.left)
        let maxY = max(scrollView.contentSize.height - scrollView.bounds.height + inset.bottom, -inset.top)
        return CGPoint(x: min(max(offset.x, -inset.left), maxX), y: min(max(offset.y, -inset.top), maxY))
    }

    /// PDFView scrolls with an internal UIScrollView.
    private static func scrollView(in view: UIView) -> UIScrollView? {
        for subview in view.subviews {
            if let scrollView = subview as? UIScrollView { return scrollView }
        }
        for subview in view.subviews {
            if let scrollView = scrollView(in: subview) { return scrollView }
        }
        return nil
    }
}

/// Tells the zoom controller when its width changes (first layout, rotation).
final class ZoomablePDFView: PDFView {
    weak var zoomController: AartiPathZoomController?
    private var lastSize: CGSize = .zero

    override func layoutSubviews() {
        let oldSize = lastSize
        let widthChanged = bounds.width > 0 && bounds.width != oldSize.width
        // Taken before PDFKit re-lays out the pages, using the old visible centre.
        let anchor = widthChanged && oldSize.width > 0
            ? zoomController?.anchor(in: self, center: CGPoint(x: oldSize.width / 2, y: oldSize.height / 2))
            : nil
        super.layoutSubviews()
        lastSize = bounds.size
        if widthChanged, document != nil {
            zoomController?.widthChanged(isFirstLayout: oldSize.width == 0, anchor: anchor)
        }
    }
}

/// PDFKit's view: continuous vertical scrolling, starting at fit-width, pinch to zoom.
private struct PDFKitView: UIViewRepresentable {
    let document: PDFDocument
    @Binding var currentPage: Int
    let zoom: AartiPathZoomController

    func makeCoordinator() -> Coordinator { Coordinator(currentPage: $currentPage, zoom: zoom) }

    func makeUIView(context: Context) -> ZoomablePDFView {
        let view = ZoomablePDFView()
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.autoScales = true
        view.pageShadowsEnabled = true
        view.backgroundColor = .secondarySystemBackground
        zoom.attach(view)
        view.document = document
        context.coordinator.observe(view)
        return view
    }

    func updateUIView(_ view: ZoomablePDFView, context: Context) {
        context.coordinator.currentPage = $currentPage
        if view.document !== document { view.document = document }
    }

    static func dismantleUIView(_ view: ZoomablePDFView, coordinator: Coordinator) {
        NotificationCenter.default.removeObserver(coordinator)
        coordinator.zoom.detach()
    }

    final class Coordinator: NSObject {
        var currentPage: Binding<Int>
        let zoom: AartiPathZoomController
        init(currentPage: Binding<Int>, zoom: AartiPathZoomController) {
            self.currentPage = currentPage
            self.zoom = zoom
        }

        func observe(_ view: PDFView) {
            NotificationCenter.default.addObserver(self, selector: #selector(pageChanged(_:)), name: .PDFViewPageChanged, object: view)
        }

        @objc private func pageChanged(_ notification: Notification) {
            guard let view = notification.object as? PDFView,
                  let page = view.currentPage,
                  let index = view.document?.index(for: page) else { return }
            let number = index + 1
            if currentPage.wrappedValue != number { currentPage.wrappedValue = number }
        }
    }
}
