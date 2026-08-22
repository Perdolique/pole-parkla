package com.perdolique.poleparkla.ui

import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReporterProfile

enum class ReportCompletionStep {
    PHOTOS,
    VEHICLE,
    VIOLATION,
    LOCATION,
    DELIVERY,
}

data class ReportCompletion(
    val completedCoreSteps: Int,
    val totalCoreSteps: Int,
    val nextStep: ReportCompletionStep?,
    val ready: Boolean,
)

fun Report.completion(
    profile: ReporterProfile,
    violationDescription: String?,
): ReportCompletion {
    val hasVehicle = plate.isNotBlank()
    val hasViolation = !violationDescription.isNullOrBlank()
    val hasLocation = !locationNeedsReview &&
        (address.isNotBlank() || (latitude != null && longitude != null))
    val hasDelivery = recipient.isNotBlank() &&
        subject.isNotBlank() &&
        body.isNotBlank() &&
        profile.name.isNotBlank() &&
        profile.phone.isNotBlank()
    val nextStep = when {
        photos.isEmpty() -> ReportCompletionStep.PHOTOS
        !hasVehicle -> ReportCompletionStep.VEHICLE
        !hasViolation -> ReportCompletionStep.VIOLATION
        !hasLocation -> ReportCompletionStep.LOCATION
        !hasDelivery -> ReportCompletionStep.DELIVERY
        else -> null
    }
    return ReportCompletion(
        completedCoreSteps = listOf(hasVehicle, hasViolation, hasLocation).count { it },
        totalCoreSteps = 3,
        nextStep = nextStep,
        ready = nextStep == null,
    )
}
