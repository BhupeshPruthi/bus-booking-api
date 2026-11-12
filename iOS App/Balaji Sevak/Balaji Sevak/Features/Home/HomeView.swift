import SwiftUI

/// Screens pushed from Home. Bookings, Add Event and Profile require login; Hanuman Chalisa and Aarti & Path don't.
enum HomeRoute: Hashable {
    case myBookings, addEvent, profile, liveEvents
    case chalisa(ChalisaTab), chalisaSchedule(marathonID: String?), chalisaArchive
    case aartiPath, aartiPathReader(AartiPathItem)
}

struct HomeView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var events = EventsViewModel()
    @StateObject private var chalisa = ChalisaHomeViewModel()
    @State private var path: [HomeRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            // Home is shown immediately; events appear when loaded (Android).
            ScrollView {
                LazyVStack(spacing: 18) {
                    if !events.events.isEmpty {
                        UpcomingEventsSection(events: events.events) { path.append(.liveEvents) }
                    }
                    Button { openProtected(.myBookings) } label: {
                        HomeActionCard(icon: "suitcase", title: "My Bookings", subtitle: "View all Bus, Stay, and Puja bookings")
                    }
                    .buttonStyle(.plain)
                    ChalisaHomeCard(
                        model: chalisa,
                        canManage: appState.session.canManageChalisa,
                        onOpen: { path.append(.chalisa(.myPath)) },
                        onSchedule: { path.append(.chalisaSchedule(marathonID: nil)) }
                    )
                    Button { path.append(.aartiPath) } label: {
                        HomeActionCard(image: "AartiDiya", title: "Aarti & Path", subtitle: "Chalisa, Aarti, Bajrang Baan and more")
                    }
                    .buttonStyle(.plain)
                    GallerySection()
                    if appState.session.canManageEvents {
                        Button { openProtected(.addEvent) } label: {
                            HomeActionCard(icon: "calendar.badge.plus", title: "Add Event", subtitle: "Create an upcoming event")
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding()
            }
            .navigationTitle("Home")
            .toolbar(.hidden, for: .navigationBar)
            .overlay(alignment: .topTrailing) {
                Button { openProtected(.profile) } label: {
                    Image(systemName: "person.fill")
                        .font(.title3)
                        .foregroundStyle(.primary)
                        .frame(width: 44, height: 44)
                        .background(Color(uiColor: .systemBackground).opacity(0.92), in: Circle())
                        .shadow(color: .black.opacity(0.18), radius: 4, y: 1)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Profile")
                .padding(.trailing, 16)
                .padding(.top, 8)
            }
            // Re-runs whenever Home becomes visible again, like Android's ON_RESUME refresh.
            .task { await events.load(using: appState) }
            .task { await chalisa.refresh(using: appState) }
            .refreshable {
                async let loadEvents: Void = events.load(using: appState)
                async let loadChalisa: Void = chalisa.refresh(using: appState)
                _ = await (loadEvents, loadChalisa)
            }
            .navigationDestination(for: HomeRoute.self) { route in
                switch route {
                case .myBookings: MyBookingsView()
                case .addEvent: AddEventView()
                case .profile: ProfileView(onOpenChalisa: { path.append(.chalisa(.myPath)) })
                case .liveEvents: LiveEventsWebView()
                case .chalisa(let tab):
                    ChalisaView(
                        initialTab: tab,
                        onOpenArchive: { path.append(.chalisaArchive) },
                        onSchedule: { path.append(.chalisaSchedule(marathonID: $0)) }
                    )
                case .chalisaSchedule(let marathonID):
                    ChalisaScheduleView(marathonID: marathonID) {
                        // Show the Path, replacing the form (Android pops it and opens the Live Path tab).
                        path.removeLast()
                        if case .chalisa = path.last {} else { path.append(.chalisa(.group)) }
                    }
                case .chalisaArchive:
                    ChalisaArchiveView()
                case .aartiPath:
                    AartiPathListView { path.append(.aartiPathReader($0)) }
                case .aartiPathReader(let item):
                    AartiPathReaderView(item: item)
                }
            }
        }
        .onChange(of: appState.session.isLoggedIn) { _, isLoggedIn in
            // After logout, don't leave account-only screens on the stack.
            guard !isLoggedIn, let index = path.firstIndex(where: { $0 == .profile || $0 == .addEvent }) else { return }
            path.removeSubrange(index...)
        }
    }

    private func openProtected(_ route: HomeRoute) {
        if appState.session.isLoggedIn { path.append(route) } else { appState.loginSheetPresented = true }
    }
}

private struct UpcomingEventsSection: View {
    let events: [EventListItem]
    let onOpen: () -> Void
    @State private var position: String?

    private var currentIndex: Int { events.firstIndex { $0.id == position } ?? 0 }

    var body: some View {
        VStack(spacing: 12) {
            SectionTitle(title: "Upcoming Events")
            // A horizontal ScrollView sizes to its tallest card, so the pager
            // never clips the content (unlike a fixed-height page TabView).
            ScrollView(.horizontal) {
                HStack(alignment: .top, spacing: 14) {
                    ForEach(events) { event in
                        Button(action: onOpen) { EventCard(event: event) }
                            .buttonStyle(.plain)
                            .containerRelativeFrame(.horizontal)
                            .id(event.id)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollIndicators(.hidden)
            .scrollTargetBehavior(.viewAligned)
            .scrollPosition(id: $position)

            if events.count > 1 {
                HStack(spacing: 8) {
                    ForEach(Array(events.indices), id: \.self) { index in
                        let selected = index == currentIndex
                        Circle()
                            .fill(selected ? AppTheme.brandOrange : Color.secondary.opacity(0.4))
                            .frame(width: selected ? 8 : 6, height: selected ? 8 : 6)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 4)
                .animation(.easeInOut(duration: 0.2), value: currentIndex)
                .accessibilityHidden(true)
            }
        }
    }
}

private struct EventCard: View {
    let event: EventListItem
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Bundled banner is the placeholder and the fallback (Android Coil).
            Color.clear
                .aspectRatio(1600 / 1066, contentMode: .fit)
                .overlay { Image("upcoming_event_banner").resizable().scaledToFill() }
                .overlay {
                    if let url = event.imageURL {
                        AsyncImage(url: url) { phase in
                            if case .success(let image) = phase { image.resizable().scaledToFill() }
                        }
                    }
                }
                .clipped()
                .accessibilityElement()
                .accessibilityLabel(event.header)
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .center, spacing: 10) {
                    Text(event.header).font(.subheadline.weight(.bold)).lineLimit(1)
                    Spacer(minLength: 0)
                    Text(dateChip)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .fixedSize()
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(AppTheme.eventPurple, in: RoundedRectangle(cornerRadius: 12))
                }
                // Reserve three lines so every page has the same height.
                Text(event.subHeader)
                    .font(.footnote)
                    .foregroundStyle(.primary)
                    .lineLimit(3, reservesSpace: true)
                    .multilineTextAlignment(.leading)
            }
            .padding(12)
        }
        .background(Color(uiColor: .systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.primary.opacity(0.15), lineWidth: 0.5))
        .contentShape(RoundedRectangle(cornerRadius: 24))
    }

    /// "dd MMM" in India time (Android `formatEventDate`).
    private var dateChip: String {
        AppFormatters.parseISO(event.eventDate).map { AppFormatters.dayMonth($0) } ?? event.eventDate
    }
}

private struct HomeActionCard: View {
    /// An SF Symbol name, or an asset name when `isAsset` (e.g. the Aarti & Path diya).
    private let icon: String; private let isAsset: Bool
    let title: String; let subtitle: String

    init(icon: String, title: String, subtitle: String) {
        self.icon = icon; isAsset = false; self.title = title; self.subtitle = subtitle
    }

    init(image: String, title: String, subtitle: String) {
        icon = image; isAsset = true; self.title = title; self.subtitle = subtitle
    }

    var body: some View {
        // Android ActionCard: outlined surface card, dark icon on a peach tile, → arrow.
        HStack(spacing: 14) {
            iconImage
                .foregroundStyle(.primary)
                .frame(width: 48, height: 48)
                .background(AppTheme.brandOrange.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.headline).foregroundStyle(.primary)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "arrow.right").font(.title3.weight(.medium)).foregroundStyle(.primary)
        }
        .padding(16)
        .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.primary.opacity(0.18), lineWidth: 1))
        .shadow(color: .black.opacity(0.06), radius: 3, y: 1)
        .contentShape(RoundedRectangle(cornerRadius: 14))
    }

    @ViewBuilder private var iconImage: some View {
        if isAsset {
            Image(icon).renderingMode(.template).resizable().scaledToFit().frame(width: 24, height: 24)
        } else {
            Image(systemName: icon).font(.title3)
        }
    }
}

