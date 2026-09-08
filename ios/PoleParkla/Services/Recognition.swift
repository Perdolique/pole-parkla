import CoreGraphics
import Foundation
import ImageIO
import OnnxRuntimeBindings
import Vision

struct LetterboxTransform: Equatable, Sendable {
    var imageWidth: Int
    var imageHeight: Int
    var scale: Float
    var scaledWidth: Int
    var scaledHeight: Int
    var paddingX: Float
    var paddingY: Float
    var left: Int
    var top: Int
}

struct PlateDetection: Equatable, Sendable {
    var left: Int
    var top: Int
    var right: Int
    var bottom: Int
    var confidence: Float
    var relativeArea: Float
}

struct DecodedPlate: Equatable, Sendable {
    var value: String
    var meanCharacterConfidence: Float
}

enum PlateModelProcessing {
    static let detectorInputSize = 512
    static let detectorConfidenceThreshold: Float = 0.25
    static let ocrInputWidth = 128
    static let ocrInputHeight = 64
    static let ocrMaximumSlots = 10
    static let alphabet = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_")

    static func letterboxTransform(imageWidth: Int, imageHeight: Int, targetSize: Int = detectorInputSize) -> LetterboxTransform {
        precondition(imageWidth > 0 && imageHeight > 0 && targetSize > 0)
        let scale = min(Float(targetSize) / Float(imageHeight), Float(targetSize) / Float(imageWidth))
        let scaledWidth = Int((Float(imageWidth) * scale).rounded())
        let scaledHeight = Int((Float(imageHeight) * scale).rounded())
        let paddingX = Float(targetSize - scaledWidth) / 2
        let paddingY = Float(targetSize - scaledHeight) / 2
        return LetterboxTransform(
            imageWidth: imageWidth,
            imageHeight: imageHeight,
            scale: scale,
            scaledWidth: scaledWidth,
            scaledHeight: scaledHeight,
            paddingX: paddingX,
            paddingY: paddingY,
            left: Int(paddingX),
            top: Int(paddingY)
        )
    }

    static func decodeDetections(
        _ output: [Float],
        transform: LetterboxTransform,
        confidenceThreshold: Float = detectorConfidenceThreshold
    ) throws -> [PlateDetection] {
        guard output.count.isMultiple(of: 7) else { throw RecognitionError.unexpectedModelOutput }
        let imageArea = Double(transform.imageWidth * transform.imageHeight)
        return stride(from: 0, to: output.count, by: 7).compactMap { offset in
            let confidence = output[offset + 6]
            guard confidence.isFinite, confidence >= confidenceThreshold else { return nil }
            let left = Int((output[offset + 1] - transform.paddingX) / transform.scale).clamped(to: 0 ... transform.imageWidth)
            let top = Int((output[offset + 2] - transform.paddingY) / transform.scale).clamped(to: 0 ... transform.imageHeight)
            let right = Int((output[offset + 3] - transform.paddingX) / transform.scale).clamped(to: 0 ... transform.imageWidth)
            let bottom = Int((output[offset + 4] - transform.paddingY) / transform.scale).clamped(to: 0 ... transform.imageHeight)
            guard right > left, bottom > top else { return nil }
            return PlateDetection(
                left: left,
                top: top,
                right: right,
                bottom: bottom,
                confidence: confidence,
                relativeArea: Float(Double((right - left) * (bottom - top)) / imageArea)
            )
        }.sorted {
            if $0.confidence != $1.confidence { return $0.confidence > $1.confidence }
            if $0.relativeArea != $1.relativeArea { return $0.relativeArea > $1.relativeArea }
            return ($0.top, $0.left) < ($1.top, $1.left)
        }
    }

    static func decodePlate(_ output: [Float]) throws -> DecodedPlate? {
        let expected = ocrMaximumSlots * alphabet.count
        guard output.count >= expected else { throw RecognitionError.unexpectedModelOutput }
        var characters: [Character] = []
        var confidences: [Float] = []
        for slot in 0 ..< ocrMaximumSlots {
            let offset = slot * alphabet.count
            let range = offset ..< offset + alphabet.count
            guard let best = range.max(by: { output[$0] < output[$1] }) else { continue }
            characters.append(alphabet[best - offset])
            confidences.append(output[best])
        }
        while characters.last == "_" { characters.removeLast(); confidences.removeLast() }
        guard !characters.isEmpty else { return nil }
        return DecodedPlate(
            value: String(characters),
            meanCharacterConfidence: confidences.reduce(0, +) / Float(confidences.count)
        )
    }
}

