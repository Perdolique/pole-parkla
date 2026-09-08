@preconcurrency import AVFoundation
import Observation
import SwiftUI

@MainActor
@Observable
final class CameraController: NSObject, AVCapturePhotoCaptureDelegate {
    private let captureSession = CameraCaptureSession()
    private struct PendingCapture {
        var settingsID: Int64
        var continuation: CheckedContinuation<Data, Error>
    }

    private var pendingCapture: PendingCapture?
    private var lifecycleGeneration = 0
    private var isStarting = false
    private var restartRequested = false
    var state = CameraState.idle
    var zoomFactor: CGFloat = 1
    var session: AVCaptureSession { captureSession.session }

    func start() async {
        guard state != .running else { return }
        if isStarting {
            restartRequested = true
            return
        }
        repeat {
            isStarting = true
            restartRequested = false
            await performStart()
            isStarting = false
        } while restartRequested && state != .running
    }

    private func performStart() async {
        lifecycleGeneration += 1
        let generation = lifecycleGeneration
        let granted: Bool
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: granted = true
        case .notDetermined: granted = await AVCaptureDevice.requestAccess(for: .video)
        default: granted = false
        }
        guard generation == lifecycleGeneration else { return }
        guard granted else { state = .permissionDenied; return }
        do {
            try await captureSession.start()
            guard generation == lifecycleGeneration else { return }
            state = .running
        } catch {
            state = .unavailable
        }
    }

    func stop() {
        lifecycleGeneration += 1
        restartRequested = false
        captureSession.stop()
        pendingCapture?.continuation.resume(throwing: CameraError.captureFailed)
        pendingCapture = nil
        if state == .running { state = .idle }
    }

    func capture() async throws -> Data {
        guard state == .running, pendingCapture == nil else { throw CameraError.notReady }
        let settings = AVCapturePhotoSettings(format: [AVVideoCodecKey: AVVideoCodecType.jpeg])
        settings.photoQualityPrioritization = .quality
        return try await withCheckedThrowingContinuation { continuation in
            pendingCapture = PendingCapture(settingsID: settings.uniqueID, continuation: continuation)
            captureSession.capture(settings: settings, delegate: self)
        }
    }

    func setZoom(_ factor: CGFloat) {
        if let value = captureSession.setZoom(factor) { zoomFactor = value }
    }

    nonisolated func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        let result: Result<Data, CameraError>
        if error != nil {
            result = .failure(.captureFailed)
        } else if let data = photo.fileDataRepresentation() {
            result = .success(data)
        } else {
            result = .failure(.noData)
        }

        Task { @MainActor in
            guard pendingCapture?.settingsID == photo.resolvedSettings.uniqueID else { return }
            switch result {
            case let .success(data):
                pendingCapture?.continuation.resume(returning: data)
            case let .failure(error):
                pendingCapture?.continuation.resume(throwing: error)
            }
            pendingCapture = nil
        }
    }

}

private final class CameraCaptureSession: @unchecked Sendable {
    let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private let queue = DispatchQueue(label: "com.perdolique.poleparkla.camera-session")
    private var device: AVCaptureDevice?

    func start() async throws {
        try await withCheckedThrowingContinuation { continuation in
            queue.async {
                do {
                    try self.configure()
                    if !self.session.isRunning { self.session.startRunning() }
                    continuation.resume()
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    func stop() {
        queue.async {
            if self.session.isRunning { self.session.stopRunning() }
        }
    }

    func capture(settings: AVCapturePhotoSettings, delegate: AVCapturePhotoCaptureDelegate) {
        let delegate = PhotoCaptureDelegateBox(value: delegate)
        queue.async {
            if let connection = self.output.connection(with: .video), connection.isVideoRotationAngleSupported(90) {
                connection.videoRotationAngle = 90
            }
            self.output.capturePhoto(with: settings, delegate: delegate.value)
        }
    }

    func setZoom(_ factor: CGFloat) -> CGFloat? {
        guard let device else { return nil }
        let value = min(device.activeFormat.videoMaxZoomFactor, max(1, factor))
        do {
            try device.lockForConfiguration()
            device.videoZoomFactor = value
            device.unlockForConfiguration()
            return value
        } catch {
            return nil
        }
    }

    private func configure() throws {
        guard session.inputs.isEmpty else { return }
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        session.sessionPreset = .photo
        let discovery = AVCaptureDevice.DiscoverySession(
            deviceTypes: [.builtInWideAngleCamera, .builtInDualCamera, .builtInTripleCamera],
            mediaType: .video,
            position: .unspecified
        )
        guard let selected = discovery.devices.first(where: { $0.position == .back })
            ?? discovery.devices.first(where: { $0.position == .front })
        else { throw CameraError.unavailable }
        let input = try AVCaptureDeviceInput(device: selected)
        guard session.canAddInput(input), session.canAddOutput(output) else { throw CameraError.unavailable }
        session.addInput(input)
        session.addOutput(output)
        output.maxPhotoQualityPrioritization = .quality
        device = selected
    }
}

private struct PhotoCaptureDelegateBox: @unchecked Sendable {
    let value: AVCapturePhotoCaptureDelegate
}

enum CameraState: Equatable { case idle, running, permissionDenied, unavailable }
enum CameraError: Error, Equatable, Sendable { case unavailable, notReady, noData, captureFailed }

struct CameraCaptureGate: Equatable, Sendable {
    private(set) var isInFlight = false

    mutating func begin(whenReady ready: Bool) -> Bool {
        guard ready, !isInFlight else { return false }
        isInFlight = true
        return true
    }

    mutating func finish() {
        isInFlight = false
    }
}

struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        uiView.previewLayer.session = session
        if let connection = uiView.previewLayer.connection, connection.isVideoRotationAngleSupported(90) {
            connection.videoRotationAngle = 90
        }
    }
}

final class PreviewView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
}
