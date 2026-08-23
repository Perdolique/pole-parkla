package com.perdolique.poleparkla.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

data class ParsedReportLocation(
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val occurredAtEpochMillis: Long,
)

enum class ReportLocationInputError {
    LOCATION,
    TIME,
    COORDINATES,
}

sealed interface ReportLocationInputResult {
    data class Valid(val location: ParsedReportLocation) : ReportLocationInputResult
    data class Invalid(val error: ReportLocationInputError) : ReportLocationInputResult
}

object ReportLocationInput {
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")
        .withResolverStyle(ResolverStyle.STRICT)

    fun validate(
        address: String,
        latitude: String,
        longitude: String,
        occurredAt: String,
        zoneId: ZoneId = ZoneId.of("Europe/Tallinn"),
    ): ReportLocationInputResult {
        val occurredAtEpochMillis = try {
            LocalDateTime.parse(occurredAt.trim(), formatter)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
            return ReportLocationInputResult.Invalid(ReportLocationInputError.TIME)
        }
        val normalizedLatitude = latitude.normalizeCoordinate()
        val normalizedLongitude = longitude.normalizeCoordinate()
        if (normalizedLatitude.isBlank() != normalizedLongitude.isBlank()) {
            return ReportLocationInputResult.Invalid(ReportLocationInputError.COORDINATES)
        }
        val parsedLatitude = if (normalizedLatitude.isBlank()) {
            null
        } else {
            parseLatitude(normalizedLatitude)
                ?: return ReportLocationInputResult.Invalid(ReportLocationInputError.COORDINATES)
        }
        val parsedLongitude = if (normalizedLongitude.isBlank()) {
            null
        } else {
            parseLongitude(normalizedLongitude)
                ?: return ReportLocationInputResult.Invalid(ReportLocationInputError.COORDINATES)
        }
        if (address.isBlank() && parsedLatitude == null && parsedLongitude == null) {
            return ReportLocationInputResult.Invalid(ReportLocationInputError.LOCATION)
        }
        return ReportLocationInputResult.Valid(
            ParsedReportLocation(
                address = address.trim(),
                latitude = parsedLatitude,
                longitude = parsedLongitude,
                occurredAtEpochMillis = occurredAtEpochMillis,
            ),
        )
    }

    fun format(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.of("Europe/Tallinn"),
    ): String = Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(formatter)

    fun parseLatitude(value: String): Double? =
        value.normalizeCoordinate().toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 }

    fun parseLongitude(value: String): Double? =
        value.normalizeCoordinate().toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }

    private fun String.normalizeCoordinate(): String = trim().replace(',', '.')
}
