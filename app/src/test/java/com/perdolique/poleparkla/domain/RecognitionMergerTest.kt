package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.PlateObservation
import com.perdolique.poleparkla.model.RecognitionResult
import com.perdolique.poleparkla.model.RecognitionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecognitionMergerTest {
    @Test
    fun `recognition fills untouched fields without confirming them`() {
        val report = sampleReport(locationConfirmed = false).copy(
            plateObservations = listOf(observation("ocr", RecognitionSource.ML_KIT_OCR, "003 PUK")),
        )
        val merged = RecognitionMerger.merge(
            report,
            RecognitionResult(
                source = RecognitionSource.ML_KIT_OCR,
                plateCandidates = listOf("003 PUK"),
                vehicleMake = "Toyota",
                vehicleModel = "Corolla",
            ),
        )

        assertEquals("003 PUK", merged.plate)
        assertEquals("Toyota", merged.vehicleMake)
        assertEquals("Corolla", merged.vehicleModel)
        assertFalse(merged.vehicleConfirmed)
        assertFalse(merged.locationConfirmed)
    }

    @Test
    fun `recognition never overwrites confirmed or manually edited fields`() {
        val report = sampleReport(
            plate = "USER 1",
            plateManuallyEdited = true,
            vehicleMake = "My make",
            vehicleModel = "My model",
            vehicleManuallyEdited = true,
            vehicleConfirmed = true,
        ).copy(
            plateObservations = listOf(observation("cloud", RecognitionSource.OPENAI, "003 PUK")),
        )

        val merged = RecognitionMerger.merge(
            report,
            RecognitionResult(
                source = RecognitionSource.OPENAI,
                plateCandidates = listOf("003 PUK"),
                vehicleMake = "Toyota",
                vehicleModel = "Corolla",
            ),
        )

        assertEquals("USER 1", merged.plate)
        assertEquals("My make", merged.vehicleMake)
        assertEquals("My model", merged.vehicleModel)
        assertEquals("003 PUK", aggregatePlateCandidates(merged.plateObservations).single().value)
    }

    @Test
    fun `best aggregate becomes the automatic plate`() {
        val report = sampleReport(plate = "", vehicleConfirmed = false).copy(
            plateObservations = listOf(
                observation("one-local", RecognitionSource.LOCAL_PLATE_MODEL, "003 PUK", "photo-1"),
                observation("two-local", RecognitionSource.LOCAL_PLATE_MODEL, "003 PUK", "photo-2"),
                observation("cloud", RecognitionSource.OPENAI, "999 XYZ", "photo-1"),
            ),
        )

        val merged = RecognitionMerger.merge(
            report,
            RecognitionResult(RecognitionSource.LOCAL_PLATE_MODEL, listOf("003 PUK")),
        )

        assertEquals("003 PUK", merged.plate)
    }

    @Test
    fun `clearing recognition removes derived values but preserves manual edits`() {
        val automatic = sampleReport(
            plate = "003 PUK",
            vehicleMake = "Toyota",
            vehicleModel = "Corolla",
            vehicleConfirmed = false,
        ).copy(
            plateObservations = listOf(observation("ocr", RecognitionSource.ML_KIT_OCR, "003 PUK")),
        )
        val manual = automatic.copy(
            plate = "USER 1",
            plateManuallyEdited = true,
            vehicleMake = "My make",
            vehicleModel = "My model",
            vehicleManuallyEdited = true,
        )

        val clearedAutomatic = RecognitionMerger.clearAutomatic(automatic)
        val clearedManual = RecognitionMerger.clearAutomatic(manual)

        assertEquals("", clearedAutomatic.plate)
        assertEquals("", clearedAutomatic.vehicleMake)
        assertEquals(emptyList<PlateObservation>(), clearedAutomatic.plateObservations)
        assertEquals("USER 1", clearedManual.plate)
        assertEquals("My make", clearedManual.vehicleMake)
        assertEquals("My model", clearedManual.vehicleModel)
    }

    private fun observation(
        id: String,
        source: RecognitionSource,
        value: String,
        photoId: String = "photo",
    ) = PlateObservation(
        id = id,
        reportId = "report",
        photoId = photoId,
        source = source,
        value = value,
    )
}
