import Foundation

enum PlateCandidateParser {
    private static let commonPattern = try! NSRegularExpression(
        pattern: #"(?<![A-Z0-9])(\d{3})[\s-]*([A-Z]{3})(?![A-Z0-9])"#
    )
    private static let ambiguousPattern = try! NSRegularExpression(
        pattern: #"(?<![A-Z0-9])([0-9O]{3})[\s-]*([A-Z0-9]{3})(?![A-Z0-9])"#
    )
    private static let separatedPattern = try! NSRegularExpression(
        pattern: #"(?<![A-Z0-9])([A-Z0-9]{1,4}(?:[\s-]+[A-Z0-9]{1,4}){1,3})(?![A-Z0-9])"#
    )
    private static let compactPattern = try! NSRegularExpression(
        pattern: #"(?<![A-Z0-9])([A-Z0-9]{4,8})(?![A-Z0-9])"#
    )
    private static let commonNormalizedPattern = try! NSRegularExpression(
        pattern: #"^(\d{3})([A-Z]{3})$"#
    )

    static func parse(_ text: String, limit: Int = 5) -> [String] {
        let upper = text.uppercased(with: Locale(identifier: "en_US_POSIX"))
        var values: [String] = []

        for match in matches(commonPattern, in: upper) {
            guard let digits = group(1, match: match, in: upper),
                  let letters = group(2, match: match, in: upper)
            else { continue }
            values.append("\(digits) \(letters)")
        }
        for match in matches(ambiguousPattern, in: upper) {
            guard var digits = group(1, match: match, in: upper),
                  var letters = group(2, match: match, in: upper)
            else { continue }
            digits = digits.replacingOccurrences(of: "O", with: "0")
            letters = letters.replacingOccurrences(of: "0", with: "O")
            guard digits.allSatisfy(\.isNumber), letters.allSatisfy(\.isLetter) else { continue }
            values.append("\(digits) \(letters)")
        }
        for expression in [separatedPattern, compactPattern] {
            for match in matches(expression, in: upper) {
                guard let raw = group(1, match: match, in: upper),
                      let normalized = normalize(raw)
                else { continue }
                values.append(normalized)
            }
        }

        return Array(Set(values)).sorted {
            let lhs = (rank($0), $0.count, $0)
            let rhs = (rank($1), $1.count, $1)
            return lhs < rhs
        }.prefix(limit).map { $0 }
    }

    static func normalize(_ raw: String) -> String? {
        let compact = raw
            .uppercased(with: Locale(identifier: "en_US_POSIX"))
            .unicodeScalars
            .filter { scalar in
                (48 ... 57).contains(scalar.value) || (65 ... 90).contains(scalar.value)
            }
            .map(String.init)
            .joined()
        guard (4 ... 8).contains(compact.count), compact.contains(where: \.isNumber) else { return nil }

        let range = NSRange(compact.startIndex ..< compact.endIndex, in: compact)
        if let match = commonNormalizedPattern.firstMatch(in: compact, range: range),
           let digits = group(1, match: match, in: compact),
           let letters = group(2, match: match, in: compact)
        {
            return "\(digits) \(letters)"
        }
        return compact
    }

