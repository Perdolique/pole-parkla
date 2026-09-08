import Foundation
import Testing
@testable import PoleParkla

@Suite("Report contracts")
struct DomainContractsTests {
    @Test func readinessRequiresExplicitVehicleAndLocationConfirmation() {
        let description = ViolationTemplates.cyclePathDescription
        #expect(!testReport(vehicleConfirmed: false).isReady(profile: testProfile, violationDescription: description))
        #expect(!testReport(locationConfirmed: false).isReady(profile: testProfile, violationDescription: description))
        #expect(testReport().isReady(profile: testProfile, violationDescription: description))
    }

    @Test func readinessRejectsEveryMissingRequiredInput() {
        let description = ViolationTemplates.cyclePathDescription
        func expectNotReady(_ mutate: (inout Report) -> Void) {
            var report = testReport()
            mutate(&report)
            #expect(!report.isReady(profile: testProfile, violationDescription: description))
        }

        expectNotReady { $0.photos = [] }
        expectNotReady { $0.plate = "  " }
        expectNotReady { $0.vehicleConfirmed = false }
        expectNotReady { $0.recipient = "  " }
        expectNotReady { $0.subject = "" }
        expectNotReady { $0.body = "" }
        expectNotReady { $0.locationConfirmed = false }
        expectNotReady { $0.address = ""; $0.latitude = nil; $0.longitude = nil }
        expectNotReady { $0.latitude = 91 }
        expectNotReady { $0.longitude = -181 }
        expectNotReady { $0.longitude = nil }

        var profile = testProfile
        profile.name = " "
        #expect(!testReport().isReady(profile: profile, violationDescription: description))
        profile = testProfile
        profile.phone = " "
        #expect(!testReport().isReady(profile: profile, violationDescription: description))
        #expect(!testReport().isReady(profile: testProfile, violationDescription: nil))
        #expect(!testReport().isReady(profile: testProfile, violationDescription: "  "))
    }

    @Test func handoffNeverCreatesASentState() throws {
        var ready = testReport()
        ready.status = .ready
        let handedOff = try ReportTransitions.markHandedOff(ready, now: Date(timeIntervalSince1970: 42))
        #expect(handedOff.status == .handedOffToMail)
        #expect(handedOff.mailOpenedAt == Date(timeIntervalSince1970: 42))
        #expect(ReportStatus.allCases == [.draft, .ready, .handedOffToMail])
    }

    @Test func editingAHandedOffReportReturnsItToReady() throws {
        var report = testReport()
        report.status = .ready
        let handedOff = try ReportTransitions.markHandedOff(report, now: Date(timeIntervalSince1970: 42))
        let refreshed = ReportTransitions.refreshReadiness(
            report: handedOff,
            profile: testProfile,
            violationDescription: ViolationTemplates.cyclePathDescription
        )
        #expect(refreshed.status == .ready)
        #expect(refreshed.mailOpenedAt == nil)
    }

    @Test func candidateOrderingIsDeterministicAcrossPhotosAndSources() {
        let observations = [
            observation("one", photo: "photo-1", source: .visionOCR, value: "003PUK", area: 0.01),
            observation("two", photo: "photo-2", source: .visionOCR, value: "003 PUK", area: 0.02),
            observation("three", photo: "photo-3", source: .localPlateModel, value: "003-PUX", area: 0.05),
        ]
        let candidates = aggregatePlateCandidates(observations)
        #expect(candidates.map(\.value) == ["003 PUK", "003 PUX"])
        #expect(candidates[0].supportingPhotoCount == 2)
    }

    @Test func equalSupportCandidateTieBreakPrefersLocalModelThenLexicalValue() {
        let observations = [
            observation("vision", photo: "photo-1", source: .visionOCR, value: "999 XYZ", area: 0.02),
            observation("local-b", photo: "photo-2", source: .localPlateModel, value: "123 ABC", area: 0.02),
            observation("local-a", photo: "photo-3", source: .localPlateModel, value: "003 PUK", area: 0.02),
        ]

        #expect(aggregatePlateCandidates(observations).map(\.value) == ["003 PUK", "123 ABC", "999 XYZ"])
    }

    @Test func changingCandidateSelectsThatCandidatesEvidenceBounds() throws {
        let firstBounds = NormalizedPhotoRect(left: 0.1, top: 0.2, right: 0.5, bottom: 0.3)
        let secondBounds = NormalizedPhotoRect(left: 0.3, top: 0.4, right: 0.8, bottom: 0.55)
        let observations = [
            PlateObservation(
                id: "first",
                reportID: "report",
                photoID: "photo-1",
                source: .localPlateModel,
                value: "003 PUK",
                bounds: firstBounds
            ),
            PlateObservation(
                id: "second",
                reportID: "report",
                photoID: "photo-2",
                source: .localPlateModel,
                value: "123 ABC",
                bounds: secondBounds
            ),
        ]

        let first = try #require(exactPlateCandidate(plate: "003 PUK", observations: observations))
        let second = try #require(exactPlateCandidate(plate: "123 ABC", observations: observations))

        #expect(first.observations.first?.photoID == "photo-1")
        #expect(first.observations.first?.bounds == firstBounds)
        #expect(second.observations.first?.photoID == "photo-2")
        #expect(second.observations.first?.bounds == secondBounds)
    }

