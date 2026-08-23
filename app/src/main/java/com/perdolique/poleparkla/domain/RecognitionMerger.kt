package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.RecognitionResult
import com.perdolique.poleparkla.model.Report

object RecognitionMerger {
    fun merge(report: Report, result: RecognitionResult): Report {
        val recognizedPlate = aggregatePlateCandidates(report.plateObservations).firstOrNull()?.value

        return report.copy(
            plate = if (!report.plateManuallyEdited && !report.vehicleConfirmed) {
                recognizedPlate.orEmpty()
            } else {
                report.plate
            },
            vehicleMake = if (
                !report.vehicleManuallyEdited &&
                !report.vehicleConfirmed &&
                !result.vehicleMake.isNullOrBlank()
            ) {
                result.vehicleMake
            } else {
                report.vehicleMake
            },
            vehicleModel = if (
                !report.vehicleManuallyEdited &&
                !report.vehicleConfirmed &&
                !result.vehicleModel.isNullOrBlank()
            ) {
                result.vehicleModel
            } else {
                report.vehicleModel
            },
            suggestedViolationType = result.suggestedViolationType ?: report.suggestedViolationType,
        )
    }

    fun clearAutomatic(report: Report): Report = report.copy(
        plate = if (report.plateManuallyEdited) report.plate else "",
        vehicleMake = if (report.vehicleManuallyEdited) report.vehicleMake else "",
        vehicleModel = if (report.vehicleManuallyEdited) report.vehicleModel else "",
        plateObservations = emptyList(),
        suggestedViolationType = null,
    )
}