    private static func rank(_ candidate: String) -> Int {
        if candidate.range(of: #"^\d{3} [A-Z]{3}$"#, options: .regularExpression) != nil { return 0 }
        let compact = candidate.replacingOccurrences(of: " ", with: "")
        if compact.range(of: #"^\d{2,4}[A-Z]{2,4}$"#, options: .regularExpression) != nil { return 1 }
        return 2
    }

    private static func matches(_ expression: NSRegularExpression, in value: String) -> [NSTextCheckingResult] {
        expression.matches(in: value, range: NSRange(value.startIndex ..< value.endIndex, in: value))
    }

    private static func group(_ index: Int, match: NSTextCheckingResult, in value: String) -> String? {
        guard let range = Range(match.range(at: index), in: value) else { return nil }
        return String(value[range])
    }
}

func aggregatePlateCandidates(
    _ observations: [PlateObservation],
    limit: Int = 5
) -> [PlateCandidate] {
    let normalized = observations.compactMap { observation -> PlateObservation? in
        guard let value = PlateCandidateParser.normalize(observation.value) else { return nil }
        var copy = observation
        copy.value = value
        return copy
    }
    let candidates = Dictionary(grouping: normalized, by: \.value).map { value, matches in
        PlateCandidate(
            value: value,
            sources: Set(matches.map(\.source)),
            supportingPhotoCount: Set(matches.map(\.photoID)).count,
            observations: matches.sorted(by: observationPrecedes)
        )
    }
    return Array(candidates.sorted(by: candidatePrecedes).prefix(limit))
}

func exactPlateCandidate(plate: String, observations: [PlateObservation]) -> PlateCandidate? {
    guard let normalized = PlateCandidateParser.normalize(plate) else { return nil }
    return aggregatePlateCandidates(observations, limit: .max).first { $0.value == normalized }
}

private func candidatePrecedes(_ lhs: PlateCandidate, _ rhs: PlateCandidate) -> Bool {
    if lhs.supportingPhotoCount != rhs.supportingPhotoCount {
        return lhs.supportingPhotoCount > rhs.supportingPhotoCount
    }
    let lhsLocal = lhs.sources.contains(.localPlateModel)
    let rhsLocal = rhs.sources.contains(.localPlateModel)
    if lhsLocal != rhsLocal { return lhsLocal }
    if lhs.sources.count != rhs.sources.count { return lhs.sources.count > rhs.sources.count }

    for selector in [
        { (value: PlateObservation) in value.relativeArea },
        { (value: PlateObservation) in value.detectionConfidence },
        { (value: PlateObservation) in value.characterConfidence },
    ] {
        let lhsMaximum = lhs.observations.compactMap(selector).max() ?? -.infinity
        let rhsMaximum = rhs.observations.compactMap(selector).max() ?? -.infinity
        if lhsMaximum != rhsMaximum { return lhsMaximum > rhsMaximum }
    }
    return lhs.value < rhs.value
}

private func observationPrecedes(_ lhs: PlateObservation, _ rhs: PlateObservation) -> Bool {
    if (lhs.bounds != nil) != (rhs.bounds != nil) { return lhs.bounds != nil }
    for pair in [
        (lhs.relativeArea, rhs.relativeArea),
        (lhs.detectionConfidence, rhs.detectionConfidence),
        (lhs.characterConfidence, rhs.characterConfidence),
    ] {
        let lhsValue = pair.0 ?? -.infinity
        let rhsValue = pair.1 ?? -.infinity
        if lhsValue != rhsValue { return lhsValue > rhsValue }
    }
    return lhs.photoID < rhs.photoID
}

enum RecognitionMerger {
    static func merge(report: Report, result: RecognitionResult) -> Report {
        var merged = report
        let recognizedPlate = aggregatePlateCandidates(report.plateObservations).first?.value ?? ""
        if !report.plateManuallyEdited, !report.vehicleConfirmed {
            merged.plate = recognizedPlate
        }
        return merged
    }

}

enum ViolationTemplates {
    static let cyclePathDescription = "Sõiduk on pargitud jalgrattateele või jalgrattarajale."
    static let pedestrianPathDescription = "Sõiduk on pargitud jalgteele või kõnniteele."

    static func description(for type: ViolationType?) -> String? {
        switch type {
        case .cyclePath: cyclePathDescription
        case .pedestrianPath: pedestrianPathDescription
        case .custom, nil: nil
        }
    }
}

enum ReportTransitions {
    static func refreshReadiness(
        report: Report,
        profile: ReporterProfile,
        violationDescription: String?
    ) -> Report {
        var refreshed = report
        if !report.isReady(profile: profile, violationDescription: violationDescription) {
            refreshed.status = .draft
        } else {
            refreshed.status = .ready
        }
        refreshed.mailOpenedAt = nil
        return refreshed
    }

    static func markHandedOff(_ report: Report, now: Date = .now) throws -> Report {
        guard report.status != .handedOffToMail else { return report }
        guard report.status == .ready else { throw ReportRuleError.notReady }
        var handedOff = report
        handedOff.status = .handedOffToMail
        handedOff.mailOpenedAt = now
        return handedOff
    }
}

enum ReportRuleError: Error, Equatable {
    case notReady
    case invalidCoordinates
    case missingLocation
}

enum ReportLocationInput {
    static func validate(
        address: String,
        latitude: String,
        longitude: String,
        accuracyMeters: Double?,
        occurredAt: Date
    ) throws -> AddressDraft {
        let latitudeText = normalizeCoordinate(latitude)
        let longitudeText = normalizeCoordinate(longitude)
        guard latitudeText.isEmpty == longitudeText.isEmpty else {
            throw ReportRuleError.invalidCoordinates
        }
        let parsedLatitude = try parseCoordinate(latitudeText, range: -90 ... 90)
        let parsedLongitude = try parseCoordinate(longitudeText, range: -180 ... 180)
        let normalizedAddress = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedAddress.isEmpty || parsedLatitude != nil else {
            throw ReportRuleError.missingLocation
        }
        return AddressDraft(
            address: normalizedAddress,
            latitude: parsedLatitude,
            longitude: parsedLongitude,
            accuracyMeters: accuracyMeters,
            occurredAt: occurredAt
        )
    }

    private static func parseCoordinate(_ value: String, range: ClosedRange<Double>) throws -> Double? {
        guard !value.isEmpty else { return nil }
        guard let number = Double(value), number.isFinite, range.contains(number) else {
            throw ReportRuleError.invalidCoordinates
        }
        return number
    }

    private static func normalizeCoordinate(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")
    }
}

enum AddressDraftPolicy {
    static func applyingLookup(
        _ result: AddressLookupResult,
        to draft: AddressDraft,
        applyLocation: Bool
    ) -> AddressDraft {
        var updated = draft
        let suggestedAddress = result.resolution?.suggested?.address
        if applyLocation {
            updated.address = suggestedAddress ?? draft.address
            updated.latitude = result.location.latitude
            updated.longitude = result.location.longitude
            updated.accuracyMeters = result.location.accuracyMeters
        } else if draft.address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            updated.address = suggestedAddress ?? ""
        }
        updated.candidates = result.resolution?.candidates ?? []
        updated.lookupFailed = result.resolution == nil
        return updated
    }

    static func mapSelection(
        currentAddress: String,
        location: LocationSnapshot,
        resolution: AddressResolution?
    ) -> AddressMapSelection {
        let suggestedAddress = resolution?.suggested?.address
        return AddressMapSelection(
            address: suggestedAddress ?? currentAddress,
            latitude: location.latitude,
            longitude: location.longitude,
            candidates: resolution?.candidates ?? [],
            addressLookupFailed: suggestedAddress == nil
        )
    }

    static func applyingMapSelection(_ selection: AddressMapSelection, to draft: AddressDraft) -> AddressDraft {
        var updated = draft
        updated.address = selection.address
        updated.latitude = selection.latitude
        updated.longitude = selection.longitude
        updated.accuracyMeters = nil
        updated.candidates = selection.candidates
        updated.lookupFailed = selection.addressLookupFailed
        return updated
    }

    static func clearingCandidatesForManualCoordinates(_ draft: AddressDraft) -> AddressDraft {
        var updated = draft
        updated.accuracyMeters = nil
        updated.candidates = []
        updated.lookupFailed = false
        return updated
    }
}

struct EmailTemplateRenderer: Sendable {
    private let timezone = TimeZone(identifier: "Europe/Tallinn")!

