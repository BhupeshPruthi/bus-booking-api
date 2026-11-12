import Combine
import SwiftUI

// MARK: - Home

@MainActor final class ChalisaHomeViewModel: ObservableObject {
    @Published private(set) var summary: ChalisaSummary?
    /// The latest load failed (as opposed to still loading).
    @Published private(set) var loadFailed = false

    /// Called each time Home appears, so an ended Path disappears from the Home page.
    func refresh(using appState: AppState) async {
        do {
            let loaded = try await ChalisaService(appState: appState).summary()
            loadFailed = false
            summary = loaded
            // A lock-screen counter left on from an ended Path is cleared here.
            if loaded.state == "none" { await ChalisaLockScreen.shared.onNoMarathonRunning() }
        } catch is CancellationError {
        } catch {
            loadFailed = true
        }
    }
}

/// Home page entry for the Hanuman Chalisa Path (Android `ChalisaHomeCard`). Users see it only
/// while a Path is scheduled or running; the super admin always sees it, to schedule the next one.
struct ChalisaHomeCard: View {
    @ObservedObject var model: ChalisaHomeViewModel
    let canManage: Bool
    let onOpen: () -> Void
    let onSchedule: () -> Void

    var body: some View {
        if let summary = model.summary {
            if let marathon = summary.marathon, summary.state == "running" {
                Button(action: onOpen) { runningCard(summary, marathon) }.buttonStyle(.plain)
            } else if let marathon = summary.marathon, summary.state == "scheduled" {
                Button(action: onOpen) { scheduledCard(summary, marathon) }.buttonStyle(.plain)
            } else if canManage {
                adminScheduleCard(summary, action: onSchedule)
            }
        } else if canManage && model.loadFailed {
            // Couldn't load: the super admin still gets a way in (the screen shows its own error).
            adminScheduleCard(nil, action: onOpen)
        }
    }

    private func runningCard(_ summary: ChalisaSummary, _ marathon: ChalisaMarathon) -> some View {
        ChalisaSaffronCard(alignment: .leading) {
            entryHeader(title: "Hanuman Chalisa Counter", subtitle: marathon.title)
            Divider().overlay(AppTheme.brandOrange.opacity(0.3)).padding(.vertical, 8)
            HStack {
                stat(ChalisaFormat.count(summary.total), "Group")
                stat(ChalisaFormat.count(summary.participantCount), "Devotees")
                stat(ChalisaFormat.count(summary.myToday), "You today")
            }
        }
        .contentShape(RoundedRectangle(cornerRadius: 14))
    }

    private func scheduledCard(_ summary: ChalisaSummary, _ marathon: ChalisaMarathon) -> some View {
        let start = AppFormatters.parseISO(marathon.startsAt)
        let serverNow = AppFormatters.parseISO(summary.serverTime) ?? Date()
        let goal = marathon.goal.map { " · Goal \(ChalisaFormat.count($0)) readings" } ?? ""
        return ChalisaSaffronCard(alignment: .leading) {
            entryHeader(title: marathon.title, subtitle: "Hanuman Chalisa · group reading")
            Divider().overlay(AppTheme.brandOrange.opacity(0.3)).padding(.vertical, 8)
            if let start {
                VStack(spacing: 2) {
                    Text("Starts \(ChalisaFormat.start(start))").fontWeight(.semibold)
                    Text(ChalisaFormat.startsIn(start, now: serverNow) + goal).font(.caption)
                }
                .frame(maxWidth: .infinity)
                .multilineTextAlignment(.center)
            }
        }
        .contentShape(RoundedRectangle(cornerRadius: 14))
    }

    private func adminScheduleCard(_ summary: ChalisaSummary?, action: @escaping () -> Void) -> some View {
        ChalisaOutlinedCard(borderColor: AppTheme.brandOrange.opacity(0.5)) {
            HStack(spacing: 12) {
                Image(systemName: "flame.fill").foregroundStyle(AppTheme.brandOrange)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Hanuman Chalisa Path").font(.headline)
                    Text(summary == nil ? "Couldn't load · only you can see this" : "No Path scheduled · only you can see this")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Button(action: action) {
                Text(summary == nil ? "Open Hanuman Chalisa" : summary?.lastEnded != nil ? "Schedule the next Path" : "Schedule a Path")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .foregroundStyle(.white)
                    .background(AppTheme.brandOrange, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
            if let last = summary?.lastEnded {
                Text("Last: \(last.title) · \(ChalisaFormat.count(last.finalTotal)) readings")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
            }
        }
    }

    private func entryHeader(title: String, subtitle: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "flame.fill")
                .foregroundStyle(AppTheme.brandOrange)
                .frame(width: 44, height: 44)
                .background(AppTheme.brandOrange.opacity(0.2), in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading)
                Text(subtitle).font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "arrow.right").foregroundStyle(.secondary)
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.headline).monospacedDigit()
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Profile

@MainActor final class ChalisaProfileViewModel: ObservableObject {
    @Published private(set) var stats: ChalisaMyStats?

    func refresh(using appState: AppState) async {
        if let loaded = try? await ChalisaService(appState: appState).myStats() { stats = loaded }
    }
}

/// Lifetime Hanuman Chalisa readings and each Path's part (Android `ChalisaProfileSection`).
/// Shown even between Paths, once the person has joined or read.
struct ChalisaProfileSection: View {
    let stats: ChalisaMyStats

    var body: some View {
        let since = stats.firstReadDate.flatMap(ChalisaFormat.dayDate).map { "lifetime readings since \(ChalisaFormat.date($0))" }
        ChalisaSaffronCard(alignment: .leading) {
            HStack {
                Image(systemName: "flame.fill").foregroundStyle(AppTheme.brandOrange)
                Text("Your Hanuman Chalisa").fontWeight(.semibold)
                Spacer()
                Image(systemName: "arrow.right").foregroundStyle(.secondary).accessibilityLabel("Open your history")
            }
            Text(ChalisaFormat.count(stats.lifetime))
                .font(.system(size: 32, weight: .bold))
                .monospacedDigit()
                .frame(maxWidth: .infinity)
            Text(since ?? "lifetime readings").font(.caption).frame(maxWidth: .infinity)
            HStack(spacing: 8) {
                tile(ChalisaFormat.count(stats.today), "Today")
                tile(ChalisaFormat.count(stats.thisMonth), "This month")
                tile("\(stats.streakDays)", "Day streak")
            }
            .padding(.top, 4)
            if !stats.marathons.isEmpty {
                Text("Your Paths").fontWeight(.semibold).padding(.top, 8)
                ForEach(stats.marathons.prefix(5)) { marathon in
                    HStack {
                        Text("\(marathon.title) · rank \(marathon.rank)").font(.subheadline)
                        Spacer()
                        Text(ChalisaFormat.count(marathon.count)).fontWeight(.semibold)
                    }
                }
            }
        }
        .foregroundStyle(.primary)
        .contentShape(RoundedRectangle(cornerRadius: 14))
    }

    private func tile(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).fontWeight(.bold).monospacedDigit()
            Text(label).font(.caption)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(AppTheme.brandOrange.opacity(0.18), in: RoundedRectangle(cornerRadius: 8))
    }
}
