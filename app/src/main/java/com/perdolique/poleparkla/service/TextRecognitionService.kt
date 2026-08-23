package com.perdolique.poleparkla.service

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.perdolique.poleparkla.domain.PlateCandidateParser
import com.perdolique.poleparkla.model.RecognitionResult
import com.perdolique.poleparkla.model.RecognitionPlateObservation
import com.perdolique.poleparkla.model.RecognitionSource
import com.perdolique.poleparkla.model.ReportPhoto
import kotlinx.coroutines.tasks.await

class TextRecognitionService(private val photoStore: PhotoStore) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val textByPhoto = mutableMapOf<PhotoRecognitionCacheKey, String>()

    suspend fun recognize(photos: List<ReportPhoto>): RecognitionResult {
        val photosWithKeys = photos.map { it to it.recognitionCacheKey() }
        synchronized(textByPhoto) {
            textByPhoto.keys.retainAll(photosWithKeys.mapTo(mutableSetOf()) { it.second })
        }
        val recognizedTexts = photosWithKeys.map { (photo, key) ->
            val cached = synchronized(textByPhoto) { textByPhoto[key] }
            val recognized = cached ?: recognizePhoto(photo).also { value ->
                synchronized(textByPhoto) { textByPhoto[key] = value }
            }
            photo.id to recognized
        }
        val observations = parsePlateObservationsByPhoto(recognizedTexts)
        return RecognitionResult(
            source = RecognitionSource.ML_KIT_OCR,
            plateObservations = observations,
        )
    }

    fun invalidate(photoId: String) {
        synchronized(textByPhoto) {
            textByPhoto.keys.removeAll { it.id == photoId }
        }
    }

    fun clear() {
        synchronized(textByPhoto) { textByPhoto.clear() }
    }

    private suspend fun recognizePhoto(photo: ReportPhoto): String {
        val bitmap = photoStore.decodeForModel(photo, OCR_MAX_LONG_SIDE)
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image).await().text
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val OCR_MAX_LONG_SIDE = 2_048
    }
}

internal fun parsePlateObservationsByPhoto(
    recognizedTexts: List<Pair<String, String>>,
): List<RecognitionPlateObservation> = buildList {
    recognizedTexts.forEach { (photoId, text) ->
        PlateCandidateParser.parse(text).forEach { value ->
            add(RecognitionPlateObservation(photoId = photoId, value = value))
        }
    }
}