    func render(
        report: Report,
        profile: ReporterProfile,
        violationDescription: String
    ) -> LetterDraft {
        let coordinates = coordinates(for: report)
        let locationForSubject = report.address.nilIfBlank ?? coordinates
        let vehicle = [report.vehicleMake.nilIfBlank, report.vehicleModel.nilIfBlank]
            .compactMap { $0 }
            .joined(separator: " ")
        let vehiclePrefix = vehicle.isEmpty ? "" : "\(vehicle), "
        let photoLabel = report.photos.count == 1 ? "Foto" : "Fotod"

        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timezone
        formatter.dateFormat = "dd.MM.yyyy HH:mm"

        var details = [
            "Olukorra kirjeldus: \(violationDescription)",
            "Sõiduk: \(vehiclePrefix)registreerimisnumber \(report.plate.trimmingCharacters(in: .whitespacesAndNewlines))",
        ]
        if let address = report.address.nilIfBlank { details.append("Asukoht: \(address)") }
        if !coordinates.isEmpty { details.append("Koordinaadid: \(coordinates)") }
        details.append("Aeg: \(formatter.string(from: report.occurredAt))")

        return LetterDraft(
            subject: "Teade valesti pargitud sõidukist – \(report.plate.trimmingCharacters(in: .whitespacesAndNewlines)), \(locationForSubject)",
            body: """
            Tere

            Soovin teatada valesti pargitud sõidukist.

            \(details.joined(separator: "\n"))

            \(photoLabel) on kirjale lisatud.

            Lugupidamisega
            \(profile.name.trimmingCharacters(in: .whitespacesAndNewlines))
            Telefon: \(profile.phone.trimmingCharacters(in: .whitespacesAndNewlines))
            """
        )
    }

