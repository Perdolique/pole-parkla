package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.RecognitionResult
import com.perdolique.poleparkla.model.RecognitionSource
import com.perdolique.poleparkla.model.PlateSuggestion
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionMergerTest {
    @Test
    fun `recognition fills untouched empty fields`() {
        val merged = RecognitionMerger.merge(
            sampleReport(),
            RecognitionResult(
                source = RecognitionSource.ML_KIT_OCR,
                plateCandidates = listOf("123 ABC"),
                vehicleMake = "Toyota",
                vehicleModel = "Corolla",
            ),
        )

        assertEquals("123 ABC", merged.plate)
        assertEquals("Toyota", merged.vehicleMake)
        assertEquals("Corolla", merged.vehicleModel)
    }

    @Test
    fun `recognition never overwrites user edited fields`() {
        val merged = RecognitionMerger.merge(
            sampleReport(
                plate = "USER 1",
                plateManuallyEdited = true,
                vehicleMake = "My make",
                vehicleModel = "My model",
                vehicleManuallyEdited = true,
            ),
            RecognitionResult(
                source = RecognitionSource.OPENAI,
                plateCandidates = listOf("123 ABC"),
                vehicleMake = "Toyota",
                vehicleModel = "Corolla",
            ),
        )

        assertEquals("USER 1", merged.plate)
        assertEquals("My make", merged.vehicleMake)
        assertEquals("My model", merged.vehicleModel)
        assertEquals("123 ABC", merged.plateSuggestions.single().value)
    }

    @Test
    fun `rerunning a source replaces its stale suggestions and automatic plate`() {
        val report = sampleReport(plate = "OLD 1").copy(
            plateSuggestions = listOf(
                PlateSuggestion(RecognitionSource.ML_KIT_OCR, "OLD 1"),
                PlateSuggestion(RecognitionSource.OPENAI, "CLOUD 2"),
            ),
        )

        val merged = RecognitionMerger.merge(
            report,
            RecognitionResult(
                source = RecognitionSource.ML_KIT_OCR,
                plateCandidates = listOf("NEW 3"),
            ),
        )

        assertEquals("NEW 3", merged.plate)
        assertEquals(
            listOf(
                PlateSuggestion(RecognitionSource.OPENAI, "CLOUD 2"),
                PlateSuggestion(RecognitionSource.ML_KIT_OCR, "NEW 3"),
            ),
            merged.plateSuggestions,
        )
    }

    @Test
    fun `clearing recognition removes derived values but preserves manual edits`() {
        val automatic = sampleReport(
            plate = "123 ABC",
            vehicleMake = "Toyota",
            vehicleModel = "Corolla",
        ).copy(
            plateSuggestions = listOf(PlateSuggestion(RecognitionSource.ML_KIT_OCR, "123 ABC")),
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
        assertEquals(emptyList<PlateSuggestion>(), clearedAutomatic.plateSuggestions)
        assertEquals("USER 1", clearedManual.plate)
        assertEquals("My make", clearedManual.vehicleMake)
        assertEquals("My model", clearedManual.vehicleModel)
    }
}
