package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ReporterProfile

object ReportTransitions {
    fun refreshReadiness(
        report: Report,
        profile: ReporterProfile,
        violationDescription: String?,
    ): Report {
        if (report.status == ReportStatus.HANDED_OFF_TO_MAIL) return report
        val status = if (report.isReady(profile, violationDescription)) {
            ReportStatus.READY
        } else {
            ReportStatus.DRAFT
        }
        return report.copy(status = status)
    }

    fun markHandedOff(report: Report, nowEpochMillis: Long = System.currentTimeMillis()): Report {
        if (report.status == ReportStatus.HANDED_OFF_TO_MAIL) return report
        require(report.status == ReportStatus.READY)
        return report.copy(
            status = ReportStatus.HANDED_OFF_TO_MAIL,
            mailOpenedAtEpochMillis = nowEpochMillis,
        )
    }
}
