import Foundation
@preconcurrency import MapLibre
import MessageUI
import Observation

@MainActor
@Observable
final class AppModel {
    private let repository: ReportRepository
    let settings: SettingsStore
    let photoStore: PhotoStore
    let locationService = ForegroundLocationService()
    private let addressService: any AddressResolving
    private let recognition: RecognitionCoordinator?
    private let renderer = EmailTemplateRenderer()
    private let canSendMail: @MainActor () -> Bool
    private let mapCacheReset: @MainActor () async -> Bool
    private static let reportPageSize = 40

    var reports: [Report] = []
    var templates: [CustomViolationTemplate] = []
    var selectedTab = AppTab.camera
    var cameraReportID = UUID().uuidString {
        didSet {
            if oldValue != cameraReportID {
                cancelAddressLookup()
            }
        }
    }
    var selectedReportID: String? {
        didSet {
            if oldValue != selectedReportID {
                cancelAddressLookup()
            }
        }
    }
    private var activeWorkCount = 0
    var isWorking: Bool { activeWorkCount > 0 || isDeletingAllData || isPreparingMail }
    var notice: String?
    var mailPayload: MailDraftPayload?
    var presentingMailComposer = false
    var presentingShareSheet = false
    var asksShareHandoffConfirmation = false
    var pendingShareReportID: String?
    private(set) var isPreparingMail = false
    private var addressLookupToken: UUID?
    private var addressLookupTask: Task<AddressResolution, Error>?
    private(set) var dataGeneration = 0
    private var isDeletingAllData = false
    private var loadedReportLimit = reportPageSize
    private(set) var canLoadMoreReports = false

    private enum MutationOutcome: Equatable {
        case changed
        case unchanged
        case failed

        var succeeded: Bool { self != .failed }
    }

    init(
        repository: ReportRepository,
        settings: SettingsStore,
        photoStore: PhotoStore,
        addressService: any AddressResolving = InAksAddressService(),
        recognition: RecognitionCoordinator?,
        canSendMail: @escaping @MainActor () -> Bool = { MFMailComposeViewController.canSendMail() },
        mapCacheReset: @escaping @MainActor () async -> Bool = AppModel.resetMapLibreCache
    ) {
        self.repository = repository
        self.settings = settings
        self.photoStore = photoStore
        self.addressService = addressService
        self.recognition = recognition
        self.canSendMail = canSendMail
        self.mapCacheReset = mapCacheReset
        reload()
        if settings.value.onboardingComplete {
            selectedReportID = reports.first(where: { !$0.photos.isEmpty })?.id
        }
    }

    var selectedReport: Report? {
        guard let selectedReportID else { return nil }
        return reports.first { $0.id == selectedReportID }
    }

    var cameraReport: Report? {
        reports.first { $0.id == cameraReportID }
    }

    func reload() {
        do {
            reports = try repository.reports(limit: loadedReportLimit)
            canLoadMoreReports = reports.count < (try repository.reportCount())
            templates = try repository.templates()
        } catch {
            notice = localized("error.storage")
        }
    }

    func loadMoreReports(after reportID: String) {
        guard canLoadMoreReports, reports.last?.id == reportID else { return }
        loadedReportLimit += Self.reportPageSize
        reload()
    }

    func startNewCameraSession() {
        cancelAddressLookup()
        cameraReportID = UUID().uuidString
        selectedReportID = nil
        selectedTab = .camera
    }

    func continueCameraSession(reportID: String) {
        cancelAddressLookup()
        cameraReportID = reportID
        selectedReportID = nil
        selectedTab = .camera
    }

    func openCameraDraftForReview() {
        guard let report = cameraReport, !report.photos.isEmpty else { return }
        selectedReportID = report.id
    }

