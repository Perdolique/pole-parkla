package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ViolationType

fun sampleReport(
    status: ReportStatus = ReportStatus.DRAFT,
    plate: String = "",
    plateManuallyEdited: Boolean = false,
    vehicleMake: String = "",
    vehicleModel: String = "",
    vehicleManuallyEdited: Boolean = false,
    violationType: ViolationType? = null,
    recipient: String = "mupo@example.com",
    address: String = "Lastekodu tn 42, Tallinn",
    latitude: Double? = 59.437,
    longitude: Double? = 24.7536,
    locationNeedsReview: Boolean = false,
    vehicleConfirmed: Boolean = plate.isNotBlank(),
    locationConfirmed: Boolean = true,
    subject: String = "Teade valesti pargitud sõidukist",
    body: String = "Tere\n\nSoovin teatada valesti pargitud sõidukist.",
): Report = Report(
    id = "report",
    createdAtEpochMillis = 1,
    updatedAtEpochMillis = 1,
    occurredAtEpochMillis = 1_786_629_480_000,
    status = status,
    plate = plate,
    vehicleMake = vehicleMake,
    vehicleModel = vehicleModel,
    violationType = violationType,
    customTemplateId = null,
    recipient = recipient,
    address = address,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = 8f,
    locationNeedsReview = locationNeedsReview,
    subject = subject,
    body = body,
    plateManuallyEdited = plateManuallyEdited,
    vehicleManuallyEdited = vehicleManuallyEdited,
    vehicleConfirmed = vehicleConfirmed,
    locationConfirmed = locationConfirmed,
    plateObservations = emptyList(),
    suggestedViolationType = null,
    mailOpenedAtEpochMillis = null,
    photos = listOf(
        ReportPhoto(
            id = "photo",
            reportId = "report",
            filePath = "/tmp/photo.jpg",
            source = PhotoSource.CAMERA,
            capturedAtEpochMillis = 1,
            isPrimary = true,
        ),
    ),
)