    @Test func recognitionDoesNotOverwriteManualOrConfirmedVehicle() {
        var report = testReport(plate: "999 XYZ")
        report.plateManuallyEdited = true
        report.vehicleManuallyEdited = true
        report.vehicleConfirmed = true
        let result = RecognitionResult(
            source: .localPlateModel,
            plateCandidates: ["003 PUK"]
        )
        let merged = RecognitionMerger.merge(report: report, result: result)
        #expect(merged.plate == "999 XYZ")
        #expect(merged.vehicleMake == "Toyota")
        #expect(merged.vehicleModel == "Corolla")
        #expect(merged.vehicleConfirmed)
    }

    @Test func estonianLetterIsRenderedWithoutSyntheticAccuracy() {
        let letter = EmailTemplateRenderer().render(
            report: testReport(),
            profile: testProfile,
            violationDescription: ViolationTemplates.cyclePathDescription
        )
        #expect(letter.subject.contains("003 PUK"))
        #expect(letter.body.contains("Sõiduk: Toyota Corolla, registreerimisnumber 003 PUK"))
        #expect(letter.body.contains("59.437000, 24.753600"))
        #expect(!letter.body.contains("±"))
        #expect(letter.body.contains("Foto on kirjale lisatud."))
    }

    @Test func locationValidationRejectsHalfCoordinatePairs() {
        #expect(throws: ReportRuleError.invalidCoordinates) {
            try ReportLocationInput.validate(
                address: "",
                latitude: "59.4",
                longitude: "",
                accuracyMeters: nil,
                occurredAt: Date(timeIntervalSince1970: 1_700_000_000)
            )
        }
    }

    @Test func invalidCoordinatesCannotHideBehindAnAddress() {
        var report = testReport()
        report.address = "Lastekodu tn 42, Tallinn"
        report.latitude = 999
        report.longitude = 999
        #expect(!report.hasValidLocation)
        #expect(!report.isReady(
            profile: testProfile,
            violationDescription: ViolationTemplates.cyclePathDescription
        ))
    }

    @Test func backgroundAddressLookupFillsOnlyAnEmptyAddressAndKeepsCoordinates() {
        let location = LocationSnapshot(latitude: 59.432, longitude: 24.757, accuracyMeters: 4.5, capturedAt: .now)
        let candidate = AddressCandidate(address: "Liivalaia tn 2, Tallinn", distanceMeters: 5, type: .building)
        let result = AddressLookupResult(
            location: location,
            resolution: AddressResolution(
                location: location,
                candidates: [candidate],
                suggested: candidate,
                needsReview: false
            )
        )
        let original = AddressDraft(
            address: "",
            latitude: 59.437,
            longitude: 24.7536,
            accuracyMeters: 8.5,
            occurredAt: .now
        )

        let filled = AddressDraftPolicy.applyingLookup(result, to: original, applyLocation: false)
        var manual = original
        manual.address = "My corrected address"
        let preserved = AddressDraftPolicy.applyingLookup(result, to: manual, applyLocation: false)

        #expect(filled.address == candidate.address)
        #expect(filled.latitude == original.latitude)
        #expect(filled.longitude == original.longitude)
        #expect(filled.candidates == [candidate])
        #expect(preserved.address == manual.address)
    }

    @Test func explicitLocationLookupAppliesPointSuggestionAndCandidates() {
        let location = LocationSnapshot(latitude: 59.432, longitude: 24.757, accuracyMeters: 4.5, capturedAt: .now)
        let candidate = AddressCandidate(address: "Liivalaia tn 2, Tallinn", distanceMeters: 5, type: .building)
        let result = AddressLookupResult(
            location: location,
            resolution: AddressResolution(
                location: location,
                candidates: [candidate],
                suggested: candidate,
                needsReview: false
            )
        )
        let original = AddressDraft(
            address: "My corrected address",
            latitude: 59.437,
            longitude: 24.7536,
            accuracyMeters: 8.5,
            occurredAt: .now
        )

        let updated = AddressDraftPolicy.applyingLookup(result, to: original, applyLocation: true)

        #expect(updated.address == candidate.address)
        #expect(updated.latitude == location.latitude)
        #expect(updated.longitude == location.longitude)
        #expect(updated.accuracyMeters == location.accuracyMeters)
        #expect(updated.candidates == [candidate])
    }

    @Test func mapSelectionCarriesCandidatesAndFailedLookupPreservesManualAddress() {
        let location = LocationSnapshot(latitude: 59.432, longitude: 24.757, accuracyMeters: nil, capturedAt: .now)
        let candidate = AddressCandidate(address: "Liivalaia tn 2, Tallinn", distanceMeters: 5, type: .building)
        let resolution = AddressResolution(
            location: location,
            candidates: [candidate],
            suggested: candidate,
            needsReview: false
        )

        let ready = AddressDraftPolicy.mapSelection(
            currentAddress: "Manual address",
            location: location,
            resolution: resolution
        )
        let failed = AddressDraftPolicy.mapSelection(
            currentAddress: "Manual address",
            location: location,
            resolution: nil
        )

        #expect(ready.address == candidate.address)
        #expect(ready.candidates == [candidate])
        #expect(!ready.addressLookupFailed)
        #expect(failed.address == "Manual address")
        #expect(failed.candidates.isEmpty)
        #expect(failed.addressLookupFailed)

        let original = AddressDraft(
            address: "Old address",
            latitude: 59.437,
            longitude: 24.7536,
            accuracyMeters: 8,
            occurredAt: .now,
            candidates: [],
            lookupFailed: false
        )
        let applied = AddressDraftPolicy.applyingMapSelection(ready, to: original)
        #expect(applied.address == candidate.address)
        #expect(applied.latitude == location.latitude)
        #expect(applied.longitude == location.longitude)
        #expect(applied.accuracyMeters == nil)
        #expect(applied.candidates == [candidate])
    }

    @Test func manualCoordinateEditClearsCandidatesAndAccuracyWithoutChangingAddress() {
        let candidate = AddressCandidate(address: "Liivalaia tn 2, Tallinn", distanceMeters: 5, type: .building)
        let original = AddressDraft(
            address: "Manual address",
            latitude: 59.437,
            longitude: 24.7536,
            accuracyMeters: 8,
            occurredAt: .now,
            candidates: [candidate],
            lookupFailed: true
        )

        let updated = AddressDraftPolicy.clearingCandidatesForManualCoordinates(original)

        #expect(updated.address == original.address)
        #expect(updated.latitude == original.latitude)
        #expect(updated.longitude == original.longitude)
        #expect(updated.accuracyMeters == nil)
        #expect(updated.candidates.isEmpty)
        #expect(!updated.lookupFailed)
    }

    @Test func lest97MatchesExistingReferencePoint() {
        let point = Lest97Converter().convert(
            latitude: 59.40802641572147,
            longitude: 24.693720710505183
        )
        #expect(abs(point.easting - 539_399) < 5)
        #expect(abs(point.northing - 6_585_772) < 5)
    }

    @Test func plateModelDecodersMatchAndroidContracts() throws {
        let transform = PlateModelProcessing.letterboxTransform(imageWidth: 1_000, imageHeight: 500)
        #expect(transform.scaledWidth == 512)
        #expect(transform.scaledHeight == 256)
        let mappingTransform = LetterboxTransform(
            imageWidth: 640,
            imageHeight: 480,
            scale: 0.5,
            scaledWidth: 320,
            scaledHeight: 240,
            paddingX: 0,
            paddingY: 40,
            left: 0,
            top: 40
        )
        let detector: [Float] = [0, 50, 60, 200, 220, 0, 0.9]
        let detection = try #require(PlateModelProcessing.decodeDetections(detector, transform: mappingTransform).first)
        #expect(detection.left == 100)
        #expect(detection.top == 40)
        #expect(detection.right == 400)
        #expect(detection.bottom == 360)

        let alphabet = PlateModelProcessing.alphabet
        var ocr = [Float](repeating: 0, count: PlateModelProcessing.ocrMaximumSlots * alphabet.count)
        for (slot, character) in Array("003PUK____").enumerated() {
            ocr[slot * alphabet.count + alphabet.firstIndex(of: character)!] = 0.95
        }
        let decoded = try #require(try PlateModelProcessing.decodePlate(ocr))
        #expect(decoded.value == "003PUK")
    }

    @Test func wizardRestoresTheFirstIncompleteStep() {
        var report = testReport()
        report.plate = ""
        report.vehicleConfirmed = false
        #expect(report.resolvedWizardStep == .vehicle)

        report.plate = "003 PUK"
        report.vehicleConfirmed = true
        report.address = ""
        report.latitude = nil
        report.longitude = nil
        report.locationConfirmed = false
        #expect(report.resolvedWizardStep == .place)

        report.address = "Lastekodu tn 42, Tallinn"
        report.locationConfirmed = true
        report.violationType = nil
        #expect(report.resolvedWizardStep == .violation)

        report.violationType = .cyclePath
        #expect(report.resolvedWizardStep == .summary)

        report.status = .handedOffToMail
        report.vehicleConfirmed = false
        #expect(report.resolvedWizardStep == .summary)
    }

    private func observation(
        _ id: String,
        photo: String,
        source: RecognitionSource,
        value: String,
        area: Float
    ) -> PlateObservation {
        PlateObservation(
            id: id,
            reportID: "report",
            photoID: photo,
            source: source,
            value: value,
            relativeArea: area
        )
    }
}