    func completeOnboarding(languageTag: String, profile: ReporterProfile, recipient: String) -> Bool {
        let normalizedProfile = ReporterProfile(
            name: profile.name.trimmingCharacters(in: .whitespacesAndNewlines),
            phone: profile.phone.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        let normalizedRecipient = recipient.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedProfile.name.isEmpty, !normalizedProfile.phone.isEmpty, !normalizedRecipient.isEmpty else {
            return false
        }
        do {
            try settings.update { value in
                value.languageTag = languageTag
                value.profile = normalizedProfile
                value.defaultRecipient = normalizedRecipient
                value.onboardingComplete = true
            }
        } catch {
            notice = localized("error.storage")
            return false
        }
        startNewCameraSession()
        return true
    }

    @discardableResult
    func importCameraPhoto(
        data: Data,
        fallbackLocation: LocationSnapshot?,
        expectedGeneration: Int? = nil
    ) async -> Bool {
        await importPhotos(
            dataItems: [data],
            source: .camera,
            reportID: cameraReportID,
            fallbackLocation: fallbackLocation,
            expectedGeneration: expectedGeneration
        )
    }

    @discardableResult
    func importGalleryPhotos(
        dataItems: [Data],
        fallbackLocation: LocationSnapshot?,
        expectedGeneration: Int? = nil
    ) async -> Bool {
        await importPhotos(
            dataItems: dataItems,
            source: .gallery,
            reportID: cameraReportID,
            fallbackLocation: fallbackLocation,
            expectedGeneration: expectedGeneration
        )
    }

    private func importPhotos(
        dataItems: [Data],
        source: PhotoSource,
        reportID: String,
        fallbackLocation: LocationSnapshot?,
        fileExtension: String = "jpg",
        expectedGeneration: Int? = nil
    ) async -> Bool {
        let generation = expectedGeneration ?? dataGeneration
        let existing = reports.first { $0.id == reportID }
        let existingCount = existing?.photos.count ?? 0
        guard existingCount < maximumReportPhotos else {
            notice = localized("camera.limit.replace")
            return false
        }
        guard !isWorking, !isDeletingAllData, generation == dataGeneration, !dataItems.isEmpty,
              existingCount + dataItems.count <= maximumReportPhotos
        else {
            if existingCount + dataItems.count > maximumReportPhotos {
                notice = localized("camera.limit.replace")
            } else if isWorking {
                notice = localized("photo.busy")
            }
            return false
        }
        activeWorkCount += 1
        defer { activeWorkCount -= 1 }
        var importedItems: [(photo: ReportPhoto, metadata: ImportedPhoto)] = []
        do {
            for (index, data) in dataItems.enumerated() {
                guard generation == dataGeneration, !isDeletingAllData else {
                    for item in importedItems { try? await photoStore.delete(item.photo) }
                    return false
                }
                let imported = try await photoStore.importPhoto(
                    data: data,
                    reportID: reportID,
                    preferredExtension: fileExtension
                )
                importedItems.append((
                    ReportPhoto(
                        id: UUID().uuidString,
                        reportID: reportID,
                        relativePath: imported.relativePath,
                        source: source,
                        capturedAt: imported.capturedAt,
                        isPrimary: existingCount == 0 && index == 0
                    ),
                    imported
                ))
            }
            guard generation == dataGeneration, !isDeletingAllData else {
                for item in importedItems { try? await photoStore.delete(item.photo) }
                return false
            }
            if existing == nil, let first = importedItems.first {
                let exifLocation: LocationSnapshot? = if let latitude = first.metadata.latitude,
                                                          let longitude = first.metadata.longitude
                {
                    LocationSnapshot(
                        latitude: latitude,
                        longitude: longitude,
                        accuracyMeters: nil,
                        capturedAt: first.metadata.capturedAt
                    )
                } else {
                    fallbackLocation.map {
                        LocationSnapshot(
                            latitude: $0.latitude,
                            longitude: $0.longitude,
                            accuracyMeters: $0.accuracyMeters,
                            capturedAt: first.metadata.capturedAt
                        )
                    }
                }
                let needsReview = source == .gallery
                    ? (!first.metadata.hasLocationMetadata || !first.metadata.hasCapturedAtMetadata)
                    : exifLocation == nil
                try repository.createDraft(
                    id: reportID,
                    photos: importedItems.map(\.photo),
                    settings: settings.value,
                    location: exifLocation,
                    occurredAt: first.metadata.capturedAt,
                    locationNeedsReview: needsReview
                )
                reload()
                if let exifLocation {
                    let resolution = await resolveAddress(reportID: reportID, location: exifLocation)
                    setLocationNeedsReview(
                        reportID: reportID,
                        needsReview || resolution == nil || resolution?.needsReview == true
                    )
                }
            } else {
                try repository.addPhotos(reportID: reportID, photos: importedItems.map(\.photo))
                reload()
            }
            await recognizeLocally(reportID: reportID)
            return true
        } catch {
            for item in importedItems { try? await photoStore.delete(item.photo) }
            notice = localized("error.photo.import")
            return false
        }
    }

    @discardableResult
    func removePhoto(_ photo: ReportPhoto) async -> Bool {
        guard !isWorking, let report = reports.first(where: { $0.id == photo.reportID }) else { return false }
        activeWorkCount += 1
        defer { activeWorkCount -= 1 }
        do {
            if report.photos.count == 1 {
                try repository.deleteReport(id: report.id)
                if selectedReportID == report.id {
                    cameraReportID = report.id
                    selectedReportID = nil
                    selectedTab = .camera
                }
                reload()
                do {
                    try await photoStore.deleteReportDirectory(reportID: report.id)
                } catch {
                    try? await photoStore.reconcile(references: repository.photoReferences())
                    notice = localized("error.storage")
                }
                return true
            }
            guard try repository.removePhoto(reportID: report.id, photoID: photo.id) != nil else { return false }
            reload()
            do {
                try await photoStore.delete(photo)
            } catch {
                try? await photoStore.reconcile(references: repository.photoReferences())
                notice = localized("error.storage")
            }
            await recognizeLocally(reportID: report.id)
            return true
        } catch {
            notice = localized("error.storage")
            return false
        }
    }

    private func setLocationNeedsReview(reportID: String, _ needsReview: Bool) {
        _ = mutate(id: reportID) { report in
            var updated = report
            updated.locationNeedsReview = needsReview
            return updated
        }
    }

    @discardableResult
    func saveVehicle(plate: String, make: String, model: String) -> Bool {
        guard let id = selectedReportID else { return false }
        let normalizedPlate = PlateCandidateParser.normalize(plate)
            ?? plate.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let normalizedMake = make.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedModel = model.trimmingCharacters(in: .whitespacesAndNewlines)
        let outcome = mutate(id: id) { report in
            guard report.plate != normalizedPlate ||
                report.vehicleMake != normalizedMake ||
                report.vehicleModel != normalizedModel ||
                !report.vehicleConfirmed
            else { return report }
            var updated = report
            updated.plate = normalizedPlate
            updated.vehicleMake = normalizedMake
            updated.vehicleModel = normalizedModel
            updated.plateManuallyEdited = true
            updated.vehicleManuallyEdited = true
            updated.vehicleConfirmed = !updated.plate.isEmpty
            return updated
        }
        guard outcome.succeeded else { return false }
        if outcome == .changed { refreshLetterAndReadiness(id: id) }
        return true
    }

    @discardableResult
    func confirmLocation(_ draft: AddressDraft) -> Bool {
        let latitude = draft.latitude.map { String($0) } ?? ""
        let longitude = draft.longitude.map { String($0) } ?? ""
        return confirmLocation(
            address: draft.address,
            latitude: latitude,
            longitude: longitude,
            accuracyMeters: draft.accuracyMeters,
            occurredAt: draft.occurredAt
        )
    }

    @discardableResult
    func confirmLocation(
        address: String,
        latitude: String,
        longitude: String,
        accuracyMeters: Double?,
        occurredAt: Date
    ) -> Bool {
        guard let id = selectedReportID else { return false }
        let draft: AddressDraft
        do {
            draft = try ReportLocationInput.validate(
                address: address,
                latitude: latitude,
                longitude: longitude,
                accuracyMeters: accuracyMeters,
                occurredAt: occurredAt
            )
        } catch ReportRuleError.invalidCoordinates {
            notice = localized("place.invalid.coordinates")
            return false
        } catch {
            notice = localized("place.missing.location")
            return false
        }
        let outcome = mutate(id: id) { report in
            guard report.address != draft.address ||
                report.latitude != draft.latitude ||
                report.longitude != draft.longitude ||
                report.accuracyMeters != draft.accuracyMeters ||
                report.occurredAt != draft.occurredAt ||
                report.locationNeedsReview ||
                !report.locationConfirmed
            else { return report }
            var updated = report
            updated.address = draft.address.trimmingCharacters(in: .whitespacesAndNewlines)
            updated.latitude = draft.latitude
            updated.longitude = draft.longitude
            updated.accuracyMeters = draft.accuracyMeters
            updated.occurredAt = draft.occurredAt
            updated.locationNeedsReview = false
            updated.locationConfirmed = updated.hasValidLocation
            return updated
        }
        guard outcome.succeeded else { return false }
        if outcome == .changed { refreshLetterAndReadiness(id: id) }
        return true
    }

    func useCurrentLocation() async -> AddressLookupResult? {
        do {
            let location = try await locationService.requestLocation()
            let resolution: AddressResolution? = if let id = selectedReportID {
                await resolveAddress(reportID: id, location: location)
            } else {
                nil
            }
            return AddressLookupResult(location: location, resolution: resolution)
        } catch {
            notice = localized("location.unavailable")
            return nil
        }
    }

    func metadata(for photo: ReportPhoto) async -> PhotoMetadata? {
        try? await photoStore.metadata(for: photo)
    }

    @discardableResult
    func resolveAddress(reportID: String, location: LocationSnapshot) async -> AddressResolution? {
        cancelAddressLookup()
        let token = UUID()
        addressLookupToken = token
        let task = Task { try await addressService.resolve(location) }
        addressLookupTask = task
        do {
            let resolution = try await task.value
            guard addressLookupToken == token,
                  selectedReportID == reportID || cameraReportID == reportID
            else { return nil }
            addressLookupTask = nil
            return resolution
        } catch is CancellationError {
            return nil
        } catch {
            if addressLookupToken == token {
                addressLookupTask = nil
            }
            return nil
        }
    }

    func cancelAddressResolution() {
        cancelAddressLookup()
    }

    func chooseViolation(_ type: ViolationType, customTemplateID: String? = nil) {
        guard let id = selectedReportID else { return }
        let outcome = mutate(id: id) { report in
            var updated = report
            updated.violationType = type
            updated.customTemplateID = type == .custom ? customTemplateID : nil
            return updated
        }
        if outcome == .changed { refreshLetterAndReadiness(id: id) }
    }

    func saveTemplate(id: String? = nil, name: String, description: String) {
        let now = Date.now
        let template = CustomViolationTemplate(
            id: id ?? UUID().uuidString,
            displayName: name.trimmingCharacters(in: .whitespacesAndNewlines),
            estonianDescription: description.trimmingCharacters(in: .whitespacesAndNewlines),
            createdAt: templates.first(where: { $0.id == id })?.createdAt ?? now,
            updatedAt: now
        )
        guard !template.displayName.isEmpty, !template.estonianDescription.isEmpty else { return }
        do {
            try repository.upsertTemplate(template)
            reload()
            if id != nil { try refreshReports { $0.customTemplateID == template.id } }
        } catch {
            notice = localized("error.storage")
        }
    }

    func deleteTemplate(_ template: CustomViolationTemplate) {
        do {
            try repository.deleteTemplate(id: template.id)
            reload()
        } catch {
            notice = localized("error.storage")
        }
    }

    func recognizeLocally(reportID: String) async {
        guard let recognition, let report = try? repository.report(id: reportID) else { return }
        let fingerprint = LocalRecognitionState.fingerprint(for: report.photos)
        guard !LocalRecognitionState.isCurrent(report) else { return }
        do {
            let pendingPhotos = report.photos.filter { !$0.localRecognitionComplete }
            let batch = await recognition.recognize(photos: pendingPhotos)
            for result in batch.results {
                try repository.applyRecognition(
                    id: reportID,
                    result: result,
                    expectedFingerprint: fingerprint
                )
            }
            if batch.isComplete {
                try repository.markLocalRecognitionComplete(id: reportID, expectedFingerprint: fingerprint)
            } else {
                notice = localized("recognition.local.partial")
            }
            reload()
        } catch {
            notice = localized("recognition.local.failed")
        }
    }

    func resumePendingLocalRecognition() async {
        guard let reportIDs = try? repository.pendingRecognitionReportIDs() else {
            notice = localized("error.storage")
            return
        }
        for reportID in reportIDs {
            await recognizeLocally(reportID: reportID)
        }
    }

    func saveProfile(_ profile: ReporterProfile) {
        let normalized = ReporterProfile(
            name: profile.name.trimmingCharacters(in: .whitespacesAndNewlines),
            phone: profile.phone.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        let previous = settings.value.profile
        do {
            try refreshReports(profile: normalized)
            if normalized != previous {
                try settings.update { $0.profile = normalized }
            }
        } catch {
            try? refreshReports(profile: previous)
            notice = localized("error.storage")
        }
    }

    func saveLanguage(_ languageTag: String) {
        do {
            try settings.update { $0.languageTag = languageTag }
        } catch {
            notice = localized("error.storage")
        }
    }

    func saveDefaultRecipient(_ recipient: String) {
        let normalized = recipient.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty, normalized != settings.value.defaultRecipient else { return }
        do {
            try settings.update { $0.defaultRecipient = normalized }
        } catch {
            notice = localized("error.storage")
        }
    }

    @discardableResult
    func saveReportRecipient(_ recipient: String) -> Bool {
        guard let id = selectedReportID else { return false }
        let normalized = recipient.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return false }
        let outcome = mutate(id: id) { report in
            var updated = report
            updated.recipient = normalized
            return updated
        }
        guard outcome.succeeded else { return false }
        if outcome == .changed { refreshLetterAndReadiness(id: id) }
        return true
    }

    func prepareMail() async {
        guard !isPreparingMail else { return }
        guard let report = selectedReport, report.status == .ready || report.status == .handedOffToMail else {
            notice = localized("summary.not.ready")
            return
        }
        isPreparingMail = true
        defer { isPreparingMail = false }
        do {
            if let previousReportID = mailPayload?.reportID {
                try await photoStore.deleteMailCopies(reportID: previousReportID)
            }
            let attachments = try await photoStore.prepareMailCopies(photos: report.photos)
            guard selectedReportID == report.id else {
                try await photoStore.deleteMailCopies(reportID: report.id)
                return
            }
            mailPayload = MailDraftPayload(
                reportID: report.id,
                recipient: report.recipient,
                subject: report.subject,
                body: report.body,
                attachments: attachments
            )
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--ui-testing-mail-fallback") {
                pendingShareReportID = report.id
                asksShareHandoffConfirmation = true
                return
            }
            #endif
            if canSendMail() { presentingMailComposer = true }
            else { pendingShareReportID = report.id; presentingShareSheet = true }
        } catch {
            notice = localized("mail.prepare.failed")
        }
    }