actor VisionTextRecognitionService {
    func recognize(photo: ReportPhoto, data: Data) throws -> RecognitionResult {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                  kCGImageSourceCreateThumbnailFromImageAlways: true,
                  kCGImageSourceThumbnailMaxPixelSize: 2_048,
                  kCGImageSourceCreateThumbnailWithTransform: true,
              ] as CFDictionary)
        else { throw RecognitionError.invalidImage }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        request.minimumTextHeight = 0.015
        let handler = VNImageRequestHandler(cgImage: image, orientation: .up, options: [:])
        try handler.perform([request])
        let observations = (request.results ?? []).flatMap { observation -> [RecognitionPlateObservation] in
            guard let candidate = observation.topCandidates(1).first else { return [] }
            return PlateCandidateParser.parse(candidate.string).map { value in
                RecognitionPlateObservation(
                    photoID: photo.id,
                    value: value,
                    bounds: NormalizedPhotoRect(
                        left: Float(observation.boundingBox.minX),
                        top: Float(1 - observation.boundingBox.maxY),
                        right: Float(observation.boundingBox.maxX),
                        bottom: Float(1 - observation.boundingBox.minY)
                    ),
                    characterConfidence: candidate.confidence,
                    relativeArea: Float(observation.boundingBox.width * observation.boundingBox.height)
                )
            }
        }
        return RecognitionResult(source: .visionOCR, photoID: photo.id, plateObservations: observations)
    }
}

actor LocalPlateRecognitionService {
    private let bundle: Bundle
    private var environment: ORTEnv?
    private var detector: ORTSession?
    private var recognizer: ORTSession?

    init(bundle: Bundle = .main) {
        self.bundle = bundle
    }

    private func loadModelsIfNeeded() throws {
        guard detector == nil || recognizer == nil else { return }
        guard let detectorURL = bundle.url(forResource: "yolo-v9-t-512-license-plates-end2end", withExtension: "onnx"),
              let recognizerURL = bundle.url(forResource: "cct_s_v2_global", withExtension: "onnx")
        else { throw RecognitionError.modelMissing }
        let environment = try ORTEnv(loggingLevel: .warning)
        let options = try ORTSessionOptions()
        try options.setGraphOptimizationLevel(.all)
        try options.setIntraOpNumThreads(1)
        detector = try ORTSession(env: environment, modelPath: detectorURL.path, sessionOptions: options)
        recognizer = try ORTSession(env: environment, modelPath: recognizerURL.path, sessionOptions: options)
        self.environment = environment
    }

    func recognize(photo: ReportPhoto, data: Data) throws -> RecognitionResult {
        try loadModelsIfNeeded()
        guard let detector else { throw RecognitionError.modelMissing }
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let orientedImage = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                  kCGImageSourceCreateThumbnailFromImageAlways: true,
                  kCGImageSourceThumbnailMaxPixelSize: 2_048,
                  kCGImageSourceCreateThumbnailWithTransform: true,
              ] as CFDictionary)
        else { throw RecognitionError.invalidImage }
        let transform = PlateModelProcessing.letterboxTransform(
            imageWidth: orientedImage.width,
            imageHeight: orientedImage.height
        )
        let detectorInput = try detectorTensor(image: orientedImage, transform: transform)
        let outputNames = try detector.outputNames()
        guard let firstName = outputNames.first,
              let output = try detector.run(
                  withInputs: ["images": detectorInput],
                  outputNames: Set(outputNames),
                  runOptions: nil
              )[firstName]
        else { throw RecognitionError.unexpectedModelOutput }
        let detectorOutput = try floats(from: output)
        let detections = try PlateModelProcessing.decodeDetections(detectorOutput, transform: transform)
        let observations = try detections.compactMap { detection -> RecognitionPlateObservation? in
            guard let crop = orientedImage.cropping(to: CGRect(
                x: detection.left,
                y: detection.top,
                width: detection.right - detection.left,
                height: detection.bottom - detection.top
            )) else { return nil }
            let decoded = try recognizeCrop(crop)
            guard let decoded, let normalized = PlateCandidateParser.normalize(decoded.value) else { return nil }
            return RecognitionPlateObservation(
                photoID: photo.id,
                value: normalized,
                bounds: NormalizedPhotoRect(
                    left: Float(detection.left) / Float(orientedImage.width),
                    top: Float(detection.top) / Float(orientedImage.height),
                    right: Float(detection.right) / Float(orientedImage.width),
                    bottom: Float(detection.bottom) / Float(orientedImage.height)
                ),
                detectionConfidence: detection.confidence,
                characterConfidence: decoded.meanCharacterConfidence,
                relativeArea: detection.relativeArea
            )
        }
        return RecognitionResult(source: .localPlateModel, photoID: photo.id, plateObservations: observations)
    }

    private func recognizeCrop(_ image: CGImage) throws -> DecodedPlate? {
        guard let recognizer else { throw RecognitionError.modelMissing }
        let tensor = try uint8NHWCTensor(
            image: image,
            width: PlateModelProcessing.ocrInputWidth,
            height: PlateModelProcessing.ocrInputHeight
        )
        let outputs = try recognizer.run(
            withInputs: ["input": tensor],
            outputNames: ["plate"],
            runOptions: nil
        )
        guard let output = outputs["plate"] else { throw RecognitionError.unexpectedModelOutput }
        return try PlateModelProcessing.decodePlate(try floats(from: output))
    }

    private func detectorTensor(image: CGImage, transform: LetterboxTransform) throws -> ORTValue {
        let width = PlateModelProcessing.detectorInputSize
        let height = PlateModelProcessing.detectorInputSize
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        var pixels = [UInt8](repeating: 114, count: width * height * 4)
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { throw RecognitionError.invalidImage }
        context.interpolationQuality = .high
        context.draw(image, in: CGRect(x: transform.left, y: transform.top, width: transform.scaledWidth, height: transform.scaledHeight))
        var floats = [Float](repeating: 0, count: width * height * 3)
        let plane = width * height
        for index in 0 ..< plane {
            floats[index] = Float(pixels[index * 4]) / 255
            floats[plane + index] = Float(pixels[index * 4 + 1]) / 255
            floats[plane * 2 + index] = Float(pixels[index * 4 + 2]) / 255
        }
        let data = NSMutableData(bytes: floats, length: floats.count * MemoryLayout<Float>.size)
        return try ORTValue(
            tensorData: data,
            elementType: .float,
            shape: [1, 3, NSNumber(value: height), NSNumber(value: width)]
        )
    }

    private func uint8NHWCTensor(
        image: CGImage,
        width: Int,
        height: Int
    ) throws -> ORTValue {
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { throw RecognitionError.invalidImage }
        context.interpolationQuality = .high
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        var rgb = [UInt8]()
        rgb.reserveCapacity(width * height * 3)
        for index in 0 ..< width * height {
            rgb.append(contentsOf: pixels[index * 4 ... index * 4 + 2])
        }
        let data = NSMutableData(bytes: rgb, length: rgb.count)
        let shape: [NSNumber] = [1, NSNumber(value: height), NSNumber(value: width), 3]
        return try ORTValue(tensorData: data, elementType: .uInt8, shape: shape)
    }

    private func floats(from value: ORTValue) throws -> [Float] {
        let data = try value.tensorData()
        guard data.count.isMultiple(of: MemoryLayout<Float>.size) else {
            throw RecognitionError.unexpectedModelOutput
        }
        return (data as Data).withUnsafeBytes { rawBuffer in Array(rawBuffer.bindMemory(to: Float.self)) }
    }
}

