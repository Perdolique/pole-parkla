package com.perdolique.poleparkla.domain

import com.perdolique.poleparkla.model.LetterDraft
import com.perdolique.poleparkla.model.Report
import com.perdolique.poleparkla.model.ReporterProfile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class EmailTemplateRenderer(
    private val zoneId: ZoneId = ZoneId.of("Europe/Tallinn"),
) {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun render(
        report: Report,
        profile: ReporterProfile,
        violationDescription: String,
    ): LetterDraft {
        val coordinates = coordinates(report)
        val locationForSubject = report.address.ifBlank { coordinates }
        val vehiclePrefix = listOf(report.vehicleMake, report.vehicleModel)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .takeIf(String::isNotBlank)
            ?.plus(", ")
            .orEmpty()
        val photoLabel = if (report.photos.size == 1) "Foto" else "Fotod"
        val occurredAt = Instant.ofEpochMilli(report.occurredAtEpochMillis)
            .atZone(zoneId)
            .format(dateTimeFormatter)

        val details = buildList {
            add("Olukorra kirjeldus: $violationDescription")
            add("Sõiduk: ${vehiclePrefix}registreerimisnumber ${report.plate.trim()}")
            report.address.trim().takeIf(String::isNotBlank)?.let { add("Asukoht: $it") }
            coordinates.takeIf(String::isNotBlank)?.let { add("Koordinaadid: $it") }
            add("Aeg: $occurredAt")
        }.joinToString("\n")

        return LetterDraft(
            subject = "Teade valesti pargitud sõidukist – ${report.plate.trim()}, $locationForSubject",
            body = """
                Tere

                Soovin teatada valesti pargitud sõidukist.

                $details

                $photoLabel on kirjale lisatud.

                Lugupidamisega
                ${profile.name.trim()}
                Telefon: ${profile.phone.trim()}
            """.trimIndent(),
        )
    }

    private fun coordinates(report: Report): String {
        val latitude = report.latitude ?: return ""
        val longitude = report.longitude ?: return ""
        return String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    }
}
