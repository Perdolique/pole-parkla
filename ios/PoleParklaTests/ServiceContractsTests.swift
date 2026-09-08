import CoreLocation
import Foundation
import Testing
@testable import PoleParkla

@Suite("Service contracts")
struct ServiceContractsTests {
    @Test func inAksParserFiltersInvalidRowsAndOrdersCandidates() throws {
        let data = Data(#"{"addresses":[{"olek":"K","liikVal":"TANAV","liikluspind":"Pärnu mnt","omavalitsus":"Tallinn","kaugus":"3"},{"olek":"K","liikVal":"EHITISHOONE","liikluspind":"Pärnu mnt","aadress_nr":"10","omavalitsus":"Tallinn","kaugus":"0"},{"olek":"A","liikVal":"TANAV","liikluspind":"Old","omavalitsus":"Tallinn","kaugus":"0"}]}"#.utf8)
        let location = LocationSnapshot(latitude: 59.4, longitude: 24.7, accuracyMeters: 10, capturedAt: .now)
        let result = try InAksParser.parse(data: data, location: location)
        #expect(result.candidates.map(\.address) == ["Pärnu mnt, Tallinn", "Pärnu mnt 10, Tallinn"])
        #expect(result.suggested?.address == "Pärnu mnt 10, Tallinn")
        #expect(!result.needsReview)
    }

    @Test func inAksParserIgnoresDistancesThatCannotFitInAnInteger() throws {
        let data = Data(#"{"addresses":[{"olek":"K","liikVal":"TANAV","liikluspind":"Overflow","omavalitsus":"Tallinn","kaugus":"1e309"},{"olek":"K","liikVal":"TANAV","liikluspind":"Finite","omavalitsus":"Tallinn","kaugus":"4"}]}"#.utf8)
        let location = LocationSnapshot(latitude: 59.4, longitude: 24.7, accuracyMeters: 10, capturedAt: .now)

        let result = try InAksParser.parse(data: data, location: location)

        #expect(result.candidates.map(\.address) == ["Finite, Tallinn"])
    }

    @Test func inAksRadiusClampsNonFiniteAccuracyBeforeIntegerConversion() {
        let service = InAksAddressService()
        #expect(service.radiusMeters(.infinity) == 50)
        #expect(service.radiusMeters(.nan) == 50)
        #expect(service.radiusMeters(Double.greatestFiniteMagnitude) == 100)
    }

    @Test @MainActor func cancelledAddressLookupCannotPublishALateResponse() async throws {
        let persistence = try PersistenceController.inMemory()
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaAddress-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let service = ControlledAddressService()
        let model = AppModel(
            repository: ReportRepository(container: persistence.container),
            settings: try makeTestSettingsStore(
                defaults: try #require(UserDefaults(suiteName: "PoleParklaAddress-\(UUID().uuidString)")),
                root: root
            ),
            photoStore: try PhotoStore(rootURL: root),
            addressService: service,
            recognition: nil,
            mapCacheReset: { true }
        )
        model.selectedReportID = "report"
        let location = LocationSnapshot(latitude: 59.4, longitude: 24.7, accuracyMeters: 10, capturedAt: .now)
        let lookup = Task { await model.resolveAddress(reportID: "report", location: location) }
        while !(await service.hasPendingRequest) { await Task.yield() }

        model.cancelAddressResolution()
        await service.complete(with: AddressResolution(
            location: location,
            candidates: [AddressCandidate(address: "Late address", distanceMeters: 1, type: .building)],
            suggested: nil,
            needsReview: true
        ))

        #expect(await lookup.value == nil)
        #expect(model.notice == nil)
    }

    @Test func foregroundLocationRejectsStaleCachedValues() {
        let now = Date(timeIntervalSince1970: 1_000)
        let fresh = LocationSnapshot(latitude: 59.4, longitude: 24.7, accuracyMeters: 10, capturedAt: now.addingTimeInterval(-119))
        let stale = LocationSnapshot(latitude: 59.4, longitude: 24.7, accuracyMeters: 10, capturedAt: now.addingTimeInterval(-121))
        #expect(LocationFreshness.isFresh(fresh, now: now))
        #expect(!LocationFreshness.isFresh(stale, now: now))
    }

    @Test @MainActor func foregroundLocationLifecycleDoesNotReuseThePreviousCameraSession() {
        let service = ForegroundLocationService()
        let location = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 59.4, longitude: 24.7),
            altitude: 0,
            horizontalAccuracy: 5,
            verticalAccuracy: 5,
            timestamp: .now
        )
        service.locationManager(CLLocationManager(), didUpdateLocations: [location])
        #expect(service.latestLocation != nil)

        service.stopUpdating()
        #expect(service.latestLocation == nil)
        service.startUpdating()
        #expect(service.latestLocation == nil)
        service.stopUpdating()
    }

