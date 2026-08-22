package com.perdolique.poleparkla.service

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.perdolique.poleparkla.domain.PlateCandidateParser
import com.perdolique.poleparkla.model.RecognitionResult
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
        val text = buildString {
            photosWithKeys.forEach { (photo, key) ->
                val cached = synchronized(textByPhoto) { textByPhoto[key] }
                val recognized = cached ?: recognizePhoto(photo).also { value ->
                    synchronized(textByPhoto) { textByPhoto[key] = value }
                }
                appendLine(recognized)
            }
        }
        return RecognitionResult(
            source = RecognitionSource.ML_KIT_OCR,
            plateCandidates = PlateCandidateParser.parse(text),
        )
    }

    fun invalidate(photoId: String) {
        synchronized(textByPhoto) {
            textByPhoto.keys.removeAll { it.id == photoId }
        }
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
