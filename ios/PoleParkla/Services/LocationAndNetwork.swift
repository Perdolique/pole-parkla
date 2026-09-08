import CoreLocation
import Foundation
import Observation

enum LocationAuthorizationState: Equatable, Sendable {
    case notDetermined
    case authorized
    case denied
}

@MainActor
@Observable
final class ForegroundLocationService: NSObject, @preconcurrency CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<LocationSnapshot, Error>?
    private var retriedAfterStaleLocation = false
    private var keepsUpdating = false
    private(set) var authorizationState = LocationAuthorizationState.notDetermined
    private(set) var latestLocation: LocationSnapshot?

    var freshLocation: LocationSnapshot? {
        guard let latestLocation, LocationFreshness.isFresh(latestLocation) else { return nil }
        return latestLocation
    }

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        authorizationState = Self.authorizationState(for: manager.authorizationStatus)
    }

    func requestPermission() {
        guard manager.authorizationStatus == .notDetermined else { return }
        manager.requestWhenInUseAuthorization()
    }

    func startUpdating() {
        latestLocation = nil
        keepsUpdating = true
        switch manager.authorizationStatus {
        case .notDetermined:
            break
        case .authorizedAlways, .authorizedWhenInUse:
            manager.startUpdatingLocation()
        case .restricted, .denied:
            break
        @unknown default:
            break
        }
    }

    func stopUpdating() {
        keepsUpdating = false
        manager.stopUpdatingLocation()
        latestLocation = nil
    }

    func cancelPendingRequest() {
        continuation?.resume(throwing: CancellationError())
        continuation = nil
        retriedAfterStaleLocation = false
    }

    func requestLocation() async throws -> LocationSnapshot {
        guard continuation == nil else { throw LocationServiceError.requestInProgress }
        if let freshLocation { return freshLocation }
        switch manager.authorizationStatus {
        case .restricted, .denied:
            throw LocationServiceError.permissionDenied
        case .notDetermined, .authorizedAlways, .authorizedWhenInUse:
            return try await withCheckedThrowingContinuation { continuation in
                self.continuation = continuation
                retriedAfterStaleLocation = false
                if manager.authorizationStatus == .notDetermined {
                    manager.requestWhenInUseAuthorization()
                } else {
                    manager.requestLocation()
                }
            }
        @unknown default:
            throw LocationServiceError.unavailable
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationState = Self.authorizationState(for: manager.authorizationStatus)
        guard manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways else {
            if manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted {
                continuation?.resume(throwing: LocationServiceError.permissionDenied)
                continuation = nil
            }
            return
        }
        if keepsUpdating { manager.startUpdatingLocation() }
        if continuation != nil { manager.requestLocation() }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let snapshots = locations
            .filter({ $0.horizontalAccuracy >= 0 })
            .sorted(by: { $0.timestamp > $1.timestamp })
            .map {
                LocationSnapshot(
                    latitude: $0.coordinate.latitude,
                    longitude: $0.coordinate.longitude,
                    accuracyMeters: $0.horizontalAccuracy,
                    capturedAt: $0.timestamp
                )
            }
        if let newest = snapshots.first { latestLocation = newest }
        guard let location = snapshots.first(where: { LocationFreshness.isFresh($0) }) else {
            guard continuation != nil else { return }
            if retriedAfterStaleLocation {
                continuation?.resume(throwing: LocationServiceError.unavailable)
                continuation = nil
            } else {
                retriedAfterStaleLocation = true
                manager.requestLocation()
            }
            return
        }
        continuation?.resume(returning: location)
        continuation = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        continuation?.resume(throwing: error)
        continuation = nil
    }

    private static func authorizationState(for status: CLAuthorizationStatus) -> LocationAuthorizationState {
        switch status {
        case .notDetermined: .notDetermined
        case .authorizedAlways, .authorizedWhenInUse: .authorized
        case .restricted, .denied: .denied
        @unknown default: .denied
        }
    }
}

enum LocationServiceError: Error, Equatable {
    case requestInProgress
    case permissionDenied
    case unavailable
}

protocol AddressResolving: Sendable {
    func resolve(_ location: LocationSnapshot) async throws -> AddressResolution
}

struct InAksAddressService: AddressResolving, Sendable {
    var session: URLSession = .shared

