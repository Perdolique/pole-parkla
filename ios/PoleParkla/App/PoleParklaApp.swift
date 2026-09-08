import OSLog
import SwiftData
import SwiftUI

@main
struct PoleParklaApp: App {
    private let logger = Logger(subsystem: "com.perdolique.poleparkla", category: "startup")
    @State private var model: AppModel?
    @State private var startupError: String?

    var body: some Scene {
        WindowGroup {
            Group {
                if let model {
                    RootView(model: model)
                        .environment(\.locale, configuredLocale(model.settings.value.languageTag))
                } else if let startupError {
                    ContentUnavailableView(
                        "startup.failed",
                        systemImage: "exclamationmark.triangle",
                        description: Text(startupError)
                    )
                } else {
                    ProgressView("startup.loading")
                        .task { await bootstrap() }
                }
            }
        }
    }

    @MainActor
    private func bootstrap() async {
        do {
            #if DEBUG
            let isUITesting = ProcessInfo.processInfo.arguments.contains("--ui-testing")
            let uiTestRoot = isUITesting ? try uiTestRootURL() : nil
            let persistence = try (isUITesting ? PersistenceController.inMemory() : PersistenceController.live())
            let settings = if let uiTestRoot {
                try SettingsStore(
                    defaults: uiTestDefaults(),
                    sensitiveURL: uiTestRoot.appendingPathComponent("Settings.json")
                )
            } else {
                try SettingsStore()
            }
            #else
            let persistence = try PersistenceController.live()
            let settings = try SettingsStore()
            #endif
            let repository = ReportRepository(container: persistence.container)
            #if DEBUG
            let photoStore = try PhotoStore(rootURL: uiTestRoot)
            #else
            let photoStore = try PhotoStore()
            #endif
            try await photoStore.reconcile(references: repository.photoReferences())
            #if DEBUG
            let recognition = isUITesting ? nil : RecognitionCoordinator(photoStore: photoStore)
            let addressService: any AddressResolving = if isUITesting,
                ProcessInfo.processInfo.arguments.contains("--ui-testing-map-address")
            {
                UITestAddressService()
            } else {
                InAksAddressService()
            }
            #else
            let recognition = RecognitionCoordinator(photoStore: photoStore)
            let addressService: any AddressResolving = InAksAddressService()
            #endif
            let appModel = AppModel(
                repository: repository,
                settings: settings,
                photoStore: photoStore,
                addressService: addressService,
                recognition: recognition
            )
            #if DEBUG
            if let uiTestRoot { seedUITestState(model: appModel, repository: repository, photoRoot: uiTestRoot) }
            #endif
            model = appModel
            await appModel.resumePendingLocalRecognition()
        } catch {
            logger.error("Bootstrap failed: \(String(reflecting: error), privacy: .public)")
            startupError = String(localized: "startup.failed.body")
        }
    }

    #if DEBUG
    private func uiTestDefaults() -> UserDefaults {
        let suiteName = "com.perdolique.poleparkla.ui-testing"
        guard let defaults = UserDefaults(suiteName: suiteName) else {
            preconditionFailure("Could not create UI-test UserDefaults suite")
        }
        defaults.removePersistentDomain(forName: suiteName)
        return defaults
    }

    private func uiTestRootURL() throws -> URL {
        let directory = URL(fileURLWithPath: "/tmp/PoleParklaUITesting", isDirectory: true)
        if FileManager.default.fileExists(atPath: directory.path) {
            try FileManager.default.removeItem(at: directory)
        }
        return directory
    }

