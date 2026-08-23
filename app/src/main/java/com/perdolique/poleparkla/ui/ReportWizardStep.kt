package com.perdolique.poleparkla.ui

import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportStatus

enum class ReportWizardStep {
    VEHICLE,
    LOCATION,
    PROBLEM,
    SUMMARY,
}

fun Report.resolveWizardStep(): ReportWizardStep {
    if (status == ReportStatus.READY || status == ReportStatus.HANDED_OFF_TO_MAIL) {
        return ReportWizardStep.SUMMARY
    }
    return when {
        plate.isBlank() || !vehicleConfirmed -> ReportWizardStep.VEHICLE
        !hasValidLocation() || !locationConfirmed -> ReportWizardStep.LOCATION
        violationType == null -> ReportWizardStep.PROBLEM
        else -> ReportWizardStep.SUMMARY
    }
}
