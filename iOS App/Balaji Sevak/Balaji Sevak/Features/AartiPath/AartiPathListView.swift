import SwiftUI
import UniformTypeIdentifiers

/// Aarti & Path: a list of PDFs anyone can read. Admins (Super Admin, Bus Admin) can add
/// a PDF with a title or delete one (Android `AartiPathScreen`).
struct AartiPathListView: View {
    @EnvironmentObject private var appState: AppState
    @StateObject private var model = AartiPathListViewModel()
    @State private var search = ""
    @State private var pendingDelete: AartiPathItem?
    @State private var addSheetShown = false
    let onOpen: (AartiPathItem) -> Void

    private var isAdmin: Bool { appState.session.canManageAartiPath }

    private var filtered: [AartiPathItem] {
        let query = search.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return model.items }
        return model.items.filter { $0.title.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        content
            .screenTitle("Aarti & Path")
            .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search aarti or path")
            .safeAreaInset(edge: .top, spacing: 0) {
                if model.isShowingSavedList { offlineNotice }
            }
            .overlay(alignment: .bottomTrailing) {
                // Hidden under a sheet or alert: it would show through the sheet's glass.
                if isAdmin && !addSheetShown && pendingDelete == nil && model.actionError == nil { addButton }
            }
            .overlay(alignment: .bottom) {
                if let toast = model.toast {
                    AartiPathToast(text: toast)
                        .padding(.bottom, isAdmin ? 76 : 8)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.easeOut(duration: 0.2), value: model.toast)
            .task(id: model.toast) {
                guard model.toast != nil else { return }
                try? await Task.sleep(for: .seconds(3))
                model.toast = nil
            }
            // Runs again when returning from a PDF, so newly saved ones get their tick.
            .task { await model.load(using: appState) }
            .onAppear { model.refreshCachedIDs() }
            .sheet(isPresented: $addSheetShown) {
                AddAartiPathSheet { item in
                    model.added(item)
                    Task { await model.load(using: appState) }
                }
            }
            .alert(
                Text("Delete \(pendingDelete?.title ?? "")?"),
                isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
                presenting: pendingDelete
            ) { item in
                Button("Cancel", role: .cancel) {}
                Button("Delete", role: .destructive) { Task { await model.delete(item, using: appState) } }
            } message: { _ in
                Text("It will be removed from the list for everyone. This can't be undone.")
            }
            .alert("Couldn't delete", isPresented: Binding(get: { model.actionError != nil }, set: { if !$0 { model.actionError = nil } })) {
                Button("OK") { model.actionError = nil }
            } message: { Text(model.actionError ?? "") }
    }

    @ViewBuilder private var content: some View {
        if model.isLoading && !model.hasList {
            LoadingView()
        } else if let error = model.error, !model.hasList {
            ErrorStateView(message: error) { Task { await model.load(using: appState) } }
        } else if model.items.isEmpty {
            ScrollView {
                ContentUnavailableView {
                    Label { Text("No aartis or paths yet") } icon: { Image("AartiDiya").renderingMode(.template) }
                } description: {
                    if isAdmin { Text("Tap Add PDF to add the first one.") }
                }
                .padding(.top, 100)
            }
            .refreshable { await model.load(using: appState) }
        } else if filtered.isEmpty {
            ContentUnavailableView.search(text: search)
        } else {
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(filtered) { item in
                        AartiPathRow(
                            item: item,
                            isSaved: model.cachedIDs.contains(item.id),
                            isAdmin: isAdmin,
                            isDeleting: model.deletingID == item.id,
                            onOpen: { onOpen(item) },
                            onDelete: { pendingDelete = item }
                        )
                        if item.id != filtered.last?.id { Divider().padding(.leading, 68) }
                    }
                }
                .padding(.bottom, isAdmin ? 88 : 16)
            }
            .refreshable { await model.load(using: appState) }
        }
    }

    /// Small and non-blocking: saved PDFs still open, and pulling down tries again.
    private var offlineNotice: some View {
        Label("Offline. Showing saved list.", systemImage: "wifi.slash")
            .font(.footnote.weight(.medium))
            .foregroundStyle(.secondary)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(AppTheme.softSurface, in: Capsule())
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(Color(uiColor: .systemBackground))
    }

    private var addButton: some View {
        Button { addSheetShown = true } label: {
            Label("Add PDF", systemImage: "plus")
                .font(.body.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .frame(height: 52)
                .background(AppTheme.brandOrange, in: Capsule())
                .shadow(color: .black.opacity(0.18), radius: 8, y: 3)
        }
        .buttonStyle(.plain)
        .padding(16)
    }
}

