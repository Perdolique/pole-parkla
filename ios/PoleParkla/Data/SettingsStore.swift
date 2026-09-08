import Foundation
import Observation

@MainActor
@Observable
final class SettingsStore {
    private(set) var value: AppSettings
    private let defaults: UserDefaults
    private let sensitiveURL: URL
    private let fileManager: FileManager
    private let encoder = JSONEncoder()

    init(
        defaults: UserDefaults = .standard,
        sensitiveURL: URL? = nil,
        fileManager: FileManager = .default
    ) throws {
        self.defaults = defaults
        self.fileManager = fileManager
        self.sensitiveURL = try sensitiveURL ?? Self.defaultSensitiveURL(fileManager: fileManager)
        let decoder = JSONDecoder()

        let publicSettings = defaults.data(forKey: Self.publicSettingsKey)
            .flatMap { try? decoder.decode(PublicSettings.self, from: $0) }
        let sensitiveSettings = try Self.readSensitiveSettings(
            at: self.sensitiveURL,
            fileManager: fileManager,
            decoder: decoder
        )
        if let publicSettings, let sensitiveSettings {
            value = AppSettings(
                onboardingComplete: publicSettings.onboardingComplete,
                languageTag: publicSettings.languageTag,
                profile: sensitiveSettings.profile,
                defaultRecipient: sensitiveSettings.defaultRecipient
            )
        } else if let legacyData = defaults.data(forKey: Self.legacySettingsKey),
                  let legacy = try? decoder.decode(AppSettings.self, from: legacyData)
        {
            value = legacy
        } else {
            value = AppSettings()
        }
        try persist()
        defaults.removeObject(forKey: Self.legacySettingsKey)
    }

    func update(_ mutate: (inout AppSettings) -> Void) throws {
        let previous = value
        mutate(&value)
        do {
            try persist()
        } catch {
            value = previous
            throw error
        }
    }

    func reset() throws {
        if fileManager.fileExists(atPath: sensitiveURL.path) {
            try fileManager.removeItem(at: sensitiveURL)
        }
        defaults.removeObject(forKey: Self.publicSettingsKey)
        defaults.removeObject(forKey: Self.legacySettingsKey)
        value = AppSettings()
    }

    private func persist() throws {
        let publicData = try encoder.encode(PublicSettings(settings: value))
        let sensitiveData = try encoder.encode(SensitiveSettings(settings: value))
        try Self.writeProtected(
            sensitiveData,
            to: sensitiveURL,
            fileManager: fileManager
        )
        defaults.set(publicData, forKey: Self.publicSettingsKey)
    }

    private static func defaultSensitiveURL(fileManager: FileManager) throws -> URL {
        let applicationSupport = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return applicationSupport
            .appendingPathComponent("PoleParkla", isDirectory: true)
            .appendingPathComponent("Settings.json")
    }

    private static func readSensitiveSettings(
        at url: URL,
        fileManager: FileManager,
        decoder: JSONDecoder
    ) throws -> SensitiveSettings? {
        guard fileManager.fileExists(atPath: url.path) else { return nil }
        return try decoder.decode(SensitiveSettings.self, from: Data(contentsOf: url))
    }

    private static func writeProtected(
        _ data: Data,
        to url: URL,
        fileManager: FileManager
    ) throws {
        let directory = url.deletingLastPathComponent()
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        var protectedDirectory = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try protectedDirectory.setResourceValues(values)
        try fileManager.setAttributes(
            [.protectionKey: FileProtectionType.complete],
            ofItemAtPath: directory.path
        )
        try data.write(to: url, options: [.atomic, .completeFileProtection])
        var protectedFile = url
        try protectedFile.setResourceValues(values)
    }

    private static let legacySettingsKey = "pole-parkla.settings.v1"
    private static let publicSettingsKey = "pole-parkla.settings.public.v2"
}

private struct PublicSettings: Codable {
    var onboardingComplete: Bool
    var languageTag: String

    init(settings: AppSettings) {
        onboardingComplete = settings.onboardingComplete
        languageTag = settings.languageTag
    }
}

private struct SensitiveSettings: Codable {
    var profile: ReporterProfile
    var defaultRecipient: String

    init(settings: AppSettings) {
        profile = settings.profile
        defaultRecipient = settings.defaultRecipient
    }
}
