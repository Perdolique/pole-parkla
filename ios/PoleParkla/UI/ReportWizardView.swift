import CoreLocation
import SwiftUI

struct ReportWizardView: View {
    @Bindable var model: AppModel
    @State private var step = ReportWizardStep.vehicle

    var body: some View {
        NavigationStack {
            Group {
                if let report = model.selectedReport {
                    ScrollView {
                        VStack(spacing: 18) {
                            stepIndicator
                            switch step {
                            case .vehicle:
                                VehicleStep(model: model, report: report) { step = .place }
                            case .place:
                                PlaceStep(model: model, report: report) { step = .violation }
                            case .violation:
                                ViolationStep(model: model, report: report) { step = .summary }
                            case .summary:
                                SummaryStep(model: model, report: report)
                            }
                        }
                        .padding(20)
                    }
                    .safeAreaInset(edge: .bottom) { navigationControls }
                    .ppScreenBackground()
                } else {
                    ContentUnavailableView("error.storage", systemImage: "exclamationmark.triangle")
                }
            }
            .navigationTitle("report.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.close") {
                        guard !model.isPreparingMail else { return }
                        model.selectedReportID = nil
                    }
                    .disabled(model.isPreparingMail)
                }
            }
            .alert("common.notice", isPresented: Binding(
                get: { model.notice != nil },
                set: { if !$0 { model.notice = nil } }
            )) {
                Button("common.ok") { model.notice = nil }
            } message: { Text(model.notice ?? "") }
        }
        .interactiveDismissDisabled(model.isWorking)
        .onAppear {
            if let report = model.selectedReport { step = report.resolvedWizardStep }
        }
        .onDisappear {
            Task { await model.discardPreparedMail() }
        }
    }

    private var stepIndicator: some View {
        HStack(spacing: 6) {
            ForEach(ReportWizardStep.allCases, id: \.self) { item in
                Capsule()
                    .fill(item.rawValue <= step.rawValue ? PpColor.forest : PpColor.outlineVariant)
                    .frame(height: 7)
                    .accessibilityLabel(stepName(item))
                    .accessibilityValue(item == step ? "common.current" : "")
            }
        }
    }

    private var navigationControls: some View {
        VStack(spacing: 10) {
            if step == .summary, model.asksShareHandoffConfirmation {
                Text("mail.share.confirm.title").font(.headline)
                Text("mail.share.confirm.body").font(.footnote).foregroundStyle(PpColor.muted)
                HStack {
                    Button("common.no") { Task { await model.confirmShareHandoff(false) } }
                        .buttonStyle(PpSecondaryButtonStyle())
                    Button("common.yes") { Task { await model.confirmShareHandoff(true) } }
                        .buttonStyle(PpPrimaryButtonStyle())
                }
            } else {
                HStack {
                    if let previous = step.previous {
                        Button("common.back") { step = previous }
                            .buttonStyle(PpSecondaryButtonStyle())
                    }
                    if step == .summary, let report = model.selectedReport {
                        Button("mail.open") { Task { await model.prepareMail() } }
                            .buttonStyle(PpPrimaryButtonStyle())
                            .disabled(report.status == .draft || model.isPreparingMail)
                    }
                }
            }
        }
        .padding(16)
        .background(PpColor.canvas)
        .overlay(alignment: .top) { Divider().foregroundStyle(PpColor.outlineVariant) }
    }

    private func stepName(_ value: ReportWizardStep) -> LocalizedStringKey {
        switch value {
        case .vehicle: "report.step.vehicle"
        case .place: "report.step.place"
        case .violation: "report.step.violation"
        case .summary: "report.step.summary"
        }
    }
}

private struct VehicleStep: View {
    @Bindable var model: AppModel
    let report: Report
    let saved: () -> Void
    @State private var plate: String
    @State private var make: String
    @State private var vehicleModel: String
    @State private var loadedPlate: String
    @State private var loadedMake: String
    @State private var loadedModel: String
    @State private var optionalExpanded: Bool