    func mailComposerFinished(_ result: MailComposerResult) async {
        presentingMailComposer = false
        let reportID = mailPayload?.reportID
        if result.countsAsHandoff, let reportID { markHandedOff(id: reportID) }
        if result == .failed { notice = localized("mail.open.failed") }
        if let reportID { try? await photoStore.deleteMailCopies(reportID: reportID) }
        mailPayload = nil
    }

    func shareSheetReturned() {
        presentingShareSheet = false
        asksShareHandoffConfirmation = true
    }

    func confirmShareHandoff(_ openedDraft: Bool) async {
        asksShareHandoffConfirmation = false
        let reportID = pendingShareReportID
        if openedDraft, let reportID { markHandedOff(id: reportID) }
        pendingShareReportID = nil
        if let reportID { try? await photoStore.deleteMailCopies(reportID: reportID) }
        mailPayload = nil
    }

    func discardPreparedMail() async {
        guard !presentingMailComposer, !presentingShareSheet else { return }
        let reportID = mailPayload?.reportID ?? pendingShareReportID
        asksShareHandoffConfirmation = false
        pendingShareReportID = nil
        mailPayload = nil
        if let reportID { try? await photoStore.deleteMailCopies(reportID: reportID) }
    }

    func deleteReport(_ report: Report) async {
        do {
            try repository.deleteReport(id: report.id)
            if selectedReportID == report.id { selectedReportID = nil }
            if cameraReportID == report.id { cameraReportID = UUID().uuidString }
            reload()
            do {
                try await photoStore.deleteReportDirectory(reportID: report.id)
            } catch {
                try? await photoStore.reconcile(references: repository.photoReferences())
                notice = localized("error.storage")
            }
        } catch {
            reload()
            notice = localized("error.storage")
        }
    }