/// Bundled gallery images, matching Android's `GalleryImages.kt`. Add or remove
/// names here when changing the `Gallery` folder in Assets.xcassets.
private enum GalleryImages {
    static let names = ["gallery_01", "gallery_02", "gallery_03", "gallery_04", "gallery_05", "gallery_06", "gallery_07", "gallery_10"]
    /// Shown uncropped, without parallax zoom (same as Android).
    static let fitImage = "gallery_01"
}

/// Horizontal paging carousel with parallax and page dots. The shuffled list is
/// repeated so the user can keep swiping in both directions, as on Android.
private struct GallerySection: View {
    private static let repeatCount = 5
    private static let pageSpacing: CGFloat = 14
    private static let parallaxFraction: CGFloat = 0.34
    private static let parallaxScale: CGFloat = 1.26

    @State private var baseNames = GalleryImages.names.shuffled()
    /// Start in the middle copy so the user can swipe both ways. Set as the initial
    /// state (not scrolled to in onAppear) so the outer Home scroll view isn't
    /// dragged down to the gallery on launch.
    @State private var position: Int? = GalleryImages.names.count * 2
    @State private var cardWidth: CGFloat = 0

    private var pages: [String] { Array(repeating: baseNames, count: Self.repeatCount).flatMap { $0 } }
    private var dotIndex: Int { (position ?? baseNames.count * 2) % max(baseNames.count, 1) }
    /// 3:4 portrait frame plus 40pt, matching Android's card height.
    private var cardHeight: CGFloat { cardWidth * 4 / 3 + 40 }

