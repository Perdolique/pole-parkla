import Foundation
import SwiftData

@Model
final class ReportRecord {
    @Attribute(.unique) var id: String
    var createdAt: Date
    var updatedAt: Date
    var occurredAt: Date
    var statusRaw: String
    var plate: String
    var vehicleMake: String
    var vehicleModel: String
    var violationTypeRaw: String?
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
    var mailOpenedAt: Date?
    var localRecognitionFingerprint: String

    @Relationship(deleteRule: .cascade, inverse: \PhotoRecord.report)
    var photos: [PhotoRecord]

    @Relationship(deleteRule: .cascade, inverse: \PlateObservationRecord.report)
    var plateObservations: [PlateObservationRecord]

    init(
        id: String,
        createdAt: Date,
        updatedAt: Date,
        occurredAt: Date,
        statusRaw: String,
        plate: String,
        vehicleMake: String,
        vehicleModel: String,
        violationTypeRaw: String?,
        customTemplateID: String?,
        recipient: String,
        address: String,
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Double?,
        locationNeedsReview: Bool,
        subject: String,
        body: String,
        plateManuallyEdited: Bool,
        vehicleManuallyEdited: Bool,
        vehicleConfirmed: Bool,
        locationConfirmed: Bool,
        mailOpenedAt: Date?,
        localRecognitionFingerprint: String,
        photos: [PhotoRecord] = [],
        plateObservations: [PlateObservationRecord] = []
    ) {
        self.id = id
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.occurredAt = occurredAt
        self.statusRaw = statusRaw
        self.plate = plate
        self.vehicleMake = vehicleMake
        self.vehicleModel = vehicleModel
        self.violationTypeRaw = violationTypeRaw
        self.customTemplateID = customTemplateID
        self.recipient = recipient
        self.address = address
        self.latitude = latitude
        self.longitude = longitude
        self.accuracyMeters = accuracyMeters
        self.locationNeedsReview = locationNeedsReview
        self.subject = subject
        self.body = body
        self.plateManuallyEdited = plateManuallyEdited
        self.vehicleManuallyEdited = vehicleManuallyEdited
        self.vehicleConfirmed = vehicleConfirmed
        self.locationConfirmed = locationConfirmed
        self.mailOpenedAt = mailOpenedAt
        self.localRecognitionFingerprint = localRecognitionFingerprint
        self.photos = photos
        self.plateObservations = plateObservations
    }
}

@Model
final class PhotoRecord {
    @Attribute(.unique) var id: String
    var reportID: String
    var relativePath: String
    var sourceRaw: String
    var capturedAt: Date
    var isPrimary: Bool
    var visionRecognitionComplete = false
    var plateRecognitionComplete = false
    var report: ReportRecord?

    init(photo: ReportPhoto, report: ReportRecord?) {
        id = photo.id
        reportID = photo.reportID
        relativePath = photo.relativePath
        sourceRaw = photo.source.rawValue
        capturedAt = photo.capturedAt
        isPrimary = photo.isPrimary
        visionRecognitionComplete = photo.visionRecognitionComplete
        plateRecognitionComplete = photo.plateRecognitionComplete
        self.report = report
    }

    var domain: ReportPhoto {
        ReportPhoto(
            id: id,
            reportID: reportID,
            relativePath: relativePath,
            source: PhotoSource(rawValue: sourceRaw) ?? .gallery,
            capturedAt: capturedAt,
            isPrimary: isPrimary,
            visionRecognitionComplete: visionRecognitionComplete,
            plateRecognitionComplete: plateRecognitionComplete
        )
    }
}

@Model
final class PlateObservationRecord {
    @Attribute(.unique) var id: String
    var reportID: String
    var photoID: String
    var sourceRaw: String
    var value: String
    var boundsLeft: Float?
    var boundsTop: Float?
    var boundsRight: Float?
    var boundsBottom: Float?
    var detectionConfidence: Float?
    var characterConfidence: Float?
    var relativeArea: Float?
    var report: ReportRecord?

    init(observation: PlateObservation, report: ReportRecord?) {
        id = observation.id
        reportID = observation.reportID
        photoID = observation.photoID
        sourceRaw = observation.source.rawValue
        value = observation.value
        boundsLeft = observation.bounds?.left
        boundsTop = observation.bounds?.top
        boundsRight = observation.bounds?.right
        boundsBottom = observation.bounds?.bottom
        detectionConfidence = observation.detectionConfidence
        characterConfidence = observation.characterConfidence
        relativeArea = observation.relativeArea
        self.report = report
    }