protocol PhotoRecognitionService: Sendable {
    func recognize(photo: ReportPhoto, data: Data) async throws -> RecognitionResult
}

extension VisionTextRecognitionService: PhotoRecognitionService {}
extension LocalPlateRecognitionService: PhotoRecognitionService {}

struct RecognitionBatch: Equatable, Sendable {
    var results: [RecognitionResult]
    var failureCount: Int
    var isComplete: Bool { failureCount == 0 }
}

actor RecognitionCoordinator {
    private let photoStore: PhotoStore
    private let vision: any PhotoRecognitionService
    private let plates: any PhotoRecognitionService

    init(photoStore: PhotoStore, bundle: Bundle = .main) {
        self.photoStore = photoStore
        vision = VisionTextRecognitionService()
        plates = LocalPlateRecognitionService(bundle: bundle)
    }

    init(
        photoStore: PhotoStore,
        vision: any PhotoRecognitionService,
        plates: any PhotoRecognitionService
    ) {
        self.photoStore = photoStore
        self.vision = vision
        self.plates = plates
    }

    func recognize(photos: [ReportPhoto]) async -> RecognitionBatch {
        var results: [RecognitionResult] = []
        var failureCount = 0
        for photo in photos.sorted(by: { $0.capturedAt < $1.capturedAt }) {
            let data: Data
            do {
                data = try await photoStore.data(for: photo)
            } catch {
                if !photo.visionRecognitionComplete { failureCount += 1 }
                if !photo.plateRecognitionComplete { failureCount += 1 }
                continue
            }
            if !photo.visionRecognitionComplete {
                do {
                    results.append(try await vision.recognize(photo: photo, data: data))
                } catch {
                    failureCount += 1
                }
            }
            if !photo.plateRecognitionComplete {
                do {
                    results.append(try await plates.recognize(photo: photo, data: data))
                } catch {
                    failureCount += 1
                }
            }
        }
        return RecognitionBatch(results: results, failureCount: failureCount)
    }
}

enum RecognitionError: Error, Equatable {
    case modelMissing
    case invalidImage
    case unexpectedModelOutput
}

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        Swift.min(range.upperBound, Swift.max(range.lowerBound, self))
    }
}
