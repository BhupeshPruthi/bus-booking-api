import SwiftUI

enum ChalisaField: Hashable { case name, startingCount, adminRounds, endConfirm }

// MARK: - Building blocks

struct ChalisaMutedText: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }
}

/// Saffron card used for the marathon and Home entries (Android `SaffronCard`).
struct ChalisaSaffronCard<Content: View>: View {
    var alignment: HorizontalAlignment = .center
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: alignment, spacing: 4) { content }
            .frame(maxWidth: .infinity, alignment: Alignment(horizontal: alignment, vertical: .center))
            .padding(16)
            .background(AppTheme.brandOrange.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(AppTheme.brandOrange.opacity(0.4), lineWidth: 1))
    }
}

struct ChalisaOutlinedCard<Content: View>: View {
    var borderColor: Color = Color.primary.opacity(0.15)
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(borderColor, lineWidth: 1))
    }
}

/// A labelled input on a soft background, like the other forms in the app.
struct ChalisaInput<Field: View>: View {
    let label: String
    var helper: String?
    var helperIsError = false
    @ViewBuilder let field: Field
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            field
                .padding(.horizontal, 14)
                .frame(height: 50)
                .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 12))
            if let helper { Text(helper).font(.caption).foregroundStyle(helperIsError ? Color.red : Color.secondary) }
        }
    }
}

/// Bottom message with an optional Undo (Android snackbar).
struct ChalisaSnackbar: View {
    let message: ChalisaMessage
    let onAction: () -> Void
    let onDismiss: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Text(message.text)
                .font(.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
            if message.action != nil {
                Button("Undo", action: onAction).font(.subheadline.weight(.bold)).foregroundStyle(AppTheme.brandOrange)
                Button(action: onDismiss) { Image(systemName: "xmark").font(.footnote.weight(.semibold)) }
                    .accessibilityLabel("Dismiss")
            }
        }
        .foregroundStyle(Color(uiColor: .systemBackground))
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color(uiColor: .label).opacity(0.92), in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
        .accessibilityElement(children: .contain)
    }
}

// MARK: - Joining and counting

struct StartingCountField: View {
    @Binding var text: String
    var focusedField: FocusState<ChalisaField?>.Binding
    var body: some View {
        ChalisaInput(label: "Already read? Starting count", helper: "Leave it at 0 if you're starting fresh. You can add it once, before your first round.") {
            TextField("0", text: $text)
                .keyboardType(.numberPad)
                .focused(focusedField, equals: .startingCount)
        }
    }
}

struct JoinCard: View {
    @ObservedObject var model: ChalisaViewModel
    let phase: MarathonPhase
    var focusedField: FocusState<ChalisaField?>.Binding
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Join the Live Path").font(.headline)
            Text(phase == .running
                 ? "Enter the name to show on the board, then tap +1 after each round."
                 : "Enter your name now. Counting opens when the Path starts.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            ChalisaInput(label: "Your name on the board") {
                TextField("Name", text: $model.joinName)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .textContentType(.name)
                    .submitLabel(.done)
                    .focused(focusedField, equals: .name)
            }
            if phase == .running {
                StartingCountField(text: $model.startingCountText, focusedField: focusedField)
            }
            Button {
                focusedField.wrappedValue = nil
                Task { await model.join() }
            } label: {
                Group {
                    if model.isJoining { ProgressView().tint(.white) } else { Text(phase == .running ? "Start counting" : "Join") }
                }
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .foregroundStyle(.white)
                .background(AppTheme.brandOrange, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            .disabled(model.isJoining)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.primary.opacity(0.15), lineWidth: 1))
    }
}

/// Admin only: add rounds read offline to the admin's own entry, any time during a live Path.
struct AdminAddRoundsCard: View {
    @ObservedObject var model: ChalisaViewModel
    var focusedField: FocusState<ChalisaField?>.Binding
    var body: some View {
        ChalisaOutlinedCard {
            Text("Add rounds").font(.subheadline.weight(.semibold))
            Text("Admin only. Adds rounds to your own count on the board. You can undo an addition for 2 minutes.")
                .font(.caption)
                .foregroundStyle(.secondary)
            HStack(spacing: 12) {
                TextField("Rounds", text: $model.adminAddText)
                    .keyboardType(.numberPad)
                    .focused(focusedField, equals: .adminRounds)
                    .padding(.horizontal, 14)
                    .frame(height: 50)
                    .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 12))
                    .disabled(model.isAddingAdmin)
                Button {
                    focusedField.wrappedValue = nil
                    Task { await model.addAdminRounds() }
                } label: {
                    Group {
                        if model.isAddingAdmin { ProgressView().tint(.white) } else { Text("Add") }
                    }
                    .font(.body.weight(.semibold))
                    .frame(width: 80, height: 50)
                    .foregroundStyle(.white)
                    .background(AppTheme.brandOrange.opacity(model.adminAddText.isEmpty ? 0.45 : 1), in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(model.isAddingAdmin || model.adminAddText.isEmpty)
            }
        }
    }
}

