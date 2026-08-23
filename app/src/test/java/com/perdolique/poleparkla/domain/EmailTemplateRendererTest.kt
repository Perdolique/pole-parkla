package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.PhotoSource
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReportPhoto
import com.perdolique.poleparkla.model.ReportStatus
import com.perdolique.poleparkla.model.ReporterProfile
import com.perdolique.poleparkla.model.ViolationType
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailTemplateRendererTest {
    private val renderer = EmailTemplateRenderer(ZoneId.of("Europe/Tallinn"))
    private val profile = ReporterProfile("Pier Dolique", "+37256789012")

    @Test
    fun `renders one photo and omits missing vehicle data cleanly`() {
        val letter = renderer.render(
            report = report(vehicleMake = "", vehicleModel = "", photoCount = 1),
            profile = profile,
            violationDescription = ViolationTemplates.CYCLE_PATH_DESCRIPTION,
        )

        assertTrue(letter.body.contains("Sõiduk: registreerimisnumber 003 PUK"))
        assertFalse(letter.body.contains("Sõiduk: ,"))
        assertTrue(letter.body.contains("Foto on kirjale lisatud."))
        assertTrue(letter.body.contains("Pier Dolique"))
        assertTrue(letter.body.contains("Telefon: +37256789012"))
        assertFalse(letter.body.contains("E-post:"))
    }

    @Test
    fun `renders make model coordinates and plural photos`() {
        val letter = renderer.render(
            report = report(vehicleMake = "Toyota", vehicleModel = "Corolla", photoCount = 3),
            profile = profile,
            violationDescription = ViolationTemplates.PEDESTRIAN_PATH_DESCRIPTION,
        )

        assertTrue(letter.body.contains("Toyota Corolla, registreerimisnumber 003 PUK"))
        assertTrue(letter.body.contains("59.437000, 24.753600"))
        assertFalse(letter.body.contains("±"))
        assertTrue(letter.body.contains("Fotod on kirjale lisatud."))
    }

    @Test
    fun `uses coordinates when address is missing`() {
        val base = report(vehicleMake = "", vehicleModel = "", photoCount = 1)
        val letter = renderer.render(
            report = base.copy(address = ""),
            profile = profile,
            violationDescription = "Sõiduk blokeerib läbipääsu.",
        )

        assertTrue(letter.subject.endsWith("59.437000, 24.753600"))
        assertFalse(letter.body.contains("Asukoht:"))
        assertTrue(letter.body.contains("Olukorra kirjeldus: Sõiduk blokeerib läbipääsu."))
    }

    private fun report(
        vehicleMake: String,
        vehicleModel: String,
        photoCount: Int,
    ): Report = Report(
        id = "report",
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
        occurredAtEpochMillis = Instant.parse("2026-08-13T13:58:00Z").toEpochMilli(),
        status = ReportStatus.DRAFT,
        plate = "003 PUK",
        vehicleMake = vehicleMake,
        vehicleModel = vehicleModel,
        violationType = ViolationType.CYCLE_PATH,
        customTemplateId = null,
        recipient = "mupo@example.com",
        address = "Lastekodu tn 42, Tallinn",
        latitude = 59.437,
        longitude = 24.7536,
        accuracyMeters = 8.8f,
        locationNeedsReview = false,
        subject = "",
        body = "",
        plateManuallyEdited = false,
        vehicleManuallyEdited = false,
        vehicleConfirmed = true,
        locationConfirmed = true,
        plateObservations = emptyList(),
        suggestedViolationType = null,
        mailOpenedAtEpochMillis = null,
        photos = List(photoCount) { index ->
            ReportPhoto(
                id = "photo-$index",
                reportId = "report",
                filePath = "/tmp/photo-$index.jpg",
                source = PhotoSource.CAMERA,
                capturedAtEpochMillis = index.toLong(),
                isPrimary = index == 0,
            )
        },
    )
}