    init(model: AppModel, report: Report, saved: @escaping () -> Void) {
        self.model = model
        self.report = report
        self.saved = saved
        _plate = State(initialValue: report.plate)
        _make = State(initialValue: report.vehicleMake)
        _vehicleModel = State(initialValue: report.vehicleModel)
        _loadedPlate = State(initialValue: report.plate)
        _loadedMake = State(initialValue: report.vehicleMake)
        _loadedModel = State(initialValue: report.vehicleModel)
        _optionalExpanded = State(initialValue: !report.vehicleMake.isEmpty || !report.vehicleModel.isEmpty)
    }

    var body: some View {
        VStack(spacing: 14) {
            PpCard {
                VStack(alignment: .leading, spacing: 14) {
                    Text("vehicle.title").font(.title2.bold())
                    Text("vehicle.confirm.hint").foregroundStyle(PpColor.muted)
                    if let evidencePhoto {
                        Text("vehicle.plate.evidence").font(.headline)
                        ReportPhotoEvidence(
                            photoStore: model.photoStore,
                            photo: evidencePhoto,
                            crop: evidenceObservation?.bounds
                        )
                    }
                    PpField(title: "vehicle.plate", text: $plate)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                    if !candidates.isEmpty {
                        Text("vehicle.recognized.variants").font(.subheadline.bold())
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) {
                                ForEach(candidates) { candidate in
                                    Button { plate = candidate.value } label: {
                                        candidateLabel(
                                            candidate,
                                            selected: PlateCandidateParser.normalize(plate) == candidate.value
                                        )
                                    }
                                        .buttonStyle(.plain)
                                        .accessibilityLabel(
                                            "\(model.localized("vehicle.candidate")) \(candidate.value), \(sourceText(candidate.sources)), \(candidate.supportingPhotoCount)"
                                        )
                                }
                            }
                        }
                    }
                    PpExpansionRow(
                        title: "vehicle.optional.details",
                        value: optionalSummary,
                        systemImage: "car.side",
                        expanded: optionalExpanded
                    ) {
                        withAnimation(.easeInOut(duration: 0.2)) { optionalExpanded.toggle() }
                    }
                    .accessibilityIdentifier("vehicle.optional.toggle")
                    if optionalExpanded {
                        PpField(title: "vehicle.make", text: $make)
                            .accessibilityIdentifier("vehicle.make")
                        PpField(title: "vehicle.model", text: $vehicleModel)
                            .accessibilityIdentifier("vehicle.model")
                    }
                }
            }
            Button("vehicle.save.confirm") {
                if !plate.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                   model.saveVehicle(plate: plate, make: make, model: vehicleModel)
                {
                    saved()
                }
            }
            .buttonStyle(PpPrimaryButtonStyle())
            if model.isWorking { ProgressView("recognition.working") }
        }
        .onChange(of: report.updatedAt) { _, _ in
            if !report.vehicleConfirmed, !isDirty { load() }
        }
    }

    private var isDirty: Bool {
        plate != loadedPlate || make != loadedMake || vehicleModel != loadedModel
    }

    private var candidates: [PlateCandidate] {
        aggregatePlateCandidates(report.plateObservations)
    }

    private var selectedCandidate: PlateCandidate? {
        exactPlateCandidate(plate: plate, observations: report.plateObservations) ?? candidates.first
    }

    private var evidenceObservation: PlateObservation? {
        selectedCandidate?.observations.first { $0.bounds?.isValid == true }
    }

    private var evidencePhoto: ReportPhoto? {
        if let evidenceObservation,
           let photo = report.photos.first(where: { $0.id == evidenceObservation.photoID })
        {
            return photo
        }
        return report.primaryPhoto
    }

    private var optionalSummary: String? {
        let summary = [make, vehicleModel]
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        return summary.isEmpty ? nil : summary
    }

    private func candidateLabel(_ candidate: PlateCandidate, selected: Bool) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(candidate.value).font(.headline.monospaced()).foregroundStyle(PpColor.ink)
            HStack(spacing: 8) {
                Text(sourceText(candidate.sources))
                Label("\(candidate.supportingPhotoCount)", systemImage: "photo.on.rectangle.angled")
            }
            .font(.caption)
            .foregroundStyle(PpColor.muted)
        }
        .padding(10)
        .frame(width: 190, alignment: .leading)
        .background(selected ? PpColor.signal.opacity(0.35) : PpColor.surfaceVariant, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(selected ? PpColor.forest : PpColor.outlineVariant, lineWidth: selected ? 2 : 1)
        )
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    private func sourceText(_ sources: Set<RecognitionSource>) -> String {
        var labels: [String] = []
        for source in sources.sorted(by: { $0.rawValue < $1.rawValue }) {
            let label = switch source {
            case .visionOCR: "Vision"
            case .localPlateModel: "ONNX"
            }
            if !labels.contains(label) { labels.append(label) }
        }
        return labels.joined(separator: " + ")
    }

    private func load() {
        plate = report.plate
        make = report.vehicleMake
        vehicleModel = report.vehicleModel
        loadedPlate = plate
        loadedMake = make
        loadedModel = vehicleModel
        if !make.isEmpty || !vehicleModel.isEmpty { optionalExpanded = true }
    }
}

