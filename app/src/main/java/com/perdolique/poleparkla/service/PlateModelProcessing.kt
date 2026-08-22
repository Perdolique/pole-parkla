package com.perdolique.poleparkla.service

import kotlin.math.roundToInt

internal const val PLATE_DETECTOR_INPUT_SIZE = 512
internal const val PLATE_DETECTOR_CONFIDENCE_THRESHOLD = 0.25f
internal const val PLATE_OCR_INPUT_WIDTH = 128
internal const val PLATE_OCR_INPUT_HEIGHT = 64
internal const val PLATE_OCR_MAX_SLOTS = 10
internal const val PLATE_OCR_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_"

internal data class LetterboxTransform(
    val imageWidth: Int,
    val imageHeight: Int,
    val scale: Float,
    val scaledWidth: Int,
    val scaledHeight: Int,
    val paddingX: Float,
    val paddingY: Float,
    val left: Int,
    val top: Int,
)

internal data class PlateDetection(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
    val relativeArea: Float,
)

internal data class DecodedPlate(
    val value: String,
    val meanCharacterConfidence: Float,
)

internal data class PlateObservation(
    val photoId: String,
    val value: String,
    val detectionConfidence: Float,
    val characterConfidence: Float,
    val relativeArea: Float,
)

internal fun letterboxTransform(
    imageWidth: Int,
    imageHeight: Int,
    targetSize: Int = PLATE_DETECTOR_INPUT_SIZE,
): LetterboxTransform {
    require(imageWidth > 0 && imageHeight > 0 && targetSize > 0)
    val scale = minOf(targetSize.toFloat() / imageHeight, targetSize.toFloat() / imageWidth)
    val scaledWidth = (imageWidth * scale).roundToInt()
    val scaledHeight = (imageHeight * scale).roundToInt()
    val paddingX = (targetSize - scaledWidth) / 2f
    val paddingY = (targetSize - scaledHeight) / 2f
    return LetterboxTransform(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        scale = scale,
        scaledWidth = scaledWidth,
        scaledHeight = scaledHeight,
        paddingX = paddingX,
        paddingY = paddingY,
        left = paddingX.toInt(),
        top = paddingY.toInt(),
    )
}

internal fun decodePlateDetections(
    output: FloatArray,
    transform: LetterboxTransform,
    confidenceThreshold: Float = PLATE_DETECTOR_CONFIDENCE_THRESHOLD,
): List<PlateDetection> {
    require(output.size % DETECTION_ROW_SIZE == 0) { "Unexpected plate detector output size" }
    val imageArea = transform.imageWidth.toLong() * transform.imageHeight
    return buildList {
        for (offset in output.indices step DETECTION_ROW_SIZE) {
            val confidence = output[offset + 6]
            if (!confidence.isFinite() || confidence < confidenceThreshold) continue

            val left = ((output[offset + 1] - transform.paddingX) / transform.scale)
                .toInt()
                .coerceIn(0, transform.imageWidth)
            val top = ((output[offset + 2] - transform.paddingY) / transform.scale)
                .toInt()
                .coerceIn(0, transform.imageHeight)
            val right = ((output[offset + 3] - transform.paddingX) / transform.scale)
                .toInt()
                .coerceIn(0, transform.imageWidth)
            val bottom = ((output[offset + 4] - transform.paddingY) / transform.scale)
                .toInt()
                .coerceIn(0, transform.imageHeight)
            if (right <= left || bottom <= top) continue

            add(
                PlateDetection(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    confidence = confidence,
                    relativeArea = ((right - left).toLong() * (bottom - top) / imageArea.toDouble()).toFloat(),
                ),
            )
        }
    }.sortedByDescending(PlateDetection::confidence)
}

internal fun decodePlate(output: FloatArray): DecodedPlate? {
    val expectedSize = PLATE_OCR_MAX_SLOTS * PLATE_OCR_ALPHABET.length
    require(output.size >= expectedSize) { "Unexpected plate OCR output size" }
    val chars = CharArray(PLATE_OCR_MAX_SLOTS)
    val confidences = FloatArray(PLATE_OCR_MAX_SLOTS)
    for (slot in 0 until PLATE_OCR_MAX_SLOTS) {
        val offset = slot * PLATE_OCR_ALPHABET.length
        var bestIndex = 0
        var bestProbability = output[offset]
        for (index in 1 until PLATE_OCR_ALPHABET.length) {
            val probability = output[offset + index]
            if (probability > bestProbability) {
                bestProbability = probability
                bestIndex = index
            }
        }
        chars[slot] = PLATE_OCR_ALPHABET[bestIndex]
        confidences[slot] = bestProbability
    }

    val value = chars.concatToString().trimEnd('_')
    if (value.isEmpty()) return null
    return DecodedPlate(
        value = value,
        meanCharacterConfidence = confidences.take(value.length).average().toFloat(),
    )
}

internal fun rankPlateCandidates(
    observations: List<PlateObservation>,
    limit: Int = 5,
): List<String> = observations
    .groupBy(PlateObservation::value)
    .map { (value, matches) ->
        RankedPlate(
            value = value,
            photoCount = matches.map(PlateObservation::photoId).distinct().size,
            largestRelativeArea = matches.maxOf(PlateObservation::relativeArea),
            bestDetectionConfidence = matches.maxOf(PlateObservation::detectionConfidence),
            bestCharacterConfidence = matches.maxOf(PlateObservation::characterConfidence),
        )
    }
    .sortedWith(
        compareByDescending<RankedPlate>(RankedPlate::photoCount)
            .thenByDescending(RankedPlate::largestRelativeArea)
            .thenByDescending(RankedPlate::bestDetectionConfidence)
            .thenByDescending(RankedPlate::bestCharacterConfidence)
            .thenBy(RankedPlate::value),
    )
    .take(limit)
    .map(RankedPlate::value)

private data class RankedPlate(
    val value: String,
    val photoCount: Int,
    val largestRelativeArea: Float,
    val bestDetectionConfidence: Float,
    val bestCharacterConfidence: Float,
)

private const val DETECTION_ROW_SIZE = 7