    var body: some View {
        if !baseNames.isEmpty {
            VStack(spacing: 12) {
                SectionTitle(title: "Gallery")
                ScrollView(.horizontal) {
                    LazyHStack(spacing: Self.pageSpacing) {
                        ForEach(Array(pages.enumerated()), id: \.offset) { index, name in
                            card(name)
                                .containerRelativeFrame(.horizontal)
                                .id(index)
                        }
                    }
                    .scrollTargetLayout()
                }
                .frame(height: cardHeight)
                .scrollIndicators(.hidden)
                .scrollTargetBehavior(.viewAligned)
                .scrollPosition(id: $position)
                .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { cardWidth = $0 }

                GalleryPageDots(count: baseNames.count, current: dotIndex)
                    .padding(.top, 4)
            }
        }
    }

    private func card(_ name: String) -> some View {
        let isFit = name == GalleryImages.fitImage
        let fraction = Self.parallaxFraction
        return Color(uiColor: .secondarySystemBackground).opacity(0.35)
            .overlay {
                Image(name)
                    .resizable()
                    .aspectRatio(contentMode: isFit ? .fit : .fill)
                    .scaleEffect(isFit ? 1 : Self.parallaxScale)
                    .visualEffect { content, proxy in
                        content.offset(x: -proxy.frame(in: .scrollView).minX * fraction)
                    }
            }
            .frame(height: cardHeight)
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .accessibilityHidden(true)
    }
}

private struct GalleryPageDots: View {
    let count: Int
    let current: Int
    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<count, id: \.self) { index in
                let selected = index == current
                Circle()
                    .fill(selected ? AppTheme.brandOrange : Color.secondary.opacity(0.4))
                    .frame(width: selected ? 8 : 6, height: selected ? 8 : 6)
            }
        }
        .frame(maxWidth: .infinity)
        .animation(.easeInOut(duration: 0.2), value: current)
    }
}