private struct PlaceStep: View {
    @Bindable var model: AppModel
    let report: Report
    let saved: () -> Void
    @State private var draft: AddressDraft
    @State private var photoMetadata: PhotoMetadata?
    @State private var showingMap = false
    @State private var latitudeText: String
    @State private var longitudeText: String
    @State private var coordinatesExpanded = false
    @State private var coordinateError = false

    init(model: AppModel, report: Report, saved: @escaping () -> Void) {
        self.model = model
        self.report = report
        self.saved = saved
        _draft = State(initialValue: AddressDraft(
            address: report.address,
            latitude: report.latitude,
            longitude: report.longitude,
            accuracyMeters: report.accuracyMeters,
            occurredAt: report.occurredAt
        ))
        _latitudeText = State(initialValue: report.latitude.map { String($0) } ?? "")
        _longitudeText = State(initialValue: report.longitude.map { String($0) } ?? "")
    }

    var body: some View {
        VStack(spacing: 14) {
            PpCard {
                VStack(alignment: .leading, spacing: 14) {
                    Text("place.title").font(.title2.bold())
                    Text("place.confirm.hint").foregroundStyle(PpColor.muted)
                    PpField(title: "place.address", text: $draft.address, axis: .vertical)
                    addressCandidates(type: .street, title: "place.nearby.streets")
                    addressCandidates(type: .building, title: "place.nearby.addresses")
                    if draft.lookupFailed { PpNotice(text: model.localized("map.address.failed")) }
                    DatePicker("place.time", selection: $draft.occurredAt)
                        .datePickerStyle(.compact)
                    PpExpansionRow(
                        title: "place.coordinates",
                        value: coordinateSummary,
                        systemImage: "location",
                        expanded: coordinatesExpanded
                    ) {
                        withAnimation(.easeInOut(duration: 0.2)) { coordinatesExpanded.toggle() }
                    }
                    .accessibilityIdentifier("place.coordinates.toggle")
                    if coordinatesExpanded {
                        PpField(
                            title: "place.latitude",
                            text: coordinateBinding($latitudeText),
                            keyboard: .decimalPad,
                            isError: coordinateError
                        )
                        .accessibilityIdentifier("place.latitude")
                        PpField(
                            title: "place.longitude",
                            text: coordinateBinding($longitudeText),
                            keyboard: .decimalPad,
                            isError: coordinateError
                        )
                        .accessibilityIdentifier("place.longitude")
                        if coordinateError { PpNotice(text: model.localized("place.invalid.coordinates")) }
                    }
                }
            }

            HStack(spacing: 10) {
                if photoMetadata?.latitude != nil, photoMetadata?.longitude != nil {
                    Button("place.from.photo", action: usePhotoLocation)
                        .buttonStyle(PpSecondaryButtonStyle())
                }
                Button("place.current") { Task { await useCurrentLocation() } }
                    .buttonStyle(PpSecondaryButtonStyle())
            }
            Button("place.map") { showingMap = true }
                .buttonStyle(PpSecondaryButtonStyle())
                .accessibilityIdentifier("place.map.open")
            Button("place.confirm") {
                coordinateError = false
                if model.confirmLocation(
                    address: draft.address,
                    latitude: latitudeText,
                    longitude: longitudeText,
                    accuracyMeters: confirmedAccuracyMeters,
                    occurredAt: draft.occurredAt
                ) {
                    saved()
                } else if coordinatesAreInvalid {
                    coordinateError = true
                    withAnimation(.easeInOut(duration: 0.2)) { coordinatesExpanded = true }
                }
            }
            .buttonStyle(PpPrimaryButtonStyle())
        }
        .sheet(isPresented: $showingMap) {
            MapSelectionSheet(
                model: model,
                report: report,
                originalCoordinate: mapOrigin,
                currentAddress: draft.address
            ) { selection in
                draft = AddressDraftPolicy.applyingMapSelection(selection, to: draft)
                syncCoordinateText()
                coordinateError = false
                showingMap = false
            }
        }
        .task(id: report.primaryPhoto?.id) {
            photoMetadata = if let photo = report.primaryPhoto { await model.metadata(for: photo) } else { nil }
            guard let latitude = report.latitude, let longitude = report.longitude else { return }
            let location = LocationSnapshot(
                latitude: latitude,
                longitude: longitude,
                accuracyMeters: report.accuracyMeters,
                capturedAt: report.occurredAt
            )
            let resolution = await model.resolveAddress(reportID: report.id, location: location)
            guard coordinateMatches(location) else { return }
            draft = AddressDraftPolicy.applyingLookup(
                AddressLookupResult(location: location, resolution: resolution),
                to: draft,
                applyLocation: false
            )
        }
    }