    var domain: PlateObservation {
        let bounds: NormalizedPhotoRect? = if let boundsLeft, let boundsTop, let boundsRight, let boundsBottom {
            NormalizedPhotoRect(left: boundsLeft, top: boundsTop, right: boundsRight, bottom: boundsBottom)
        } else {
            nil
        }
        return PlateObservation(
            id: id,
            reportID: reportID,
            photoID: photoID,
            source: RecognitionSource(rawValue: sourceRaw) ?? .visionOCR,
            value: value,
            bounds: bounds,
            detectionConfidence: detectionConfidence,
            characterConfidence: characterConfidence,
            relativeArea: relativeArea
        )
    }
}

@Model
final class CustomViolationTemplateRecord {
    @Attribute(.unique) var id: String
    var displayName: String
    var estonianDescription: String
    var createdAt: Date
    var updatedAt: Date

    init(template: CustomViolationTemplate) {
        id = template.id
        displayName = template.displayName
        estonianDescription = template.estonianDescription
        createdAt = template.createdAt
        updatedAt = template.updatedAt
    }

    var domain: CustomViolationTemplate {
        CustomViolationTemplate(
            id: id,
            displayName: displayName,
            estonianDescription: estonianDescription,
            createdAt: createdAt,
            updatedAt: updatedAt
        )
    }
}

struct PersistenceController {
    let container: ModelContainer
    let rootDirectory: URL

    static func live() throws -> PersistenceController {
        let root = try privateApplicationDirectory()
        let schema = Schema([
            ReportRecord.self,
            PhotoRecord.self,
            PlateObservationRecord.self,
            CustomViolationTemplateRecord.self,
        ])
        let configuration = ModelConfiguration(
            "PoleParkla",
            schema: schema,
            url: root.appendingPathComponent("pole-parkla.store"),
            cloudKitDatabase: .none
        )
        let container = try ModelContainer(for: schema, configurations: [configuration])
        return PersistenceController(container: container, rootDirectory: root)
    }

    static func inMemory() throws -> PersistenceController {
        let schema = Schema([
            ReportRecord.self,
            PhotoRecord.self,
            PlateObservationRecord.self,
            CustomViolationTemplateRecord.self,
        ])
        let configuration = ModelConfiguration(isStoredInMemoryOnly: true)
        return PersistenceController(
            container: try ModelContainer(for: schema, configurations: [configuration]),
            rootDirectory: FileManager.default.temporaryDirectory
                .appendingPathComponent("PoleParklaTests-\(UUID().uuidString)", isDirectory: true)
        )
    }

    static func persistent(at storeURL: URL) throws -> PersistenceController {
        let schema = Schema([
            ReportRecord.self,
            PhotoRecord.self,
            PlateObservationRecord.self,
            CustomViolationTemplateRecord.self,
        ])
        let root = storeURL.deletingLastPathComponent()
        try protectPrivateDirectory(root)
        let configuration = ModelConfiguration(
            "PoleParklaTests",
            schema: schema,
            url: storeURL,
            cloudKitDatabase: .none
        )
        return PersistenceController(
            container: try ModelContainer(for: schema, configurations: [configuration]),
            rootDirectory: root
        )
    }

    private static func privateApplicationDirectory() throws -> URL {
        let base = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let directory = base.appendingPathComponent("PoleParkla", isDirectory: true)
        try protectPrivateDirectory(directory)
        return directory
    }

    private static func protectPrivateDirectory(_ directory: URL) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutableDirectory = directory
        try mutableDirectory.setResourceValues(values)
        try FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.complete],
            ofItemAtPath: directory.path
        )
    }
}

@MainActor
final class ReportRepository {
    private let container: ModelContainer
    private let context: ModelContext

    init(container: ModelContainer) {
        self.container = container
        context = container.mainContext
        context.autosaveEnabled = false
    }

    func reports(limit: Int? = nil) throws -> [Report] {
        var descriptor = FetchDescriptor<ReportRecord>(
            sortBy: [SortDescriptor(\ReportRecord.updatedAt, order: .reverse)]
        )
        if let limit { descriptor.fetchLimit = limit }
        descriptor.relationshipKeyPathsForPrefetching = [\.photos, \.plateObservations]
        return try context.fetch(descriptor).map(\.domain)
    }

    func reportCount() throws -> Int {
        try context.fetchCount(FetchDescriptor<ReportRecord>())
    }

