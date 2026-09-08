import Foundation

let defaultReportRecipient = "korrapidaja@tallinnlv.ee"
let maximumReportPhotos = 3

enum ViolationType: String, Codable, CaseIterable, Sendable {
    case cyclePath = "CYCLE_PATH"
    case pedestrianPath = "PEDESTRIAN_PATH"
    case custom = "CUSTOM"
}

enum ReportStatus: String, Codable, CaseIterable, Sendable {
    case draft = "DRAFT"
    case ready = "READY"
    case handedOffToMail = "HANDED_OFF_TO_MAIL"
}

enum PhotoSource: String, Codable, Sendable {
    case camera = "CAMERA"
    case gallery = "GALLERY"
}

enum RecognitionSource: String, Codable, CaseIterable, Hashable, Sendable {
    case visionOCR = "VISION_OCR"
    case localPlateModel = "LOCAL_PLATE_MODEL"
}

struct ReporterProfile: Codable, Equatable, Sendable {
    var name = ""
    var phone = ""
}

struct AppSettings: Codable, Equatable, Sendable {
    var onboardingComplete = false
    var languageTag = ""
    var profile = ReporterProfile()
    var defaultRecipient = defaultReportRecipient

    var locale: Locale {
        languageTag.isEmpty ? .autoupdatingCurrent : Locale(identifier: languageTag)
    }
}

struct LocationSnapshot: Codable, Equatable, Sendable {
    var latitude: Double
    var longitude: Double
    var accuracyMeters: Double?
    var capturedAt: Date
}

enum AddressCandidateType: String, Codable, Sendable {
    case street = "STREET"
    case building = "BUILDING"
}

struct AddressCandidate: Codable, Equatable, Identifiable, Sendable {
    var address: String
    var distanceMeters: Int
    var type: AddressCandidateType

    var id: String { "\(type.rawValue):\(address.lowercased())" }
}

struct AddressResolution: Equatable, Sendable {
    var location: LocationSnapshot
    var candidates: [AddressCandidate]
    var suggested: AddressCandidate?
    var needsReview: Bool
}

struct AddressLookupResult: Equatable, Sendable {
    var location: LocationSnapshot
    var resolution: AddressResolution?
}

struct AddressMapSelection: Equatable, Sendable {
    var address: String
    var latitude: Double
    var longitude: Double
    var candidates: [AddressCandidate]
    var addressLookupFailed: Bool
}

struct AddressDraft: Equatable, Sendable {
    var address: String
    var latitude: Double?
    var longitude: Double?
    var accuracyMeters: Double?
    var occurredAt: Date
    var candidates: [AddressCandidate] = []
    var lookupFailed = false
}

struct NormalizedPhotoRect: Codable, Equatable, Sendable {
    var left: Float
    var top: Float
    var right: Float
    var bottom: Float

    var isValid: Bool {
        (0 ... 1).contains(left) &&
            (0 ... 1).contains(top) &&
            (0 ... 1).contains(right) &&
            (0 ... 1).contains(bottom) &&
            right > left && bottom > top
    }
}

struct ReportPhoto: Codable, Equatable, Identifiable, Sendable {
    var id: String
    var reportID: String
    var relativePath: String
    var source: PhotoSource
    var capturedAt: Date
    var isPrimary: Bool
    var visionRecognitionComplete = false
    var plateRecognitionComplete = false

    var localRecognitionComplete: Bool {
        visionRecognitionComplete && plateRecognitionComplete
    }
}

struct StoredPhotoReference: Equatable, Sendable {
    var reportID: String
    var relativePath: String
}

struct RecognitionPlateObservation: Equatable, Sendable {
    var photoID: String
    var value: String
    var bounds: NormalizedPhotoRect?
    var detectionConfidence: Float?
    var characterConfidence: Float?
    var relativeArea: Float?
}

struct PlateObservation: Codable, Equatable, Identifiable, Sendable {
    var id: String
    var reportID: String
    var photoID: String
    var source: RecognitionSource
    var value: String
    var bounds: NormalizedPhotoRect?
    var detectionConfidence: Float?
    var characterConfidence: Float?
    var relativeArea: Float?
}

struct PlateCandidate: Equatable, Identifiable, Sendable {
    var value: String
    var sources: Set<RecognitionSource>
    var supportingPhotoCount: Int
    var observations: [PlateObservation]

    var id: String { value }
}

struct RecognitionResult: Equatable, Sendable {
    var source: RecognitionSource
    var photoID: String? = nil
    var plateCandidates: [String] = []
    var plateObservations: [RecognitionPlateObservation] = []
}

struct Report: Codable, Equatable, Identifiable, Sendable {
    var id: String
    var createdAt: Date
    var updatedAt: Date
    var occurredAt: Date
    var status: ReportStatus
    var plate: String
    var vehicleMake: String
    var vehicleModel: String
    var violationType: ViolationType?
    var customTemplateID: String?
    var recipient: String
    var address: String
    var latitude: Double?
    var longitude: Double?
    var accuracyMeters: Double?
    var locationNeedsReview: Bool
    var subject: String
    var body: String
    var plateManuallyEdited: Bool
    var vehicleManuallyEdited: Bool
    var vehicleConfirmed: Bool
    var locationConfirmed: Bool
    var plateObservations: [PlateObservation]
    var mailOpenedAt: Date?
    var photos: [ReportPhoto]
    var localRecognitionFingerprint: String

    var primaryPhoto: ReportPhoto? {
        photos.first(where: \.isPrimary) ?? photos.first
    }

    var hasValidLocation: Bool {
        let hasAddress = !address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        switch (latitude, longitude) {
        case (nil, nil):
            return hasAddress
        case let (.some(latitude), .some(longitude)):
            return latitude.isFinite && longitude.isFinite &&
                (-90 ... 90).contains(latitude) && (-180 ... 180).contains(longitude)
        default:
            return false
        }
    }

    func isReady(profile: ReporterProfile, violationDescription: String?) -> Bool {
        !photos.isEmpty &&
            !plate.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            vehicleConfirmed &&
            !(violationDescription?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true) &&
            !recipient.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !subject.isEmpty &&
            !body.isEmpty &&
            !profile.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !profile.phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            locationConfirmed &&
            hasValidLocation
    }
}

struct CustomViolationTemplate: Codable, Equatable, Identifiable, Sendable {
    var id: String
    var displayName: String
    var estonianDescription: String
    var createdAt: Date
    var updatedAt: Date
}

struct LetterDraft: Equatable, Sendable {
    var subject: String
    var body: String
}

enum AppTab: Hashable, Sendable {
    case camera
    case history
    case settings
}

enum ReportWizardStep: Int, CaseIterable, Sendable {
    case vehicle
    case place
    case violation
    case summary

    var previous: ReportWizardStep? {
        ReportWizardStep(rawValue: rawValue - 1)
    }
}

extension Report {
    var resolvedWizardStep: ReportWizardStep {
        if status == .ready || status == .handedOffToMail {
            return .summary
        }
        if plate.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !vehicleConfirmed {
            return .vehicle
        }
        if !hasValidLocation || !locationConfirmed {
            return .place
        }
        if violationType == nil {
            return .violation
        }
        return .summary
    }
}
