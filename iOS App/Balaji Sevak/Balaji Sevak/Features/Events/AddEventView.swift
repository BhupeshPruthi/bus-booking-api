import PhotosUI
import SwiftUI
import UIKit

/// Admin screen for creating an upcoming event (Android `AddEventScreen`).
struct AddEventView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = AddEventViewModel()
    @State private var photo: PhotosPickerItem?
    @State private var previewImage: UIImage?
    @State private var isPreparingPhoto = false
    @State private var datePickerShown = false
    @State private var pickerDate = Date()

    private var isBusy: Bool { viewModel.isSubmitting }

    var body: some View {
        Form {
            Section {
                Text("Create an upcoming event for users to see on the Home page.")
                    .foregroundStyle(.secondary)
            }

            Section {
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Header", text: $viewModel.header)
                    counter(viewModel.header.count, AddEventViewModel.headerLimit)
                }
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Sub Header", text: $viewModel.subHeader, axis: .vertical).lineLimit(2...6)
                    counter(viewModel.subHeader.count, AddEventViewModel.subHeaderLimit)
                }
                Button {
                    pickerDate = viewModel.eventDate ?? Date()
                    datePickerShown = true
                } label: {
                    HStack {
                        Label("Event date", systemImage: "calendar")
                            .foregroundStyle(.primary)
                        Spacer()
                        Text(viewModel.eventDate.map { AppFormatters.fullDate($0, timeZone: .current) } ?? "Select Event Date")
                            .foregroundStyle(viewModel.eventDate == nil ? AppTheme.brandOrange : .primary)
                    }
                }
            } header: { Text("Event details") }
            .disabled(isBusy)

            Section {
                photoPreview
                    .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))
                let pickerTitle = viewModel.imageData == nil ? "Select Photo" : "Change Photo"
                HStack(spacing: 12) {
                    PhotosPicker(selection: $photo, matching: .images) {
                        Label(pickerTitle, systemImage: "photo.on.rectangle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(AppTheme.brandOrange)
                    if viewModel.imageData != nil {
                        Button(role: .destructive) {
                            photo = nil
                            previewImage = nil
                            viewModel.imageData = nil
                        } label: {
                            Label("Remove", systemImage: "xmark").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .disabled(isBusy || isPreparingPhoto)
            } header: {
                Text("Event Photo")
            } footer: {
                Text("Use a landscape image, ideally 1600 × 1066 px. The Home banner shows it at this ratio, so the edges of other sizes may be cropped.")
            }

            Section {
                Button { Task { await viewModel.submit(using: appState) } } label: {
                    HStack(spacing: 8) {
                        Spacer()
                        if isBusy { ProgressView() }
                        else { Image(systemName: "calendar.badge.plus"); Text("Add Event").fontWeight(.semibold) }
                        Spacer()
                    }
                }
                .disabled(isBusy || isPreparingPhoto)
            }
        }
        .navigationTitle("Add Event").navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(isBusy)
        .interactiveDismissDisabled(isBusy)
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task { await preparePhoto(item) }
        }
        .sheet(isPresented: $datePickerShown) { datePickerSheet }
        .alert("Error", isPresented: Binding(get: { viewModel.error != nil }, set: { if !$0 { viewModel.error = nil } })) {
            Button("OK") { viewModel.error = nil }
        } message: { Text(viewModel.error ?? "") }
        .alert("Event Added", isPresented: Binding(get: { viewModel.createdEvent != nil }, set: { _ in })) {
            Button("Done") { viewModel.createdEvent = nil; dismiss() }
        } message: { Text(successMessage) }
    }

    private func counter(_ count: Int, _ limit: Int) -> some View {
        Text("\(count)/\(limit)").font(.caption2).foregroundStyle(.tertiary).frame(maxWidth: .infinity, alignment: .trailing)
    }

    private var photoPreview: some View {
        Color(uiColor: .tertiarySystemFill)
            .aspectRatio(1600 / 1066, contentMode: .fit)
            .overlay {
                if let previewImage {
                    Image(uiImage: previewImage).resizable().scaledToFill()
                } else if isPreparingPhoto {
                    ProgressView()
                } else {
                    VStack(spacing: 6) {
                        Image(systemName: "photo.on.rectangle").font(.title2)
                        Text("Default banner will be used").font(.subheadline)
                    }
                    .foregroundStyle(.secondary)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .accessibilityLabel(previewImage == nil ? "Default banner will be used" : "Selected event photo")
    }

    private var datePickerSheet: some View {
        NavigationStack {
            DatePicker("Event date", selection: $pickerDate, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .tint(AppTheme.brandOrange)
                .padding()
                .navigationTitle("Select Event Date")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { datePickerShown = false } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { viewModel.eventDate = pickerDate; viewModel.error = nil; datePickerShown = false }
                    }
                }
        }
        .presentationDetents([.medium, .large])
    }

    private var successMessage: String {
        guard let event = viewModel.createdEvent else { return "" }
        let date = AppFormatters.parseISO(event.eventDate).map { AppFormatters.fullDate($0) } ?? event.eventDate
        let photo = event.imageURL == nil ? "Default banner" : "Uploaded"
        return "Header: \(event.header)\nSub Header: \(event.subHeader)\nDate: \(date)\nPhoto: \(photo)"
    }

    private func preparePhoto(_ item: PhotosPickerItem) async {
        isPreparingPhoto = true
        defer { isPreparingPhoto = false }
        guard let encoded = await EventImageEncoder.encode(from: item) else {
            viewModel.error = "Could not prepare selected image"
            return
        }
        // Ignore a stale result if the photo was removed or replaced meanwhile.
        guard photo == item else { return }
        viewModel.imageData = encoded.data
        previewImage = encoded.preview
    }
}

/// Scales a picked photo so its longest side is at most 1600 px and encodes it
/// as JPEG (quality 85), matching Android's upload preparation.
enum EventImageEncoder {
    static func encode(from item: PhotosPickerItem) async -> (data: Data, preview: UIImage)? {
        guard let raw = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: raw) else { return nil }
        let maxSide: CGFloat = 1600
        let scale = min(1, maxSide / max(image.size.width, image.size.height))
        let size = CGSize(width: (image.size.width * scale).rounded(), height: (image.size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1 // pixel-exact output, not screen-scale
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let data = renderer.jpegData(withCompressionQuality: 0.85) { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        guard let preview = UIImage(data: data) else { return nil }
        return (data, preview)
    }
}