    func photoReferences() throws -> [StoredPhotoReference] {
        try context.fetch(FetchDescriptor<PhotoRecord>()).map {
            StoredPhotoReference(reportID: $0.reportID, relativePath: $0.relativePath)
        }
    }

    func pendingRecognitionReportIDs() throws -> [String] {
        let photos = try context.fetch(FetchDescriptor<PhotoRecord>())
        return Array(Set(
            photos
                .filter { !$0.visionRecognitionComplete || !$0.plateRecognitionComplete }
                .map(\.reportID)
        )).sorted()
    }

    func report(id: String) throws -> Report? {
        try record(id: id)?.domain
    }

    func templates() throws -> [CustomViolationTemplate] {
        try context.fetch(FetchDescriptor<CustomViolationTemplateRecord>())
            .map(\.domain)
            .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }

    func createDraft(
        id: String,
        photos: [ReportPhoto],
        settings: AppSettings,
        location: LocationSnapshot?,
        occurredAt: Date,
        locationNeedsReview: Bool
    ) throws {
        guard !photos.isEmpty, photos.count <= maximumReportPhotos else {
            throw PersistenceError.invalidPhotoCount
        }
        guard photos.allSatisfy({ $0.reportID == id }) else { throw PersistenceError.wrongReport }
        let primaryID = photos.first(where: \.isPrimary)?.id ?? photos[0].id
        let now = Date.now
        let record = ReportRecord(
            id: id,
            createdAt: now,
            updatedAt: now,
            occurredAt: occurredAt,
            statusRaw: ReportStatus.draft.rawValue,
            plate: "",
            vehicleMake: "",
            vehicleModel: "",
            violationTypeRaw: nil,
            customTemplateID: nil,
            recipient: settings.defaultRecipient,
            address: "",
            latitude: location?.latitude,
            longitude: location?.longitude,
            accuracyMeters: location?.accuracyMeters,
            locationNeedsReview: locationNeedsReview,
            subject: "",
            body: "",
            plateManuallyEdited: false,
            vehicleManuallyEdited: false,
            vehicleConfirmed: false,
            locationConfirmed: false,
            mailOpenedAt: nil,
            localRecognitionFingerprint: ""
        )
        try context.transaction {
            context.insert(record)
            for photo in photos {
                let normalized = ReportPhoto(
                    id: photo.id,
                    reportID: photo.reportID,
                    relativePath: photo.relativePath,
                    source: photo.source,
                    capturedAt: photo.capturedAt,
                    isPrimary: photo.id == primaryID
                )
                let photoRecord = PhotoRecord(photo: normalized, report: record)
                context.insert(photoRecord)
                record.photos.append(photoRecord)
            }
            try context.save()
        }
    }

    func addPhotos(reportID: String, photos: [ReportPhoto]) throws {
        guard !photos.isEmpty else { return }
        guard let record = try record(id: reportID) else { throw PersistenceError.missingReport }
        guard record.photos.count + photos.count <= maximumReportPhotos else {
            throw PersistenceError.invalidPhotoCount
        }
        guard photos.allSatisfy({ $0.reportID == reportID }) else { throw PersistenceError.wrongReport }
        let primaryID = record.photos.contains(where: \.isPrimary)
            ? nil
            : photos.first(where: \.isPrimary)?.id ?? photos.first?.id
        try context.transaction {
            resetAfterPhotoChange(record)
            for photo in photos {
                var normalized = photo
                normalized.isPrimary = photo.id == primaryID
                let photoRecord = PhotoRecord(photo: normalized, report: record)
                context.insert(photoRecord)
                record.photos.append(photoRecord)
            }
            try context.save()
        }
    }

    func removePhoto(reportID: String, photoID: String) throws -> String? {
        guard let record = try record(id: reportID) else { throw PersistenceError.missingReport }
        guard record.photos.count > 1 else { throw PersistenceError.invalidPhotoCount }
        guard let photo = record.photos.first(where: { $0.id == photoID }) else { return nil }
        let removedPath = photo.relativePath
        let wasPrimary = photo.isPrimary
        try context.transaction {
            let removedObservations = record.plateObservations.filter { $0.photoID == photoID }
            for observation in removedObservations {
                record.plateObservations.removeAll { $0.id == observation.id }
                context.delete(observation)
            }
            record.photos.removeAll { $0.id == photoID }
            context.delete(photo)
            if wasPrimary { record.photos.first?.isPrimary = true }
            resetAfterPhotoChange(record)
            try context.save()
        }
        return removedPath
    }

