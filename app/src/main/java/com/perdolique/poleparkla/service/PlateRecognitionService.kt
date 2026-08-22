package com.perdolique.poleparkla.service

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.perdolique.poleparkla.domain.PlateCandidateParser
import com.perdolique.poleparkla.model.RecognitionResult
import com.perdolique.poleparkla.model.RecognitionSource
import com.perdolique.poleparkla.model.ReportPhoto
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlateRecognitionService(
    context: Context,
    private val photoStore: PhotoStore,
) {
    private val assets = context.applicationContext.assets
    private val environment = OrtEnvironment.getEnvironment()
    private val detectorSession by lazy { loadSession(DETECTOR_MODEL_ASSET) }
    private val ocrSession by lazy { loadSession(OCR_MODEL_ASSET) }
    private val observationsByPhoto = mutableMapOf<PhotoRecognitionCacheKey, List<PlateObservation>>()

    suspend fun recognize(photos: List<ReportPhoto>): RecognitionResult =
        withContext(Dispatchers.Default) {
            val photosWithKeys = photos.map { it to it.recognitionCacheKey() }
            synchronized(observationsByPhoto) {
                observationsByPhoto.keys.retainAll(photosWithKeys.mapTo(mutableSetOf()) { it.second })
            }
            val observations = buildList {
                photosWithKeys.forEach { (photo, key) ->
                    val cached = synchronized(observationsByPhoto) { observationsByPhoto[key] }
                    val recognized = cached ?: recognizePhoto(photo).also { value ->
                        synchronized(observationsByPhoto) { observationsByPhoto[key] = value }
                    }
                    addAll(recognized)
                }
            }
            RecognitionResult(
                source = RecognitionSource.LOCAL_PLATE_MODEL,
                plateCandidates = rankPlateCandidates(observations),
            )
        }

    fun invalidate(photoId: String) {
        synchronized(observationsByPhoto) {
            observationsByPhoto.keys.removeAll { it.id == photoId }
        }
    }

    internal fun recognizePlateCrop(bitmap: Bitmap): DecodedPlate? {
        val inputBuffer = createOcrInput(bitmap)
        return OnnxTensor.createTensor(
            environment,
            inputBuffer,
            longArrayOf(1, PLATE_OCR_INPUT_HEIGHT.toLong(), PLATE_OCR_INPUT_WIDTH.toLong(), 3),
            OnnxJavaType.UINT8,
        ).use { input ->
            ocrSession.run(mapOf(OCR_INPUT_NAME to input)).use { result ->
                val output = result.get(OCR_OUTPUT_NAME).orElseThrow() as OnnxTensor
                decodePlate(output.floatBuffer.toFloatArray())
            }
        }
    }

    private fun recognizeBitmap(photoId: String, bitmap: Bitmap): List<PlateObservation> =
        detectPlates(bitmap).mapNotNull { detection ->
            val crop = Bitmap.createBitmap(
                bitmap,
                detection.left,
                detection.top,
                detection.right - detection.left,
                detection.bottom - detection.top,
            )
            try {
                val decoded = recognizePlateCrop(crop) ?: return@mapNotNull null
                val normalized = PlateCandidateParser.normalize(decoded.value) ?: return@mapNotNull null
                PlateObservation(
                    photoId = photoId,
                    value = normalized,
                    detectionConfidence = detection.confidence,
                    characterConfidence = decoded.meanCharacterConfidence,
                    relativeArea = detection.relativeArea,
                )
            } finally {
                crop.recycle()
            }
        }

    private suspend fun recognizePhoto(photo: ReportPhoto): List<PlateObservation> {
        val bitmap = photoStore.decodeForModel(photo, MODEL_MAX_LONG_SIDE)
        return try {
            recognizeBitmap(photo.id, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun detectPlates(bitmap: Bitmap): List<PlateDetection> {
        val transform = letterboxTransform(bitmap.width, bitmap.height)
        val inputBuffer = createDetectorInput(bitmap, transform)
        val output = OnnxTensor.createTensor(
            environment,
            inputBuffer,
            longArrayOf(
                1,
                3,
                PLATE_DETECTOR_INPUT_SIZE.toLong(),
                PLATE_DETECTOR_INPUT_SIZE.toLong(),
            ),
        ).use { input ->
            detectorSession.run(mapOf(DETECTOR_INPUT_NAME to input)).use { result ->
                (result[0] as OnnxTensor).floatBuffer.toFloatArray()
            }
        }
        return decodePlateDetections(output, transform)
    }

    private fun createDetectorInput(
        bitmap: Bitmap,
        transform: LetterboxTransform,
    ): FloatBuffer {
        val letterboxed = createBitmap(
            PLATE_DETECTOR_INPUT_SIZE,
            PLATE_DETECTOR_INPUT_SIZE,
            Bitmap.Config.ARGB_8888,
        )
        try {
            Canvas(letterboxed).apply {
                drawColor(Color.rgb(114, 114, 114))
                drawBitmap(
                    bitmap,
                    null,
                    Rect(
                        transform.left,
                        transform.top,
                        transform.left + transform.scaledWidth,
                        transform.top + transform.scaledHeight,
                    ),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                )
            }
            val pixels = IntArray(PLATE_DETECTOR_INPUT_SIZE * PLATE_DETECTOR_INPUT_SIZE)
            letterboxed.getPixels(
                pixels,
                0,
                PLATE_DETECTOR_INPUT_SIZE,
                0,
                0,
                PLATE_DETECTOR_INPUT_SIZE,
                PLATE_DETECTOR_INPUT_SIZE,
            )
            val planeSize = pixels.size
            val values = FloatArray(planeSize * 3)
            pixels.forEachIndexed { index, pixel ->
                values[index] = Color.red(pixel) / 255f
                values[planeSize + index] = Color.green(pixel) / 255f
                values[(planeSize * 2) + index] = Color.blue(pixel) / 255f
            }
            return FloatBuffer.wrap(values)
        } finally {
            letterboxed.recycle()
        }
    }

    private fun createOcrInput(bitmap: Bitmap): ByteBuffer {
        val resized = bitmap.scale(
            width = PLATE_OCR_INPUT_WIDTH,
            height = PLATE_OCR_INPUT_HEIGHT,
        )
        try {
            val pixels = IntArray(PLATE_OCR_INPUT_WIDTH * PLATE_OCR_INPUT_HEIGHT)
            resized.getPixels(
                pixels,
                0,
                PLATE_OCR_INPUT_WIDTH,
                0,
                0,
                PLATE_OCR_INPUT_WIDTH,
                PLATE_OCR_INPUT_HEIGHT,
            )
            return ByteBuffer.allocateDirect(pixels.size * 3)
                .order(ByteOrder.nativeOrder())
                .apply {
                    pixels.forEach { pixel ->
                        put(Color.red(pixel).toByte())
                        put(Color.green(pixel).toByte())
                        put(Color.blue(pixel).toByte())
                    }
                    rewind()
                }
        } finally {
            if (resized !== bitmap) resized.recycle()
        }
    }

    private fun loadSession(assetPath: String): OrtSession {
        val model = assets.open(assetPath).use { it.readBytes() }
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return try {
            environment.createSession(model, options)
        } finally {
            options.close()
        }
    }

    private fun FloatBuffer.toFloatArray(): FloatArray {
        val values = FloatArray(remaining())
        get(values)
        return values
    }

    private companion object {
        const val DETECTOR_MODEL_ASSET = "models/yolo-v9-t-512-license-plates-end2end.onnx"
        const val OCR_MODEL_ASSET = "models/cct_s_v2_global.onnx"
        const val DETECTOR_INPUT_NAME = "images"
        const val OCR_INPUT_NAME = "input"
        const val OCR_OUTPUT_NAME = "plate"
        const val MODEL_MAX_LONG_SIDE = 2_048
    }
}