    private var mapOrigin: CLLocationCoordinate2D {
        validCoordinate(latitude: latitudeText, longitude: longitudeText)
            ?? CLLocationCoordinate2D(latitude: 59.437, longitude: 24.7536)
    }

    private func useCurrentLocation() async {
        guard let result = await model.useCurrentLocation() else { return }
        draft = AddressDraftPolicy.applyingLookup(result, to: draft, applyLocation: true)
        syncCoordinateText()
        coordinateError = false
    }

    private func usePhotoLocation() {
        guard let latitude = photoMetadata?.latitude, let longitude = photoMetadata?.longitude else { return }
        if let capturedAt = photoMetadata?.capturedAt { draft.occurredAt = capturedAt }
        let location = LocationSnapshot(
            latitude: latitude,
            longitude: longitude,
            accuracyMeters: nil,
            capturedAt: draft.occurredAt
        )
        draft.latitude = latitude
        draft.longitude = longitude
        draft.accuracyMeters = nil
        syncCoordinateText()
        Task {
            let resolution = await model.resolveAddress(reportID: report.id, location: location)
            guard coordinateMatches(location) else { return }
            draft = AddressDraftPolicy.applyingLookup(
                AddressLookupResult(location: location, resolution: resolution),
                to: draft,
                applyLocation: true
            )
            syncCoordinateText()
            coordinateError = false
        }
    }

    private func syncCoordinateText() {
        latitudeText = draft.latitude.map { String($0) } ?? ""
        longitudeText = draft.longitude.map { String($0) } ?? ""
    }

