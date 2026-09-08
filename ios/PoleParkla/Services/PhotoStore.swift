import Foundation
import ImageIO
import UniformTypeIdentifiers
import UIKit

struct ImportedPhoto: Sendable {
    var relativePath: String
    var capturedAt: Date
    var latitude: Double?
    var longitude: Double?
    var hasCapturedAtMetadata: Bool
    var hasLocationMetadata: Bool
}

struct PhotoMetadata: Equatable, Sendable {
    var capturedAt: Date?
    var latitude: Double?
    var longitude: Double?
}

actor PhotoStore {
    static let mailMaximumBytes = 2_000_000
    static let mailMaximumLongSide = 2_560
    static let metadataBudgetBytes = 256_000

    private let rootURL: URL
    private let fileManager: FileManager

    init(fileManager: FileManager = .default, rootURL: URL? = nil) throws {
        self.fileManager = fileManager
        if let rootURL {
            self.rootURL = rootURL
        } else {
            let applicationSupport = try fileManager.url(
                for: .applicationSupportDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: true
            )
            self.rootURL = applicationSupport.appendingPathComponent("PoleParkla", isDirectory: true)
        }
        let photos = self.rootURL.appendingPathComponent("Photos", isDirectory: true)
        let temporaryCopies = self.rootURL.appendingPathComponent("TemporaryCopies", isDirectory: true)
        try fileManager.createDirectory(at: photos, withIntermediateDirectories: true)
        try fileManager.createDirectory(at: temporaryCopies, withIntermediateDirectories: true)
        var protectedRoot = self.rootURL
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try protectedRoot.setResourceValues(values)
        try fileManager.setAttributes(
            [.protectionKey: FileProtectionType.complete],
            ofItemAtPath: self.rootURL.path
        )
    }

    func importPhoto(data: Data, reportID: String, preferredExtension: String = "jpg") throws -> ImportedPhoto {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              CGImageSourceGetCount(source) > 0
        else { throw PhotoStoreError.invalidImage }

        let metadata = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] ?? [:]
        let reportDirectory = photosURL.appendingPathComponent(reportID, isDirectory: true)
        try fileManager.createDirectory(at: reportDirectory, withIntermediateDirectories: true)
        let detectedExtension = CGImageSourceGetType(source)
            .flatMap { UTType($0 as String)?.preferredFilenameExtension }
        let fileName = "\(UUID().uuidString).\(sanitizedExtension(detectedExtension ?? preferredExtension))"
        let destination = reportDirectory.appendingPathComponent(fileName)
        try data.write(to: destination, options: [.atomic, .completeFileProtection])
        try excludeFromBackup(reportDirectory)

        let exif = metadata[kCGImagePropertyExifDictionary] as? [CFString: Any]
        let tiff = metadata[kCGImagePropertyTIFFDictionary] as? [CFString: Any]
        let metadataDate = Self.captureDate(exif: exif, tiff: tiff)
        let capturedAt = metadataDate ?? .now
        let coordinate = Self.coordinate(from: metadata[kCGImagePropertyGPSDictionary] as? [CFString: Any])
        return ImportedPhoto(
            relativePath: try relativePath(for: destination),
            capturedAt: capturedAt,
            latitude: coordinate?.latitude,
            longitude: coordinate?.longitude,
            hasCapturedAtMetadata: metadataDate != nil,
            hasLocationMetadata: coordinate != nil
        )
    }

    func url(for relativePath: String) throws -> URL {
        guard !relativePath.isEmpty, !relativePath.hasPrefix("/") else {
            throw PhotoStoreError.invalidRelativePath
        }
        let rootPath = normalizedRootPath
        let candidate = rootURL.appendingPathComponent(relativePath).standardizedFileURL
        guard candidate.path.hasPrefix(rootPath + "/") else {
            throw PhotoStoreError.invalidRelativePath
        }
        return candidate
    }

    func data(for photo: ReportPhoto) throws -> Data {
        try Data(contentsOf: url(for: photo.relativePath), options: .mappedIfSafe)
    }

    func metadata(for photo: ReportPhoto) throws -> PhotoMetadata {
        let source = try imageSource(for: photo)
        let metadata = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] ?? [:]
        let exif = metadata[kCGImagePropertyExifDictionary] as? [CFString: Any]
        let tiff = metadata[kCGImagePropertyTIFFDictionary] as? [CFString: Any]
        let coordinate = Self.coordinate(from: metadata[kCGImagePropertyGPSDictionary] as? [CFString: Any])
        return PhotoMetadata(
            capturedAt: Self.captureDate(exif: exif, tiff: tiff),
            latitude: coordinate?.latitude,
            longitude: coordinate?.longitude
        )
    }

    func thumbnailData(
        for photo: ReportPhoto,
        maximumPixelSize: Int,
        crop: NormalizedPhotoRect? = nil
    ) throws -> Data {
        guard maximumPixelSize > 0 else { throw PhotoStoreError.invalidImage }
        let source = try imageSource(for: photo)
        let decodeSize = crop == nil ? maximumPixelSize : max(2_048, maximumPixelSize * 4)
        guard let decoded = CGImageSourceCreateThumbnailAtIndex(source, 0, [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: decodeSize,
            kCGImageSourceCreateThumbnailWithTransform: true,
        ] as CFDictionary) else { throw PhotoStoreError.invalidImage }
        let selected: CGImage
        if let crop, crop.isValid {
            guard let rectangle = PlateCropGeometry.rectangle(
                bounds: crop,
                pixelWidth: decoded.width,
                pixelHeight: decoded.height
            ) else { throw PhotoStoreError.invalidImage }
            guard let cropped = decoded.cropping(to: rectangle) else { throw PhotoStoreError.invalidImage }
            selected = cropped
        } else {
            selected = decoded
        }
        return try Self.jpegData(image: Self.scaledImage(selected, maximumPixelSize: maximumPixelSize))
    }

    func delete(_ photo: ReportPhoto) throws {
        let target = try url(for: photo.relativePath)
        if fileManager.fileExists(atPath: target.path) {
            try fileManager.removeItem(at: target)
        }
        try deleteTemporaryCopies(photo: photo)
    }

    func deleteReportDirectory(reportID: String) throws {
        let target = photosURL.appendingPathComponent(reportID, isDirectory: true)
        if fileManager.fileExists(atPath: target.path) { try fileManager.removeItem(at: target) }
        try deleteTemporaryCopies(reportID: reportID)
    }

    func prepareMailCopies(photos: [ReportPhoto]) throws -> [URL] {
        guard (1 ... maximumReportPhotos).contains(photos.count) else { throw PhotoStoreError.invalidPhotoCount }
        return try photos.map { photo in
            try deleteMailCopies(photo: photo)
            let original = try url(for: photo.relativePath)
            let data = try Data(contentsOf: original, options: .mappedIfSafe)
            let source = try imageSource(data: data)
            let dimensions = Self.dimensions(source)
            let outputDirectory = temporaryCopiesURL.appendingPathComponent(photo.reportID, isDirectory: true)
            try fileManager.createDirectory(at: outputDirectory, withIntermediateDirectories: true)
            let preservesOriginal = data.count <= Self.mailMaximumBytes &&
                max(dimensions.width, dimensions.height) <= Self.mailMaximumLongSide
            let outputExtension = preservesOriginal ? original.pathExtension : "jpg"
            let output = outputDirectory.appendingPathComponent("mail-\(photo.id).\(outputExtension.isEmpty ? "bin" : outputExtension)")
            if preservesOriginal {
                try data.write(to: output, options: [.atomic, .completeFileProtection])
            } else {
                let metadata = Self.boundedMetadata(from: source)
                try encodeJPEG(
                    source: source,
                    output: output,
                    maximumBytes: Self.mailMaximumBytes,
                    maximumLongSide: Self.mailMaximumLongSide,
                    metadata: metadata
                )
            }
            return output
        }
    }

    func deleteTemporaryCopies(reportID: String? = nil) throws {
        let target = reportID.map { temporaryCopiesURL.appendingPathComponent($0, isDirectory: true) } ?? temporaryCopiesURL
        if fileManager.fileExists(atPath: target.path) { try fileManager.removeItem(at: target) }
        if reportID == nil { try fileManager.createDirectory(at: temporaryCopiesURL, withIntermediateDirectories: true) }
    }

    func deleteMailCopies(reportID: String) throws {
        let directory = temporaryCopiesURL.appendingPathComponent(reportID, isDirectory: true)
        try deleteTemporaryFiles(in: directory, prefix: "mail-")
    }

    func reconcile(references: [StoredPhotoReference]) throws {
        try deleteTemporaryCopies()
        let livePaths = Set(references.map(\.relativePath))
        let liveReportIDs = Set(references.map(\.reportID))
        guard fileManager.fileExists(atPath: photosURL.path) else {
            try fileManager.createDirectory(at: photosURL, withIntermediateDirectories: true)
            return
        }
        for directory in try fileManager.contentsOfDirectory(
            at: photosURL,
            includingPropertiesForKeys: [.isDirectoryKey]
        ) {
            let isDirectory = try directory.resourceValues(forKeys: [.isDirectoryKey]).isDirectory == true
            guard isDirectory, liveReportIDs.contains(directory.lastPathComponent) else {
                try fileManager.removeItem(at: directory)
                continue
            }
            for file in try fileManager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
                let relativePath = try relativePath(for: file)
                if !livePaths.contains(relativePath) { try fileManager.removeItem(at: file) }
            }
        }
    }

    func deleteAll() throws {
        if fileManager.fileExists(atPath: photosURL.path) { try fileManager.removeItem(at: photosURL) }
        if fileManager.fileExists(atPath: temporaryCopiesURL.path) { try fileManager.removeItem(at: temporaryCopiesURL) }
        try fileManager.createDirectory(at: photosURL, withIntermediateDirectories: true)
        try fileManager.createDirectory(at: temporaryCopiesURL, withIntermediateDirectories: true)
        try excludeFromBackup(rootURL)
        try protect(rootURL)
    }

    private var photosURL: URL { rootURL.appendingPathComponent("Photos", isDirectory: true) }
    private var temporaryCopiesURL: URL { rootURL.appendingPathComponent("TemporaryCopies", isDirectory: true) }
    private var normalizedRootPath: String {
        var path = rootURL.standardizedFileURL.path
        while path.count > 1, path.hasSuffix("/") { path.removeLast() }
        return path
    }

    private func relativePath(for fileURL: URL) throws -> String {
        let rootPath = normalizedRootPath
        let filePath = fileURL.standardizedFileURL.path
        guard filePath.hasPrefix(rootPath + "/") else { throw PhotoStoreError.invalidRelativePath }
        return String(filePath.dropFirst(rootPath.count + 1))
    }

    private func deleteTemporaryCopies(photo: ReportPhoto) throws {
        try deleteMailCopies(photo: photo)
    }

    private func deleteMailCopies(photo: ReportPhoto) throws {
        let directory = temporaryCopiesURL.appendingPathComponent(photo.reportID, isDirectory: true)
        try deleteTemporaryFiles(in: directory, prefix: "mail-\(photo.id).")
    }

    private func deleteTemporaryFiles(in directory: URL, prefix: String) throws {
        guard fileManager.fileExists(atPath: directory.path) else { return }
        for item in try fileManager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
            if item.lastPathComponent.hasPrefix(prefix) { try fileManager.removeItem(at: item) }
        }
        if try fileManager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil).isEmpty {
            try fileManager.removeItem(at: directory)
        }
    }

    private func imageSource(for photo: ReportPhoto) throws -> CGImageSource {
        try imageSource(data: data(for: photo))
    }

    private func imageSource(data: Data) throws -> CGImageSource {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else {
            throw PhotoStoreError.invalidImage
        }
        return source
    }

    private func encodeJPEG(
        source: CGImageSource,
        output: URL,
        maximumBytes: Int,
        maximumLongSide: Int,
        metadata: [CFString: Any]
    ) throws {
        let original = Self.dimensions(source)
        var longSide = min(maximumLongSide, max(original.width, original.height))
        var quality: CGFloat = 0.9
        while longSide >= 320 {
            let options: [CFString: Any] = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceThumbnailMaxPixelSize: longSide,
                kCGImageSourceCreateThumbnailWithTransform: true,
            ]
            guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
                throw PhotoStoreError.invalidImage
            }
            while quality >= 0.35 {
                let data = NSMutableData()
                guard let destination = CGImageDestinationCreateWithData(
                    data,
                    UTType.jpeg.identifier as CFString,
                    1,
                    nil
                ) else { throw PhotoStoreError.cannotEncode }
                var properties = metadata
                properties[kCGImageDestinationLossyCompressionQuality] = quality
                CGImageDestinationAddImage(destination, image, properties as CFDictionary)
                guard CGImageDestinationFinalize(destination) else { throw PhotoStoreError.cannotEncode }
                let encoded = metadata.isEmpty ? try Self.removingExif(from: data as Data) : data as Data
                if encoded.count <= maximumBytes {
                    try encoded.write(to: output, options: [.atomic, .completeFileProtection])
                    return
                }
                quality -= 0.1
            }
            longSide = Int(Double(longSide) * 0.8)
            quality = 0.85
        }
        throw PhotoStoreError.cannotMeetLimit
    }

    private static func removingExif(from jpeg: Data) throws -> Data {
        let bytes = [UInt8](jpeg)
        guard bytes.count >= 4, bytes[0] == 0xFF, bytes[1] == 0xD8 else {
            throw PhotoStoreError.cannotEncode
        }
        var output = Data(bytes.prefix(2))
        var offset = 2
        while offset < bytes.count {
            guard offset + 1 < bytes.count, bytes[offset] == 0xFF else {
                output.append(contentsOf: bytes[offset...])
                break
            }
            let marker = bytes[offset + 1]
            if marker == 0xDA || marker == 0xD9 {
                output.append(contentsOf: bytes[offset...])
                break
            }
            guard offset + 3 < bytes.count else { throw PhotoStoreError.cannotEncode }
            let length = Int(bytes[offset + 2]) << 8 | Int(bytes[offset + 3])
            guard length >= 2, offset + 2 + length <= bytes.count else {
                throw PhotoStoreError.cannotEncode
            }
            let end = offset + 2 + length
            let isExif = marker == 0xE1 && length >= 8 &&
                bytes[(offset + 4) ..< (offset + 10)].elementsEqual([0x45, 0x78, 0x69, 0x66, 0, 0])
            if !isExif { output.append(contentsOf: bytes[offset ..< end]) }
            offset = end
        }
        return output
    }

    private static func dimensions(_ source: CGImageSource) -> (width: Int, height: Int) {
        let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]
        return (
            properties?[kCGImagePropertyPixelWidth] as? Int ?? 0,
            properties?[kCGImagePropertyPixelHeight] as? Int ?? 0
        )
    }

    private static func scaledImage(_ image: CGImage, maximumPixelSize: Int) throws -> CGImage {
        let scale = min(1, CGFloat(maximumPixelSize) / CGFloat(max(image.width, image.height)))
        guard scale < 1 else { return image }
        let width = max(1, Int((CGFloat(image.width) * scale).rounded()))
        let height = max(1, Int((CGFloat(image.height) * scale).rounded()))
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { throw PhotoStoreError.invalidImage }
        context.interpolationQuality = .high
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        guard let scaled = context.makeImage() else { throw PhotoStoreError.invalidImage }
        return scaled
    }

    private static func jpegData(image: CGImage) throws -> Data {
        let data = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            data,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ) else { throw PhotoStoreError.cannotEncode }
        CGImageDestinationAddImage(
            destination,
            image,
            [kCGImageDestinationLossyCompressionQuality: 0.82] as CFDictionary
        )
        guard CGImageDestinationFinalize(destination) else { throw PhotoStoreError.cannotEncode }
        return data as Data
    }

    private static func boundedMetadata(from source: CGImageSource) -> [CFString: Any] {
        guard let metadata = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] else { return [:] }
        var essential: [CFString: Any] = [:]
        for key in [kCGImagePropertyExifDictionary, kCGImagePropertyGPSDictionary, kCGImagePropertyTIFFDictionary] {
            if let value = metadata[key] { essential[key] = value }
        }
        let serialized = try? PropertyListSerialization.data(fromPropertyList: essential, format: .binary, options: 0)
        if (serialized?.count ?? 0) <= metadataBudgetBytes { return essential }
        var minimal: [CFString: Any] = [:]
        if let gps = metadata[kCGImagePropertyGPSDictionary] { minimal[kCGImagePropertyGPSDictionary] = gps }
        if let exif = metadata[kCGImagePropertyExifDictionary] as? [CFString: Any] {
            var dates: [CFString: Any] = [:]
            for key in [kCGImagePropertyExifDateTimeOriginal, kCGImagePropertyExifDateTimeDigitized] {
                if let value = exif[key] { dates[key] = value }
            }
            if !dates.isEmpty { minimal[kCGImagePropertyExifDictionary] = dates }
        }
        return minimal
    }

    private static func captureDate(exif: [CFString: Any]?, tiff: [CFString: Any]?) -> Date? {
        let raw = (exif?[kCGImagePropertyExifDateTimeOriginal] as? String)
            ?? (exif?[kCGImagePropertyExifDateTimeDigitized] as? String)
            ?? (tiff?[kCGImagePropertyTIFFDateTime] as? String)
        guard let raw else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
        return formatter.date(from: raw)
    }

    private static func coordinate(from gps: [CFString: Any]?) -> (latitude: Double, longitude: Double)? {
        guard let gps,
              var latitude = gps[kCGImagePropertyGPSLatitude] as? Double,
              var longitude = gps[kCGImagePropertyGPSLongitude] as? Double
        else { return nil }
        if (gps[kCGImagePropertyGPSLatitudeRef] as? String)?.uppercased() == "S" { latitude *= -1 }
        if (gps[kCGImagePropertyGPSLongitudeRef] as? String)?.uppercased() == "W" { longitude *= -1 }
        guard (-90 ... 90).contains(latitude), (-180 ... 180).contains(longitude) else { return nil }
        return (latitude, longitude)
    }

    private func sanitizedExtension(_ value: String) -> String {
        let lowered = value.lowercased().filter { $0.isLetter || $0.isNumber }
        return lowered.isEmpty ? "jpg" : String(lowered.prefix(8))
    }

    private func excludeFromBackup(_ url: URL) throws {
        var resourceURL = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try resourceURL.setResourceValues(values)
    }

    private func protect(_ url: URL) throws {
        try fileManager.setAttributes(
            [.protectionKey: FileProtectionType.complete],
            ofItemAtPath: url.path
        )
    }
}