    func resolve(_ location: LocationSnapshot) async throws -> AddressResolution {
        let point = Lest97Converter().convert(latitude: location.latitude, longitude: location.longitude)
        var components = URLComponents(string: "https://aks.geoportaal.ee/inaks/inaadress/gazetteer")!
        components.queryItems = [
            URLQueryItem(name: "x", value: String(format: "%.3f", locale: Locale(identifier: "en_US_POSIX"), point.easting)),
            URLQueryItem(name: "y", value: String(format: "%.3f", locale: Locale(identifier: "en_US_POSIX"), point.northing)),
            URLQueryItem(name: "radius", value: String(radiusMeters(location.accuracyMeters))),
            URLQueryItem(name: "features", value: "TANAV,EHITISHOONE"),
            URLQueryItem(name: "appartment", value: "0"),
            URLQueryItem(name: "ihist", value: "0"),
        ]
        guard let url = components.url else { throw InAksError.invalidRequest }
        var request = URLRequest(url: url)
        request.timeoutInterval = 10
        request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
            throw InAksError.http((response as? HTTPURLResponse)?.statusCode ?? -1)
        }
        return try InAksParser.parse(data: data, location: location)
    }

    func radiusMeters(_ accuracy: Double?) -> Int {
        let value = accuracy.flatMap { $0.isFinite ? $0 : nil } ?? 50
        return Int(ceil(min(100, max(30, value))))
    }
}

enum InAksParser {
    static func parse(data: Data, location: LocationSnapshot) throws -> AddressResolution {
        let root = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let entries = root?["addresses"] as? [[String: Any]] ?? []
        var buildings: [String: AddressCandidate] = [:]
        var streets: [String: AddressCandidate] = [:]
        for entry in entries where entry["olek"] as? String == "K" {
            guard let kind = entry["liikVal"] as? String,
                  let street = nonBlank(entry["liikluspind"]),
                  let municipality = nonBlank(entry["omavalitsus"]),
                  let distance = finiteDistance(entry["kaugus"])
            else { continue }
            switch kind {
            case "TANAV":
                keepNearest(
                    AddressCandidate(address: "\(street), \(municipality)", distanceMeters: distance, type: .street),
                    in: &streets
                )
            case "EHITISHOONE":
                guard let houseNumber = nonBlank(entry["aadress_nr"]) else { continue }
                keepNearest(
                    AddressCandidate(address: "\(street) \(houseNumber), \(municipality)", distanceMeters: distance, type: .building),
                    in: &buildings
                )
            default:
                continue
            }
        }
        let streetCandidates = streets.values.sorted(by: candidatePrecedes).prefix(2)
        let buildingCandidates = buildings.values.sorted(by: candidatePrecedes).prefix(3)
        let candidates = Array(streetCandidates + buildingCandidates)
        let exact = candidates.filter { $0.distanceMeters == 0 }
        let suggestion = exact.count == 1 ? exact[0] : candidates.min(by: candidatePrecedes)
        return AddressResolution(
            location: location,
            candidates: candidates,
            suggested: suggestion,
            needsReview: exact.count != 1
        )
    }

    private static func nonBlank(_ value: Any?) -> String? {
        guard let value = value as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func finiteDistance(_ value: Any?) -> Int? {
        let raw: Double?
        if let number = value as? NSNumber { raw = number.doubleValue }
        else if let string = value as? String { raw = Double(string) }
        else { raw = nil }
        guard let raw, raw.isFinite, raw >= 0 else { return nil }
        let rounded = raw.rounded()
        guard rounded < Double(Int.max) else { return nil }
        return Int(rounded)
    }

    private static func keepNearest(_ candidate: AddressCandidate, in map: inout [String: AddressCandidate]) {
        let key = candidate.address.lowercased()
        if map[key] == nil || candidate.distanceMeters < map[key]!.distanceMeters { map[key] = candidate }
    }

    private static func candidatePrecedes(_ lhs: AddressCandidate, _ rhs: AddressCandidate) -> Bool {
        if lhs.distanceMeters != rhs.distanceMeters { return lhs.distanceMeters < rhs.distanceMeters }
        return lhs.address < rhs.address
    }
}

enum InAksError: Error, Equatable {
    case invalidRequest
    case http(Int)
}