    @discardableResult
    func deleteAllData() async -> Bool {
        dataGeneration += 1
        isDeletingAllData = true
        cancelAddressLookup()
        locationService.cancelPendingRequest()
        locationService.stopUpdating()
        defer { isDeletingAllData = false }
        var failed = false
        do { try repository.deleteAll() } catch { failed = true }
        do { try await photoStore.deleteAll() } catch { failed = true }
        if !(await mapCacheReset()) { failed = true }
        if failed {
            reload()
            notice = localized("error.storage")
            return false
        }
        do {
            try settings.reset()
        } catch {
            reload()
            notice = localized("error.storage")
            return false
        }
        selectedReportID = nil
        cameraReportID = UUID().uuidString
        selectedTab = .camera
        reports = []
        templates = []
        loadedReportLimit = Self.reportPageSize
        canLoadMoreReports = false
        mailPayload = nil
        return true
    }

    private func cancelAddressLookup() {
        addressLookupTask?.cancel()
        addressLookupTask = nil
        addressLookupToken = nil
    }

    private func refreshReports(
        profile: ReporterProfile? = nil,
        where predicate: (Report) -> Bool = { _ in true }
    ) throws {
        try repository.mutateReports { report in
            guard predicate(report) else { return report }
            return refreshedLetterAndReadiness(
                for: report,
                profile: profile,
                preserveHandoffWhenLetterIsUnchanged: true
            )
        }
        reload()
    }