enum PlateCropGeometry {
    private static let horizontalPadding: CGFloat = 0.12
    private static let verticalPadding: CGFloat = 0.35

    static func rectangle(
        bounds: NormalizedPhotoRect,
        pixelWidth: Int,
        pixelHeight: Int
    ) -> CGRect? {
        guard bounds.isValid, pixelWidth > 0, pixelHeight > 0 else { return nil }
        let width = CGFloat(pixelWidth)
        let height = CGFloat(pixelHeight)
        let plateWidth = CGFloat(bounds.right - bounds.left) * width
        let plateHeight = CGFloat(bounds.bottom - bounds.top) * height
        let left = max(0, CGFloat(bounds.left) * width - plateWidth * horizontalPadding)
        let top = max(0, CGFloat(bounds.top) * height - plateHeight * verticalPadding)
        let right = min(width, CGFloat(bounds.right) * width + plateWidth * horizontalPadding)
        let bottom = min(height, CGFloat(bounds.bottom) * height + plateHeight * verticalPadding)
        let rectangle = CGRect(x: left, y: top, width: right - left, height: bottom - top).integral
        guard rectangle.width >= 1, rectangle.height >= 1 else { return nil }
        return rectangle
    }
}

enum PhotoStoreError: Error, Equatable {
    case invalidImage
    case invalidRelativePath
    case invalidPhotoCount
    case cannotEncode
    case cannotMeetLimit
}
