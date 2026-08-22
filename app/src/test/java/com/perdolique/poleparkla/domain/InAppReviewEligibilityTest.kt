package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.ReportStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppReviewEligibilityTest {
    @Test
    fun `requires a completed mail handoff`() {
        assertFalse(InAppReviewEligibility.afterMailReturn(null))
        assertFalse(
            InAppReviewEligibility.afterMailReturn(
                sampleReport(status = ReportStatus.DRAFT).copy(mailOpenedAtEpochMillis = 42L),
            ),
        )
        assertFalse(
            InAppReviewEligibility.afterMailReturn(
                sampleReport(status = ReportStatus.READY).copy(mailOpenedAtEpochMillis = 42L),
            ),
        )
        assertFalse(
            InAppReviewEligibility.afterMailReturn(
                sampleReport(status = ReportStatus.HANDED_OFF_TO_MAIL),
            ),
        )
        assertTrue(
            InAppReviewEligibility.afterMailReturn(
                sampleReport(status = ReportStatus.HANDED_OFF_TO_MAIL)
                    .copy(mailOpenedAtEpochMillis = 42L),
            ),
        )
    }
}