private struct AartiPathRow: View {
    let item: AartiPathItem
    let isSaved: Bool
    let isAdmin: Bool
    let isDeleting: Bool
    let onOpen: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onOpen) {
                HStack(spacing: 12) {
                    Image(systemName: "doc.text.fill")
                        .font(.body)
                        .foregroundStyle(.primary)
                        .frame(width: 40, height: 40)
                        .background(AppTheme.brandOrange.opacity(0.18), in: RoundedRectangle(cornerRadius: 10))
                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.title)
                            .font(.body.weight(.medium))
                            .foregroundStyle(.primary)
                            .multilineTextAlignment(.leading)
                            .lineLimit(2)
                        meta
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if !isAdmin {
                        Image(systemName: "chevron.right")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.tertiary)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint("Opens the PDF")

            if isAdmin {
                if isDeleting {
                    ProgressView().frame(width: 44, height: 44)
                } else {
                    Button(action: onDelete) {
                        Image(systemName: "trash")
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Delete \(item.title)")
                }
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, isAdmin ? 6 : 16)
        .padding(.vertical, 10)
    }

    @ViewBuilder private var meta: some View {
        HStack(spacing: 4) {
            if let pages = item.pagesText { Text(pages) }
            if !isAdmin && isSaved {
                if item.pagesText != nil { Text("·") }
                Label("Saved offline", systemImage: "checkmark.circle.fill")
                    .labelStyle(.titleAndIcon)
                    .foregroundStyle(Color(uiColor: .systemGreen))
            } else if let size = item.sizeText {
                if item.pagesText != nil { Text("·") }
                Text(size)
            }
        }
        .font(.caption)
        .foregroundStyle(.secondary)
        .lineLimit(1)
    }
}

/// Short message at the bottom of the screen (Android snackbar).
struct AartiPathToast: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(Color(uiColor: .systemBackground))
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(Color(uiColor: .label).opacity(0.92), in: RoundedRectangle(cornerRadius: 12))
            .padding(.horizontal, 16)
            .accessibilityAddTraits(.updatesFrequently)
    }
}

/// "Add Aarti or Path": pick a PDF, check the title, upload.
struct AddAartiPathSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = AddAartiPathViewModel()
    @State private var importerShown = false
    @FocusState private var titleFocused: Bool
    let onAdded: (AartiPathItem) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("Add Aarti or Path").font(.title3.weight(.semibold))
                    Spacer()
                    Button { close() } label: {
                        Image(systemName: "xmark")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .frame(width: 36, height: 36)
                            .background(AppTheme.softSurface, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Close")
                    .disabled(model.isUploading)
                }

                fileSection

                VStack(alignment: .leading, spacing: 6) {
                    Text("Title").font(.subheadline.weight(.medium))
                    TextField("e.g. Hanuman Chalisa", text: $model.title)
                        .textInputAutocapitalization(.words)
                        .submitLabel(.done)
                        .focused($titleFocused)
                        .padding(.horizontal, 14)
                        .frame(height: 48)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(AppTheme.brandOrange, lineWidth: titleFocused ? 2 : 1))
                        .disabled(model.isUploading)
                    HStack {
                        Text("Shown in the list")
                        Spacer()
                        Text("\(model.title.count) / \(AartiPathFormat.titleLimit)").monospacedDigit()
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }

                if let uploadError = model.uploadError {
                    Label(uploadError, systemImage: "exclamationmark.triangle.fill")
                        .font(.subheadline)
                        .foregroundStyle(.red)
                }

                Button { Task { await upload() } } label: {
                    Group {
                        if let progress = model.progress {
                            HStack(spacing: 8) {
                                ProgressView().tint(.white)
                                Text("Uploading… \(Int((progress * 100).rounded()))%").monospacedDigit()
                            }
                        } else {
                            Text("Upload")
                        }
                    }
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(AppTheme.brandOrange.opacity(model.canUpload || model.isUploading ? 1 : 0.4), in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(!model.canUpload)

                Text("PDF only, up to 20 MB")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
            }
            .padding(20)
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color(uiColor: .systemBackground))
        .interactiveDismissDisabled(model.isUploading)
        .fileImporter(isPresented: $importerShown, allowedContentTypes: [.pdf]) { result in
            model.pick(result)
        }
        .onDisappear { model.discardFile() }
    }

    @ViewBuilder private var fileSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let file = model.file {
                HStack(spacing: 12) {
                    Text("PDF")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 36, height: 44, alignment: .bottom)
                        .padding(.bottom, 6)
                        .background(Color(uiColor: .systemRed), in: RoundedRectangle(cornerRadius: 6))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(file.name).font(.subheadline.weight(.medium)).lineLimit(1).truncationMode(.middle)
                        Text(file.detail).font(.caption).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Button("Change") { importerShown = true }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AppTheme.brandOrange)
                        .disabled(model.isUploading)
                }
                .padding(12)
                .background(AppTheme.softSurface, in: RoundedRectangle(cornerRadius: 12))
            } else {
                Button { importerShown = true } label: {
                    VStack(spacing: 6) {
                        Image(systemName: "doc.badge.plus").font(.title2)
                        Text("Choose PDF").font(.body.weight(.semibold))
                        Text("From Files, iCloud Drive or Downloads").font(.caption).foregroundStyle(.secondary)
                    }
                    .foregroundStyle(AppTheme.brandOrange)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 22)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(AppTheme.brandOrange.opacity(0.7), style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
            if let fileError = model.fileError {
                Text(fileError).font(.caption).foregroundStyle(.red)
            }
        }
    }

    private func upload() async {
        titleFocused = false
        guard appState.session.isLoggedIn else { appState.loginSheetPresented = true; return }
        guard let item = await model.upload(using: appState) else { return }
        onAdded(item)
        dismiss()
    }

    private func close() {
        model.discardFile()
        dismiss()
    }
}
