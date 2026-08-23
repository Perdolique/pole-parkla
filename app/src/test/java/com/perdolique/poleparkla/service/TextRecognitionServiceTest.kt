package com.perdolique.poleparkla.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TextRecognitionServiceTest {
    @Test
    fun `OCR parses each photo independently and keeps provenance`() {
        val observations = parsePlateObservationsByPhoto(
            listOf(
                "photo-1" to "Vehicle 003 PUK",
                "photo-2" to "Vehicle 003 PUK and 999 XYZ",
                "photo-3" to "Vehicle 999 XYZ",
            ),
        )

        assertEquals(
            listOf(
                "photo-1" to "003 PUK",
                "photo-2" to "003 PUK",
                "photo-2" to "999 XYZ",
                "photo-3" to "999 XYZ",
            ),
            observations.map { it.photoId to it.value },
        )
    }
}