    @MainActor
    private func seedUITestState(model: AppModel, repository: ReportRepository, photoRoot: URL) {
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("--ui-testing-onboarding") { return }
        try? model.settings.update { value in
            value.onboardingComplete = true
            value.profile = ReporterProfile(name: "UI Tester", phone: "+3725555555")
        }
        guard arguments.contains("--ui-testing-report") ||
            arguments.contains("--ui-testing-report-three") ||
            arguments.contains("--ui-testing-camera-one") ||
            arguments.contains("--ui-testing-camera-three") ||
            arguments.contains("--ui-testing-history") ||
            arguments.contains("--ui-testing-ready")
        else { return }
        let reportID = "ui-test-report"
        let count = arguments.contains("--ui-testing-report-three") || arguments.contains("--ui-testing-camera-three") ? 3 : 1
        let photos = (0 ..< count).map { index in
            ReportPhoto(
                id: "ui-photo-\(index)",
                reportID: reportID,
                relativePath: "Photos/ui-test-report/ui-photo-\(index).jpg",
                source: .gallery,
                capturedAt: Date(timeIntervalSince1970: Double(index)),
                isPrimary: index == 0
            )
        }
        if let data = Data(base64Encoded: "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAX/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABBQJ//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPwF//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPwF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQAGPwJ//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPyF//9oADAMBAAIAAwAAABAf/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPxB//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPxB//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxB//9k=") {
            let directory = photoRoot
                .appendingPathComponent("Photos", isDirectory: true)
                .appendingPathComponent(reportID, isDirectory: true)
            try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            for photo in photos { try? data.write(to: photoRoot.appendingPathComponent(photo.relativePath)) }
        }
        try? repository.createDraft(
            id: reportID,
            photos: photos,
            settings: model.settings.value,
            location: nil,
            occurredAt: Date(timeIntervalSince1970: 1_700_000_000),
            locationNeedsReview: true
        )
        model.reload()
        if arguments.contains("--ui-testing-camera-one") || arguments.contains("--ui-testing-camera-three") {
            model.continueCameraSession(reportID: reportID)
        } else if arguments.contains("--ui-testing-history") {
            model.selectedReportID = nil
            model.selectedTab = .history
        } else {
            model.selectedReportID = reportID
        }
        if arguments.contains("--ui-testing-ready") {
            model.saveVehicle(plate: "003 PUK", make: "Toyota", model: "Corolla")
            model.confirmLocation(AddressDraft(
                address: "Lastekodu tn 42, Tallinn",
                latitude: 59.437,
                longitude: 24.7536,
                occurredAt: Date(timeIntervalSince1970: 1_700_000_000)
            ))
            model.chooseViolation(.cyclePath)
        }
    }
    #endif

    private func configuredLocale(_ tag: String) -> Locale {
        tag.isEmpty ? .autoupdatingCurrent : Locale(identifier: tag)
    }
}

#if DEBUG
private struct UITestAddressService: AddressResolving {
    func resolve(_ location: LocationSnapshot) async throws -> AddressResolution {
        let street = AddressCandidate(
            address: "Lastekodu tn, Tallinn",
            distanceMeters: 4,
            type: .street
        )
        let building = AddressCandidate(
            address: "Lastekodu tn 42, Tallinn",
            distanceMeters: 7,
            type: .building
        )
        return AddressResolution(
            location: location,
            candidates: [street, building],
            suggested: building,
            needsReview: true
        )
    }
}
#endif

struct RootView: View {
    @Bindable var model: AppModel

    var body: some View {
        if model.settings.value.onboardingComplete {
            MainTabs(model: model)
                .fullScreenCover(
                    isPresented: Binding(
                        get: { model.selectedReportID != nil },
                        set: { if !$0 { model.selectedReportID = nil } }
                    )
                ) {
                    if model.selectedReport != nil { ReportWizardView(model: model) }
                }
                .alert("common.notice", isPresented: Binding(
                    get: { model.selectedReportID == nil && model.notice != nil },
                    set: { if !$0 { model.notice = nil } }
                )) {
                    Button("common.ok") { model.notice = nil }
                } message: { Text(model.notice ?? "") }
        } else {
            OnboardingView(model: model)
        }
    }
}

struct MainTabs: View {
    @Bindable var model: AppModel

    var body: some View {
        TabView(selection: $model.selectedTab) {
            NavigationStack { CameraScreen(model: model) }
                .tabItem { Label("tab.camera", systemImage: "camera.fill") }
                .tag(AppTab.camera)
            NavigationStack { HistoryScreen(model: model) }
                .tabItem { Label("tab.history", systemImage: "clock.arrow.circlepath") }
                .tag(AppTab.history)
            NavigationStack { SettingsScreen(model: model) }
                .tabItem { Label("tab.settings", systemImage: "gearshape.fill") }
                .tag(AppTab.settings)
        }
        .tint(PpColor.forest)
    }
}
