package com.perdolique.poleparkla.ui

import com.perdolique.poleparkla.domain.sampleReport
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.model.ViolationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCompletionTest {
    private val profile = ReporterProfile("Mari", "+372 5555")
    private val description = "Sõiduk on pargitud jalgrattateele."

    @Test
    fun requestsPhotosBeforeOtherMissingFields() {
        val completion = sampleReport(
            plate = "",
            violationType = null,
            address = "",
            latitude = null,
            longitude = null,
        ).copy(photos = emptyList()).completion(profile, null)

        assertEquals(ReportCompletionStep.PHOTOS, completion.nextStep)
        assertEquals(0, completion.completedCoreSteps)
        assertFalse(completion.ready)
    }

    @Test
    fun requestsCoreFieldsInReportOrder() {
        val base = sampleReport(
            plate = "",
            violationType = ViolationType.CYCLE_PATH,
            address = "Tartu mnt 24",
        )

        assertEquals(ReportCompletionStep.VEHICLE, base.completion(profile, description).nextStep)
        assertEquals(
            ReportCompletionStep.VIOLATION,
            base.copy(plate = "123 ABC", violationType = null).completion(profile, null).nextStep,
        )
        assertEquals(
            ReportCompletionStep.LOCATION,
            base.copy(plate = "123 ABC", address = "", latitude = null, longitude = null)
                .completion(profile, description)
                .nextStep,
        )
    }

    @Test
    fun acceptsCoordinatesWithoutAddress() {
        val completion = sampleReport(
            plate = "123 ABC",
            violationType = ViolationType.CYCLE_PATH,
            address = "",
            latitude = 59.437,
            longitude = 24.753,
        ).completion(profile, description)

        assertNull(completion.nextStep)
        assertEquals(3, completion.completedCoreSteps)
        assertTrue(completion.ready)
    }

    @Test
    fun keepsDeliveryAsFinalRequirement() {
        val completion = sampleReport(
            plate = "123 ABC",
            violationType = ViolationType.CYCLE_PATH,
            recipient = "",
        ).completion(profile, description)

        assertEquals(ReportCompletionStep.DELIVERY, completion.nextStep)
        assertFalse(completion.ready)
    }
}