    @discardableResult
    func mutateReport(id: String, _ transform: (Report) throws -> Report) throws -> Bool {
        guard let record = try record(id: id) else { throw PersistenceError.missingReport }
        let current = record.domain
        var updated = try transform(current)
        guard updated != current else { return false }
        updated.updatedAt = .now
        try context.transaction {
            record.apply(updated)
            try context.save()
        }
        return true
    }

    func mutateReports(_ transform: (Report) throws -> Report) throws {
        let records = try context.fetch(FetchDescriptor<ReportRecord>())
        try context.transaction {
            var changed = false
            for record in records {
                let current = record.domain
                var updated = try transform(current)
                guard updated != current else { continue }
                updated.updatedAt = .now
                record.apply(updated)
                changed = true
            }
            if changed { try context.save() }
        }
    }

    func applyRecognition(
        id: String,
        result: RecognitionResult,
        expectedFingerprint: String? = nil
    ) throws {
        guard let record = try record(id: id) else { throw PersistenceError.missingReport }
        let current = record.domain
        if let expectedFingerprint,
           LocalRecognitionState.fingerprint(for: current.photos) != expectedFingerprint
        {
            return
        }
        let rawObservations: [RecognitionPlateObservation]
        if !result.plateObservations.isEmpty {
            rawObservations = result.plateObservations
        } else if let photoID = result.photoID ?? current.primaryPhoto?.id {
            rawObservations = result.plateCandidates.map {
                RecognitionPlateObservation(photoID: photoID, value: $0)
            }
        } else {
            rawObservations = []
        }
        let targetPhotoIDs = Set(rawObservations.map(\.photoID) + [result.photoID].compactMap { $0 })
        let currentPhotoIDs = Set(current.photos.map(\.id))
        guard targetPhotoIDs.isSubset(of: currentPhotoIDs) else { return }
        let replacements = rawObservations.compactMap { observation -> PlateObservation? in
            guard let value = PlateCandidateParser.normalize(observation.value) else { return nil }
            return PlateObservation(
                id: UUID().uuidString,
                reportID: id,
                photoID: observation.photoID,
                source: result.source,
                value: value,
                bounds: observation.bounds,
                detectionConfidence: observation.detectionConfidence,
                characterConfidence: observation.characterConfidence,
                relativeArea: observation.relativeArea
            )
        }
        let mergedObservations = current.plateObservations.filter {
            $0.source != result.source || !targetPhotoIDs.contains($0.photoID)
        } + replacements
        var merged = RecognitionMerger.merge(
            report: current.withPlateObservations(mergedObservations),
            result: result
        )
        merged.updatedAt = .now

        try context.transaction {
            let staleObservations = record.plateObservations.filter {
                $0.sourceRaw == result.source.rawValue && targetPhotoIDs.contains($0.photoID)
            }
            for observation in staleObservations {
                record.plateObservations.removeAll { $0.id == observation.id }
                context.delete(observation)
            }
            for observation in replacements {
                let observationRecord = PlateObservationRecord(observation: observation, report: record)
                context.insert(observationRecord)
                record.plateObservations.append(observationRecord)
            }
            for photo in record.photos where targetPhotoIDs.contains(photo.id) {
                switch result.source {
                case .visionOCR:
                    photo.visionRecognitionComplete = true
                case .localPlateModel:
                    photo.plateRecognitionComplete = true
                }
            }
            record.apply(merged)
            try context.save()
        }
    }

    func markLocalRecognitionComplete(id: String, expectedFingerprint: String) throws {
        try mutateReport(id: id) { report in
            guard LocalRecognitionState.fingerprint(for: report.photos) == expectedFingerprint,
                  report.photos.allSatisfy(\.localRecognitionComplete)
            else { return report }
            var updated = report
            updated.localRecognitionFingerprint = expectedFingerprint
            return updated
        }
    }