    private func coordinates(for report: Report) -> String {
        guard let latitude = report.latitude, let longitude = report.longitude else { return "" }
        return String(format: "%.6f, %.6f", locale: Locale(identifier: "en_US_POSIX"), latitude, longitude)
    }
}

enum LocalRecognitionState {
    static func fingerprint(for photos: [ReportPhoto]) -> String {
        "v1:" + photos.map(\.id).sorted().joined(separator: ",")
    }

    static func isCurrent(_ report: Report) -> Bool {
        report.photos.allSatisfy(\.localRecognitionComplete) &&
            report.localRecognitionFingerprint == fingerprint(for: report.photos)
    }
}

enum LocationFreshness {
    static func isFresh(_ location: LocationSnapshot, now: Date = .now) -> Bool {
        let age = location.capturedAt.timeIntervalSince(now)
        return age >= -120 && age <= 30
    }
}


struct Lest97Coordinate: Equatable, Sendable {
    var easting: Double
    var northing: Double
}

/// EPSG:3301, L-EST97 / Lambert Conic Conformal (2SP), GRS80.
struct Lest97Converter: Sendable {
    private let semiMajorAxis = 6_378_137.0
    private let inverseFlattening = 298.257_222_101
    private let latitudeOfOrigin = 57.517_553_930_555_56.radians
    private let centralMeridian = 24.0.radians
    private let firstStandardParallel = 59.333_333_333_333_336.radians
    private let secondStandardParallel = 58.0.radians
    private let falseEasting = 500_000.0
    private let falseNorthing = 6_375_000.0

    func convert(latitude: Double, longitude: Double) -> Lest97Coordinate {
        let flattening = 1 / inverseFlattening
        let eccentricity = sqrt(2 * flattening - flattening * flattening)
        let m1 = m(firstStandardParallel, eccentricity: eccentricity)
        let m2 = m(secondStandardParallel, eccentricity: eccentricity)
        let t1 = t(firstStandardParallel, eccentricity: eccentricity)
        let t2 = t(secondStandardParallel, eccentricity: eccentricity)
        let n = (log(m1) - log(m2)) / (log(t1) - log(t2))
        let f = m1 / (n * pow(t1, n))
        let rho0 = semiMajorAxis * f * pow(t(latitudeOfOrigin, eccentricity: eccentricity), n)
        let rho = semiMajorAxis * f * pow(t(latitude.radians, eccentricity: eccentricity), n)
        let theta = n * (longitude.radians - centralMeridian)
        return Lest97Coordinate(
            easting: falseEasting + rho * sin(theta),
            northing: falseNorthing + rho0 - rho * cos(theta)
        )
    }

    private func m(_ latitude: Double, eccentricity: Double) -> Double {
        cos(latitude) / sqrt(1 - pow(eccentricity * sin(latitude), 2))
    }

    private func t(_ latitude: Double, eccentricity: Double) -> Double {
        let eccentricitySine = eccentricity * sin(latitude)
        return tan(.pi / 4 - latitude / 2) /
            pow((1 - eccentricitySine) / (1 + eccentricitySine), eccentricity / 2)
    }
}

private extension Double {
    var radians: Double { self * .pi / 180 }
}

private extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