    private func validCoordinate(latitude: String, longitude: String) -> CLLocationCoordinate2D? {
        let latitude = Double(latitude.replacingOccurrences(of: ",", with: "."))
        let longitude = Double(longitude.replacingOccurrences(of: ",", with: "."))
        guard let latitude, let longitude,
              latitude.isFinite, longitude.isFinite,
              (-90 ... 90).contains(latitude), (-180 ... 180).contains(longitude)
        else { return nil }
        return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    private func coordinateMatches(_ location: LocationSnapshot) -> Bool {
        guard let coordinate = validCoordinate(latitude: latitudeText, longitude: longitudeText) else { return false }
        return coordinate.latitude == location.latitude && coordinate.longitude == location.longitude
    }

    private func coordinateBinding(_ value: Binding<String>) -> Binding<String> {
        Binding(
            get: { value.wrappedValue },
            set: { newValue in
                value.wrappedValue = newValue
                draft = AddressDraftPolicy.clearingCandidatesForManualCoordinates(draft)
                coordinateError = false
                model.cancelAddressResolution()
            }
        )
    }

    @ViewBuilder
    private func addressCandidates(type: AddressCandidateType, title: LocalizedStringKey) -> some View {
        let candidates = draft.candidates.filter { $0.type == type }.prefix(3)
        if !candidates.isEmpty {
            Text(title).font(.subheadline.bold())
            ForEach(Array(candidates)) { candidate in
                Button { draft.address = candidate.address } label: {
                    HStack {
                        Text(candidate.address).foregroundStyle(PpColor.ink)
                        Spacer()
                        Text("\(candidate.distanceMeters) m").foregroundStyle(PpColor.muted)
                    }
                    .frame(minHeight: 48)
                    .padding(.horizontal, 12)
                    .background(PpColor.surfaceVariant, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(draft.address == candidate.address ? PpColor.forest : .clear, lineWidth: 2)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("place.candidate.\(candidate.id)")
            }
        }
    }

    private var coordinateSummary: String? {
        guard let accuracy = confirmedAccuracyMeters else { return nil }
        return String(
            format: model.localized("place.accuracy"),
            locale: model.settings.value.locale,
            Int64(accuracy.rounded())
        )
    }

    private var coordinatesAreInvalid: Bool {
        let latitude = latitudeText.trimmingCharacters(in: .whitespacesAndNewlines)
        let longitude = longitudeText.trimmingCharacters(in: .whitespacesAndNewlines)
        if latitude.isEmpty != longitude.isEmpty { return true }
        return !latitude.isEmpty && validCoordinate(latitude: latitude, longitude: longitude) == nil
    }

    private var confirmedAccuracyMeters: Double? {
        guard let coordinate = validCoordinate(latitude: latitudeText, longitude: longitudeText),
              let latitude = draft.latitude, let longitude = draft.longitude,
              coordinate.latitude == latitude, coordinate.longitude == longitude
        else { return nil }
        return draft.accuracyMeters
    }
}

private struct MapSelectionSheet: View {
    private enum LookupState: Equatable {
        case idle, loading
        case ready(AddressMapSelection)
        case failed(AddressMapSelection)
    }

    @Environment(\.dismiss) private var dismiss
    @Bindable var model: AppModel
    let report: Report
    let originalCoordinate: CLLocationCoordinate2D
    let currentAddress: String
    let selected: (AddressMapSelection) -> Void
    @State private var selection: CLLocationCoordinate2D?
    @State private var mapState = MapPickerLoadState.loading
    @State private var lookupState = LookupState.idle
    @State private var mapReloadToken = UUID()
    @State private var lookupTask: Task<Void, Never>?

    var body: some View {
        NavigationStack {
            ZStack {
                MapPickerView(
                    initialCoordinate: originalCoordinate,
                    originalCoordinate: originalCoordinate,
                    selection: $selection,
                    loadState: $mapState
                )
                .id(mapReloadToken)
                .ignoresSafeArea(edges: .bottom)

                if mapState == .failed {
                    PpCard {
                        VStack(spacing: 12) {
                            Text("map.failed").font(.headline)
                            Button("map.retry.map") {
                                mapState = .loading
                                mapReloadToken = UUID()
                            }
                            .buttonStyle(PpSecondaryButtonStyle())
                        }
                    }
                    .padding(24)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 10) {
                    Text("map.select.hint").font(.footnote).foregroundStyle(PpColor.muted)
                    switch lookupState {
                    case .loading:
                        ProgressView("map.address.loading")
                    case let .failed(selection):
                        PpNotice(text: model.localized("map.address.failed"))
                        Button("map.retry.address", action: retryAddress)
                            .buttonStyle(PpSecondaryButtonStyle())
                        Button("map.use.point") { selected(selection) }
                            .buttonStyle(PpPrimaryButtonStyle())
                    case let .ready(selection):
                        Text(selection.address).font(.subheadline.weight(.medium))
                        Button("map.use.address") { selected(selection) }
                            .buttonStyle(PpPrimaryButtonStyle())
                            .accessibilityIdentifier("map.use.address")
                    case .idle:
                        EmptyView()
                    }
                }
                .padding(16)
                .background(PpColor.canvas)
                .overlay(alignment: .top) { Divider().foregroundStyle(PpColor.outlineVariant) }
            }
            .navigationTitle("place.map")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("common.cancel") { dismiss() } } }
            .onChange(of: selection?.latitude) { _, _ in resolveSelection() }
            .onChange(of: selection?.longitude) { _, _ in resolveSelection() }
        }
        #if DEBUG
        .task {
            if ProcessInfo.processInfo.arguments.contains("--ui-testing-map-address") {
                selection = CLLocationCoordinate2D(latitude: 59.437, longitude: 24.7536)
            }
        }
        #endif
        .onDisappear {
            lookupTask?.cancel()
            model.cancelAddressResolution()
        }
    }

    private func resolveSelection() {
        lookupTask?.cancel()
        guard let selection else {
            lookupState = .idle
            return
        }
        lookupState = .loading
        let expectedLatitude = selection.latitude
        let expectedLongitude = selection.longitude
        lookupTask = Task {
            let location = LocationSnapshot(
                latitude: expectedLatitude,
                longitude: expectedLongitude,
                accuracyMeters: nil,
                capturedAt: .now
            )
            let resolution = await model.resolveAddress(reportID: report.id, location: location)
            guard !Task.isCancelled,
                  self.selection?.latitude == expectedLatitude,
                  self.selection?.longitude == expectedLongitude
            else { return }
            let mapSelection = AddressDraftPolicy.mapSelection(
                currentAddress: currentAddress,
                location: location,
                resolution: resolution
            )
            lookupState = mapSelection.addressLookupFailed ? .failed(mapSelection) : .ready(mapSelection)
        }
    }

    private func retryAddress() { resolveSelection() }
}

private struct ViolationStep: View {
    @Bindable var model: AppModel
    let report: Report
    let saved: () -> Void