    func upsertTemplate(_ template: CustomViolationTemplate) throws {
        let id = template.id
        var descriptor = FetchDescriptor<CustomViolationTemplateRecord>(predicate: #Predicate { $0.id == id })
        descriptor.fetchLimit = 1
        try context.transaction {
            if let existing = try context.fetch(descriptor).first {
                existing.displayName = template.displayName
                existing.estonianDescription = template.estonianDescription
                existing.updatedAt = template.updatedAt
            } else {
                context.insert(CustomViolationTemplateRecord(template: template))
            }
            try context.save()
        }
    }

    func deleteTemplate(id: String) throws {
        var descriptor = FetchDescriptor<CustomViolationTemplateRecord>(predicate: #Predicate { $0.id == id })
        descriptor.fetchLimit = 1
        guard let template = try context.fetch(descriptor).first else { return }
        let reports = try context.fetch(FetchDescriptor<ReportRecord>())
        try context.transaction {
            for report in reports where report.customTemplateID == id {
                report.violationTypeRaw = nil
                report.customTemplateID = nil
                report.subject = ""
                report.body = ""
                report.statusRaw = ReportStatus.draft.rawValue
                report.mailOpenedAt = nil
                report.updatedAt = .now
            }
            context.delete(template)
            try context.save()
        }
    }

    func deleteReport(id: String) throws {
        guard let record = try record(id: id) else { return }
        context.delete(record)
        try context.save()
    }

    func deleteAll() throws {
        let reports = try context.fetch(FetchDescriptor<ReportRecord>())
        let templates = try context.fetch(FetchDescriptor<CustomViolationTemplateRecord>())
        try context.transaction {
            for report in reports { context.delete(report) }
            for template in templates { context.delete(template) }
            try context.save()
        }
    }

    private func record(id: String) throws -> ReportRecord? {
        var descriptor = FetchDescriptor<ReportRecord>(predicate: #Predicate { $0.id == id })
        descriptor.fetchLimit = 1
        descriptor.relationshipKeyPathsForPrefetching = [\.photos, \.plateObservations]
        return try context.fetch(descriptor).first
    }

    private func resetAfterPhotoChange(_ record: ReportRecord) {
        var current = record.domain
        if !current.plateManuallyEdited {
            current.plate = aggregatePlateCandidates(current.plateObservations).first?.value ?? ""
        }
        record.apply(current)
        record.statusRaw = ReportStatus.draft.rawValue
        record.vehicleConfirmed = false
        record.mailOpenedAt = nil
        let photos = record.photos.map(\.domain)
        record.localRecognitionFingerprint = photos.allSatisfy(\.localRecognitionComplete)
            ? LocalRecognitionState.fingerprint(for: photos)
            : ""
        record.updatedAt = .now
    }
}

enum PersistenceError: Error, Equatable {
    case missingReport
    case wrongReport
    case invalidPhotoCount
}

private extension ReportRecord {
    var domain: Report {
        Report(
            id: id,
            createdAt: createdAt,
            updatedAt: updatedAt,
            occurredAt: occurredAt,
            status: ReportStatus(rawValue: statusRaw) ?? .draft,
            plate: plate,
            vehicleMake: vehicleMake,
            vehicleModel: vehicleModel,
            violationType: violationTypeRaw.flatMap(ViolationType.init(rawValue:)),
            customTemplateID: customTemplateID,
            recipient: recipient,
            address: address,
            latitude: latitude,
            longitude: longitude,
            accuracyMeters: accuracyMeters,
            locationNeedsReview: locationNeedsReview,
            subject: subject,
            body: body,
            plateManuallyEdited: plateManuallyEdited,
            vehicleManuallyEdited: vehicleManuallyEdited,
            vehicleConfirmed: vehicleConfirmed,
            locationConfirmed: locationConfirmed,
            plateObservations: plateObservations.map(\.domain),
            mailOpenedAt: mailOpenedAt,
            photos: photos.map(\.domain).sorted { $0.capturedAt < $1.capturedAt },
            localRecognitionFingerprint: localRecognitionFingerprint
        )
    }

    func apply(_ report: Report) {
        createdAt = report.createdAt
        updatedAt = report.updatedAt
        occurredAt = report.occurredAt
        statusRaw = report.status.rawValue
        plate = report.plate
        vehicleMake = report.vehicleMake
        vehicleModel = report.vehicleModel
        violationTypeRaw = report.violationType?.rawValue
        customTemplateID = report.customTemplateID
        recipient = report.recipient
        address = report.address
        latitude = report.latitude
        longitude = report.longitude
        accuracyMeters = report.accuracyMeters
        locationNeedsReview = report.locationNeedsReview
        subject = report.subject
        body = report.body
        plateManuallyEdited = report.plateManuallyEdited
        vehicleManuallyEdited = report.vehicleManuallyEdited
        vehicleConfirmed = report.vehicleConfirmed
        locationConfirmed = report.locationConfirmed
        mailOpenedAt = report.mailOpenedAt
        localRecognitionFingerprint = report.localRecognitionFingerprint
    }
}

private extension Report {
    func withPlateObservations(_ observations: [PlateObservation]) -> Report {
        var updated = self
        updated.plateObservations = observations
        return updated
    }
}
