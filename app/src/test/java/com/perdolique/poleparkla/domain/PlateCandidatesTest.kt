package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.NormalizedPhotoRect
import com.perdolique.poleparkla.model.PlateObservation
import com.perdolique.poleparkla.model.RecognitionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlateCandidatesTest {
    @Test
    fun `same normalized plate on three photos becomes one aggregate`() {
        val candidates = aggregatePlateCandidates(
            listOf(
                observation("one", "photo-1", RecognitionSource.ML_KIT_OCR, "003PUK"),
                observation("two", "photo-2", RecognitionSource.LOCAL_PLATE_MODEL, "003 PUK"),
                observation("three", "photo-3", RecognitionSource.LOCAL_PLATE_MODEL, "003-PUK"),
            ),
        )

        assertEquals(1, candidates.size)
        assertEquals("003 PUK", candidates.single().value)
        assertEquals(3, candidates.single().supportingPhotoCount)
    }

    @Test
    fun `variant on two photos ranks above a different one on one photo`() {
        val candidates = aggregatePlateCandidates(
            listOf(
                observation("one", "photo-1", RecognitionSource.ML_KIT_OCR, "003 PUK"),
                observation("two", "photo-2", RecognitionSource.ML_KIT_OCR, "003 PUK"),
                observation("three", "photo-3", RecognitionSource.LOCAL_PLATE_MODEL, "003 PUX"),
            ),
        )

        assertEquals(listOf("003 PUK", "003 PUX"), candidates.map { it.value })
    }

    @Test
    fun `matching OCR and local model results share one UI candidate`() {
        val candidate = aggregatePlateCandidates(
            listOf(
                observation("ocr", "photo-1", RecognitionSource.ML_KIT_OCR, "003 PUK"),
                observation("local", "photo-1", RecognitionSource.LOCAL_PLATE_MODEL, "003PUK"),
            ),
        ).single()

        assertEquals(1, candidate.supportingPhotoCount)
        assertEquals(setOf(RecognitionSource.ML_KIT_OCR, RecognitionSource.LOCAL_PLATE_MODEL), candidate.sources)
    }

    @Test
    fun `similar but different values are never synthesized or merged`() {
        val candidates = aggregatePlateCandidates(
            listOf(
                observation("one", "photo-1", RecognitionSource.LOCAL_PLATE_MODEL, "003 PUK"),
                observation("two", "photo-2", RecognitionSource.ML_KIT_OCR, "003 PUX"),
            ),
        )

        assertEquals(2, candidates.size)
        assertNotEquals(candidates[0].value, candidates[1].value)
    }

    @Test
    fun `selected candidate retains its crops and strongest evidence first`() {
        val small = observation(
            "small",
            "photo-1",
            RecognitionSource.LOCAL_PLATE_MODEL,
            "003 PUK",
            area = 0.01f,
        )
        val large = observation(
            "large",
            "photo-2",
            RecognitionSource.LOCAL_PLATE_MODEL,
            "003 PUK",
            area = 0.04f,
        )
        val candidate = aggregatePlateCandidates(listOf(small, large)).single()

        assertEquals(listOf("large", "small"), candidate.observations.map { it.id })
        assertEquals(listOf("photo-2", "photo-1"), candidate.observations.map { it.photoId })
    }

    @Test
    fun `exact candidate follows a changed confirmed plate`() {
        val observations = listOf(
            observation("old", "photo-1", RecognitionSource.LOCAL_PLATE_MODEL, "003 PUK"),
            observation("new", "photo-2", RecognitionSource.ML_KIT_OCR, "999 XYZ"),
        )

        val candidate = exactPlateCandidate("999 xyz", observations)

        assertEquals("999 XYZ", candidate?.value)
        assertEquals("photo-2", candidate?.observations?.single()?.photoId)
    }

    @Test
    fun `manual plate without observation has no evidence candidate`() {
        val observations = listOf(
            observation("other", "photo-1", RecognitionSource.LOCAL_PLATE_MODEL, "003 PUK"),
        )

        assertNull(exactPlateCandidate("999 XYZ", observations))
    }

    private fun observation(
        id: String,
        photoId: String,
        source: RecognitionSource,
        value: String,
        area: Float = 0.02f,
    ) = PlateObservation(
        id = id,
        reportId = "report",
        photoId = photoId,
        source = source,
        value = value,
        bounds = NormalizedPhotoRect(0.1f, 0.2f, 0.4f, 0.3f),
        detectionConfidence = 0.8f,
        characterConfidence = 0.9f,
        relativeArea = area,
    )
}
