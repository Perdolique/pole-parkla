package com.perdolique.poleparkla.ui

import com.perdolique.poleparkla.domain.sampleReport
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ViolationType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportWizardStepTest {
    @Test
    fun `vehicle is first when plate is missing or unconfirmed`() {
        assertEquals(
            ReportWizardStep.VEHICLE,
            sampleReport(plate = "", vehicleConfirmed = false).resolveWizardStep(),
        )
        assertEquals(
            ReportWizardStep.VEHICLE,
            sampleReport(plate = "003 PUK", vehicleConfirmed = false).resolveWizardStep(),
        )
    }

    @Test
    fun `location follows a confirmed vehicle when place is invalid or unconfirmed`() {
        val vehicle = sampleReport(plate = "003 PUK", vehicleConfirmed = true)

        assertEquals(
            ReportWizardStep.LOCATION,
            vehicle.copy(locationConfirmed = false).resolveWizardStep(),
        )
        assertEquals(
            ReportWizardStep.LOCATION,
            vehicle.copy(address = "", latitude = null, longitude = null).resolveWizardStep(),
        )
    }

    @Test
    fun `problem follows confirmed vehicle and location`() {
        val report = sampleReport(
            plate = "003 PUK",
            vehicleConfirmed = true,
            locationConfirmed = true,
            violationType = null,
        )

        assertEquals(ReportWizardStep.PROBLEM, report.resolveWizardStep())
    }

    @Test
    fun `complete ready and handed off reports resolve to summary`() {
        val complete = sampleReport(
            plate = "003 PUK",
            vehicleConfirmed = true,
            locationConfirmed = true,
            violationType = ViolationType.CYCLE_PATH,
        )

        assertEquals(ReportWizardStep.SUMMARY, complete.resolveWizardStep())
        assertEquals(
            ReportWizardStep.SUMMARY,
            complete.copy(status = ReportStatus.READY).resolveWizardStep(),
        )
        assertEquals(
            ReportWizardStep.SUMMARY,
            complete.copy(status = ReportStatus.HANDED_OFF_TO_MAIL).resolveWizardStep(),
        )
    }
}