struct LockScreenToggleCard: View {
    let isOn: Bool
    let onChange: (Bool) -> Void
    var body: some View {
        ChalisaOutlinedCard {
            Toggle(isOn: Binding(get: { isOn }, set: onChange)) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Count from Lock Screen").font(.subheadline.weight(.semibold))
                    Text("Saves battery: tap +1 without unlocking your phone").font(.caption).foregroundStyle(.secondary)
                }
            }
            .tint(AppTheme.brandOrange)
            if isOn {
                Text("If you don't see it on the Lock Screen, turn on Live Activities for Balaji Sevak in Settings.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.top, 2)
            }
        }
    }
}

// MARK: - Live Path

struct MarathonHeaderCard: View {
    let marathon: ChalisaMarathon
    let board: ChalisaBoard
    let phase: MarathonPhase
    let now: Date
    var body: some View {
        let start = AppFormatters.parseISO(marathon.startsAt)
        ChalisaSaffronCard {
            Text(marathon.title).font(.headline).multilineTextAlignment(.center)
            if phase == .scheduled, let start {
                Text("Starts \(ChalisaFormat.startsIn(start, now: now))").font(.title2.weight(.bold))
                Text(ChalisaFormat.start(start)).font(.subheadline)
                if let goal = marathon.goal { Text("Goal \(ChalisaFormat.count(goal)) readings").font(.caption) }
            } else {
                if let start { Text("Since \(ChalisaFormat.shortDate(start))").font(.caption) }
                Text(ChalisaFormat.count(board.total)).font(.system(size: 34, weight: .bold)).monospacedDigit()
                if let goal = marathon.goal, goal > 0 {
                    let progress = min(max(Double(board.total) / Double(goal), 0), 1)
                    ProgressView(value: progress).tint(AppTheme.brandOrange).padding(.vertical, 6)
                    Text("\(Int(progress * 100))% of \(ChalisaFormat.count(goal)) goal · \(ChalisaFormat.count(board.participantCount)) devotees")
                        .font(.caption)
                } else {
                    Text("readings by \(ChalisaFormat.count(board.participantCount)) devotees").font(.caption)
                }
            }
        }
    }
}

struct NoMarathonCard: View {
    @ObservedObject var model: ChalisaViewModel
    let canManage: Bool
    let now: Date
    let onSchedule: () -> Void
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "flame.fill").font(.system(size: 36)).foregroundStyle(AppTheme.brandOrange)
            Text("No Path is running right now").font(.headline).multilineTextAlignment(.center)
            ChalisaMutedText("When the next Path is scheduled, it'll appear on the Home page. Your own readings are always in your Profile.")
            if canManage {
                if let ended = model.recentlyEnded, let until = AppFormatters.parseISO(ended.restoreUntil), until > now {
                    HStack(spacing: 12) {
                        Text("You ended \(ended.title). You can undo this until \(ChalisaFormat.time(until)).")
                            .font(.subheadline)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Button("Undo") { Task { await model.restoreMarathon(ended.id) } }.font(.subheadline.weight(.semibold))
                    }
                    .padding(12)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.primary.opacity(0.15), lineWidth: 1))
                    .padding(.top, 8)
                }
                Button("Schedule a Path", action: onSchedule)
                    .buttonStyle(.borderedProminent)
                    .tint(AppTheme.brandOrange)
                    .padding(.top, 8)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }
}

struct MyRankRow: View {
    let rank: Int?
    let count: Int
    var body: some View {
        HStack {
            Text(rank.map { "You · rank \($0)" } ?? "You haven't participated in this Path yet").font(.subheadline)
            Spacer()
            if rank != nil { Text(ChalisaFormat.count(count)).fontWeight(.bold) }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(AppTheme.brandOrange.opacity(0.6), lineWidth: 1))
    }
}

