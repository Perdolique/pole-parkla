@preconcurrency import MapLibre
import SwiftUI

enum MapPickerLoadState: Equatable {
    case loading
    case loaded
    case failed
}

struct MapPickerView: UIViewRepresentable {
    let initialCoordinate: CLLocationCoordinate2D
    let originalCoordinate: CLLocationCoordinate2D
    @Binding var selection: CLLocationCoordinate2D?
    @Binding var loadState: MapPickerLoadState
    @Environment(\.locale) private var locale

    init(
        initialCoordinate: CLLocationCoordinate2D,
        originalCoordinate: CLLocationCoordinate2D? = nil,
        selection: Binding<CLLocationCoordinate2D?>,
        loadState: Binding<MapPickerLoadState> = .constant(.loading)
    ) {
        self.initialCoordinate = initialCoordinate
        self.originalCoordinate = originalCoordinate ?? initialCoordinate
        _selection = selection
        _loadState = loadState
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(selection: $selection, loadState: $loadState, originalCoordinate: originalCoordinate)
    }

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero, styleURL: URL(string: "https://tiles.openfreemap.org/styles/positron"))
        map.setCenter(initialCoordinate, zoomLevel: 16, animated: false)
        map.delegate = context.coordinator
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.didTap(_:)))
        map.addGestureRecognizer(tap)
        context.coordinator.map = map
        context.coordinator.configureAccessibility(locale: locale)
        context.coordinator.updateAnnotations(selection)
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        context.coordinator.selection = $selection
        context.coordinator.loadState = $loadState
        context.coordinator.updateAnnotations(selection)
        context.coordinator.configureAccessibility(locale: locale)
    }

    @MainActor
    final class Coordinator: NSObject, @preconcurrency MLNMapViewDelegate {
        var selection: Binding<CLLocationCoordinate2D?>
        var loadState: Binding<MapPickerLoadState>
        let originalCoordinate: CLLocationCoordinate2D
        weak var map: MLNMapView?
        private let originalAnnotation = MLNPointAnnotation()
        private let selectedAnnotation = MLNPointAnnotation()

        init(
            selection: Binding<CLLocationCoordinate2D?>,
            loadState: Binding<MapPickerLoadState>,
            originalCoordinate: CLLocationCoordinate2D
        ) {
            self.selection = selection
            self.loadState = loadState
            self.originalCoordinate = originalCoordinate
            super.init()
            originalAnnotation.coordinate = originalCoordinate
            originalAnnotation.title = "Original"
            selectedAnnotation.title = "Selected"
        }

        @objc func didTap(_ recognizer: UITapGestureRecognizer) {
            guard let map else { return }
            let point = recognizer.location(in: map)
            let selected = map.convert(point, toCoordinateFrom: map)
            selection.wrappedValue = selected
            updateAnnotations(selected)
        }

        func mapViewDidFinishLoadingMap(_ mapView: MLNMapView) {
            loadState.wrappedValue = .loaded
        }

        func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: any Error) {
            loadState.wrappedValue = .failed
        }

        func mapView(_ mapView: MLNMapView, viewFor annotation: any MLNAnnotation) -> MLNAnnotationView? {
            let isOriginal = annotation === originalAnnotation
            let identifier = isOriginal ? "original" : "selected"
            let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier)
                ?? MLNAnnotationView(reuseIdentifier: identifier)
            view.frame = CGRect(x: 0, y: 0, width: isOriginal ? 18 : 22, height: isOriginal ? 18 : 22)
            view.backgroundColor = isOriginal ? UIColor(PpColor.forest) : UIColor(PpColor.danger)
            view.layer.cornerRadius = view.frame.width / 2
            view.layer.borderWidth = 3
            view.layer.borderColor = UIColor.white.cgColor
            return view
        }

        func configureAccessibility(locale: Locale) {
            guard let map else { return }
            map.isAccessibilityElement = true
            map.accessibilityLabel = String(localized: "map.accessibility", locale: locale)
            map.accessibilityHint = String(localized: "map.accessibility.hint", locale: locale)
            map.accessibilityCustomActions = [
                UIAccessibilityCustomAction(
                    name: String(localized: "map.accessibility.select", locale: locale),
                    target: self,
                    selector: #selector(selectCenter)
                ),
                UIAccessibilityCustomAction(
                    name: String(localized: "map.accessibility.north", locale: locale),
                    target: self,
                    selector: #selector(moveNorth)
                ),
                UIAccessibilityCustomAction(
                    name: String(localized: "map.accessibility.south", locale: locale),
                    target: self,
                    selector: #selector(moveSouth)
                ),
                UIAccessibilityCustomAction(
                    name: String(localized: "map.accessibility.east", locale: locale),
                    target: self,
                    selector: #selector(moveEast)
                ),
                UIAccessibilityCustomAction(
                    name: String(localized: "map.accessibility.west", locale: locale),
                    target: self,
                    selector: #selector(moveWest)
                ),
            ]
            updateAccessibilityValue()
        }

        @objc private func selectCenter() -> Bool {
            guard let map else { return false }
            selection.wrappedValue = map.centerCoordinate
            updateAnnotations(map.centerCoordinate)
            return true
        }

        @objc private func moveNorth() -> Bool { move(latitude: 0.0005, longitude: 0) }
        @objc private func moveSouth() -> Bool { move(latitude: -0.0005, longitude: 0) }
        @objc private func moveEast() -> Bool { move(latitude: 0, longitude: 0.0005) }
        @objc private func moveWest() -> Bool { move(latitude: 0, longitude: -0.0005) }

        private func move(latitude: Double, longitude: Double) -> Bool {
            guard let map else { return false }
            let current = map.centerCoordinate
            let moved = CLLocationCoordinate2D(
                latitude: min(90, max(-90, current.latitude + latitude)),
                longitude: min(180, max(-180, current.longitude + longitude))
            )
            map.setCenter(moved, animated: true)
            selection.wrappedValue = moved
            updateAnnotations(moved)
            return true
        }

        func updateAnnotations(_ value: CLLocationCoordinate2D?) {
            guard let map else { return }
            if map.annotations?.contains(where: { $0 === originalAnnotation }) != true {
                map.addAnnotation(originalAnnotation)
            }
            if let value {
                selectedAnnotation.coordinate = value
                if map.annotations?.contains(where: { $0 === selectedAnnotation }) != true {
                    map.addAnnotation(selectedAnnotation)
                }
            } else if map.annotations?.contains(where: { $0 === selectedAnnotation }) == true {
                map.removeAnnotation(selectedAnnotation)
            }
            updateAccessibilityValue()
        }

        private func updateAccessibilityValue() {
            guard let map else { return }
            let coordinate = selection.wrappedValue ?? map.centerCoordinate
            map.accessibilityValue = String(format: "%.6f, %.6f", coordinate.latitude, coordinate.longitude)
        }
    }
}