    private func refreshedLetterAndReadiness(
        for report: Report,
        profile: ReporterProfile? = nil,
        preserveHandoffWhenLetterIsUnchanged: Bool = false
    ) -> Report {
        let profile = profile ?? settings.value.profile
        guard let description = violationDescription(for: report) else {
            var updated = report
            updated.subject = ""
            updated.body = ""
            updated.status = .draft
            updated.mailOpenedAt = nil
            return updated
        }
        let letter = renderer.render(
            report: report,
            profile: profile,
            violationDescription: description
        )
        let preservesHandoff = preserveHandoffWhenLetterIsUnchanged &&
            report.status == .handedOffToMail &&
            report.subject == letter.subject &&
            report.body == letter.body
        var updated = report
        updated.subject = letter.subject
        updated.body = letter.body
        var refreshed = ReportTransitions.refreshReadiness(
            report: updated,
            profile: profile,
            violationDescription: description
        )
        if preservesHandoff, refreshed.status == .ready {
            refreshed.status = .handedOffToMail
            refreshed.mailOpenedAt = report.mailOpenedAt
        }
        return refreshed
    }

    private func refreshLetterAndReadiness(id: String) {
        guard let report = try? repository.report(id: id) else { return }
        mutate(id: id) { [self] _ in refreshedLetterAndReadiness(for: report) }
    }

    private func violationDescription(for report: Report) -> String? {
        if report.violationType == .custom {
            return templates.first(where: { $0.id == report.customTemplateID })?.estonianDescription
        }
        return ViolationTemplates.description(for: report.violationType)
    }

    @discardableResult
    private func mutate(id: String, _ transform: (Report) -> Report) -> MutationOutcome {
        do {
            let changed = try repository.mutateReport(id: id, transform)
            reload()
            return changed ? .changed : .unchanged
        } catch {
            notice = localized("error.storage")
            return .failed
        }
    }

    private func markHandedOff(id: String) {
        mutate(id: id) { report in (try? ReportTransitions.markHandedOff(report)) ?? report }
    }

    private static func resetMapLibreCache() async -> Bool {
        await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            MLNOfflineStorage.shared.resetDatabase { error in continuation.resume(returning: error == nil) }
        }
    }

    func localized(_ key: String) -> String {
        String(localized: String.LocalizationValue(key), locale: settings.value.locale)
    }
}