    var body: some View {
        PpCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("violation.title").font(.title2.bold())
                Text("violation.tap.hint").foregroundStyle(PpColor.muted)
                PpChoiceRow(
                    title: "violation.cycle",
                    systemImage: "bicycle",
                    selected: report.violationType == .cyclePath
                ) {
                    model.chooseViolation(.cyclePath)
                    saved()
                }
                PpChoiceRow(
                    title: "violation.pedestrian",
                    systemImage: "figure.walk",
                    selected: report.violationType == .pedestrianPath
                ) {
                    model.chooseViolation(.pedestrianPath)
                    saved()
                }
                ForEach(model.templates) { template in
                    PpChoiceRow(
                        title: LocalizedStringKey(template.displayName),
                        supportingText: template.estonianDescription,
                        systemImage: "doc.text",
                        selected: report.customTemplateID == template.id
                    ) {
                        model.chooseViolation(.custom, customTemplateID: template.id)
                        saved()
                    }
                }
            }
        }
    }
}

private enum SummaryEditor: String, Identifiable {
    case photos, vehicle, location, problem, recipient, sender
    var id: String { rawValue }
}

private struct SummaryStep: View {
    @Bindable var model: AppModel
    let report: Report
    @State private var editor: SummaryEditor?

    var body: some View {
        VStack(spacing: 12) {
            PpBadge(
                text: report.status == .draft ? "summary.incomplete" : "summary.ready",
                isComplete: report.status != .draft,
                isError: report.status == .draft
            )
            PpDisclosureRow(title: "report.photos", value: "\(report.photos.count)/\(maximumReportPhotos)", systemImage: "photo.stack") {
                editor = .photos
            }
            .accessibilityIdentifier("summary.photos")
            PpDisclosureRow(title: "report.step.vehicle", value: vehicleText, systemImage: "car.side", complete: report.vehicleConfirmed) {
                editor = .vehicle
            }
            .accessibilityIdentifier("summary.vehicle")
            PpDisclosureRow(title: "place.title", value: locationText, systemImage: "mappin.and.ellipse", complete: report.locationConfirmed) {
                editor = .location
            }
            .accessibilityIdentifier("summary.location")
            PpDisclosureRow(title: "summary.violation", value: violationText, systemImage: "exclamationmark.bubble", complete: report.violationType != nil) {
                editor = .problem
            }
            .accessibilityIdentifier("summary.problem")
            PpDisclosureRow(title: "settings.recipient", value: report.recipient, systemImage: "envelope") {
                editor = .recipient
            }
            .accessibilityIdentifier("summary.recipient")
            PpDisclosureRow(title: "summary.sender", value: senderText, systemImage: "person.crop.circle") {
                editor = .sender
            }
            .accessibilityIdentifier("summary.sender")
            PpCard {
                VStack(alignment: .leading, spacing: 6) {
                    Label("summary.subject", systemImage: "text.quote").font(.headline)
                    Text(report.subject.isEmpty ? "—" : report.subject).foregroundStyle(PpColor.muted)
                }
            }
            if model.settings.value.profile.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                model.settings.value.profile.phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            {
                PpNotice(text: model.localized("summary.profile.missing"))
            }
            Text("mail.never.sends")
                .font(.footnote)
                .foregroundStyle(PpColor.muted)
                .multilineTextAlignment(.center)
        }
        .sheet(item: $editor) { editor in
            switch editor {
            case .photos:
                PhotoSummarySheet(model: model, reportID: report.id)
            case .vehicle:
                SummaryEditSheet(title: "report.step.vehicle") { dismiss in
                    VehicleStep(model: model, report: model.selectedReport ?? report, saved: dismiss)
                }
            case .location:
                SummaryEditSheet(title: "report.step.place") { dismiss in
                    PlaceStep(model: model, report: model.selectedReport ?? report, saved: dismiss)
                }
            case .problem:
                SummaryEditSheet(title: "report.step.violation") { dismiss in
                    ViolationStep(model: model, report: model.selectedReport ?? report, saved: dismiss)
                }
            case .recipient:
                ReportRecipientSheet(model: model, recipient: report.recipient)
            case .sender:
                ReportSenderSheet(model: model)
            }
        }
        .sheet(isPresented: $model.presentingMailComposer) {
            if let payload = model.mailPayload {
                MailComposerView(payload: payload) { result in Task { await model.mailComposerFinished(result) } }
            }
        }
        .sheet(isPresented: $model.presentingShareSheet) {
            if let payload = model.mailPayload { ShareSheetView(payload: payload, completion: model.shareSheetReturned) }
        }
    }

