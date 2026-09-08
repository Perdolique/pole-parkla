import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
import UIKit
@testable import PoleParkla

@Suite("Photo storage", .serialized)
struct PhotoStoreIntegrationTests {
    @Test func originalAndSmallMailCopyRemainByteExact() async throws {
        let store = try PhotoStore()
        let data = try smallPNGData()
        #expect(data.count < PhotoStore.mailMaximumBytes)
        let reportID = UUID().uuidString
        defer { Task { try? await store.deleteReportDirectory(reportID: reportID) } }
        let imported = try await store.importPhoto(data: data, reportID: reportID, preferredExtension: "jpg")
        #expect((imported.relativePath as NSString).pathExtension == "png")
        let photo = ReportPhoto(
            id: "photo",
            reportID: reportID,
            relativePath: imported.relativePath,
            source: .gallery,
            capturedAt: imported.capturedAt,
            isPrimary: true
        )
        #expect(try await store.data(for: photo) == data)
        let metadata = try await store.metadata(for: photo)
        #expect(metadata.capturedAt == nil)
        let copy = try #require(try await store.prepareMailCopies(photos: [photo]).first)
        #expect(try Data(contentsOf: copy) == data)
    }

    @Test func recompressedMailCopyKeepsEssentialMetadataAndLimits() async throws {
        let store = try PhotoStore()
        let data = try jpegWithMetadata(width: 3_000, height: 2_000)
        let reportID = UUID().uuidString
        defer { Task { try? await store.deleteReportDirectory(reportID: reportID) } }
        let imported = try await store.importPhoto(data: data, reportID: reportID)
        let photo = ReportPhoto(
            id: "photo",
            reportID: reportID,
            relativePath: imported.relativePath,
            source: .gallery,
            capturedAt: imported.capturedAt,
            isPrimary: true
        )
        let copy = try #require(try await store.prepareMailCopies(photos: [photo]).first)
        let output = try Data(contentsOf: copy)
        #expect(output.count <= PhotoStore.mailMaximumBytes)
        let source = try #require(CGImageSourceCreateWithData(output as CFData, nil))
        let properties = try #require(CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any])
        #expect(max(properties[kCGImagePropertyPixelWidth] as? Int ?? 0, properties[kCGImagePropertyPixelHeight] as? Int ?? 0) <= PhotoStore.mailMaximumLongSide)
        #expect(properties[kCGImagePropertyGPSDictionary] != nil)
        let exif = properties[kCGImagePropertyExifDictionary] as? [CFString: Any]
        #expect(exif?[kCGImagePropertyExifDateTimeOriginal] != nil)
        #expect(try await store.data(for: photo) == data)
    }

    @Test func deletingPhotoRemovesItsOriginalAndMailDerivative() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("Pole Parkla Photo Delete-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try PhotoStore(rootURL: root)
        let data = try jpegWithMetadata()
        let imported = try await store.importPhoto(data: data, reportID: "report")
        let photo = ReportPhoto(
            id: "photo",
            reportID: "report",
            relativePath: imported.relativePath,
            source: .gallery,
            capturedAt: imported.capturedAt,
            isPrimary: true
        )
        let original = try await store.url(for: photo.relativePath)
        let mail = try #require(try await store.prepareMailCopies(photos: [photo]).first)
        #expect(FileManager.default.fileExists(atPath: original.path))
        #expect(FileManager.default.fileExists(atPath: mail.path))

        try await store.delete(photo)

        #expect(!FileManager.default.fileExists(atPath: original.path))
        #expect(!FileManager.default.fileExists(atPath: mail.path))
    }

    @Test func originalsUseCompleteProtectionAndStayOutOfDeviceBackup() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaPhotoProtection-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try PhotoStore(rootURL: root)
        let imported = try await store.importPhoto(data: smallPNGData(), reportID: "report")
        let original = try await store.url(for: imported.relativePath)

