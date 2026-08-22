package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.model.ViolationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReportTransitionsTest {
    private val profile = ReporterProfile("Mari", "+372 5555")

    @Test
    fun `moves from draft to ready and then handed off`() {
        val draft = sampleReport(plate = "123 ABC", violationType = ViolationType.CYCLE_PATH)
        val ready = ReportTransitions.refreshReadiness(
            draft,
            profile,
            ViolationTemplates.CYCLE_PATH_DESCRIPTION,
        )
        val handedOff = ReportTransitions.markHandedOff(ready, nowEpochMillis = 42)

        assertEquals(ReportStatus.READY, ready.status)
        assertEquals(ReportStatus.HANDED_OFF_TO_MAIL, handedOff.status)
        assertEquals(42L, handedOff.mailOpenedAtEpochMillis)
        assertFalse(ReportStatus.entries.any { it.name == "SENT" })
    }

    @Test
    fun `repeated handoff preserves the original open time`() {
        val handedOff = sampleReport(
            status = ReportStatus.HANDED_OFF_TO_MAIL,
        ).copy(mailOpenedAtEpochMillis = 42L)

        assertEquals(
            handedOff,
            ReportTransitions.markHandedOff(handedOff, nowEpochMillis = 99L),
        )
    }

    @Test
    fun `missing recipient remains a draft`() {
        val report = sampleReport(
            plate = "123 ABC",
            violationType = ViolationType.CYCLE_PATH,
            recipient = "",
        )

        assertEquals(
            ReportStatus.DRAFT,
            ReportTransitions.refreshReadiness(
                report,
                profile,
                ViolationTemplates.CYCLE_PATH_DESCRIPTION,
            ).status,
        )
    }

    @Test
    fun `location requiring review remains a draft`() {
        val report = sampleReport(
            plate = "123 ABC",
            violationType = ViolationType.CYCLE_PATH,
            locationNeedsReview = true,
        )

        assertEquals(
            ReportStatus.DRAFT,
            ReportTransitions.refreshReadiness(
                report,
                profile,
                ViolationTemplates.CYCLE_PATH_DESCRIPTION,
            ).status,
        )
    }

    @Test
    fun `empty letter remains a draft`() {
        val report = sampleReport(
            plate = "123 ABC",
            violationType = ViolationType.CYCLE_PATH,
            subject = "",
            body = "",
        )

        assertEquals(
            ReportStatus.DRAFT,
            ReportTransitions.refreshReadiness(
                report,
                profile,
                ViolationTemplates.CYCLE_PATH_DESCRIPTION,
            ).status,
        )
    }
}