    private var vehicleText: String {
        let details = [report.plate, report.vehicleMake, report.vehicleModel].filter { !$0.isEmpty }
        return details.isEmpty ? "—" : details.joined(separator: " · ")
    }

    private var locationText: String {
        let location = report.address.isEmpty ? coordinateText : report.address
        let time = report.occurredAt.formatted(
            Date.FormatStyle(date: .abbreviated, time: .shortened).locale(model.settings.value.locale)
        )
        return "\(location)\n\(time)"
    }

    private var coordinateText: String {
        guard let latitude = report.latitude, let longitude = report.longitude else { return "—" }
        return String(format: "%.6f, %.6f", latitude, longitude)
    }

    private var violationText: String {
        switch report.violationType {
        case .cyclePath: model.localized("violation.cycle")
        case .pedestrianPath: model.localized("violation.pedestrian")
        case .custom: model.templates.first(where: { $0.id == report.customTemplateID })?.displayName ?? "—"
        case nil: "—"
        }
    }

    private var senderText: String {
        let profile = model.settings.value.profile
        let value = [profile.name, profile.phone].filter { !$0.isEmpty }.joined(separator: " · ")
        return value.isEmpty ? "—" : value
    }
}

private struct SummaryEditSheet<Content: View>: View {
    @Environment(\.dismiss) private var dismiss
    let title: LocalizedStringKey
    @ViewBuilder let content: (@escaping () -> Void) -> Content

    var body: some View {
        NavigationStack {
            ScrollView { content { dismiss() }.padding(20) }
                .scrollDismissesKeyboard(.interactively)
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("common.cancel") { dismiss() } } }
                .ppScreenBackground()
        }
    }
}