struct BoardRow: View {
    let entry: ChalisaBoardEntry
    let isMe: Bool
    var body: some View {
        let tags = [entry.isGuest ? "guest" : nil, entry.hasAdminAdditions ? "incl. admin additions" : nil].compactMap { $0 }
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Text("\(entry.rank)")
                    .fontWeight(entry.rank <= 3 ? .bold : .regular)
                    .foregroundStyle(entry.rank <= 3 ? AppTheme.brandOrange : .secondary)
                    .frame(width: 36, alignment: .leading)
                VStack(alignment: .leading, spacing: 1) {
                    Text(entry.displayName + (isMe ? " (you)" : ""))
                        .fontWeight(isMe ? .semibold : .regular)
                        .lineLimit(1)
                    if !tags.isEmpty { Text(tags.joined(separator: " · ")).font(.caption2).foregroundStyle(.secondary) }
                }
                Spacer(minLength: 8)
                Text(ChalisaFormat.count(entry.count)).fontWeight(.semibold).monospacedDigit()
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 4)
            Divider()
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Sheets

struct StartingCountConfirmSheet: View {
    let count: Int
    let marathonTitle: String?
    let onChange: () -> Void
    let onConfirm: () -> Void
    var body: some View {
        VStack(spacing: 10) {
            Text("Add your starting count?").font(.title3.weight(.bold))
            Text(ChalisaFormat.count(count))
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(AppTheme.brandOrange)
            Text("readings already done").font(.subheadline)
            Text("These will be added to \(marathonTitle ?? "the Path") and your lifetime count. You can add a starting count only once for this Path.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.top, 4)
            HStack(spacing: 12) {
                BottomSheetActionButton(title: "Change", style: .secondary, action: onChange)
                BottomSheetActionButton(title: "Yes, add \(ChalisaFormat.count(count))", action: onConfirm)
            }
            .padding(.top, 8)
        }
        .padding(24)
    }
}

/// Super admin: ends the running Path after typing END (Android `EndMarathonSheet`).
struct EndMarathonSheet: View {
    @ObservedObject var model: ChalisaViewModel
    @State private var triedWithoutConfirm = false
    @FocusState private var confirmFocused: Bool

    var body: some View {
        let board = model.board
        let typedCorrectly = model.endConfirmText.trimmingCharacters(in: .whitespaces) == "END"
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("End the Path for everyone?").font(.title2.weight(.bold)).foregroundStyle(.red)
                if let marathon = model.marathon {
                    let since = AppFormatters.parseISO(marathon.startsAt).map { " · since \(ChalisaFormat.shortDate($0))" } ?? ""
                    VStack(alignment: .leading, spacing: 2) {
                        Text(marathon.title).fontWeight(.semibold)
                        Text("\(ChalisaFormat.count(board?.total ?? 0)) readings · \(ChalisaFormat.count(board?.participantCount ?? 0)) devotees\(since)")
                            .font(.caption)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 12))
                }
                VStack(alignment: .leading, spacing: 4) {
                    ForEach([
                        "Counting stops for all \(ChalisaFormat.count(board?.participantCount ?? 0)) devotees",
                        "It disappears from everyone's Home page",
                        "Results are saved in History",
                        "Everyone's lifetime count stays the same",
                    ], id: \.self) { Text("• \($0)").font(.subheadline) }
                }
                ChalisaInput(label: "Type END to confirm", helper: triedWithoutConfirm && !typedCorrectly ? "Type END exactly to continue" : nil, helperIsError: true) {
                    TextField("END", text: $model.endConfirmText)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .focused($confirmFocused)
                        .onChange(of: model.endConfirmText) { triedWithoutConfirm = false }
                }
                HStack(spacing: 12) {
                    BottomSheetActionButton(title: "Cancel", style: .secondary) { model.showEndSheet = false }
                    Button {
                        if typedCorrectly { Task { await model.endMarathon() } } else { triedWithoutConfirm = true }
                    } label: {
                        Group {
                            if model.isEnding { ProgressView().tint(.white) } else { Text("End Path") }
                        }
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .foregroundStyle(.white)
                        .background(Color.red, in: RoundedRectangle(cornerRadius: 14))
                    }
                    .buttonStyle(.plain)
                    .disabled(model.isEnding)
                }
            }
            .padding(24)
        }
    }
}
