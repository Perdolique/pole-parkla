import Foundation
import ImageIO
import SwiftData
import Testing
import UniformTypeIdentifiers
import UIKit
@testable import PoleParkla

@Suite("SwiftData contracts", .serialized)
@MainActor
struct PersistenceContractsTests {
    @Test func addingAPhotoPreservesCompletedEvidenceAndQueuesOnlyNewWork() throws {
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        let settings = AppSettings(profile: testProfile)
        try repository.createDraft(
            id: "report",
            photos: [testPhoto()],
            settings: settings,
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        try repository.applyRecognition(
            id: "report",
            result: RecognitionResult(source: .localPlateModel, plateCandidates: ["003 PUK"])
        )
        try repository.mutateReport(id: "report") { report in
            var updated = report
            updated.vehicleConfirmed = true
            updated.status = .ready
            updated.mailOpenedAt = Date(timeIntervalSince1970: 42)
            return updated
        }
        try repository.addPhotos(
            reportID: "report",
            photos: [testPhoto(id: "photo-2", primary: false)]
        )
        let updated = try #require(try repository.report(id: "report"))
        #expect(updated.photos.count == 2)
        #expect(updated.plateObservations.map(\.photoID) == ["photo-1"])
        #expect(updated.photos.first(where: { $0.id == "photo-1" })?.plateRecognitionComplete == true)
        #expect(updated.photos.first(where: { $0.id == "photo-2" })?.localRecognitionComplete == false)
        #expect(!updated.vehicleConfirmed)
        #expect(updated.status == .draft)
        #expect(updated.mailOpenedAt == nil)
        #expect(updated.localRecognitionFingerprint.isEmpty)
    }

    @Test func lastPhotoCannotBeRemovedButRemovingOneOfTwoKeepsAPrimary() throws {
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        try repository.createDraft(
            id: "report",
            photos: [testPhoto()],
            settings: AppSettings(),
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        #expect(throws: PersistenceError.invalidPhotoCount) {
            try repository.removePhoto(reportID: "report", photoID: "photo-1")
        }
        try repository.addPhotos(reportID: "report", photos: [testPhoto(id: "photo-2", primary: false)])
        #expect(try repository.removePhoto(reportID: "report", photoID: "photo-1") != nil)
        let report = try #require(try repository.report(id: "report"))
        #expect(report.photos.map(\.id) == ["photo-2"])
        #expect(report.photos.first?.isPrimary == true)
    }

    @Test func cascadeDeletionRemovesReportChildren() throws {
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        try repository.createDraft(
            id: "report",
            photos: [testPhoto()],
            settings: AppSettings(),
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        try repository.applyRecognition(
            id: "report",
            result: RecognitionResult(source: .visionOCR, plateCandidates: ["003 PUK"])
        )
        try repository.deleteReport(id: "report")
        #expect(try repository.reports().isEmpty)
        let context = persistence.container.mainContext
        #expect(try context.fetch(FetchDescriptor<PhotoRecord>()).isEmpty)
        #expect(try context.fetch(FetchDescriptor<PlateObservationRecord>()).isEmpty)
    }

    @Test func fullResetDeletesReportsAndCustomTemplates() throws {
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        try repository.createDraft(
            id: "report",
            photos: [testPhoto()],
            settings: AppSettings(),
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        try repository.upsertTemplate(CustomViolationTemplate(
            id: "template",
            displayName: "Blocked access",
            estonianDescription: "Sõiduk blokeerib läbipääsu.",
            createdAt: .now,
            updatedAt: .now
        ))
        try repository.deleteAll()
        #expect(try repository.reports().isEmpty)
        #expect(try repository.templates().isEmpty)
    }

    @Test func deletingTemplateReturnsReferencingReportsToDraftAtomically() throws {
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        let template = CustomViolationTemplate(
            id: "template",
            displayName: "Blocked access",
            estonianDescription: "Sõiduk blokeerib läbipääsu.",
            createdAt: .now,
            updatedAt: .now
        )
        try repository.upsertTemplate(template)
        try repository.createDraft(
            id: "report",
            photos: [testPhoto()],
            settings: AppSettings(profile: testProfile),
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        try repository.mutateReport(id: "report") { report in
            var updated = report
            updated.violationType = .custom
            updated.customTemplateID = template.id
            updated.subject = "Subject"
            updated.body = "Body"
            updated.status = .handedOffToMail
            updated.mailOpenedAt = .now
            return updated
        }

        try repository.deleteTemplate(id: template.id)

        let report = try #require(try repository.report(id: "report"))
        #expect(try repository.templates().isEmpty)
        #expect(report.violationType == nil)
        #expect(report.customTemplateID == nil)
        #expect(report.subject.isEmpty)
        #expect(report.body.isEmpty)
        #expect(report.status == .draft)
        #expect(report.mailOpenedAt == nil)
    }

    @Test func unchangedProfileSavePreservesMailHandoffAndChangedProfileRefreshesOnce() async throws {
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        let suiteName = "PoleParklaTests-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaAppModel-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let settings = try makeTestSettingsStore(defaults: defaults, root: root)
        try settings.update { $0.profile = testProfile }
        let photoStore = try PhotoStore(rootURL: root)
        try repository.createDraft(
            id: "report",
            photos: [testPhoto()],
            settings: settings.value,
            location: nil,
            occurredAt: .now,
            locationNeedsReview: false
        )
        try repository.mutateReport(id: "report") { report in
            var updated = testReport()
            updated.id = report.id
            updated.status = .handedOffToMail
            updated.mailOpenedAt = Date(timeIntervalSince1970: 42)
            return updated
        }
        let model = AppModel(
            repository: repository,
            settings: settings,
            photoStore: photoStore,
            recognition: nil,
            mapCacheReset: { true }
        )

        model.saveProfile(testProfile)
        var report = try #require(try repository.report(id: "report"))
        #expect(report.status == .handedOffToMail)
        #expect(report.mailOpenedAt == Date(timeIntervalSince1970: 42))

        model.saveProfile(ReporterProfile(name: "New Name", phone: testProfile.phone))
        report = try #require(try repository.report(id: "report"))
        #expect(report.status == .ready)
        #expect(report.mailOpenedAt == nil)
        #expect(report.body.contains("New Name"))
    }

    @Test func confirmingAnUneditedRecognizedPlateCompletesTheVehicleStep() throws {
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaRecognizedPlate-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let suiteName = "PoleParklaRecognizedPlate-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let settings = try makeTestSettingsStore(defaults: defaults, root: root)
        try repository.createDraft(
            id: "report",
            photos: [testPhoto()],
            settings: settings.value,
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        try repository.applyRecognition(
            id: "report",
            result: RecognitionResult(source: .localPlateModel, plateCandidates: ["003 PUK"])
        )
        let recognized = try #require(try repository.report(id: "report"))
        #expect(recognized.plate == "003 PUK")
        #expect(!recognized.plateManuallyEdited)
        #expect(!recognized.vehicleManuallyEdited)
        #expect(!recognized.vehicleConfirmed)

        let model = AppModel(
            repository: repository,
            settings: settings,
            photoStore: try PhotoStore(rootURL: root),
            recognition: nil,
            mapCacheReset: { true }
        )
        model.selectedReportID = "report"

        #expect(model.saveVehicle(plate: "003 PUK", make: "", model: ""))
        let confirmed = try #require(model.selectedReport)
        #expect(confirmed.plate == "003 PUK")
        #expect(confirmed.plateManuallyEdited)
        #expect(confirmed.vehicleManuallyEdited)
        #expect(confirmed.vehicleConfirmed)
        #expect(confirmed.resolvedWizardStep == .place)
    }

    @Test func noOpEditorSavesPreserveMailHandoff() throws {
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaNoOpSave-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let suiteName = "PoleParklaNoOpSave-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let settings = try makeTestSettingsStore(defaults: defaults, root: root)
        try settings.update { $0.profile = testProfile }
        try repository.createDraft(
            id: "report",
            photos: [testPhoto()],
            settings: settings.value,
            location: nil,
            occurredAt: .now,
            locationNeedsReview: false
        )
        let handedOffAt = Date(timeIntervalSince1970: 42)
        try repository.mutateReport(id: "report") { record in
            var report = testReport()
            report.id = record.id
            report.status = .handedOffToMail
            report.mailOpenedAt = handedOffAt
            return report
        }
        let model = AppModel(
            repository: repository,
            settings: settings,
            photoStore: try PhotoStore(rootURL: root),
            recognition: nil,
            mapCacheReset: { true }
        )
        model.selectedReportID = "report"

        #expect(model.saveVehicle(plate: "003 PUK", make: "Toyota", model: "Corolla"))
        #expect(model.confirmLocation(AddressDraft(
            address: "Lastekodu tn 42, Tallinn",
            latitude: 59.437,
            longitude: 24.7536,
            accuracyMeters: 8.8,
            occurredAt: Date(timeIntervalSince1970: 1_700_000_000)
        )))
        model.chooseViolation(.cyclePath)
        #expect(model.saveReportRecipient("mupo@example.com"))

        let report = try #require(try repository.report(id: "report"))
        #expect(report.status == .handedOffToMail)
        #expect(report.mailOpenedAt == handedOffAt)
    }

    @Test func appResetPreservesTheLiveStoreAndItRemainsWritable() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaReset-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let persistence = try PersistenceController.persistent(at: root.appendingPathComponent("pole-parkla.store"))
        let repository = ReportRepository(container: persistence.container)
        let suiteName = "PoleParklaReset-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let settings = try makeTestSettingsStore(defaults: defaults, root: root)
        let photoStore = try PhotoStore(rootURL: root)
        try repository.createDraft(
            id: "before",
            photos: [testPhoto(reportID: "before")],
            settings: settings.value,
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        let model = AppModel(
            repository: repository,
            settings: settings,
            photoStore: photoStore,
            recognition: nil,
            mapCacheReset: { true }
        )

        await model.deleteAllData()
        #expect(try repository.reports().isEmpty)
        #expect(FileManager.default.fileExists(atPath: root.appendingPathComponent("pole-parkla.store").path))

        try repository.createDraft(
            id: "after",
            photos: [testPhoto(reportID: "after")],
            settings: settings.value,
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        #expect(try repository.report(id: "after") != nil)
    }

    @Test func failedDataResetDoesNotLookSuccessfulAndCanBeRetried() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaResetRetry-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        let suiteName = "PoleParklaResetRetry-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let settings = try makeTestSettingsStore(defaults: defaults, root: root)
        try settings.update {
            $0.onboardingComplete = true
            $0.profile = testProfile
            $0.defaultRecipient = "recipient@example.com"
        }
        try repository.createDraft(
            id: "report",
            photos: [testPhoto()],
            settings: settings.value,
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        let mapReset = MapResetGate()
        let model = AppModel(
            repository: repository,
            settings: settings,
            photoStore: try PhotoStore(rootURL: root),
            recognition: nil,
            mapCacheReset: { mapReset.allowsReset }
        )

        #expect(!(await model.deleteAllData()))
        #expect(model.settings.value.onboardingComplete)
        #expect(model.notice != nil)

        mapReset.allowsReset = true
        #expect(await model.deleteAllData())
        #expect(model.settings.value == AppSettings())
        #expect(model.reports.isEmpty)
    }

    @Test func pendingRecognitionResumesAfterRelaunchWithoutRepeatingCompletedWork() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaRecognitionResume-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        let photoStore = try PhotoStore(rootURL: root)
        let imported = try await photoStore.importPhoto(data: testPNGData(), reportID: "report")
        let photo = ReportPhoto(
            id: "photo",
            reportID: "report",
            relativePath: imported.relativePath,
            source: .camera,
            capturedAt: imported.capturedAt,
            isPrimary: true
        )
        try repository.createDraft(
            id: "report",
            photos: [photo],
            settings: AppSettings(),
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        let fingerprint = LocalRecognitionState.fingerprint(for: [photo])
        try repository.applyRecognition(
            id: "report",
            result: RecognitionResult(source: .visionOCR, photoID: photo.id),
            expectedFingerprint: fingerprint
        )
        let vision = CountingRecognizer(result: .failure(.failed))
        let plates = CountingRecognizer(result: .success(RecognitionResult(
            source: .localPlateModel,
            photoID: photo.id,
            plateCandidates: ["003 PUK"]
        )))
        let suiteName = "PoleParklaRecognitionResume-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let model = AppModel(
            repository: repository,
            settings: try makeTestSettingsStore(defaults: defaults, root: root),
            photoStore: photoStore,
            recognition: RecognitionCoordinator(photoStore: photoStore, vision: vision, plates: plates),
            mapCacheReset: { true }
        )

        await model.resumePendingLocalRecognition()

        let resumed = try #require(try repository.report(id: "report"))
        #expect(await vision.callCount == 0)
        #expect(await plates.callCount == 1)
        #expect(resumed.photos.allSatisfy { $0.localRecognitionComplete })
        #expect(resumed.localRecognitionFingerprint == fingerprint)
    }

    @Test func historyLoadsInBoundedPages() throws {
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        for index in 0 ..< 41 {
            let id = "report-\(index)"
            try repository.createDraft(
                id: id,
                photos: [testPhoto(id: "photo-\(index)", reportID: id)],
                settings: AppSettings(),
                location: nil,
                occurredAt: Date(timeIntervalSince1970: TimeInterval(index)),
                locationNeedsReview: true
            )
        }
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaHistoryPaging-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let suiteName = "PoleParklaHistoryPaging-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let model = AppModel(
            repository: repository,
            settings: try makeTestSettingsStore(defaults: defaults, root: root),
            photoStore: try PhotoStore(rootURL: root),
            recognition: nil,
            mapCacheReset: { true }
        )

        #expect(model.reports.count == 40)
        #expect(model.canLoadMoreReports)
        model.loadMoreReports(after: try #require(model.reports.last?.id))
        #expect(model.reports.count == 41)
        #expect(!model.canLoadMoreReports)
    }

    @Test func productionShareReturnChangesStatusOnlyAfterConfirmationAndCleansCopies() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaShareReturn-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        let photoStore = try PhotoStore(rootURL: root)
        let imported = try await photoStore.importPhoto(data: testPNGData(), reportID: "report")
        let photo = ReportPhoto(
            id: "photo",
            reportID: "report",
            relativePath: imported.relativePath,
            source: .camera,
            capturedAt: imported.capturedAt,
            isPrimary: true
        )
        let suiteName = "PoleParklaShareReturn-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let settings = try makeTestSettingsStore(defaults: defaults, root: root)
        try settings.update { $0.profile = testProfile }
        try repository.createDraft(
            id: "report",
            photos: [photo],
            settings: settings.value,
            location: nil,
            occurredAt: Date(timeIntervalSince1970: 1_700_000_000),
            locationNeedsReview: true
        )
        let model = AppModel(
            repository: repository,
            settings: settings,
            photoStore: photoStore,
            recognition: nil,
            canSendMail: { false },
            mapCacheReset: { true }
        )
        model.selectedReportID = "report"
        #expect(model.saveVehicle(plate: "003 PUK", make: "Toyota", model: "Corolla"))
        #expect(model.confirmLocation(AddressDraft(
            address: "Lastekodu tn 42, Tallinn",
            latitude: 59.437,
            longitude: 24.7536,
            accuracyMeters: nil,
            occurredAt: Date(timeIntervalSince1970: 1_700_000_000)
        )))
        model.chooseViolation(.cyclePath)

        await model.prepareMail()
        let firstAttachment = try #require(model.mailPayload?.attachments.first)
        #expect(FileManager.default.fileExists(atPath: firstAttachment.path))
        #expect(model.presentingShareSheet)
        model.shareSheetReturned()
        #expect(model.asksShareHandoffConfirmation)
        #expect(try repository.report(id: "report")?.status == .ready)

        await model.confirmShareHandoff(false)
        #expect(try repository.report(id: "report")?.status == .ready)
        #expect(!FileManager.default.fileExists(atPath: firstAttachment.path))
        #expect(model.mailPayload == nil)

        await model.prepareMail()
        model.shareSheetReturned()
        await model.confirmShareHandoff(true)
        let handedOff = try #require(try repository.report(id: "report"))
        #expect(handedOff.status == .handedOffToMail)
        #expect(handedOff.mailOpenedAt != nil)
        #expect(model.mailPayload == nil)
    }

    @Test func recognitionReplacesOnlyTheMatchingSourceAndPhoto() throws {
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        let photos = [testPhoto(id: "photo-1"), testPhoto(id: "photo-2", primary: false)]
        try repository.createDraft(
            id: "report",
            photos: photos,
            settings: AppSettings(),
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        let fingerprint = LocalRecognitionState.fingerprint(for: photos)
        try repository.applyRecognition(
            id: "report",
            result: RecognitionResult(source: .visionOCR, photoID: "photo-1", plateCandidates: ["003 PUK"]),
            expectedFingerprint: fingerprint
        )
        try repository.applyRecognition(
            id: "report",
            result: RecognitionResult(source: .visionOCR, photoID: "photo-2", plateCandidates: ["123 ABC"]),
            expectedFingerprint: fingerprint
        )
        var updated = try #require(try repository.report(id: "report"))
        #expect(Set(updated.plateObservations.map(\.photoID)) == ["photo-1", "photo-2"])

        try repository.applyRecognition(
            id: "report",
            result: RecognitionResult(source: .visionOCR, photoID: "photo-2"),
            expectedFingerprint: fingerprint
        )
        updated = try #require(try repository.report(id: "report"))
        #expect(updated.plateObservations.map(\.photoID) == ["photo-1"])
    }

    @Test func staleRecognitionCannotAttachToAChangedPhotoSet() throws {
        let persistence = try PersistenceController.inMemory()
        let repository = ReportRepository(container: persistence.container)
        let originalPhoto = testPhoto(id: "photo-1")
        try repository.createDraft(
            id: "report",
            photos: [originalPhoto],
            settings: AppSettings(),
            location: nil,
            occurredAt: .now,
            locationNeedsReview: true
        )
        let staleFingerprint = LocalRecognitionState.fingerprint(for: [originalPhoto])
        try repository.addPhotos(reportID: "report", photos: [testPhoto(id: "photo-2", primary: false)])
        try repository.applyRecognition(
            id: "report",
            result: RecognitionResult(source: .localPlateModel, photoID: "photo-1", plateCandidates: ["003 PUK"]),
            expectedFingerprint: staleFingerprint
        )
        #expect(try repository.report(id: "report")?.plateObservations.isEmpty == true)
    }

    @Test func persistentStoreSurvivesContainerRecreation() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("Pole Parkla Persistence-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let storeURL = directory.appendingPathComponent("pole-parkla.store")

        do {
            let persistence = try PersistenceController.persistent(at: storeURL)
            let repository = ReportRepository(container: persistence.container)
            try repository.createDraft(
                id: "report",
                photos: [testPhoto()],
                settings: AppSettings(),
                location: nil,
                occurredAt: Date(timeIntervalSince1970: 42),
                locationNeedsReview: true
            )
        }

        let reopened = try PersistenceController.persistent(at: storeURL)
        let reopenedRepository = ReportRepository(container: reopened.container)
        let report = try #require(try reopenedRepository.report(id: "report"))
        #expect(report.photos.map(\.id) == ["photo-1"])
        #expect(report.occurredAt == Date(timeIntervalSince1970: 42))
        let resources = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        #expect(resources.isExcludedFromBackup == true)
        let attributes = try FileManager.default.attributesOfItem(atPath: directory.path)
        #expect(attributes[.protectionKey] as? FileProtectionType == .complete)
    }

    @Test func cameraSessionKeepsOneDraftUntilExplicitReviewAndFreesSlots() async throws {
        let fixture = try makeAppModelFixture()
        defer { fixture.cleanup() }
        let model = fixture.model
        let reportID = model.cameraReportID
        let image = try testPNGData()

        #expect(await model.importCameraPhoto(data: image, fallbackLocation: nil))
        #expect(model.cameraReportID == reportID)
        #expect(model.cameraReport?.photos.count == 1)
        #expect(model.selectedReportID == nil)

        #expect(await model.importCameraPhoto(data: image, fallbackLocation: nil))
        #expect(await model.importCameraPhoto(data: image, fallbackLocation: nil))
        #expect(model.cameraReport?.photos.count == maximumReportPhotos)
        #expect(!(await model.importCameraPhoto(data: image, fallbackLocation: nil)))
        #expect(model.selectedReportID == nil)

        model.openCameraDraftForReview()
        #expect(model.selectedReportID == reportID)
        model.continueCameraSession(reportID: reportID)
        #expect(model.cameraReportID == reportID)
        #expect(model.selectedReportID == nil)

        let photo = try #require(model.cameraReport?.photos.first)
        #expect(await model.removePhoto(photo))
        #expect(model.cameraReport?.photos.count == 2)
        #expect(await model.importGalleryPhotos(dataItems: [image], fallbackLocation: nil))
        #expect(model.cameraReport?.photos.count == maximumReportPhotos)
    }

    @Test func galleryPrefersExifLocationAndFallsBackToFreshForegroundLocation() async throws {
        let fixture = try makeAppModelFixture()
        defer { fixture.cleanup() }
        let model = fixture.model
        let foreground = LocationSnapshot(
            latitude: 58.0,
            longitude: 23.0,
            accuracyMeters: 12,
            capturedAt: Date(timeIntervalSince1970: 1_800_000_000)
        )

        #expect(await model.importGalleryPhotos(
            dataItems: [try testJPEGWithMetadata()],
            fallbackLocation: foreground
        ))
        #expect(model.cameraReport?.latitude == 59.437)
        #expect(model.cameraReport?.longitude == 24.7536)

        model.startNewCameraSession()
        #expect(await model.importGalleryPhotos(
            dataItems: [try testPNGData()],
            fallbackLocation: foreground
        ))
        #expect(model.cameraReport?.latitude == foreground.latitude)
        #expect(model.cameraReport?.longitude == foreground.longitude)
        #expect(model.cameraReport?.accuracyMeters == foreground.accuracyMeters)
        #expect(model.cameraReport?.locationNeedsReview == true)
    }

    @Test func onboardingCompletionSavesAllFieldsTogether() throws {
        let fixture = try makeAppModelFixture()
        defer { fixture.cleanup() }
        let model = fixture.model
        let original = model.settings.value

        #expect(!model.completeOnboarding(
            languageTag: "et",
            profile: ReporterProfile(name: "Reporter", phone: ""),
            recipient: "recipient@example.com"
        ))
        #expect(model.settings.value == original)

        #expect(model.completeOnboarding(
            languageTag: "et",
            profile: ReporterProfile(name: " Reporter ", phone: " +3725555555 "),
            recipient: " recipient@example.com "
        ))
        #expect(model.settings.value.onboardingComplete)
        #expect(model.settings.value.languageTag == "et")
        #expect(model.settings.value.profile == ReporterProfile(name: "Reporter", phone: "+3725555555"))
        #expect(model.settings.value.defaultRecipient == "recipient@example.com")
        #expect(model.selectedTab == .camera)
        #expect(model.selectedReportID == nil)
    }

    @Test func legacySettingsMigrateSensitiveValuesOutOfUserDefaults() throws {
        let suiteName = "PoleParklaLegacySettings-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaLegacySettings-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let legacy: [String: Any] = [
            "onboardingComplete": true,
            "languageTag": "et",
            "profile": ["name": "Reporter", "phone": "+3725555555"],
            "defaultRecipient": "recipient@example.com",
        ]
        defaults.set(try JSONSerialization.data(withJSONObject: legacy), forKey: "pole-parkla.settings.v1")

        let store = try makeTestSettingsStore(defaults: defaults, root: root)

        #expect(store.value.onboardingComplete)
        #expect(store.value.languageTag == "et")
        #expect(store.value.profile == ReporterProfile(name: "Reporter", phone: "+3725555555"))
        #expect(store.value.defaultRecipient == "recipient@example.com")
        #expect(defaults.data(forKey: "pole-parkla.settings.v1") == nil)
        let persisted = try #require(defaults.data(forKey: "pole-parkla.settings.public.v2"))
        let object = try #require(JSONSerialization.jsonObject(with: persisted) as? [String: Any])
        #expect(object["onboardingComplete"] as? Bool == true)
        #expect(object["languageTag"] as? String == "et")
        #expect(object["profile"] == nil)
        #expect(object["defaultRecipient"] == nil)

        let sensitiveURL = root.appendingPathComponent("Settings.json")
        let sensitiveData = try Data(contentsOf: sensitiveURL)
        let sensitiveJSON = try #require(JSONSerialization.jsonObject(with: sensitiveData) as? [String: Any])
        #expect((sensitiveJSON["profile"] as? [String: Any])?["name"] as? String == "Reporter")
        #expect(sensitiveJSON["defaultRecipient"] as? String == "recipient@example.com")
        let resources = try sensitiveURL.resourceValues(forKeys: [.isExcludedFromBackupKey])
        #expect(resources.isExcludedFromBackup == true)
        let attributes = try FileManager.default.attributesOfItem(atPath: sensitiveURL.path)
        #expect(attributes[.protectionKey] as? FileProtectionType == .complete)

        let reopened = try makeTestSettingsStore(defaults: defaults, root: root)
        #expect(reopened.value == store.value)
    }

    private func makeAppModelFixture() throws -> AppModelFixture {
        let persistence = try PersistenceController.inMemory()
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaSession-\(UUID().uuidString)", isDirectory: true)
        let suiteName = "PoleParklaSession-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        let model = AppModel(
            repository: ReportRepository(container: persistence.container),
            settings: try makeTestSettingsStore(defaults: defaults, root: root),
            photoStore: try PhotoStore(rootURL: root),
            addressService: StaticAddressService(),
            recognition: nil,
            mapCacheReset: { true }
        )
        return AppModelFixture(model: model, root: root, suiteName: suiteName)
    }

    private func testPNGData() throws -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 48))
        let image = renderer.image { context in
            UIColor.darkGray.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 64, height: 48))
        }
        return try #require(image.pngData())
    }

    private func testJPEGWithMetadata() throws -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 48))
        let image = renderer.image { context in
            UIColor.darkGray.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 64, height: 48))
        }
        let mutable = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(
            mutable,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ))
        CGImageDestinationAddImage(destination, try #require(image.cgImage), [
            kCGImagePropertyGPSDictionary: [
                kCGImagePropertyGPSLatitude: 59.437,
                kCGImagePropertyGPSLatitudeRef: "N",
                kCGImagePropertyGPSLongitude: 24.7536,
                kCGImagePropertyGPSLongitudeRef: "E",
            ],
            kCGImagePropertyExifDictionary: [
                kCGImagePropertyExifDateTimeOriginal: "2026:08:13 16:58:00",
            ],
        ] as CFDictionary)
        #expect(CGImageDestinationFinalize(destination))
        return mutable as Data
    }
}

@MainActor
private struct AppModelFixture {
    let model: AppModel
    let root: URL
    let suiteName: String

    func cleanup() {
        UserDefaults(suiteName: suiteName)?.removePersistentDomain(forName: suiteName)
        try? FileManager.default.removeItem(at: root)
    }
}

private struct StaticAddressService: AddressResolving {
    func resolve(_ location: LocationSnapshot) async throws -> AddressResolution {
        let candidate = AddressCandidate(address: "Test address", distanceMeters: 0, type: .building)
        return AddressResolution(location: location, candidates: [candidate], suggested: candidate, needsReview: false)
    }
}

@MainActor
private final class MapResetGate {
    var allowsReset = false
}