    @Test func localRecognitionKeepsSuccessfulResultsWhenOneRecognizerFails() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaRecognition-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try PhotoStore(rootURL: root)
        let photo = testPhoto()
        let url = try await store.url(for: photo.relativePath)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try Data("fixture".utf8).write(to: url)
        let expected = RecognitionResult(source: .visionOCR, photoID: photo.id, plateCandidates: ["003 PUK"])
        let coordinator = RecognitionCoordinator(
            photoStore: store,
            vision: StubRecognizer(result: .success(expected)),
            plates: StubRecognizer(result: .failure(.failed))
        )

        let batch = await coordinator.recognize(photos: [photo])

        #expect(batch.results == [expected])
        #expect(batch.failureCount == 1)
        #expect(!batch.isComplete)
    }

    @Test func localRecognitionRetriesOnlyTheIncompleteEngine() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaRecognitionRetry-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try PhotoStore(rootURL: root)
        let photo = testPhoto(visionRecognitionComplete: true)
        let url = try await store.url(for: photo.relativePath)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try Data("fixture".utf8).write(to: url)
        let vision = CountingRecognizer(result: .failure(.failed))
        let plates = CountingRecognizer(result: .success(RecognitionResult(
            source: .localPlateModel,
            photoID: photo.id,
            plateCandidates: ["003 PUK"]
        )))
        let coordinator = RecognitionCoordinator(photoStore: store, vision: vision, plates: plates)

        let batch = await coordinator.recognize(photos: [photo])

        #expect(await vision.callCount == 0)
        #expect(await plates.callCount == 1)
        #expect(batch.isComplete)
        #expect(batch.results.map(\.source) == [.localPlateModel])
    }

    @Test func captureGateRejectsRapidSecondCaptureUntilTheFirstFinishes() {
        var gate = CameraCaptureGate()

        let first = gate.begin(whenReady: true)
        #expect(first)
        #expect(gate.isInFlight)
        let second = gate.begin(whenReady: true)
        #expect(!second)

        gate.finish()

        #expect(!gate.isInFlight)
        let third = gate.begin(whenReady: true)
        #expect(third)
    }

    @Test func messageComposerResultsRecordOnlyThatTheComposerOpened() {
        #expect(MailComposerResult.cancelled.countsAsHandoff)
        #expect(MailComposerResult.saved.countsAsHandoff)
        #expect(MailComposerResult.sent.countsAsHandoff)
        #expect(!MailComposerResult.failed.countsAsHandoff)
    }
}

private struct StubRecognizer: PhotoRecognitionService {
    var result: Result<RecognitionResult, StubRecognitionError>

    func recognize(photo: ReportPhoto, data: Data) async throws -> RecognitionResult {
        try result.get()
    }
}

private enum StubRecognitionError: Error, Sendable {
    case failed
}

private actor ControlledAddressService: AddressResolving {
    private var continuation: CheckedContinuation<AddressResolution, Error>?
    var hasPendingRequest: Bool { continuation != nil }

    func resolve(_ location: LocationSnapshot) async throws -> AddressResolution {
        try await withCheckedThrowingContinuation { continuation = $0 }
    }

    func complete(with resolution: AddressResolution) {
        continuation?.resume(returning: resolution)
        continuation = nil
    }
}

@Suite("Bundled models")
struct BundledModelIntegrationTests {
    @Test(.timeLimit(.minutes(2))) func bothOnnxModelsRecognizeExisting003PukFixture() async throws {
        let bundle = Bundle(for: TestBundleToken.self)
        let fixture = try #require(bundle.url(forResource: "street_plate_test_image", withExtension: "png"))
        let data = try Data(contentsOf: fixture)
        let service = LocalPlateRecognitionService(bundle: .main)
        let result = try await service.recognize(photo: testPhoto(), data: data)
        #expect(result.plateObservations.contains(where: { $0.value == "003 PUK" }))
    }
}

private final class TestBundleToken {}