private struct PhotoSummarySheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var model: AppModel
    let reportID: String
    @State private var pendingDeletion: ReportPhoto?

    private var report: Report? { model.reports.first { $0.id == reportID } }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    if let report {
                        ForEach(report.photos) { photo in
                            HStack(spacing: 14) {
                                ReportPhotoThumbnail(photoStore: model.photoStore, photo: photo, size: 92, cornerRadius: 14)
                                Text(photo.capturedAt.formatted(
                                    Date.FormatStyle(date: .abbreviated, time: .shortened).locale(model.settings.value.locale)
                                ))
                                .font(.subheadline)
                                Spacer()
                                Button(role: .destructive) { pendingDeletion = photo } label: {
                                    Image(systemName: "trash").frame(width: 48, height: 48)
                                }
                                .accessibilityLabel("report.photo.delete")
                            }
                            .padding(12)
                            .background(PpColor.surface, in: RoundedRectangle(cornerRadius: 18))
                        }
                        if report.photos.count == maximumReportPhotos {
                            PpNotice(text: model.localized("camera.limit.replace"))
                        } else {
                            Button("report.photo.add") {
                                dismiss()
                                model.continueCameraSession(reportID: reportID)
                            }
                            .buttonStyle(PpPrimaryButtonStyle())
                            .accessibilityIdentifier("summary.photos.add")
                        }
                    }
                }
                .padding(20)
            }
            .navigationTitle("report.photos")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("common.done") { dismiss() } }
            .confirmationDialog(
                "report.photo.delete.confirm.title",
                isPresented: Binding(get: { pendingDeletion != nil }, set: { if !$0 { pendingDeletion = nil } }),
                titleVisibility: .visible
            ) {
                Button("report.photo.delete", role: .destructive) {
                    guard let pendingDeletion else { return }
                    Task {
                        let removed = await model.removePhoto(pendingDeletion)
                        self.pendingDeletion = nil
                        if removed, model.selectedReportID == nil { dismiss() }
                    }
                }
                Button("common.cancel", role: .cancel) { pendingDeletion = nil }
            } message: { Text("report.photo.delete.confirm.body") }
            .ppScreenBackground()
        }
    }
}

private struct ReportRecipientSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var model: AppModel
    @State private var recipient: String

    init(model: AppModel, recipient: String) {
        self.model = model
        _recipient = State(initialValue: recipient)
    }

    var body: some View {
        SimpleReportEditSheet(title: "settings.recipient", canSave: !recipient.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
            if model.saveReportRecipient(recipient) { dismiss() }
        } content: {
            PpField(title: "settings.recipient", text: $recipient, keyboard: .emailAddress)
                .textContentType(.emailAddress)
        }
    }
}

private struct ReportSenderSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var model: AppModel
    @State private var profile: ReporterProfile

    init(model: AppModel) {
        self.model = model
        _profile = State(initialValue: model.settings.value.profile)
    }

    var body: some View {
        SimpleReportEditSheet(title: "summary.sender", canSave: canSave) {
            model.saveProfile(profile)
            dismiss()
        } content: {
            VStack(spacing: 12) {
                PpField(title: "settings.name", text: $profile.name).textContentType(.name)
                PpField(title: "settings.phone", text: $profile.phone, keyboard: .phonePad).textContentType(.telephoneNumber)
            }
        }
    }

    private var canSave: Bool {
        !profile.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !profile.phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

private struct SimpleReportEditSheet<Content: View>: View {
    @Environment(\.dismiss) private var dismiss
    let title: LocalizedStringKey
    let canSave: Bool
    let save: () -> Void
    @ViewBuilder let content: Content

    var body: some View {
        NavigationStack {
            ScrollView { content.padding(20) }
                .scrollDismissesKeyboard(.interactively)
                .safeAreaInset(edge: .bottom) {
                    Button("common.save", action: save)
                        .buttonStyle(PpPrimaryButtonStyle())
                        .disabled(!canSave)
                        .padding(20)
                        .background(PpColor.canvas)
                }
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("common.cancel") { dismiss() } } }
                .ppScreenBackground()
        }
    }
}
