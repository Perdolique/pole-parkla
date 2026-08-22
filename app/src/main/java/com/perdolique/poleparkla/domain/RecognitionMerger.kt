package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.PlateSuggestion
import com.perdolique.poleparkla.model.RecognitionResult
import com.perdolique.poleparkla.model.Report

object RecognitionMerger {
    fun merge(report: Report, result: RecognitionResult): Report {
        val otherSuggestions = report.plateSuggestions.filterNot { it.source == result.source }
        val newSuggestions = result.plateCandidates.map { PlateSuggestion(result.source, it) }
        val mergedSuggestions = (otherSuggestions + newSuggestions).distinctBy { it.source to it.value }
        val recognizedPlate = result.plateCandidates.firstOrNull()
            ?: mergedSuggestions.firstOrNull()?.value

        return report.copy(
            plate = if (!report.plateManuallyEdited) {
                recognizedPlate.orEmpty()
            } else {
                report.plate
            },
            vehicleMake = if (!report.vehicleManuallyEdited && !result.vehicleMake.isNullOrBlank()) {
                result.vehicleMake
            } else {
                report.vehicleMake
            },
            vehicleModel = if (!report.vehicleManuallyEdited && !result.vehicleModel.isNullOrBlank()) {
                result.vehicleModel
            } else {
                report.vehicleModel
            },
            plateSuggestions = mergedSuggestions,
            suggestedViolationType = result.suggestedViolationType ?: report.suggestedViolationType,
        )
    }

    fun clearAutomatic(report: Report): Report = report.copy(
        plate = if (report.plateManuallyEdited) report.plate else "",
        vehicleMake = if (report.vehicleManuallyEdited) report.vehicleMake else "",
        vehicleModel = if (report.vehicleManuallyEdited) report.vehicleModel else "",
        plateSuggestions = emptyList(),
        suggestedViolationType = null,
    )
}
