package com.perdolique.poleparkla.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateModelProcessingTest {
    @Test
    fun `letterbox keeps source aspect ratio and centers padding`() {
        val transform = letterboxTransform(imageWidth = 964, imageHeight = 1280)

        assertEquals(512, transform.scaledHeight)
        assertEquals(386, transform.scaledWidth)
        assertEquals(63, transform.left)
        assertEquals(0, transform.top)
    }

    @Test
    fun `detector output is mapped back to source coordinates`() {
        val transform = LetterboxTransform(
            imageWidth = 640,
            imageHeight = 480,
            scale = 0.5f,
            scaledWidth = 320,
            scaledHeight = 240,
            paddingX = 0f,
            paddingY = 40f,
            left = 0,
            top = 40,
        )

        val detections = decodePlateDetections(
            output = floatArrayOf(0f, 50f, 60f, 200f, 220f, 0f, 0.9f),
            transform = transform,
            confidenceThreshold = 0.5f,
        )

        assertEquals(1, detections.size)
        assertEquals(100, detections.single().left)
        assertEquals(40, detections.single().top)
        assertEquals(400, detections.single().right)
        assertEquals(360, detections.single().bottom)
    }

    @Test
    fun `plate output decodes characters and removes trailing padding`() {
        val output = FloatArray(PLATE_OCR_MAX_SLOTS * PLATE_OCR_ALPHABET.length)
        "003OOO____".forEachIndexed { slot, char ->
            output[(slot * PLATE_OCR_ALPHABET.length) + PLATE_OCR_ALPHABET.indexOf(char)] = 1f
        }

        val plate = requireNotNull(decodePlate(output))

        assertEquals("003OOO", plate.value)
        assertEquals(1f, plate.meanCharacterConfidence)
    }

    @Test
    fun `candidate seen across photos outranks a larger one-off plate`() {
        val observations = listOf(
            observation(photoId = "one", value = "003 OOO", relativeArea = 0.01f),
            observation(photoId = "two", value = "003 OOO", relativeArea = 0.02f),
            observation(photoId = "one", value = "999 XYZ", relativeArea = 0.05f),
        )

        assertEquals(listOf("003 OOO", "999 XYZ"), rankPlateCandidates(observations))
    }

    @Test
    fun `detector rejects rows below its confidence threshold`() {
        val transform = letterboxTransform(512, 512)

        val detections = decodePlateDetections(
            output = floatArrayOf(0f, 1f, 1f, 10f, 10f, 0f, 0.24f),
            transform = transform,
        )

        assertTrue(detections.isEmpty())
    }

    private fun observation(
        photoId: String,
        value: String,
        relativeArea: Float,
    ) = PlateObservation(
        photoId = photoId,
        value = value,
        detectionConfidence = 0.8f,
        characterConfidence = 0.9f,
        relativeArea = relativeArea,
    )
}
