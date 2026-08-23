package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.LocationSnapshot
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressResolutionFreshnessTest {
    private val location = LocationSnapshot(59.437, 24.7536, 8.5f, 1_000L)

    @Test
    fun `applies while location and address are unchanged`() {
        assertTrue(
            AddressResolutionFreshness.canApply(
                report = report().copy(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracyMeters,
                    address = "",
                ),
                requestedLocation = location,
                addressBeforeLookup = "",
            ),
        )
    }

    @Test
    fun `rejects a result after manual address edit`() {
        assertFalse(
            AddressResolutionFreshness.canApply(
                report = report().copy(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracyMeters,
                    address = "Manual address",
                ),
                requestedLocation = location,
                addressBeforeLookup = "",
            ),
        )
    }

    @Test
    fun `rejects a result after location edit`() {
        assertFalse(
            AddressResolutionFreshness.canApply(
                report = report().copy(
                    latitude = 58.0,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracyMeters,
                    address = "",
                ),
                requestedLocation = location,
                addressBeforeLookup = "",
            ),
        )
    }

    private fun report() = Report(
        id = "report",
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
        occurredAtEpochMillis = 0L,
        status = ReportStatus.DRAFT,
        plate = "",
        vehicleMake = "",
        vehicleModel = "",
        violationType = null,
        customTemplateId = null,
        recipient = "",
        address = "",
        latitude = null,
        longitude = null,
        accuracyMeters = null,
        locationNeedsReview = true,
        subject = "",
        body = "",
        plateManuallyEdited = false,
        vehicleManuallyEdited = false,
        vehicleConfirmed = false,
        locationConfirmed = false,
        plateObservations = emptyList(),
        suggestedViolationType = null,
        mailOpenedAtEpochMillis = null,
        photos = emptyList(),
    )
}