        let resources = try root.resourceValues(forKeys: [.isExcludedFromBackupKey])
        #expect(resources.isExcludedFromBackup == true)
        let rootAttributes = try FileManager.default.attributesOfItem(atPath: root.path)
        #expect(rootAttributes[.protectionKey] as? FileProtectionType == .complete)
        let photoAttributes = try FileManager.default.attributesOfItem(atPath: original.path)
        #expect(photoAttributes[.protectionKey] as? FileProtectionType == .complete)
    }

    @Test func startupReconciliationRemovesOrphanedOriginalsAndTemporaryCopies() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaReconciliation-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try PhotoStore(rootURL: root)
        let live = try await store.importPhoto(data: smallPNGData(), reportID: "live")
        let orphan = try await store.importPhoto(data: smallPNGData(), reportID: "orphan")
        let liveURL = try await store.url(for: live.relativePath)
        let orphanURL = try await store.url(for: orphan.relativePath)
        let directory = root.appendingPathComponent("TemporaryCopies/live", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let mail = directory.appendingPathComponent("mail-photo.png")
        try Data("mail".utf8).write(to: mail)

        try await store.reconcile(references: [
            StoredPhotoReference(reportID: "live", relativePath: live.relativePath),
        ])

        #expect(FileManager.default.fileExists(atPath: liveURL.path))
        #expect(!FileManager.default.fileExists(atPath: orphanURL.path))
        #expect(!FileManager.default.fileExists(atPath: mail.path))
    }

    @Test func plateCropAddsPaddingClampsEdgesAndStaysWide() throws {
        let center = try #require(PlateCropGeometry.rectangle(
            bounds: NormalizedPhotoRect(left: 0.2, top: 0.4, right: 0.8, bottom: 0.5),
            pixelWidth: 1_000,
            pixelHeight: 500
        ))
        #expect(abs(center.minX - 128) <= 1)
        #expect(abs(center.maxX - 872) <= 1)
        #expect(abs(center.minY - 182) <= 1)
        #expect(abs(center.maxY - 268) <= 1)
        #expect(center.width > 600)
        #expect(center.height > 50)
        #expect(center.width / center.height > 8)

        let edge = try #require(PlateCropGeometry.rectangle(
            bounds: NormalizedPhotoRect(left: 0, top: 0, right: 0.2, bottom: 0.2),
            pixelWidth: 1_000,
            pixelHeight: 500
        ))
        #expect(edge.minX == 0)
        #expect(edge.minY == 0)
        #expect(edge.maxX <= 1_000)
        #expect(edge.maxY <= 500)
    }

    @Test func deletingAllPhotosPreservesSiblingPersistentStore() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("PoleParklaPhotoReset-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try PhotoStore(rootURL: root)
        let storeURL = root.appendingPathComponent("pole-parkla.store")
        let storeData = Data("database".utf8)
        try storeData.write(to: storeURL)
        _ = try await store.importPhoto(data: smallPNGData(), reportID: "report")

        try await store.deleteAll()

        #expect(try Data(contentsOf: storeURL) == storeData)
        #expect(try FileManager.default.contentsOfDirectory(atPath: root.appendingPathComponent("Photos").path).isEmpty)
    }

    private func smallPNGData() throws -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 320, height: 240))
        let image = renderer.image { context in
            UIColor.darkGray.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 320, height: 240))
            UIColor.white.setFill()
            context.fill(CGRect(x: 80, y: 105, width: 160, height: 30))
        }
        return try #require(image.pngData())
    }

    private func jpegWithMetadata(width: Int = 640, height: Int = 480) throws -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height))
        let image = renderer.image { context in
            UIColor.darkGray.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
            UIColor.white.setFill()
            context.fill(CGRect(x: width / 4, y: height / 2, width: width / 2, height: height / 8))
        }
        let mutable = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(
            mutable,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ))
        let gps: [CFString: Any] = [
            kCGImagePropertyGPSLatitude: 59.437,
            kCGImagePropertyGPSLatitudeRef: "N",
            kCGImagePropertyGPSLongitude: 24.7536,
            kCGImagePropertyGPSLongitudeRef: "E",
        ]
        let exif: [CFString: Any] = [kCGImagePropertyExifDateTimeOriginal: "2026:08:13 16:58:00"]
        CGImageDestinationAddImage(
            destination,
            try #require(image.cgImage),
            [
                kCGImageDestinationLossyCompressionQuality: 0.95,
                kCGImagePropertyGPSDictionary: gps,
                kCGImagePropertyExifDictionary: exif,
            ] as CFDictionary
        )
        #expect(CGImageDestinationFinalize(destination))
        return mutable as Data
    }
}
