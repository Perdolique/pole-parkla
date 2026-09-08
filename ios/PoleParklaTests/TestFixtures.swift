import Foundation
@testable import PoleParkla

func testPhoto(
    id: String = "photo-1",
    reportID: String = "report",
    primary: Bool = true,
    visionRecognitionComplete: Bool = false,
    plateRecognitionComplete: Bool = false
) -> ReportPhoto {
    ReportPhoto(
        id: id,
        reportID: reportID,
        relativePath: "Photos/\(reportID)/\(id).jpg",
        source: .camera,
        capturedAt: Date(timeIntervalSince1970: 1_700_000_000),
        isPrimary: primary,
        visionRecognitionComplete: visionRecognitionComplete,
        plateRecognitionComplete: plateRecognitionComplete
    )
}

func testReport(
    plate: String = "003 PUK",
    vehicleConfirmed: Bool = true,
    locationConfirmed: Bool = true,
    photos: [ReportPhoto] = [testPhoto()]
) -> Report {
    Report(
        id: "report",
        createdAt: Date(timeIntervalSince1970: 1_700_000_000),
        updatedAt: Date(timeIntervalSince1970: 1_700_000_000),
        occurredAt: Date(timeIntervalSince1970: 1_700_000_000),
        status: .draft,
        plate: plate,
        vehicleMake: "Toyota",
        vehicleModel: "Corolla",
        violationType: .cyclePath,
        customTemplateID: nil,
        recipient: "mupo@example.com",
        address: "Lastekodu tn 42, Tallinn",
        latitude: 59.437,
        longitude: 24.7536,
        accuracyMeters: 8.8,
        locationNeedsReview: false,
        subject: "Subject",
        body: "Body",
        plateManuallyEdited: false,
        vehicleManuallyEdited: false,
        vehicleConfirmed: vehicleConfirmed,
        locationConfirmed: locationConfirmed,
        plateObservations: [],
        mailOpenedAt: nil,
        photos: photos,
        localRecognitionFingerprint: ""
    )
}

let testProfile = ReporterProfile(name: "Pier Dolique", phone: "+37256789012")

@MainActor
func makeTestSettingsStore(defaults: UserDefaults, root: URL) throws -> SettingsStore {
    try SettingsStore(
        defaults: defaults,
        sensitiveURL: root.appendingPathComponent("Settings.json")
    )
}

actor CountingRecognizer: PhotoRecognitionService {
    private let result: Result<RecognitionResult, TestRecognitionError>
    private(set) var callCount = 0

    init(result: Result<RecognitionResult, TestRecognitionError>) {
        self.result = result
    }

    func recognize(photo: ReportPhoto, data: Data) async throws -> RecognitionResult {
        callCount += 1
        return try result.get()
    }
}

enum TestRecognitionError: Error, Sendable {
    case failed
}
