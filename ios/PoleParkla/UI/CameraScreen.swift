import PhotosUI
import SwiftUI
import UIKit

struct CameraScreen: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Bindable var model: AppModel
    @State private var camera = CameraController()
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var zoomStart: CGFloat = 1
    @State private var zoomIndicatorVisible = false
    @State private var zoomIndicatorTask: Task<Void, Never>?
    @State private var pendingDeletion: ReportPhoto?
    @State private var captureGate = CameraCaptureGate()

    private var photos: [ReportPhoto] { model.cameraReport?.photos ?? [] }
    private var remainingSlots: Int { maximumReportPhotos - photos.count }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            cameraSurface
            cameraOverlay
            if zoomIndicatorVisible {
                Text("\(camera.zoomFactor, specifier: "%.1f")×")
                    .font(.headline.monospacedDigit())
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(PpColor.cameraScrim, in: Capsule())
                    .transition(.opacity.combined(with: .scale))
                    .accessibilityHidden(true)
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .task(id: isCameraActive) {
            guard isCameraActive else {
                camera.stop()
                model.locationService.stopUpdating()
                return
            }
            model.locationService.startUpdating()
            await camera.start()
        }
        .onDisappear {
            camera.stop()
            model.locationService.stopUpdating()
            zoomIndicatorTask?.cancel()
        }
        .onChange(of: pickerItems) { _, items in
            let generation = model.dataGeneration
            Task { await importItems(items, expectedGeneration: generation) }
        }
        .confirmationDialog(
            "camera.remove.confirm.title",
            isPresented: Binding(get: { pendingDeletion != nil }, set: { if !$0 { pendingDeletion = nil } }),
            titleVisibility: .visible
        ) {
            Button("common.delete", role: .destructive) {
                guard let pendingDeletion else { return }
                Task {
                    _ = await model.removePhoto(pendingDeletion)
                    self.pendingDeletion = nil
                }
            }
            Button("common.cancel", role: .cancel) { pendingDeletion = nil }
        } message: { Text("camera.remove.confirm.body") }
    }

    @ViewBuilder
    private var cameraSurface: some View {
        if camera.state == .running {
            CameraPreview(session: camera.session)
                .ignoresSafeArea(edges: .top)
                .gesture(
                    MagnifyGesture()
                        .onChanged { value in
                            zoomIndicatorTask?.cancel()
                            withAnimation(.easeOut(duration: 0.12)) { zoomIndicatorVisible = true }
                            camera.setZoom(zoomStart * value.magnification)
                        }
                        .onEnded { _ in
                            zoomStart = camera.zoomFactor
                            scheduleZoomIndicatorDismissal()
                        }
                )
                .accessibilityLabel("camera.preview")
        } else {
            cameraPlaceholder
        }
    }

    private var cameraOverlay: some View {
        VStack(spacing: 12) {
            VStack(spacing: 10) {
                photoSlots
                locationStatus
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)

            Spacer()

            Text(photos.count == maximumReportPhotos ? "camera.limit.replace" : "camera.slots.hint")
                .font(.caption.weight(.medium))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(PpColor.cameraScrim, in: Capsule())
                .padding(.horizontal, 20)

            captureControls
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
        }
    }

    private var photoSlots: some View {
        HStack(spacing: 10) {
            ForEach(0 ..< maximumReportPhotos, id: \.self) { index in
                if photos.indices.contains(index) {
                    let photo = photos[index]
                    Button { pendingDeletion = photo } label: {
                        ReportPhotoThumbnail(
                            photoStore: model.photoStore,
                            photo: photo,
                            size: 68,
                            cornerRadius: 14
                        )
                        .overlay(alignment: .topTrailing) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.title3)
                                .foregroundStyle(.white, PpColor.danger)
                                .offset(x: 6, y: -6)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(photoDeletionLabel(index: index)))
                    .accessibilityIdentifier("camera.slot.delete.\(index)")
                    .disabled(captureGate.isInFlight || model.isWorking)
                } else {
                    RoundedRectangle(cornerRadius: 14)
                        .fill(PpColor.cameraScrim)
                        .frame(width: 68, height: 68)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(.white.opacity(0.55), style: StrokeStyle(lineWidth: 1.5, dash: [6]))
                        )
                        .overlay(Image(systemName: "photo").foregroundStyle(.white.opacity(0.7)))
                        .accessibilityHidden(true)
                }
            }
            Spacer(minLength: 8)
            Text("\(photos.count)/\(maximumReportPhotos)")
                .font(.subheadline.bold().monospacedDigit())
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .frame(minHeight: 48)
                .background(PpColor.cameraScrim, in: Capsule())
                .accessibilityIdentifier("camera.photoCount")
        }
    }

    @ViewBuilder
    private var locationStatus: some View {
        switch model.locationService.authorizationState {
        case .authorized:
            if let accuracy = model.locationService.freshLocation?.accuracyMeters {
                Label(
                    String(
                        format: model.localized("camera.location.accuracy"),
                        locale: model.settings.value.locale,
                        Int64(accuracy.rounded())
                    ),
                    systemImage: "location.fill"
                )
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .frame(minHeight: 36)
                .background(PpColor.cameraScrim, in: Capsule())
            } else {
                Label("camera.location.waiting", systemImage: "location")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .frame(minHeight: 36)
                    .background(PpColor.cameraScrim, in: Capsule())
            }
        case .notDetermined:
            Button {
                model.locationService.requestPermission()
            } label: {
                if usesExpandedCameraLayout {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "location")
                            .frame(width: 28)
                            .frame(minHeight: 48)
                        VStack(alignment: .leading, spacing: 10) {
                            Text("camera.location.explain")
                                .font(.caption)
                                .multilineTextAlignment(.leading)
                            Text("camera.location.request")
                                .font(.caption.bold())
                                .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                        }
                    }
                    .foregroundStyle(.white)
                    .padding(12)
                    .background(PpColor.cameraScrim, in: RoundedRectangle(cornerRadius: 14))
                } else {
                    HStack(spacing: 10) {
                        Image(systemName: "location")
                        Text("camera.location.explain")
                            .font(.caption)
                            .multilineTextAlignment(.leading)
                        Spacer(minLength: 4)
                        Text("camera.location.request").font(.caption.bold())
                    }
                    .foregroundStyle(.white)
                    .padding(12)
                    .background(PpColor.cameraScrim, in: RoundedRectangle(cornerRadius: 14))
                }
            }
            .buttonStyle(.plain)
        case .denied:
            Button { openSystemSettings() } label: {
                Label("camera.location.denied", systemImage: "location.slash")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .frame(minHeight: 48)
                    .background(PpColor.danger.opacity(0.88), in: Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    private var captureControls: some View {
        HStack(spacing: 22) {
            PhotosPicker(
                selection: $pickerItems,
                maxSelectionCount: max(1, remainingSlots),
                matching: .images
            ) {
                Image(systemName: "photo.on.rectangle")
                    .font(.title2)
                    .frame(width: 56, height: 56)
                    .foregroundStyle(.white)
                    .background(.white.opacity(0.17), in: Circle())
            }
            .accessibilityLabel("camera.gallery")
            .accessibilityIdentifier("camera.gallery")
            .disabled(captureGate.isInFlight || model.isWorking || remainingSlots == 0)

            Button(action: startCapture) {
                Circle()
                    .fill(.white)
                    .frame(width: 74, height: 74)
                    .overlay(Circle().stroke(.white.opacity(0.5), lineWidth: 5).padding(-6))
                    .overlay {
                        if captureGate.isInFlight || model.isWorking {
                            ProgressView().tint(PpColor.forest)
                        }
                    }
            }
            .opacity(captureEnabled ? 1 : 0.38)
            .disabled(!captureEnabled)
            .accessibilityLabel("camera.capture")
            .accessibilityValue(captureGate.isInFlight || model.isWorking ? Text("camera.capturing") : Text(""))

            Button {
                model.openCameraDraftForReview()
            } label: {
                VStack(spacing: 4) {
                    Image(systemName: "checkmark.circle.fill").font(.title2)
                    Text("camera.review")
                        .font(.caption.bold())
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
                .frame(minWidth: 72, minHeight: 58)
                .foregroundStyle(photos.isEmpty ? .white.opacity(0.45) : PpColor.ink)
                .background(photos.isEmpty ? .white.opacity(0.12) : PpColor.signal, in: RoundedRectangle(cornerRadius: 18))
            }
            .buttonStyle(.plain)
            .disabled(photos.isEmpty || captureGate.isInFlight || model.isWorking)
            .accessibilityIdentifier("camera.review")
        }
    }

    private var cameraPlaceholder: some View {
        VStack(spacing: 16) {
            Image(systemName: camera.state == .permissionDenied ? "camera.fill.badge.xmark" : "camera")
                .font(.system(size: 46))
                .foregroundStyle(.white)
            Text(camera.state == .permissionDenied ? "camera.permission.denied" : "camera.unavailable")
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
            if camera.state == .permissionDenied {
                Button("common.settings") { openSystemSettings() }
                    .buttonStyle(.borderedProminent)
                    .tint(PpColor.signal)
                    .foregroundStyle(PpColor.ink)
            }
        }
        .padding(28)
    }

    private var captureEnabled: Bool {
        camera.state == .running && !captureGate.isInFlight && !model.isWorking && remainingSlots > 0
    }

    private func photoDeletionLabel(index: Int) -> String {
        String(format: model.localized("camera.remove.accessibility"), Int64(index + 1))
    }

    private func startCapture() {
        let ready = camera.state == .running && !model.isWorking && remainingSlots > 0
        guard captureGate.begin(whenReady: ready) else { return }
        Task { await capture() }
    }

    private func capture() async {
        defer { captureGate.finish() }
        let generation = model.dataGeneration
        do {
            let data = try await camera.capture()
            _ = await model.importCameraPhoto(
                data: data,
                fallbackLocation: model.locationService.freshLocation,
                expectedGeneration: generation
            )
        } catch CameraError.notReady {
            // A rapid second tap is ignored while the first capture is still in flight.
        } catch {
            guard isCameraActive else { return }
            model.notice = model.localized("camera.capture.failed")
        }
    }

    private func importItems(_ items: [PhotosPickerItem], expectedGeneration: Int) async {
        pickerItems = []
        guard !items.isEmpty else { return }
        var dataItems: [Data] = []
        for item in items {
            guard let data = try? await item.loadTransferable(type: Data.self) else {
                model.notice = model.localized("error.photo.import")
                return
            }
            dataItems.append(data)
        }
        _ = await model.importGalleryPhotos(
            dataItems: dataItems,
            fallbackLocation: model.locationService.freshLocation,
            expectedGeneration: expectedGeneration
        )
    }

    private func scheduleZoomIndicatorDismissal() {
        zoomIndicatorTask?.cancel()
        zoomIndicatorTask = Task {
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled else { return }
            withAnimation(.easeIn(duration: 0.18)) { zoomIndicatorVisible = false }
        }
    }

    private var usesExpandedCameraLayout: Bool {
        dynamicTypeSize == .xxxLarge || dynamicTypeSize.isAccessibilitySize
    }

    private var isCameraActive: Bool {
        model.selectedTab == .camera && model.selectedReportID == nil
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
