package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportStatus

object InAppReviewEligibility {
    fun afterMailReturn(report: Report?): Boolean =
        report?.status == ReportStatus.HANDED_OFF_TO_MAIL &&
            report.mailOpenedAtEpochMillis != null
}
